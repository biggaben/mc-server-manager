package com.otosanx.mcmanager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ServerManager {
    public enum State {
        OFFLINE, STARTING, ONLINE, STOPPING, CRASHED
    }

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private Process process;
    private PrintWriter writer;
    private volatile State state = State.OFFLINE;
    private volatile boolean stopRequested = false;
    private volatile boolean restartRequested = false;
    private AppConfig currentConfig;
    private Consumer<String> logConsumer = s -> {};
    private Consumer<State> stateConsumer = s -> {};

    public synchronized void setListeners(Consumer<String> logConsumer, Consumer<State> stateConsumer) {
        this.logConsumer = logConsumer != null ? logConsumer : s -> {};
        this.stateConsumer = stateConsumer != null ? stateConsumer : s -> {};
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    public synchronized void start(AppConfig config) throws IOException {
        if (isRunning()) {
            log("Server is already running.");
            return;
        }
        this.currentConfig = config;
        stopRequested = false;
        restartRequested = false;
        setState(State.STARTING);

        List<String> command = new ArrayList<>();
        command.add(config.javaPath == null || config.javaPath.isBlank() ? "java" : config.javaPath.trim());
        if (config.xms != null && !config.xms.isBlank()) command.add("-Xms" + config.xms.trim());
        if (config.xmx != null && !config.xmx.isBlank()) command.add("-Xmx" + config.xmx.trim());
        if (config.jvmArgs != null && !config.jvmArgs.isBlank()) {
            command.addAll(splitArgs(config.jvmArgs));
        }
        command.add("-jar");
        command.add(config.jarFile);
        if (config.serverArgs != null && !config.serverArgs.isBlank()) {
            command.addAll(splitArgs(config.serverArgs));
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(Path.of(config.serverFolder).toFile());
        pb.redirectErrorStream(true);
        log("Launching: " + String.join(" ", command));
        process = pb.start();
        writer = new PrintWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true);

        executor.submit(this::readOutput);
        executor.submit(this::waitForExit);
    }

    public synchronized void sendCommand(String cmd) {
        if (!isRunning() || writer == null) {
            log("Cannot send command. Server is not running.");
            return;
        }
        writer.println(cmd);
        writer.flush();
        log("[APP -> SERVER] " + cmd);
    }

    public synchronized void safeStop(int waitSeconds) {
        if (!isRunning()) {
            log("Server is not running.");
            return;
        }
        stopRequested = true;
        restartRequested = false;
        setState(State.STOPPING);
        executor.submit(() -> {
            try {
                sendCommand("save-all");
                TimeUnit.SECONDS.sleep(1);
                sendCommand("stop");
                boolean exited = process.waitFor(waitSeconds, TimeUnit.SECONDS);
                if (!exited) {
                    log("Safe stop timeout reached. Use Force Kill if it hangs.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("Safe stop interrupted.");
            }
        });
    }

    public synchronized void restart(int waitSeconds) {
        if (!isRunning()) {
            log("Server is offline. Starting instead.");
            try {
                start(currentConfig);
            } catch (Exception e) {
                log("Failed to start: " + e.getMessage());
                setState(State.CRASHED);
            }
            return;
        }
        stopRequested = true;
        restartRequested = true;
        setState(State.STOPPING);
        executor.submit(() -> {
            try {
                sendCommand("save-all");
                TimeUnit.SECONDS.sleep(1);
                sendCommand("stop");
                boolean exited = process.waitFor(waitSeconds, TimeUnit.SECONDS);
                if (!exited) {
                    log("Restart stop timeout reached. Force killing process.");
                    forceKill();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("Restart interrupted.");
            }
        });
    }

    public synchronized void forceKill() {
        if (!isRunning()) {
            log("Server is not running.");
            return;
        }
        stopRequested = true;
        process.destroy();
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            log("Server process killed.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void readOutput() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log(line);
                detectReadyState(line);
            }
        } catch (IOException e) {
            log("Log reader stopped: " + e.getMessage());
        }
    }

    private void waitForExit() {
        try {
            int code = process.waitFor();
            log("Process exited with code: " + code);
            boolean shouldRestart = false;
            int restartDelay = 15;

            synchronized (this) {
                writer = null;
                process = null;
                if (restartRequested) {
                    shouldRestart = true;
                    restartRequested = false;
                } else if (!stopRequested && currentConfig != null && currentConfig.autoRestartOnCrash) {
                    shouldRestart = true;
                    restartDelay = currentConfig.autoRestartDelaySeconds;
                    setState(State.CRASHED);
                    log("Crash detected. Auto-restart enabled.");
                } else {
                    setState(State.OFFLINE);
                }
                stopRequested = false;
            }

            if (shouldRestart && currentConfig != null) {
                log("Restarting in " + restartDelay + " seconds...");
                TimeUnit.SECONDS.sleep(restartDelay);
                start(currentConfig);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log("Wait for exit interrupted.");
        } catch (Exception e) {
            log("Failed to auto-restart: " + e.getMessage());
            setState(State.CRASHED);
        }
    }

    private synchronized void setState(State newState) {
        this.state = newState;
        stateConsumer.accept(newState);
    }

    private void detectReadyState(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.contains("done (") || lower.contains("for help, type \"help\"") || lower.contains("rcon running on")) {
            if (getState() == State.STARTING) {
                setState(State.ONLINE);
            }
        }
    }

    private void log(String message) {
        String stamped = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " + message;
        logConsumer.accept(stamped);
    }

    private List<String> splitArgs(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '"' || c == '\'') && (!inQuotes || c == quoteChar)) {
                inQuotes = !inQuotes;
                quoteChar = inQuotes ? c : 0;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (!current.isEmpty()) {
                    args.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            args.add(current.toString());
        }
        return args;
    }
}
