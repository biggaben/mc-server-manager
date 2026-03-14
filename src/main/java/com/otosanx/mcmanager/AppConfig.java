package com.otosanx.mcmanager;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {
    public String themeMode = "MATCH_WINDOWS";
    public String closeBehavior = "MINIMIZE_TO_TRAY";
    public String customBackgroundColor = "#1F2430";
    public String customInputColor = "#2A3140";
    public boolean settingsLocked = false;
    public boolean serverSettingsCollapsed = false;
    public boolean sidebarCollapsed = false;
    public boolean consoleCollapsed = false;
    public boolean commandCollapsed = false;
    public boolean checkPlayitBeforeStart = false;
    public boolean startPlayitIfMissing = false;
    public String playitExecutablePath = "";
    public boolean monitoringEnabled = true;
    public boolean monitorSystemCpu = true;
    public boolean monitorSystemMemory = true;
    public boolean monitorServerMemory = true;
    public boolean monitorUptime = true;
    public boolean monitorTps = false;
    public boolean monitorPlayerCount = false;
    public boolean monitorLatency = false;
    public boolean monitorJavaStatus = true;
    public boolean monitorServerType = true;
    public boolean monitorRestartState = true;
    public int monitoringPollingSeconds = 2;
    public String profileName = "Default Server";
    public String serverFolder = "";
    public String jarFile = "server.jar";
    public String javaPath = "java";
    public String xms = "4G";
    public String xmx = "8G";
    public String jvmArgs = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=100 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -Dfile.encoding=UTF-8";
    public String serverArgs = "nogui";
    public boolean autoRestartOnCrash = true;
    public boolean startMinimized = false;
    public boolean minimizeToTray = true;
    public int autoRestartDelaySeconds = 15;
    public int safeStopWaitSeconds = 10;
    public int windowWidth = 1100;
    public int windowHeight = 760;
}
