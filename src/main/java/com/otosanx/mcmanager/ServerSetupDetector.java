package com.otosanx.mcmanager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class ServerSetupDetector {
    private static final Pattern MEMORY_PATTERN = Pattern.compile("^-X(ms|mx)(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MC_VERSION_PATTERN = Pattern.compile("(1\\.\\d{1,2}(?:\\.\\d+)?)");
    private static final Set<String> SCRIPT_EXTENSIONS = Set.of(".bat", ".cmd", ".ps1", ".sh");
    private static final String DEFAULT_JVM_ARGS = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=100 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -Dfile.encoding=UTF-8";

    private ServerSetupDetector() {
    }

    public static DetectionResult detect(Path serverFolder, Path selectedJar) {
        DetectionAccumulator accumulator = new DetectionAccumulator(serverFolder, selectedJar);
        if (serverFolder == null || !Files.isDirectory(serverFolder)) {
            accumulator.messages.add("No existing server folder found for detection.");
            return accumulator.build();
        }

        Path primaryLaunch = LaunchFileService.detectPrimaryLaunchFile(serverFolder);
        if (primaryLaunch != null) {
            parseTextFile(accumulator, primaryLaunch, true);
            accumulator.messages.add("Using " + primaryLaunch.getFileName() + " as the primary launch script.");
        } else {
            parseLaunchScripts(accumulator, serverFolder);
        }
        parseKnownArgFiles(accumulator, serverFolder);
        inspectJarAndFolder(accumulator, serverFolder, selectedJar);
        finalizeDefaults(accumulator);
        return accumulator.build();
    }

    private static void parseKnownArgFiles(DetectionAccumulator accumulator, Path serverFolder) {
        List<Path> candidates = List.of(
                serverFolder.resolve("user_jvm_args.txt"),
                serverFolder.resolve("win_args.txt"),
                serverFolder.resolve("unix_args.txt")
        );

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                parseTextFile(accumulator, candidate, false);
            }
        }

        try {
            if (Files.isDirectory(serverFolder.resolve("libraries"))) {
                try (java.util.stream.Stream<Path> stream = Files.walk(serverFolder.resolve("libraries"), 6)) {
                    stream
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                            return name.equals("win_args.txt") || name.equals("unix_args.txt");
                        })
                        .sorted()
                        .forEach(path -> parseTextFile(accumulator, path, false));
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void parseLaunchScripts(DetectionAccumulator accumulator, Path serverFolder) {
        try (java.util.stream.Stream<Path> stream = Files.list(serverFolder)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(path -> hasScriptExtension(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> scriptPriority(path.getFileName().toString())))
                    .forEach(path -> parseTextFile(accumulator, path, true));
        } catch (IOException ignored) {
        }
    }

    private static void inspectJarAndFolder(DetectionAccumulator accumulator, Path serverFolder, Path selectedJar) {
        if (selectedJar != null) {
            accumulator.jarPath = relativizeIfPossible(serverFolder, selectedJar);
            accumulator.serverType = detectType(accumulator.serverType, selectedJar.getFileName().toString(), serverFolder);
            detectMinecraftVersion(accumulator, serverFolder, selectedJar);
        }

        if (accumulator.jarPath == null) {
            accumulator.jarPath = findCommonJar(serverFolder);
        }

        if (accumulator.jarPath != null) {
            accumulator.serverType = detectType(accumulator.serverType, accumulator.jarPath, serverFolder);
            detectMinecraftVersion(accumulator, serverFolder, serverFolder.resolve(accumulator.jarPath));
        } else {
            accumulator.serverType = detectType(accumulator.serverType, "", serverFolder);
        }

        if (accumulator.minecraftVersion == null) {
            detectMinecraftVersionFromFolder(accumulator, serverFolder);
        }
        accumulator.likelyRequiredJava = inferLikelyRequiredJava(accumulator.minecraftVersion, accumulator.serverType);
    }

    private static void finalizeDefaults(DetectionAccumulator accumulator) {
        if (accumulator.serverArgs == null || accumulator.serverArgs.isBlank()) {
            accumulator.serverArgs = "nogui";
        }
        if (accumulator.jvmArgs == null || accumulator.jvmArgs.isBlank()) {
            accumulator.jvmArgs = DEFAULT_JVM_ARGS;
        }
        if (accumulator.messages.isEmpty()) {
            accumulator.messages.add("No existing launch script found, using defaults.");
        }
        if (accumulator.minecraftVersion != null) {
            accumulator.messages.add("Detected Minecraft version: " + accumulator.minecraftVersion + ".");
        } else {
            accumulator.messages.add("Detected Minecraft version: Unknown.");
        }
        accumulator.messages.add("Detected loader: " + accumulator.serverType.displayName() + ".");
        if (accumulator.likelyRequiredJava > 0) {
            accumulator.messages.add("Likely Java requirement: Java " + accumulator.likelyRequiredJava + ".");
        }
    }

    private static void parseTextFile(DetectionAccumulator accumulator, Path path, boolean scriptFile) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }

        boolean imported = false;
        for (String line : lines) {
            String normalized = normalizeLine(line);
            if (normalized.isBlank()) {
                continue;
            }
            if (scriptFile && !containsLaunchHint(normalized)) {
                continue;
            }
            parseCommandTokens(accumulator, tokenize(normalized), path.getParent(), path);
            imported = true;
        }

        if (imported) {
            accumulator.messages.add("Imported launch settings from " + path.getFileName());
        }
    }

    private static void parseCommandTokens(DetectionAccumulator accumulator, List<String> tokens, Path workingDirectory, Path source) {
        Deque<String> queue = new ArrayDeque<>(tokens);
        List<String> jvmArgs = new ArrayList<>();
        List<String> serverArgs = new ArrayList<>();
        boolean afterJar = false;

        while (!queue.isEmpty()) {
            String token = queue.removeFirst();
            if (token.isBlank()) {
                continue;
            }
            String cleaned = stripWrapping(token);
            if (cleaned.isBlank()) {
                continue;
            }
            if (cleaned.startsWith("@")) {
                Path argFile = resolveArgFile(workingDirectory, cleaned.substring(1));
                if (argFile != null && Files.isRegularFile(argFile)) {
                    List<String> argTokens = readArgFileTokens(argFile);
                    for (int i = argTokens.size() - 1; i >= 0; i--) {
                        queue.addFirst(argTokens.get(i));
                    }
                }
                continue;
            }
            if (looksLikeJavaPath(cleaned)) {
                accumulator.javaPath = accumulator.javaPath == null ? cleaned : accumulator.javaPath;
                continue;
            }

            Matcher memoryMatcher = MEMORY_PATTERN.matcher(cleaned);
            if (memoryMatcher.matches()) {
                if ("ms".equalsIgnoreCase(memoryMatcher.group(1)) && accumulator.xms == null) {
                    accumulator.xms = memoryMatcher.group(2);
                } else if ("mx".equalsIgnoreCase(memoryMatcher.group(1)) && accumulator.xmx == null) {
                    accumulator.xmx = memoryMatcher.group(2);
                }
                continue;
            }

            if ("-jar".equalsIgnoreCase(cleaned) && !queue.isEmpty()) {
                String jarToken = stripWrapping(queue.removeFirst());
                accumulator.jarPath = relativizeIfPossible(accumulator.serverFolder, resolvePathString(workingDirectory, jarToken));
                accumulator.serverType = detectType(accumulator.serverType, jarToken, accumulator.serverFolder);
                if (accumulator.minecraftVersion == null) {
                    String detectedVersion = extractMcVersion(jarToken);
                    if (detectedVersion != null) {
                        accumulator.minecraftVersion = detectedVersion;
                        accumulator.versionSource = source.getFileName() + " launcher";
                    }
                }
                afterJar = true;
                continue;
            }

            if (looksLikeJar(cleaned) && accumulator.jarPath == null) {
                accumulator.jarPath = relativizeIfPossible(accumulator.serverFolder, resolvePathString(workingDirectory, cleaned));
                accumulator.serverType = detectType(accumulator.serverType, cleaned, accumulator.serverFolder);
                if (accumulator.minecraftVersion == null) {
                    String detectedVersion = extractMcVersion(cleaned);
                    if (detectedVersion != null) {
                        accumulator.minecraftVersion = detectedVersion;
                        accumulator.versionSource = source.getFileName() + " launcher";
                    }
                }
                afterJar = true;
                continue;
            }

            if (cleaned.equalsIgnoreCase("nogui")) {
                serverArgs.add("nogui");
                afterJar = true;
                continue;
            }

            if (afterJar) {
                if (!isShellNoise(cleaned)) {
                    serverArgs.add(cleaned);
                }
            } else if (cleaned.startsWith("-")) {
                jvmArgs.add(cleaned);
            }
        }

        accumulator.jvmArgs = mergeArgs(accumulator.jvmArgs, jvmArgs);
        accumulator.serverArgs = mergeArgs(accumulator.serverArgs, serverArgs);
    }

    private static List<String> readArgFileTokens(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return tokenize(content.replace("\r", " ").replace("\n", " "));
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private static String normalizeLine(String line) {
        String normalized = line.trim();
        normalized = normalized.replace("^", " ");
        normalized = normalized.replace("`", " ");
        normalized = normalized.replace("%*", " ");
        normalized = normalized.replace("$@", " ");
        normalized = normalized.replace("${@}", " ");
        if (normalized.startsWith("REM ") || normalized.startsWith("::") || normalized.startsWith("#")) {
            return "";
        }
        if (normalized.regionMatches(true, 0, "set ", 0, 4) || normalized.regionMatches(true, 0, "export ", 0, 7)) {
            int idx = normalized.indexOf('=');
            if (idx >= 0 && idx < normalized.length() - 1) {
                normalized = normalized.substring(idx + 1).trim();
            }
        }
        if (normalized.regionMatches(true, 0, "start ", 0, 6)) {
            normalized = normalized.substring(6).trim();
        }
        if (normalized.regionMatches(true, 0, "call ", 0, 5)) {
            normalized = normalized.substring(5).trim();
        }
        if (normalized.regionMatches(true, 0, "powershell ", 0, 11)) {
            normalized = normalized.substring(11).trim();
        }
        return normalized;
    }

    private static boolean containsLaunchHint(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("java")
                || lower.contains("-jar")
                || lower.contains("@user_jvm_args")
                || lower.contains("@libraries/")
                || lower.contains("@libraries\\")
                || lower.contains("fabric-server-launch.jar")
                || lower.contains("forge")
                || lower.contains("neoforge");
    }

    private static boolean hasScriptExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return SCRIPT_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private static int scriptPriority(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.contains("start") || lower.contains("run")) {
            return 0;
        }
        if (lower.contains("launch") || lower.contains("server")) {
            return 1;
        }
        return 2;
    }

    private static String findCommonJar(Path serverFolder) {
        List<String> preferred = List.of(
                "fabric-server-launch.jar",
                "server.jar"
        );
        for (String name : preferred) {
            if (Files.isRegularFile(serverFolder.resolve(name))) {
                return name;
            }
        }
        try (java.util.stream.Stream<Path> stream = Files.list(serverFolder)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(ServerSetupDetector::looksLikeJar)
                    .sorted(Comparator.comparingInt(ServerSetupDetector::jarPriority))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static int jarPriority(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("fabric")) return 0;
        if (lower.contains("neoforge")) return 1;
        if (lower.contains("forge")) return 2;
        if (lower.equals("server.jar")) return 3;
        return 4;
    }

    private static void detectMinecraftVersion(DetectionAccumulator accumulator, Path serverFolder, Path jarPath) {
        if (jarPath == null) {
            return;
        }
        if (!Files.isRegularFile(jarPath) && jarPath.getFileName() != null) {
            String fromName = extractMcVersion(jarPath.getFileName().toString());
            if (fromName != null && accumulator.minecraftVersion == null) {
                accumulator.minecraftVersion = fromName;
                accumulator.versionSource = "jar file name";
                return;
            }
        }
        if (!Files.isRegularFile(jarPath)) {
            return;
        }

        String fromName = extractMcVersion(jarPath.getFileName().toString());
        if (fromName != null && accumulator.minecraftVersion == null) {
            accumulator.minecraftVersion = fromName;
            accumulator.versionSource = "jar file name";
        }

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Manifest manifest = jarFile.getManifest();
            if (manifest != null && accumulator.minecraftVersion == null) {
                Attributes attributes = manifest.getMainAttributes();
                String[] manifestHints = {
                        attributes.getValue("Implementation-Version"),
                        attributes.getValue("Specification-Version"),
                        attributes.getValue("Implementation-Title")
                };
                for (String hint : manifestHints) {
                    String version = extractMcVersion(hint);
                    if (version != null) {
                        accumulator.minecraftVersion = version;
                        accumulator.versionSource = "jar manifest";
                        break;
                    }
                }
            }

            if (accumulator.minecraftVersion == null) {
                var entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    String lower = entry.getName().toLowerCase(Locale.ROOT);
                    if (lower.endsWith("version.json")) {
                        try (InputStream input = jarFile.getInputStream(entry)) {
                            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                            String version = extractMcVersion(content);
                            if (version != null) {
                                accumulator.minecraftVersion = version;
                                accumulator.versionSource = entry.getName();
                                break;
                            }
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void detectMinecraftVersionFromFolder(DetectionAccumulator accumulator, Path serverFolder) {
        List<Path> candidates = List.of(
                serverFolder.resolve("version.json"),
                serverFolder.resolve("versions").resolve("version.json"),
                serverFolder.resolve("libraries")
        );
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                try {
                    String content = Files.readString(candidate, StandardCharsets.UTF_8);
                    String version = extractMcVersion(content);
                    if (version != null) {
                        accumulator.minecraftVersion = version;
                        accumulator.versionSource = serverFolder.relativize(candidate).toString();
                        return;
                    }
                } catch (IOException ignored) {
                }
            }
        }

        try {
            if (Files.isDirectory(serverFolder.resolve("libraries"))) {
                try (java.util.stream.Stream<Path> stream = Files.walk(serverFolder.resolve("libraries"), 5)) {
                    stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().equalsIgnoreCase("version.json"))
                        .findFirst()
                        .ifPresent(path -> {
                            try {
                                String content = Files.readString(path, StandardCharsets.UTF_8);
                                String version = extractMcVersion(content);
                                if (version != null && accumulator.minecraftVersion == null) {
                                    accumulator.minecraftVersion = version;
                                    accumulator.versionSource = serverFolder.relativize(path).toString();
                                }
                            } catch (IOException ignored) {
                            }
                        });
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static String extractMcVersion(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = MC_VERSION_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static int inferLikelyRequiredJava(String minecraftVersion, ServerType serverType) {
        if (minecraftVersion != null) {
            if (minecraftVersion.startsWith("1.21") || minecraftVersion.startsWith("1.20.5") || minecraftVersion.startsWith("1.20.6")) {
                return 21;
            }
            if (minecraftVersion.startsWith("1.17") || minecraftVersion.startsWith("1.18") || minecraftVersion.startsWith("1.19")
                    || minecraftVersion.startsWith("1.20")) {
                return 17;
            }
        }
        if (serverType == ServerType.FABRIC || serverType == ServerType.FORGE || serverType == ServerType.NEOFORGE) {
            return 17;
        }
        return 8;
    }

    private static ServerType detectType(ServerType current, String jarOrHint, Path serverFolder) {
        if (current != ServerType.UNKNOWN) {
            return current;
        }
        String lower = jarOrHint.toLowerCase(Locale.ROOT);
        if (lower.contains("neoforge") || Files.exists(serverFolder.resolve("libraries").resolve("net").resolve("neoforged"))) {
            return ServerType.NEOFORGE;
        }
        if (lower.contains("forge") || Files.exists(serverFolder.resolve("libraries").resolve("net").resolve("minecraftforge"))) {
            return ServerType.FORGE;
        }
        if (lower.contains("fabric") || Files.exists(serverFolder.resolve("fabric-server-launch.jar")) || Files.exists(serverFolder.resolve(".fabric"))) {
            return ServerType.FABRIC;
        }
        if (lower.contains("server.jar") || lower.contains("vanilla")) {
            return ServerType.VANILLA;
        }
        return hasMinecraftServerProperties(serverFolder) ? ServerType.VANILLA : ServerType.UNKNOWN;
    }

    private static boolean hasMinecraftServerProperties(Path serverFolder) {
        return Files.exists(serverFolder.resolve("server.properties")) || Files.exists(serverFolder.resolve("eula.txt"));
    }

    private static boolean looksLikeJavaPath(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return lower.endsWith("java") || lower.endsWith("java.exe");
    }

    private static boolean looksLikeJar(String token) {
        return token.toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private static boolean isShellNoise(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return lower.equals("pause") || lower.equals("exit") || lower.equals("cmd") || lower.equals("/c");
    }

    private static Path resolveArgFile(Path workingDirectory, String value) {
        if (workingDirectory == null || value == null || value.isBlank()) {
            return null;
        }
        Path path = Path.of(value.replace("\"", ""));
        return path.isAbsolute() ? path : workingDirectory.resolve(path).normalize();
    }

    private static String resolvePathString(Path workingDirectory, String value) {
        String sanitized = value.replace("\"", "");
        Path path = Path.of(sanitized);
        if (path.isAbsolute()) {
            return path.normalize().toString();
        }
        return workingDirectory != null ? workingDirectory.resolve(path).normalize().toString() : sanitized;
    }

    private static String relativizeIfPossible(Path serverFolder, Path candidate) {
        if (candidate == null) {
            return null;
        }
        return relativizeIfPossible(serverFolder, candidate.toString());
    }

    private static String relativizeIfPossible(Path serverFolder, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(candidate);
            if (serverFolder != null && path.isAbsolute() && path.normalize().startsWith(serverFolder.normalize())) {
                return serverFolder.normalize().relativize(path.normalize()).toString();
            }
        } catch (Exception ignored) {
        }
        return candidate;
    }

    private static List<String> tokenize(String command) {
        if (command == null || command.isBlank()) {
            return Collections.emptyList();
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if ((c == '"' || c == '\'') && (!inQuotes || c == quoteChar)) {
                inQuotes = !inQuotes;
                quoteChar = inQuotes ? c : 0;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static String stripWrapping(String token) {
        String cleaned = token.trim();
        while ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    private static String mergeArgs(String existing, List<String> discovered) {
        Set<String> merged = new LinkedHashSet<>();
        if (existing != null && !existing.isBlank()) {
            merged.addAll(tokenize(existing));
        }
        merged.addAll(discovered);
        return String.join(" ", merged).trim();
    }

    private static final class DetectionAccumulator {
        private final Path serverFolder;
        private final Path selectedJar;
        private ServerType serverType = ServerType.UNKNOWN;
        private String jarPath;
        private String javaPath;
        private String xms;
        private String xmx;
        private String jvmArgs;
        private String serverArgs;
        private String minecraftVersion;
        private String versionSource;
        private int likelyRequiredJava;
        private final List<String> messages = new ArrayList<>();

        private DetectionAccumulator(Path serverFolder, Path selectedJar) {
            this.serverFolder = serverFolder;
            this.selectedJar = selectedJar;
        }

        private DetectionResult build() {
            List<String> compactMessages = messages.stream().distinct().toList();
            return new DetectionResult(
                    serverType,
                    jarPath,
                    javaPath,
                    xms,
                    xmx,
                    jvmArgs,
                    serverArgs,
                    minecraftVersion,
                    versionSource,
                    likelyRequiredJava,
                    compactMessages,
                    selectedJar != null || jarPath != null || javaPath != null || xms != null || xmx != null || (jvmArgs != null && !jvmArgs.isBlank())
            );
        }
    }

    public record DetectionResult(
            ServerType serverType,
            String jarPath,
            String javaPath,
            String xms,
            String xmx,
            String jvmArgs,
            String serverArgs,
            String minecraftVersion,
            String versionSource,
            int likelyRequiredJava,
            List<String> messages,
            boolean foundAnything
    ) {
    }

    public enum ServerType {
        VANILLA("Vanilla"),
        FABRIC("Fabric"),
        FORGE("Forge"),
        NEOFORGE("NeoForge"),
        UNKNOWN("Unknown");

        private final String displayName;

        ServerType(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}
