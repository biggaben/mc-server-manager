package com.otosanx.mcmanager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaDetectionService {
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)(?:\\.(\\d+))?.*");

    private JavaDetectionService() {
    }

    public static JavaDetectionResult detect(String configuredJavaPath, ServerSetupDetector.DetectionResult detection, Path serverFolder) {
        String configuredCandidate = isUsableJavaPath(configuredJavaPath) ? configuredJavaPath : null;
        String candidate = firstValid(
                configuredCandidate,
                detectJavaOnPath(),
                detectCommonWindowsJava()
        );
        int required = detection != null ? detection.likelyRequiredJava() : inferRequiredJavaVersion(serverFolder, "", ServerSetupDetector.ServerType.UNKNOWN);
        if (candidate == null) {
            return new JavaDetectionResult(configuredJavaPath, "", "No Java installation detected.", -1, required, "No Java found. Use Detect Java or choose a Java install manually.");
        }

        String versionText = readJavaVersion(candidate);
        int major = parseMajorVersion(versionText);
        String note;
        if (major < 0) {
            note = "Java was found, but the version could not be read.";
        } else if (required > 0 && major < required) {
            note = "Detected Java " + major + ", but this server likely needs Java " + required + " or newer.";
        } else if (required > 0) {
            note = "Detected Java " + major + ". This likely matches the server.";
        } else {
            note = "Detected Java " + major + ".";
        }
        return new JavaDetectionResult(configuredJavaPath, candidate, versionText, major, required, note);
    }

    private static String detectJavaOnPath() {
        try {
            Process process = new ProcessBuilder("where", "java").redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    return line.trim();
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static String detectCommonWindowsJava() {
        List<Path> searchRoots = List.of(
                Paths.get(System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files"), "Java"),
                Paths.get(System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files"), "Eclipse Adoptium"),
                Paths.get(System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files"), "Adoptium"),
                Paths.get(System.getenv().getOrDefault("ProgramFiles(x86)", "C:\\Program Files (x86)"), "Java")
        );
        for (Path root : searchRoots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try {
                return Files.walk(root, 3)
                        .filter(Files::isRegularFile)
                        .map(Path::normalize)
                        .filter(path -> path.getFileName().toString().equalsIgnoreCase("java.exe"))
                        .map(Path::toString)
                        .findFirst()
                        .orElse(null);
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private static String readJavaVersion(String javaPath) {
        try {
            Process process = new ProcessBuilder(javaPath, "-version").redirectErrorStream(true).start();
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line.trim());
                }
            }
            return lines.isEmpty() ? "Unknown version" : String.join(" | ", lines);
        } catch (IOException e) {
            return "Could not run java -version";
        }
    }

    private static int parseMajorVersion(String versionText) {
        if (versionText == null) {
            return -1;
        }
        String normalized = versionText.replace("\"", "").toLowerCase(Locale.ROOT);
        Matcher matcher = VERSION_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return -1;
        }
        int first = parseInt(matcher.group(1));
        int second = parseInt(matcher.group(2));
        return first == 1 ? second : first;
    }

    private static int inferRequiredJavaVersion(Path serverFolder, String jarFile, ServerSetupDetector.ServerType serverType) {
        String hints = ((jarFile == null ? "" : jarFile) + " " + (serverFolder == null ? "" : serverFolder.toString())).toLowerCase(Locale.ROOT);
        if (hints.contains("1.21") || hints.contains("1.20.5") || hints.contains("1.20.6")) {
            return 21;
        }
        if (hints.contains("1.20") || hints.contains("1.19") || hints.contains("1.18") || serverType == ServerSetupDetector.ServerType.FABRIC || serverType == ServerSetupDetector.ServerType.FORGE || serverType == ServerSetupDetector.ServerType.NEOFORGE) {
            return 17;
        }
        if (hints.contains("1.17")) {
            return 17;
        }
        return 8;
    }

    private static int parseInt(String value) {
        try {
            return value == null ? -1 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String firstValid(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static boolean isUsableJavaPath(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        Path path = Paths.get(value);
        return Files.exists(path) || "java".equalsIgnoreCase(value.trim()) || value.trim().toLowerCase(Locale.ROOT).endsWith("java.exe");
    }

    public record JavaDetectionResult(String configuredPath, String detectedPath, String versionText, int majorVersion, int requiredMajorVersion, String note) {
        public static JavaDetectionResult empty() {
            return new JavaDetectionResult("", "", "", -1, -1, "Java has not been checked yet.");
        }

        public String summary() {
            String configured = configuredPath == null || configuredPath.isBlank() ? "Not set" : configuredPath;
            String actual = detectedPath == null || detectedPath.isBlank() ? "Unavailable" : detectedPath;
            return "Configured Java: " + configured + ". Detected Java: " + shortDisplay() + " at " + actual + ". " + note;
        }

        public String shortDisplay() {
            if (majorVersion > 0) {
                return "Java " + majorVersion;
            }
            return note;
        }
    }
}
