package com.otosanx.mcmanager;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {
    public String themeMode = "MATCH_WINDOWS";
    public String closeBehavior = "MINIMIZE_TO_TRAY";
    public String customBackgroundColor = "#1F2430";
    public String customInputColor = "#2A3140";
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
