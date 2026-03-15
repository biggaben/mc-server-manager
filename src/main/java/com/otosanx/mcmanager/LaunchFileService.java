package com.otosanx.mcmanager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LaunchFileService {
    private static final Pattern JAVA_CMD_PATTERN = Pattern.compile("(?i)(^|\\s)(\"?[^\"]*java(?:\\.exe)?\"?)");
    private static final Pattern XMS_PATTERN = Pattern.compile("(?i)-Xms\\S+");
    private static final Pattern XMX_PATTERN = Pattern.compile("(?i)-Xmx\\S+");
    private static final Pattern JAR_PATTERN = Pattern.compile("(?i)-jar\\s+(\"[^\"]+\"|\\S+)");
    private static final String DEFAULT_RECOMMENDED_ARGS = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=100 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -Dfile.encoding=UTF-8";

    private LaunchFileService() {
    }

    public static Path detectPrimaryLaunchFile(Path serverFolder) {
        if (serverFolder == null || !Files.isDirectory(serverFolder)) {
            return null;
        }
        try (java.util.stream.Stream<Path> stream = Files.list(serverFolder)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".bat") || name.endsWith(".cmd") || name.endsWith(".ps1") || name.endsWith(".sh");
                    })
                    .sorted(Comparator.comparingInt(path -> priority(path.getFileName().toString())))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    public static SyncResult applySettingsToLaunchFile(Path serverFolder, AppConfig config, ServerSetupDetector.DetectionResult detection) {
        if (serverFolder == null || !Files.isDirectory(serverFolder)) {
            return new SyncResult(false, "No writable server folder found. Saved internal config only.");
        }

        Path primary = detectPrimaryLaunchFile(serverFolder);
        Path userJvmArgs = serverFolder.resolve("user_jvm_args.txt");

        if ((detection.serverType() == ServerSetupDetector.ServerType.FORGE || detection.serverType() == ServerSetupDetector.ServerType.NEOFORGE)
                && Files.isRegularFile(userJvmArgs)) {
            return updateUserJvmArgs(userJvmArgs, config.jvmArgs);
        }

        if (primary == null) {
            return new SyncResult(false, "No recognized startup file found. Saved internal config only.");
        }

        String lower = primary.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".bat") || lower.endsWith(".cmd") || lower.endsWith(".ps1") || lower.endsWith(".sh")) {
            return updateLaunchScript(primary, config);
        }

        return new SyncResult(false, "Detected launcher is too custom to update safely. Saved internal config only.");
    }

    public static List<Path> getManagedFilesForBackup(Path serverFolder, ServerSetupDetector.DetectionResult detection) {
        List<Path> files = new ArrayList<>();
        if (serverFolder == null || !Files.isDirectory(serverFolder)) {
            return files;
        }

        Path primary = detectPrimaryLaunchFile(serverFolder);
        Path userJvmArgs = serverFolder.resolve("user_jvm_args.txt");

        if ((detection.serverType() == ServerSetupDetector.ServerType.FORGE || detection.serverType() == ServerSetupDetector.ServerType.NEOFORGE)
                && Files.isRegularFile(userJvmArgs)) {
            files.add(userJvmArgs);
        }
        if (primary != null && Files.isRegularFile(primary) && !files.contains(primary)) {
            files.add(primary);
        }
        return files;
    }

    public static String describeManagedTarget(Path serverFolder, ServerSetupDetector.DetectionResult detection) {
        List<Path> files = getManagedFilesForBackup(serverFolder, detection);
        if (files.isEmpty()) {
            return "Internal app config only";
        }
        List<String> names = new ArrayList<>();
        for (Path file : files) {
            names.add(file.getFileName().toString());
        }
        return String.join(", ", names);
    }

    public static Recommendation buildRecommendedJvmArgs(ServerSetupDetector.ServerType type, String xmx, String currentArgs) {
        String normalized = currentArgs == null ? "" : currentArgs.trim();
        boolean advanced = normalized.split("\\s+").length > 6 && !normalized.equals(DEFAULT_RECOMMENDED_ARGS);
        if (advanced) {
            return new Recommendation("Custom advanced JVM args already detected; recommendation not auto-applied.", normalized, false);
        }

        String memoryHint = xmx == null || xmx.isBlank() ? "the current memory setting" : xmx;
        String recommended = switch (type) {
            case FORGE, NEOFORGE -> DEFAULT_RECOMMENDED_ARGS + " -Dfml.readTimeout=90";
            case FABRIC -> DEFAULT_RECOMMENDED_ARGS;
            case VANILLA -> DEFAULT_RECOMMENDED_ARGS;
            default -> DEFAULT_RECOMMENDED_ARGS;
        };

        return new Recommendation("Detected simple/default JVM args for " + type.displayName() + ". Recommended optimized args available for " + memoryHint + ".", recommended, true);
    }

    private static SyncResult updateUserJvmArgs(Path file, String jvmArgs) {
        String desired = (jvmArgs == null || jvmArgs.isBlank() ? DEFAULT_RECOMMENDED_ARGS : jvmArgs).trim() + System.lineSeparator();
        try {
            String existing = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
            if (Objects.equals(existing, desired)) {
                return new SyncResult(false, "user_jvm_args.txt already matches the manager settings.");
            }
            Files.writeString(file, desired, StandardCharsets.UTF_8);
            return new SyncResult(true, "Updated user_jvm_args.txt with JVM args.");
        } catch (IOException e) {
            return new SyncResult(false, "Could not update user_jvm_args.txt safely. Saved internal config only.");
        }
    }

    private static SyncResult updateLaunchScript(Path script, AppConfig config) {
        try {
            String original = Files.readString(script, StandardCharsets.UTF_8);
            String lineEnding = original.contains("\r\n") ? "\r\n" : "\n";
            String[] lines = original.split("\\R", -1);
            boolean changed = false;
            boolean updated = false;
            List<String> out = new ArrayList<>();

            for (String line : lines) {
                String updatedLine = line;
                if (!updated && looksLikeLaunchLine(line)) {
                    updatedLine = patchLaunchLine(line, config);
                    updated = true;
                }
                if (!updatedLine.equals(line)) {
                    changed = true;
                }
                out.add(updatedLine);
            }

            if (!updated) {
                return new SyncResult(false, "Detected " + script.getFileName() + " but it is too custom to patch safely. Saved internal config only.");
            }
            if (!changed) {
                return new SyncResult(false, script.getFileName() + " already matches the manager settings.");
            }

            Files.writeString(script, String.join(lineEnding, out), StandardCharsets.UTF_8);
            return new SyncResult(true, "Updated launch settings in " + script.getFileName() + ".");
        } catch (IOException e) {
            return new SyncResult(false, "Could not update " + script.getFileName() + ". Saved internal config only.");
        }
    }

    private static boolean looksLikeLaunchLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("java") && (lower.contains("-jar") || lower.contains(".jar") || lower.contains("@user_jvm_args") || lower.contains("@libraries\\") || lower.contains("@libraries/"));
    }

    private static String patchLaunchLine(String line, AppConfig config) {
        String patched = line;
        patched = replaceJavaPath(patched, config.javaPath);
        // Apply JVM args before Xms/Xmx to avoid accidentally wiping freshly inserted JVM memory args
        patched = replaceJvmArgsBlock(patched, config.jvmArgs);
        patched = replaceOrInsert(patched, XMS_PATTERN, "-Xms" + config.xms, "java");
        patched = replaceOrInsert(patched, XMX_PATTERN, "-Xmx" + config.xmx, "java");
        patched = replaceJarTarget(patched, config.jarFile);
        patched = replaceServerArgs(patched, config.serverArgs);
        return patched.trim();
    }

    private static String replaceJavaPath(String line, String javaPath) {
        Matcher matcher = JAVA_CMD_PATTERN.matcher(line);
        return matcher.find() ? matcher.replaceFirst("$1" + Matcher.quoteReplacement(javaPath)) : line;
    }

    private static String replaceOrInsert(String line, Pattern pattern, String replacement, String anchorWord) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            return matcher.replaceAll(Matcher.quoteReplacement(replacement));
        }
        int index = line.toLowerCase(Locale.ROOT).indexOf(anchorWord.toLowerCase(Locale.ROOT));
        if (index >= 0) {
            int insertAt = index + anchorWord.length();
            return line.substring(0, insertAt) + " " + replacement + line.substring(insertAt);
        }
        return line;
    }

    private static String replaceJvmArgsBlock(String line, String jvmArgs) {
        String trimmedArgs = jvmArgs == null ? "" : jvmArgs.trim();
        if (trimmedArgs.isBlank()) {
            return line;
        }
        
        int jarIndex = line.toLowerCase(Locale.ROOT).indexOf("-jar");
        if (jarIndex >= 0) {
            String beforeJar = line.substring(0, jarIndex);
            String afterJar = line.substring(jarIndex);
            
            // Wipe strictly non-memory JVM args before -jar
            // (Memory args are explicitly wiped or rewritten afterwards via Xms/Xmx logic in patchLaunchLine)
            beforeJar = beforeJar.replaceAll("(?i)\\s+-[^\\s]+", "");
            return beforeJar.trim() + " " + trimmedArgs + " " + afterJar.trim();
        }
        
        Matcher matcher = JAVA_CMD_PATTERN.matcher(line);
        if (matcher.find()) {
            String afterJava = line.substring(matcher.end()).trim();
            if (afterJava.contains(trimmedArgs)) {
                afterJava = afterJava.replace(trimmedArgs, "").replaceAll("\\s{2,}", " ").trim();
            }
            return line.substring(0, matcher.end()).trim() + " " + trimmedArgs + " " + afterJava;
        }
        return line.trim() + " " + trimmedArgs;
    }

    private static String replaceJarTarget(String line, String jarFile) {
        Matcher matcher = JAR_PATTERN.matcher(line);
        if (matcher.find()) {
            return matcher.replaceFirst("-jar " + Matcher.quoteReplacement(jarFile));
        }
        return line;
    }

    private static String replaceServerArgs(String line, String serverArgs) {
        if (serverArgs == null || serverArgs.isBlank()) {
            return line;
        }
        int jarIndex = line.toLowerCase(Locale.ROOT).indexOf("-jar");
        if (jarIndex < 0) {
            return line;
        }
        Matcher matcher = JAR_PATTERN.matcher(line);
        if (!matcher.find()) {
            return line;
        }
        return line.substring(0, matcher.end()).trim() + " " + serverArgs.trim();
    }

    private static int priority(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.equals("start.bat")) return 0;
        if (lower.equals("run.bat")) return 1;
        if (lower.equals("launch.bat")) return 2;
        if (lower.contains("start")) return 3;
        if (lower.contains("run")) return 4;
        return 5;
    }

    public record SyncResult(boolean changed, String message) {
    }

    public record Recommendation(String message, String args, boolean canApply) {
    }
}
