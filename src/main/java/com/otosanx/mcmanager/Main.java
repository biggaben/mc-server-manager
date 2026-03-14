package com.otosanx.mcmanager;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Main {
    private static final String TRANSPARENT_PANEL_PROPERTY = "mcmanager.transparentPanel";
    private static final String THEME_MATCH_WINDOWS = "MATCH_WINDOWS";
    private static final String THEME_DARK = "DARK";
    private static final String THEME_LIGHT = "LIGHT";
    private static final String THEME_CUSTOM = "CUSTOM";
    private static final String CLOSE_TO_TRAY = "MINIMIZE_TO_TRAY";
    private static final String CLOSE_TO_TASKBAR = "MINIMIZE_TO_TASKBAR";
    private static final String CLOSE_EXIT = "EXIT";

    private final ServerManager serverManager = new ServerManager();
    private AppConfig config;

    private JFrame frame;
    private JTextField profileNameField;
    private JTextField folderField;
    private JTextField jarField;
    private JTextField javaField;
    private JTextField xmsField;
    private JTextField xmxField;
    private JTextField serverArgsField;
    private JCheckBox autoRestartBox;
    private JCheckBox startMinimizedBox;
    private JCheckBox playitCheckBox;
    private JCheckBox playitStartBox;
    private JCheckBox monitoringEnabledBox;
    private JCheckBox monitorSystemCpuBox;
    private JCheckBox monitorSystemMemoryBox;
    private JCheckBox monitorServerMemoryBox;
    private JCheckBox monitorUptimeBox;
    private JCheckBox monitorTpsBox;
    private JCheckBox monitorPlayerCountBox;
    private JCheckBox monitorLatencyBox;
    private JCheckBox monitorJavaStatusBox;
    private JCheckBox monitorServerTypeBox;
    private JCheckBox monitorRestartStateBox;
    private JComboBox<Integer> restartDelayCombo;
    private JComboBox<Integer> safeStopCombo;
    private JComboBox<Integer> monitoringRateCombo;
    private JTextArea jvmArgsArea;
    private JTextArea logArea;
    private JTextArea consolePreviewArea;
    private JTextField commandField;
    private JTextField playitPathField;
    private JLabel statusLabel;
    private JLabel detectedTypeLabel;
    private JLabel jvmRecommendationLabel;
    private JLabel javaStatusLabel;
    private JLabel perfStatusLabel;
    private JLabel perfUptimeLabel;
    private JLabel perfRestartLabel;
    private JLabel perfSystemCpuLabel;
    private JLabel perfSystemMemoryLabel;
    private JLabel perfServerMemoryLabel;
    private JLabel perfServerCpuLabel;
    private JLabel perfJavaLabel;
    private JLabel perfTypeLabel;
    private JLabel perfPlayersLabel;
    private JComboBox<String> themeModeCombo;
    private JComboBox<String> closeBehaviorCombo;
    private JButton backgroundColorButton;
    private JButton inputColorButton;
    private JButton settingsLockButton;
    private JButton detectSettingsButton;
    private JButton applyRecommendedJvmButton;
    private JButton saveServerSettingsButton;
    private JButton detectJavaButton;
    private JButton javaHelpButton;
    private JButton folderBrowseButton;
    private JButton jarBrowseButton;
    private JButton playitBrowseButton;
    private JButton optionsButton;
    private JButton performanceMonitorButton;
    private CollapsibleSection serverSettingsSection;
    private CollapsibleSection sidebarSection;
    private CollapsibleSection consoleSection;
    private CollapsibleSection commandSection;
    private TrayIcon trayIcon;
    private JDialog optionsDialog;
    private JDialog performanceDialog;
    private Color customBackgroundColor = Color.decode("#1F2430");
    private Color customInputColor = Color.decode("#2A3140");
    private boolean settingsLocked;
    private boolean monitoringPausedForVisibility;
    private ServerSetupDetector.ServerType detectedServerType = ServerSetupDetector.ServerType.UNKNOWN;
    private JavaDetectionService.JavaDetectionResult detectedJavaInfo = JavaDetectionService.JavaDetectionResult.empty();
    private final List<String> recentConsoleLines = new ArrayList<>();
    private Timer performanceTimer;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().start());
    }

    private void start() {
        config = ConfigService.load();
        initializeLookAndFeel();
        serverManager.setListeners(this::appendLog, this::updateStatus);
        buildUi();
        applyConfigToUi();
        applyTheme();
        updateMonitoringControls();
        startPerformanceTimer();
        if (config.startMinimized) {
            if (shouldCloseToTray() && SystemTray.isSupported()) {
                frame.setVisible(false);
                pauseMonitoringForHiddenUi("Monitoring paused while the app is in the tray.");
                showTrayMessage("MC Server Manager", "Started minimized. App is in the system tray.");
            } else {
                frame.setVisible(true);
                frame.setState(Frame.ICONIFIED);
                pauseMonitoringForHiddenUi("Monitoring paused while the app is minimized.");
            }
        } else {
            frame.setVisible(true);
        }
    }

    private void initializeLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }

    private void buildUi() {
        frame = new JFrame("MC Server Manager - IntelliJ Version");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setSize(config.windowWidth, config.windowHeight);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        frame.setContentPane(root);
        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(buildMainPanel(), BorderLayout.CENTER);
        root.add(buildBottomPanel(), BorderLayout.SOUTH);

        buildOptionsDialog();
        buildPerformanceDialog();

        installTray();
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                configFromUi();
                config.windowWidth = frame.getWidth();
                config.windowHeight = frame.getHeight();
                saveConfig(false);
                if (shouldCloseToTray() && SystemTray.isSupported()) {
                    frame.setVisible(false);
                    pauseMonitoringForHiddenUi("Monitoring paused while the app is in the tray.");
                    showTrayMessage("MC Server Manager", "App minimized to tray.");
                } else if (shouldCloseToTaskbar()) {
                    frame.setState(Frame.ICONIFIED);
                    pauseMonitoringForHiddenUi("Monitoring paused while the app is minimized.");
                } else {
                    shutdownAndExit();
                }
            }

            @Override
            public void windowIconified(WindowEvent e) {
                pauseMonitoringForHiddenUi("Monitoring paused while the app is minimized.");
            }

            @Override
            public void windowDeiconified(WindowEvent e) {
                resumeMonitoringIfAllowed("Monitoring resumed after restoring the window.");
            }
        });
    }

    private JPanel buildTopBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        markPanelTransparent(panel);
        optionsButton = createActionButton("Options", e -> openOptionsDialog());
        performanceMonitorButton = createActionButton("Performance Monitor", e -> openPerformanceDialog());
        optionsButton.setToolTipText("Open app options and monitoring settings in a separate window.");
        performanceMonitorButton.setToolTipText("Open the live performance monitor in a separate window.");
        panel.add(optionsButton);
        panel.add(performanceMonitorButton);
        return panel;
    }

    private JPanel buildMainPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        markPanelTransparent(panel);
        JPanel leftContent = new JPanel(new BorderLayout(12, 12));
        markPanelTransparent(leftContent);
        leftContent.add(buildSettingsPanel(), BorderLayout.NORTH);
        leftContent.add(buildCenterPanel(), BorderLayout.CENTER);
        panel.add(leftContent, BorderLayout.CENTER);
        panel.add(buildSidebarPanel(), BorderLayout.EAST);
        return panel;
    }

    private JComponent buildSidebarPanel() {
        JPanel sidebarContent = new JPanel();
        markPanelTransparent(sidebarContent);
        sidebarContent.setLayout(new BoxLayout(sidebarContent, BoxLayout.Y_AXIS));
        sidebarContent.add(buildQuickActionsPanel());
        sidebarContent.add(Box.createVerticalGlue());

        sidebarSection = createSectionPanel("Quick Actions", sidebarContent, true);

        JScrollPane scrollPane = new JScrollPane(sidebarSection);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setPreferredSize(new Dimension(400, 0));
        scrollPane.setMinimumSize(new Dimension(360, 0));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    private JPanel buildSettingsPanel() {
        JPanel content = new JPanel(new GridBagLayout());
        markPanelTransparent(content);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        profileNameField = new JTextField();
        folderField = new JTextField();
        jarField = new JTextField();
        javaField = new JTextField();
        xmsField = new JTextField();
        xmxField = new JTextField();
        serverArgsField = new JTextField();
        jvmArgsArea = new JTextArea(4, 30);
        jvmArgsArea.setLineWrap(true);
        jvmArgsArea.setWrapStyleWord(true);
        detectedTypeLabel = createSectionLabel("Detected Type: Unknown");
        settingsLockButton = createActionButton("Unlock Settings", e -> setSettingsLocked(!settingsLocked));
        detectSettingsButton = createActionButton("Detect Existing Settings", e -> autoDetectServerSettings(true));
        jvmRecommendationLabel = createSectionLabel("JVM Recommendation: detect a server to see suggestions.");
        applyRecommendedJvmButton = createActionButton("Apply Recommended JVM Args", e -> applyRecommendedJvmArgs());
        saveServerSettingsButton = createActionButton("Save Server Settings", e -> saveServerSettings());
        javaStatusLabel = createSectionLabel("Java Status: not checked yet.");
        detectJavaButton = createActionButton("Detect Java", e -> detectJavaRuntime(true));
        javaHelpButton = createActionButton("Open Java Download Help", e -> openJavaDownloadHelp());

        profileNameField.setToolTipText("A name for this saved server profile inside the manager.");
        folderField.setToolTipText("The main folder that contains your Minecraft server files.");
        jarField.setToolTipText("The server jar file the manager launches, such as server.jar or a Fabric/Forge jar.");
        javaField.setToolTipText("Path to Java. Leave it as java unless you need a specific Java install.");
        xmsField.setToolTipText("Starting memory allocation for Java. Usually lower than or equal to Xmx.");
        xmxField.setToolTipText("Maximum memory the server can use.");
        serverArgsField.setToolTipText("Extra server launch arguments such as nogui.");
        jvmArgsArea.setToolTipText("Advanced Java launch options. Only change these if you know what they do.");
        detectedTypeLabel.setToolTipText("The server type detected from your jar, scripts, and server files.");
        settingsLockButton.setToolTipText("Lock settings to prevent accidental edits or auto-detection changes.");
        detectSettingsButton.setToolTipText("Scan the selected server folder for an existing launcher and import detected settings.");
        jvmRecommendationLabel.setToolTipText("Shows whether the manager found a safer or more optimized JVM arg suggestion.");
        applyRecommendedJvmButton.setToolTipText("Apply the recommended JVM args only if you want to replace the current basic set.");
        saveServerSettingsButton.setToolTipText("Save the current server settings and update the detected launcher only when it is safe.");
        javaStatusLabel.setToolTipText("Shows the detected Java version and whether it likely matches this server.");
        detectJavaButton.setToolTipText("Check for a usable Java install and read its version.");
        javaHelpButton.setToolTipText("Open a trusted Java download/help page in your browser.");

        int row = 0;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        content.add(detectedTypeLabel, gbc);
        gbc.gridx = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        content.add(settingsLockButton, gbc);
        row++;

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.weightx = 1;
        content.add(detectSettingsButton, gbc);
        row++;

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        content.add(jvmRecommendationLabel, gbc);
        gbc.gridx = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        content.add(applyRecommendedJvmButton, gbc);
        row++;

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        content.add(javaStatusLabel, gbc);
        gbc.gridx = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        content.add(detectJavaButton, gbc);
        row++;

        gbc.gridx = 2;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        content.add(javaHelpButton, gbc);
        row++;

        addLabeledRow(content, gbc, row++, "Profile Name", profileNameField, null);
        addLabeledRow(content, gbc, row++, "Server Folder", folderField, browseFolderButton());
        addLabeledRow(content, gbc, row++, "Server Jar", jarField, browseJarButton());
        addLabeledRow(content, gbc, row++, "Java Path", javaField, null);
        addLabeledRow(content, gbc, row++, "Xms", xmsField, null);
        addLabeledRow(content, gbc, row++, "Xmx", xmxField, null);
        addLabeledRow(content, gbc, row++, "Server Args", serverArgsField, null);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        content.add(createSectionLabel("JVM Args"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.gridwidth = 2;
        content.add(createWrappedScrollPane(jvmArgsArea), gbc);
        row++;

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.weightx = 1;
        content.add(saveServerSettingsButton, gbc);
        serverSettingsSection = createSectionPanel("Server Settings", content, true);
        return serverSettingsSection;
    }

    private JPanel buildQuickActionsPanel() {
        JPanel content = new JPanel();
        markPanelTransparent(content);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        RoundedButton saveButton = createActionButton("Save Settings", e -> {
            configFromUi();
            saveConfig(true);
            appendLog("Settings saved to: " + ConfigService.getConfigFile());
        });
        RoundedButton startButton = createActionButton("Start Server", e -> doStart());
        RoundedButton stopButton = createActionButton("Safe Stop", e -> serverManager.safeStop(selectedDelay(safeStopCombo, 15)));
        RoundedButton restartButton = createActionButton("Restart Server", e -> serverManager.restart(selectedDelay(safeStopCombo, 15)));
        RoundedButton killButton = createActionButton("Force Kill", e -> {
            int result = JOptionPane.showConfirmDialog(frame,
                    "Force kill can risk world/save issues if used at the wrong time.\nUse only if the server is stuck.\n\nContinue?",
                    "Confirm Force Kill",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                serverManager.forceKill();
            }
        });
        RoundedButton cancelRestartButton = createActionButton("Cancel Restart", e -> cancelPendingRestart());

        statusLabel = new JLabel("Status: OFFLINE");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 15f));
        statusLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, statusLabel.getPreferredSize().height));
        autoRestartBox = new JCheckBox("Auto-restart on crash");
        restartDelayCombo = createDelayDropdown(300);
        safeStopCombo = createDelayDropdown(300);
        JLabel restartDelayLabel = createSectionLabel("Restart Delay (sec)");
        JLabel safeStopLabel = createSectionLabel("Safe Stop Wait (sec)");
        saveButton.setToolTipText("Save the current manager settings and sync the launcher when it is safe.");
        startButton.setToolTipText("Start the selected Minecraft server.");
        stopButton.setToolTipText("Send a normal stop command and wait before forcing shutdown.");
        restartButton.setToolTipText("Restart the server using the safe stop wait you selected.");
        cancelRestartButton.setToolTipText("Cancel a pending crash restart or delayed restart countdown.");
        killButton.setToolTipText("Force the server process to close if it is stuck.");
        statusLabel.setToolTipText("Shows the current server status.");
        autoRestartBox.setToolTipText("Restart the server automatically if it crashes unexpectedly.");
        restartDelayCombo.setToolTipText("How long to wait before restarting after a crash or restart action.");
        safeStopCombo.setToolTipText("How long the manager waits after sending stop before forcing shutdown.");
        restartDelayLabel.setToolTipText(restartDelayCombo.getToolTipText());
        safeStopLabel.setToolTipText(safeStopCombo.getToolTipText());

        content.add(statusLabel);
        content.add(Box.createVerticalStrut(10));
        content.add(saveButton);
        content.add(Box.createVerticalStrut(8));
        content.add(startButton);
        content.add(Box.createVerticalStrut(6));
        content.add(stopButton);
        content.add(Box.createVerticalStrut(6));
        content.add(restartButton);
        content.add(Box.createVerticalStrut(6));
        content.add(cancelRestartButton);
        content.add(Box.createVerticalStrut(6));
        content.add(killButton);
        content.add(Box.createVerticalStrut(12));
        content.add(autoRestartBox);
        content.add(Box.createVerticalStrut(10));
        content.add(restartDelayLabel);
        content.add(restartDelayCombo);
        content.add(Box.createVerticalStrut(10));
        content.add(safeStopLabel);
        content.add(safeStopCombo);

        alignSidebarComponents(content);
        return createSectionPanel("Quick Actions", content, false);
    }

    private void buildOptionsDialog() {
        optionsDialog = new JDialog(frame, "Options", false);
        optionsDialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        optionsDialog.setSize(560, 620);
        optionsDialog.setLocationRelativeTo(frame);

        JPanel content = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        startMinimizedBox = new JCheckBox("Start minimized");
        themeModeCombo = new JComboBox<>(new String[]{"Match Windows", "Dark", "Light", "Custom"});
        closeBehaviorCombo = new JComboBox<>(new String[]{"Close to tray icon", "Close to taskbar", "Exit application"});
        backgroundColorButton = createActionButton("Choose Background Color", e -> chooseCustomBackgroundColor());
        inputColorButton = createActionButton("Choose Input Color", e -> chooseCustomInputColor());
        playitCheckBox = new JCheckBox("Check for Playit.gg before starting");
        playitStartBox = new JCheckBox("Start Playit.gg if not running");
        playitPathField = new JTextField();
        playitBrowseButton = createActionButton("Browse", e -> browsePlayitExecutable());
        monitoringEnabledBox = new JCheckBox("Enable performance monitoring");
        monitorSystemCpuBox = new JCheckBox("Monitor system CPU usage");
        monitorSystemMemoryBox = new JCheckBox("Monitor system RAM usage");
        monitorServerMemoryBox = new JCheckBox("Monitor server memory target");
        monitorUptimeBox = new JCheckBox("Monitor server uptime");
        monitorTpsBox = new JCheckBox("Monitor TPS when available");
        monitorPlayerCountBox = new JCheckBox("Monitor player count when available");
        monitorLatencyBox = new JCheckBox("Monitor latency/ping when available");
        monitorJavaStatusBox = new JCheckBox("Monitor Java version/status");
        monitorServerTypeBox = new JCheckBox("Monitor server type/status");
        monitorRestartStateBox = new JCheckBox("Monitor restart state and reason");
        monitoringRateCombo = createPollingRateDropdown();

        startMinimizedBox.setToolTipText("Open the manager minimized instead of showing the full window immediately.");
        themeModeCombo.setToolTipText("Choose whether the app matches Windows, uses dark mode, light mode, or your custom colors.");
        closeBehaviorCombo.setToolTipText("Choose what happens when you close the manager window.");
        playitCheckBox.setToolTipText("Check whether Playit.gg is already running before the server starts.");
        playitStartBox.setToolTipText("If enabled, the manager will try to start Playit.gg when it is not already running.");
        playitPathField.setToolTipText("Optional path to your Playit.gg executable for automatic startup.");
        playitBrowseButton.setToolTipText("Choose the Playit.gg executable if you want the manager to start it for you.");
        monitoringEnabledBox.setToolTipText("Turn all performance monitoring on or off.");
        monitorSystemCpuBox.setToolTipText("Check overall CPU use on this computer.");
        monitorSystemMemoryBox.setToolTipText("Check overall RAM use on this computer.");
        monitorServerMemoryBox.setToolTipText("Show the server's configured Java memory target.");
        monitorUptimeBox.setToolTipText("Track how long the current server process has been running.");
        monitorTpsBox.setToolTipText("Only use TPS checks if your setup exposes that information safely.");
        monitorPlayerCountBox.setToolTipText("Only use player-count checks if they are available safely.");
        monitorLatencyBox.setToolTipText("Latency is only shown when it can be measured safely.");
        monitorJavaStatusBox.setToolTipText("Show the detected Java version and compatibility note.");
        monitorServerTypeBox.setToolTipText("Show the detected server type, such as Vanilla or Fabric.");
        monitorRestartStateBox.setToolTipText("Show whether a restart is pending and the last restart reason.");
        monitoringRateCombo.setToolTipText("Choose how often the performance window refreshes.");

        themeModeCombo.addActionListener(e -> {
            updateThemeControlState();
            applyTheme();
        });
        closeBehaviorCombo.addActionListener(e -> saveConfigSilently());
        monitoringEnabledBox.addActionListener(e -> {
            updateMonitoringControls();
            startPerformanceTimer();
        });
        for (JCheckBox box : List.of(
                monitorSystemCpuBox, monitorSystemMemoryBox, monitorServerMemoryBox, monitorUptimeBox,
                monitorTpsBox, monitorPlayerCountBox, monitorLatencyBox, monitorJavaStatusBox,
                monitorServerTypeBox, monitorRestartStateBox)) {
            box.addActionListener(e -> {
                startPerformanceTimer();
                updatePerformanceStats();
            });
        }
        monitoringRateCombo.addActionListener(e -> startPerformanceTimer());

        addOptionRow(content, gbc, "Theme", themeModeCombo);
        addOptionRow(content, gbc, "Close Button", closeBehaviorCombo);
        addOptionRow(content, gbc, "Custom Background", backgroundColorButton);
        addOptionRow(content, gbc, "Custom Inputs", inputColorButton);
        addOptionRow(content, gbc, "Playit.gg Path", buildLabeledInlinePanel(playitPathField, playitBrowseButton));
        addOptionRow(content, gbc, "Polling Rate", monitoringRateCombo);

        gbc.gridx = 0;
        gbc.gridwidth = 2;
        content.add(startMinimizedBox, gbc);
        gbc.gridy++;
        content.add(playitCheckBox, gbc);
        gbc.gridy++;
        content.add(playitStartBox, gbc);
        gbc.gridy++;
        content.add(monitoringEnabledBox, gbc);
        gbc.gridy++;
        content.add(monitorSystemCpuBox, gbc);
        gbc.gridy++;
        content.add(monitorSystemMemoryBox, gbc);
        gbc.gridy++;
        content.add(monitorServerMemoryBox, gbc);
        gbc.gridy++;
        content.add(monitorUptimeBox, gbc);
        gbc.gridy++;
        content.add(monitorTpsBox, gbc);
        gbc.gridy++;
        content.add(monitorPlayerCountBox, gbc);
        gbc.gridy++;
        content.add(monitorLatencyBox, gbc);
        gbc.gridy++;
        content.add(monitorJavaStatusBox, gbc);
        gbc.gridy++;
        content.add(monitorServerTypeBox, gbc);
        gbc.gridy++;
        content.add(monitorRestartStateBox, gbc);
        gbc.gridy++;

        JLabel hintLabel = new JLabel("<html>Monitoring pauses automatically while the app is minimized to the tray or taskbar.</html>");
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, 12f));
        content.add(hintLabel, gbc);

        RoundedButton saveOptionsButton = createActionButton("Save Options", e -> {
            configFromUi();
            saveConfig(false);
            startPerformanceTimer();
            optionsDialog.setVisible(false);
        });
        saveOptionsButton.setToolTipText("Save options and close this window.");
        gbc.gridy++;
        content.add(saveOptionsButton, gbc);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        optionsDialog.setContentPane(scrollPane);
        optionsDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                configFromUi();
                saveConfig(false);
                startPerformanceTimer();
            }
        });
    }

    private void buildPerformanceDialog() {
        performanceDialog = new JDialog(frame, "Performance Monitor", false);
        performanceDialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        performanceDialog.setSize(460, 420);
        performanceDialog.setLocationRelativeTo(frame);

        JPanel content = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        perfStatusLabel = createSectionLabel("Status: Offline");
        perfUptimeLabel = createSectionLabel("Uptime: 00:00:00");
        perfRestartLabel = createSectionLabel("Restart: None pending");
        perfSystemCpuLabel = createSectionLabel("System CPU: Unavailable");
        perfSystemMemoryLabel = createSectionLabel("System Memory: Unavailable");
        perfServerMemoryLabel = createSectionLabel("Server Memory: Unavailable");
        perfServerCpuLabel = createSectionLabel("Server CPU Time: Unavailable");
        perfJavaLabel = createSectionLabel("Java: Unavailable");
        perfTypeLabel = createSectionLabel("Server Type: Unknown");
        perfPlayersLabel = createSectionLabel("Players/TPS: Unavailable");

        perfStatusLabel.setToolTipText("Shows the current state of the managed Minecraft server process.");
        perfUptimeLabel.setToolTipText("How long the current server process has been running.");
        perfRestartLabel.setToolTipText("Shows whether a restart countdown is pending or was cancelled.");
        perfSystemCpuLabel.setToolTipText("Approximate total CPU usage on this computer.");
        perfSystemMemoryLabel.setToolTipText("Approximate total RAM usage on this computer.");
        perfServerMemoryLabel.setToolTipText("Shows the configured Java memory target for this server.");
        perfServerCpuLabel.setToolTipText("CPU time used by the server process when available.");
        perfJavaLabel.setToolTipText("Shows the detected Java runtime version and compatibility note.");
        perfTypeLabel.setToolTipText("Shows the detected server loader type.");
        perfPlayersLabel.setToolTipText("TPS, players, and ping only appear when they are available safely.");

        for (JLabel label : List.of(
                perfStatusLabel, perfUptimeLabel, perfRestartLabel, perfSystemCpuLabel,
                perfSystemMemoryLabel, perfServerMemoryLabel, perfServerCpuLabel,
                perfJavaLabel, perfTypeLabel, perfPlayersLabel)) {
            content.add(label, gbc);
            gbc.gridy++;
        }

        performanceDialog.setContentPane(content);
    }

    private void openOptionsDialog() {
        if (optionsDialog == null) {
            buildOptionsDialog();
            applyConfigToUi();
            applyTheme();
        }
        optionsDialog.setLocationRelativeTo(frame);
        optionsDialog.setVisible(true);
    }

    private void openPerformanceDialog() {
        if (performanceDialog == null) {
            buildPerformanceDialog();
            applyTheme();
        }
        updatePerformanceStats();
        performanceDialog.setLocationRelativeTo(frame);
        performanceDialog.setVisible(true);
    }

    private JPanel buildCenterPanel() {
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setToolTipText("Shows the full live console output from the Minecraft server.");
        consolePreviewArea = new JTextArea(3, 30);
        consolePreviewArea.setEditable(false);
        consolePreviewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        consolePreviewArea.setLineWrap(true);
        consolePreviewArea.setWrapStyleWord(true);
        consolePreviewArea.setToolTipText("Shows the last three console lines while the full console is collapsed.");
        consoleSection = createSectionPanel("Live Console", createWrappedScrollPane(logArea), true, createWrappedScrollPane(consolePreviewArea));
        return consoleSection;
    }

    private JPanel buildBottomPanel() {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        markPanelTransparent(content);
        commandField = new JTextField();
        commandField.setToolTipText("Send a server command directly to the running console.");
        RoundedButton sendButton = createActionButton("Send", e -> sendCommandFromBox());
        sendButton.setToolTipText("Send the typed command to the running server.");
        commandField.addActionListener(e -> sendCommandFromBox());
        RoundedButton openFolderButton = createActionButton("Open Server Folder", e -> openServerFolder());
        openFolderButton.setToolTipText("Open the selected server folder in Windows Explorer.");
        JPanel left = new JPanel(new BorderLayout(8, 8));
        markPanelTransparent(left);
        left.add(commandField, BorderLayout.CENTER);
        left.add(sendButton, BorderLayout.EAST);
        content.add(left, BorderLayout.CENTER);
        content.add(openFolderButton, BorderLayout.EAST);
        commandSection = createSectionPanel("Send Command", content, false);
        return commandSection;
    }

    private CollapsibleSection createSectionPanel(String title, JComponent content, boolean collapsible) {
        return createSectionPanel(title, content, collapsible, null);
    }

    private CollapsibleSection createSectionPanel(String title, JComponent content, boolean collapsible, JComponent collapsedPreview) {
        CollapsibleSection panel = new CollapsibleSection(title, content, collapsible, collapsedPreview);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return panel;
    }

    private JScrollPane createWrappedScrollPane(JComponent component) {
        JScrollPane scrollPane = new JScrollPane(component);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        if (component.getToolTipText() != null) {
            scrollPane.setToolTipText(component.getToolTipText());
        }
        return scrollPane;
    }

    private void addLabeledRow(JPanel panel, GridBagConstraints gbc, int row, String label, JTextField field, JButton button) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        JLabel sectionLabel = createSectionLabel(label);
        String tooltip = tooltipForSetting(label);
        sectionLabel.setToolTipText(tooltip);
        if (field.getToolTipText() == null) {
            field.setToolTipText(tooltip);
        }
        if (button != null && button.getToolTipText() == null) {
            button.setToolTipText(tooltip);
        }
        panel.add(sectionLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(button != null ? button : Box.createHorizontalStrut(1), gbc);
    }

    private void addOptionRow(JPanel panel, GridBagConstraints gbc, String label, JComponent value) {
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        JLabel optionLabel = createSectionLabel(label);
        String tooltip = tooltipForOption(label);
        if (tooltip != null) {
            optionLabel.setToolTipText(tooltip);
            if (value.getToolTipText() == null) {
                value.setToolTipText(tooltip);
            }
        }
        panel.add(optionLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(value, gbc);
        gbc.gridy++;
    }

    private JLabel createSectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 13f));
        return label;
    }

    private String tooltipForSetting(String label) {
        return switch (label) {
            case "Server Folder" -> "The main folder that contains your Minecraft server files.";
            case "Server Jar" -> "The server jar file the manager launches.";
            case "Java Path" -> "Path to Java. Leave it as java unless you need a specific Java install.";
            case "Xms" -> "Starting memory allocation for Java. Usually lower than or equal to Xmx.";
            case "Xmx" -> "Maximum memory the server can use.";
            case "JVM Args" -> "Advanced Java launch options. Only change these if you know what they do.";
            case "Server Args" -> "Extra server launch arguments such as nogui.";
            default -> null;
        };
    }

    private String tooltipForOption(String label) {
        return switch (label) {
            case "Theme" -> "Choose whether the app matches Windows, uses dark mode, light mode, or your own custom colors.";
            case "Close Button" -> "Choose what happens when you close the manager window.";
            case "Custom Background" -> "Pick the main background color when using the custom theme.";
            case "Custom Inputs" -> "Pick the color used for text boxes and console areas in the custom theme.";
            case "Playit.gg Path" -> "Optional path to your Playit.gg executable for automatic startup.";
            case "Polling Rate" -> "Choose how often the performance monitor refreshes.";
            default -> null;
        };
    }

    private void alignSidebarComponents(JPanel panel) {
        for (Component component : panel.getComponents()) {
            if (component instanceof JComponent jComponent) {
                jComponent.setAlignmentX(Component.LEFT_ALIGNMENT);
                if (jComponent instanceof JButton button) {
                    button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
                } else if (jComponent instanceof JComboBox<?> comboBox) {
                    comboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, comboBox.getPreferredSize().height));
                }
            }
        }
    }
    private RoundedButton browseFolderButton() {
        folderBrowseButton = createActionButton("Browse", e -> {
            JFileChooser chooser = new JFileChooser(folderField.getText().isBlank() ? null : new java.io.File(folderField.getText()));
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                folderField.setText(chooser.getSelectedFile().getAbsolutePath());
                autoDetectServerSettings(true);
            }
        });
        return (RoundedButton) folderBrowseButton;
    }

    private RoundedButton browseJarButton() {
        jarBrowseButton = createActionButton("Browse", e -> {
            JFileChooser chooser = new JFileChooser(folderField.getText().isBlank() ? null : new java.io.File(folderField.getText()));
            chooser.setFileFilter(new FileNameExtensionFilter("Java Archive (*.jar)", "jar"));
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                Path selected = chooser.getSelectedFile().toPath();
                jarField.setText(selected.getFileName().toString());
                if (folderField.getText().isBlank() && selected.getParent() != null) {
                    folderField.setText(selected.getParent().toString());
                }
                autoDetectServerSettings(true);
            }
        });
        return (RoundedButton) jarBrowseButton;
    }

    private RoundedButton createActionButton(String text, ActionListener listener) {
        RoundedButton button = new RoundedButton(text);
        button.addActionListener(listener);
        return button;
    }

    private JPanel buildLabeledInlinePanel(JComponent center, JComponent east) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        markPanelTransparent(panel);
        panel.add(center, BorderLayout.CENTER);
        panel.add(east, BorderLayout.EAST);
        return panel;
    }

    private JComboBox<Integer> createDelayDropdown(int maxSeconds) {
        List<Integer> values = new ArrayList<>();
        for (int value = 15; value <= maxSeconds; value += 15) {
            values.add(value);
        }
        JComboBox<Integer> comboBox = new JComboBox<>(values.toArray(Integer[]::new));
        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Integer seconds) {
                    setText(seconds + " sec");
                }
                return this;
            }
        });
        Dimension preferred = comboBox.getPreferredSize();
        int targetHeight = Math.max(34, preferred.height + 8);
        comboBox.setPreferredSize(new Dimension(preferred.width, targetHeight));
        comboBox.setMinimumSize(new Dimension(120, targetHeight));
        comboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, targetHeight));
        return comboBox;
    }

    private JComboBox<Integer> createPollingRateDropdown() {
        JComboBox<Integer> comboBox = new JComboBox<>(new Integer[]{1, 2, 5, 10, 15, 30});
        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Integer seconds) {
                    setText(seconds + " sec");
                }
                return this;
            }
        });
        Dimension preferred = comboBox.getPreferredSize();
        int targetHeight = Math.max(34, preferred.height + 8);
        comboBox.setPreferredSize(new Dimension(preferred.width, targetHeight));
        comboBox.setMinimumSize(new Dimension(120, targetHeight));
        comboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, targetHeight));
        return comboBox;
    }

    private int nearestDelayValue(int value) {
        int minimum = 15;
        int normalized = Math.max(minimum, value);
        return ((normalized + 14) / 15) * 15;
    }

    private int selectedDelay(JComboBox<Integer> comboBox, int fallback) {
        Object selected = comboBox.getSelectedItem();
        return selected instanceof Integer seconds ? seconds : fallback;
    }

    private int nearestPollingRate(int value) {
        int[] allowed = {1, 2, 5, 10, 15, 30};
        int nearest = allowed[0];
        int smallestGap = Math.abs(value - nearest);
        for (int allowedValue : allowed) {
            int gap = Math.abs(value - allowedValue);
            if (gap < smallestGap) {
                nearest = allowedValue;
                smallestGap = gap;
            }
        }
        return nearest;
    }

    private void applyRecommendedJvmArgs() {
        LaunchFileService.Recommendation recommendation = LaunchFileService.buildRecommendedJvmArgs(detectedServerType, xmxField.getText().trim(), jvmArgsArea.getText().trim());
        if (!recommendation.canApply()) {
            appendLog(recommendation.message());
            return;
        }
        jvmArgsArea.setText(recommendation.args());
        jvmRecommendationLabel.setText("JVM Recommendation: applied suggested args.");
        appendLog("Applied recommended JVM args.");
    }

    private void saveServerSettings() {
        configFromUi();
        saveConfig(true);
        appendLog("Server settings saved.");
    }

    private void browsePlayitExecutable() {
        JFileChooser chooser = new JFileChooser(playitPathField.getText().isBlank() ? null : new java.io.File(playitPathField.getText()));
        chooser.setFileFilter(new FileNameExtensionFilter("Executable Files (*.exe, *.bat, *.cmd)", "exe", "bat", "cmd"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            playitPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void detectJavaRuntime(boolean applyToField) {
        configFromUi();
        Path serverFolder = safePath(config.serverFolder);
        detectedJavaInfo = JavaDetectionService.detect(config.javaPath, serverFolder, config.jarFile, detectedServerType);
        if (applyToField && detectedJavaInfo.detectedPath() != null && !detectedJavaInfo.detectedPath().isBlank() && !settingsLocked) {
            javaField.setText(detectedJavaInfo.detectedPath());
        }
        javaStatusLabel.setText("Java Status: " + detectedJavaInfo.summary());
        appendLog(detectedJavaInfo.summary());
        updatePerformanceStats();
    }

    private void openJavaDownloadHelp() {
        try {
            Desktop.getDesktop().browse(URI.create("https://adoptium.net/"));
        } catch (IOException e) {
            appendLog("Could not open Java download help: " + e.getMessage());
        }
    }

    private void chooseCustomBackgroundColor() {
        Color chosen = JColorChooser.showDialog(frame, "Choose App Background Color", customBackgroundColor);
        if (chosen != null) {
            customBackgroundColor = chosen;
            updateColorButtonLabel(backgroundColorButton, customBackgroundColor);
            if (THEME_CUSTOM.equals(getSelectedThemeMode())) {
                applyTheme();
            }
        }
    }

    private void chooseCustomInputColor() {
        Color chosen = JColorChooser.showDialog(frame, "Choose Input Color", customInputColor);
        if (chosen != null) {
            customInputColor = chosen;
            updateColorButtonLabel(inputColorButton, customInputColor);
            if (THEME_CUSTOM.equals(getSelectedThemeMode())) {
                applyTheme();
            }
        }
    }

    private void updateColorButtonLabel(AbstractButton button, Color color) {
        if (button != null) {
            button.setText("Color " + toHex(color));
        }
    }

    private void autoDetectServerSettings(boolean userRequested) {
        if (settingsLocked && !userRequested) {
            appendLog("Server settings are locked. Detection skipped.");
            return;
        }
        Path serverFolder = safePath(folderField.getText().trim());
        Path jarPath = null;
        if (serverFolder != null && !jarField.getText().isBlank()) {
            jarPath = serverFolder.resolve(jarField.getText().trim());
        }

        ServerSetupDetector.DetectionResult detection = ServerSetupDetector.detect(serverFolder, jarPath);
        Path launchFile = LaunchFileService.detectPrimaryLaunchFile(serverFolder);
        updateDetectedType(detection.serverType());

        if (launchFile != null) {
            appendLog("Detected custom " + launchFile.getFileName() + ".");
        }

        if (settingsLocked && userRequested) {
            appendLog("Server settings are locked. Detection results were not applied.");
            return;
        }

        if (detection.jarPath() != null && (userRequested || jarField.getText().isBlank())) {
            jarField.setText(detection.jarPath());
        }
        if (detection.javaPath() != null && (userRequested || javaField.getText().isBlank() || "java".equalsIgnoreCase(javaField.getText().trim()))) {
            javaField.setText(detection.javaPath());
        }
        boolean appliedMemorySettings = false;
        if (detection.xms() != null && (userRequested || xmsField.getText().isBlank())) {
            xmsField.setText(detection.xms());
            appendLog("Detected Xms: " + detection.xms());
            appliedMemorySettings = true;
        }
        if (detection.xmx() != null && (userRequested || xmxField.getText().isBlank())) {
            xmxField.setText(detection.xmx());
            appendLog("Detected Xmx: " + detection.xmx());
            appliedMemorySettings = true;
        }
        if (detection.jvmArgs() != null && (userRequested || jvmArgsArea.getText().isBlank() || jvmArgsArea.getText().equals(new AppConfig().jvmArgs))) {
            jvmArgsArea.setText(detection.jvmArgs());
        }
        if (detection.serverArgs() != null && (userRequested || serverArgsField.getText().isBlank() || "nogui".equalsIgnoreCase(serverArgsField.getText().trim()))) {
            serverArgsField.setText(detection.serverArgs());
        }

        for (String message : detection.messages()) {
            appendLog(message);
        }
        if (appliedMemorySettings) {
            appendLog("Applied detected memory settings from " + (launchFile != null ? launchFile.getFileName() : "detected launcher") + ".");
        } else if (detection.xms() != null || detection.xmx() != null) {
            appendLog("Detected memory settings, but kept your current Xms/Xmx values.");
        }
        appendLog("Detected server type: " + detection.serverType().displayName());
        updateJvmRecommendation();
        detectJavaRuntime(false);
    }

    private void updateDetectedType(ServerSetupDetector.ServerType serverType) {
        detectedServerType = serverType != null ? serverType : ServerSetupDetector.ServerType.UNKNOWN;
        if (detectedTypeLabel != null) {
            detectedTypeLabel.setText("Detected Type: " + detectedServerType.displayName());
        }
    }

    private void updateJvmRecommendation() {
        LaunchFileService.Recommendation recommendation = LaunchFileService.buildRecommendedJvmArgs(detectedServerType, xmxField.getText().trim(), jvmArgsArea.getText().trim());
        if (jvmRecommendationLabel != null) {
            jvmRecommendationLabel.setText("JVM Recommendation: " + recommendation.message());
        }
        if (applyRecommendedJvmButton != null) {
            applyRecommendedJvmButton.setEnabled(recommendation.canApply() && !settingsLocked);
        }
    }

    private void setSettingsLocked(boolean locked) {
        settingsLocked = locked;
        if (settingsLockButton != null) {
            settingsLockButton.setText(locked ? "\uD83D\uDD12 Locked" : "\uD83D\uDD13 Unlocked");
        }
        setEditable(profileNameField, !locked);
        setEditable(folderField, !locked);
        setEditable(jarField, !locked);
        setEditable(javaField, !locked);
        setEditable(xmsField, !locked);
        setEditable(xmxField, !locked);
        setEditable(serverArgsField, !locked);
        setEditable(jvmArgsArea, !locked);
        if (folderBrowseButton != null) {
            folderBrowseButton.setEnabled(!locked);
        }
        if (jarBrowseButton != null) {
            jarBrowseButton.setEnabled(!locked);
        }
        if (detectSettingsButton != null) {
            detectSettingsButton.setEnabled(!locked);
        }
        if (saveServerSettingsButton != null) {
            saveServerSettingsButton.setEnabled(!locked);
        }
        updateJvmRecommendation();
    }

    private void setEditable(JTextComponent textComponent, boolean editable) {
        if (textComponent != null) {
            textComponent.setEditable(editable);
        }
    }

    private Path safePath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Paths.get(value.trim());
        } catch (Exception e) {
            appendLog("Could not use path for detection: " + value);
            return null;
        }
    }

    private void applyConfigToUi() {
        profileNameField.setText(config.profileName);
        folderField.setText(config.serverFolder);
        jarField.setText(config.jarFile);
        javaField.setText(config.javaPath);
        xmsField.setText(config.xms);
        xmxField.setText(config.xmx);
        serverArgsField.setText(config.serverArgs);
        jvmArgsArea.setText(config.jvmArgs);
        autoRestartBox.setSelected(config.autoRestartOnCrash);
        if (startMinimizedBox != null) {
            startMinimizedBox.setSelected(config.startMinimized);
        }
        restartDelayCombo.setSelectedItem(nearestDelayValue(config.autoRestartDelaySeconds));
        safeStopCombo.setSelectedItem(nearestDelayValue(config.safeStopWaitSeconds));
        if (themeModeCombo != null) {
            themeModeCombo.setSelectedItem(labelForThemeMode(resolveThemeMode()));
        }
        if (closeBehaviorCombo != null) {
            closeBehaviorCombo.setSelectedItem(labelForCloseBehavior(resolveCloseBehavior()));
        }
        customBackgroundColor = parseColor(config.customBackgroundColor, customBackgroundColor);
        customInputColor = parseColor(config.customInputColor, customInputColor);
        updateColorButtonLabel(backgroundColorButton, customBackgroundColor);
        updateColorButtonLabel(inputColorButton, customInputColor);
        if (playitCheckBox != null) {
            playitCheckBox.setSelected(config.checkPlayitBeforeStart);
        }
        if (playitStartBox != null) {
            playitStartBox.setSelected(config.startPlayitIfMissing);
        }
        if (playitPathField != null) {
            playitPathField.setText(config.playitExecutablePath);
        }
        if (monitoringEnabledBox != null) {
            monitoringEnabledBox.setSelected(config.monitoringEnabled);
            monitorSystemCpuBox.setSelected(config.monitorSystemCpu);
            monitorSystemMemoryBox.setSelected(config.monitorSystemMemory);
            monitorServerMemoryBox.setSelected(config.monitorServerMemory);
            monitorUptimeBox.setSelected(config.monitorUptime);
            monitorTpsBox.setSelected(config.monitorTps);
            monitorPlayerCountBox.setSelected(config.monitorPlayerCount);
            monitorLatencyBox.setSelected(config.monitorLatency);
            monitorJavaStatusBox.setSelected(config.monitorJavaStatus);
            monitorServerTypeBox.setSelected(config.monitorServerType);
            monitorRestartStateBox.setSelected(config.monitorRestartState);
            monitoringRateCombo.setSelectedItem(nearestPollingRate(config.monitoringPollingSeconds));
        }
        updateThemeControlState();
        updateMonitoringControls();
        setSettingsLocked(config.settingsLocked);
        if (serverSettingsSection != null) serverSettingsSection.setCollapsed(config.serverSettingsCollapsed);
        if (sidebarSection != null) sidebarSection.setCollapsed(config.sidebarCollapsed);
        if (consoleSection != null) consoleSection.setCollapsed(config.consoleCollapsed);
        if (commandSection != null) commandSection.setCollapsed(config.commandCollapsed);
        Path configFolder = safePath(config.serverFolder);
        updateDetectedType(ServerSetupDetector.detect(
                configFolder,
                configFolder == null || config.jarFile == null || config.jarFile.isBlank() ? null : configFolder.resolve(config.jarFile)
        ).serverType());
        detectJavaRuntime(false);
        updateJvmRecommendation();
    }

    private void configFromUi() {
        config.profileName = profileNameField.getText().trim();
        config.serverFolder = folderField.getText().trim();
        config.jarFile = jarField.getText().trim();
        config.javaPath = javaField.getText().trim().isEmpty() ? "java" : javaField.getText().trim();
        config.xms = xmsField.getText().trim();
        config.xmx = xmxField.getText().trim();
        config.serverArgs = serverArgsField.getText().trim();
        config.jvmArgs = jvmArgsArea.getText().trim();
        config.autoRestartOnCrash = autoRestartBox.isSelected();
        config.startMinimized = startMinimizedBox != null && startMinimizedBox.isSelected();
        config.autoRestartDelaySeconds = selectedDelay(restartDelayCombo, 15);
        config.safeStopWaitSeconds = selectedDelay(safeStopCombo, 15);
        config.themeMode = getSelectedThemeMode();
        config.closeBehavior = getSelectedCloseBehavior();
        config.customBackgroundColor = toHex(customBackgroundColor);
        config.customInputColor = toHex(customInputColor);
        config.checkPlayitBeforeStart = playitCheckBox != null && playitCheckBox.isSelected();
        config.startPlayitIfMissing = playitStartBox != null && playitStartBox.isSelected();
        config.playitExecutablePath = playitPathField != null ? playitPathField.getText().trim() : "";
        config.monitoringEnabled = monitoringEnabledBox == null || monitoringEnabledBox.isSelected();
        config.monitorSystemCpu = monitorSystemCpuBox == null || monitorSystemCpuBox.isSelected();
        config.monitorSystemMemory = monitorSystemMemoryBox == null || monitorSystemMemoryBox.isSelected();
        config.monitorServerMemory = monitorServerMemoryBox == null || monitorServerMemoryBox.isSelected();
        config.monitorUptime = monitorUptimeBox == null || monitorUptimeBox.isSelected();
        config.monitorTps = monitorTpsBox != null && monitorTpsBox.isSelected();
        config.monitorPlayerCount = monitorPlayerCountBox != null && monitorPlayerCountBox.isSelected();
        config.monitorLatency = monitorLatencyBox != null && monitorLatencyBox.isSelected();
        config.monitorJavaStatus = monitorJavaStatusBox == null || monitorJavaStatusBox.isSelected();
        config.monitorServerType = monitorServerTypeBox == null || monitorServerTypeBox.isSelected();
        config.monitorRestartState = monitorRestartStateBox == null || monitorRestartStateBox.isSelected();
        config.monitoringPollingSeconds = selectedDelay(monitoringRateCombo, 2);
        config.minimizeToTray = CLOSE_TO_TRAY.equals(config.closeBehavior);
        config.settingsLocked = settingsLocked;
        config.serverSettingsCollapsed = serverSettingsSection != null && serverSettingsSection.isCollapsed();
        config.sidebarCollapsed = sidebarSection != null && sidebarSection.isCollapsed();
        config.consoleCollapsed = consoleSection != null && consoleSection.isCollapsed();
        config.commandCollapsed = commandSection != null && commandSection.isCollapsed();
    }

    private void updateThemeControlState() {
        boolean customEnabled = THEME_CUSTOM.equals(getSelectedThemeMode());
        if (backgroundColorButton != null) {
            backgroundColorButton.setEnabled(customEnabled);
        }
        if (inputColorButton != null) {
            inputColorButton.setEnabled(customEnabled);
        }
    }

    private void applyTheme() {
        if (frame == null) {
            return;
        }
        configFromUi();
        ThemePalette palette = createThemePalette();
        applyThemeToComponent(frame.getContentPane(), palette);
        frame.getContentPane().setBackground(palette.windowBackground);
        if (optionsDialog != null) {
            applyThemeToDialog(optionsDialog, palette);
        }
        if (performanceDialog != null) {
            applyThemeToDialog(performanceDialog, palette);
        }
        frame.repaint();
    }

    private void applyThemeToDialog(JDialog dialog, ThemePalette palette) {
        applyThemeToComponent(dialog.getContentPane(), palette);
        dialog.getContentPane().setBackground(palette.windowBackground);
        dialog.repaint();
    }

    private ThemePalette createThemePalette() {
        String themeMode = getSelectedThemeMode();
        return switch (themeMode) {
            case THEME_DARK -> new ThemePalette(new Color(24, 28, 34), new Color(33, 38, 46), new Color(44, 51, 61), new Color(224, 229, 236), new Color(170, 180, 194), new Color(74, 88, 103), new Color(67, 131, 214), Color.WHITE);
            case THEME_LIGHT -> new ThemePalette(new Color(240, 244, 249), new Color(252, 253, 255), new Color(255, 255, 255), new Color(31, 37, 45), new Color(89, 99, 112), new Color(206, 216, 228), new Color(74, 128, 204), Color.WHITE);
            case THEME_CUSTOM -> new ThemePalette(customBackgroundColor, mix(customBackgroundColor, Color.WHITE, isDark(customBackgroundColor) ? 0.08f : 0.35f), customInputColor, contrastColor(customBackgroundColor), mix(contrastColor(customBackgroundColor), customBackgroundColor, 0.35f), mix(customInputColor, contrastColor(customInputColor), isDark(customInputColor) ? 0.18f : 0.45f), mix(customInputColor, customBackgroundColor, 0.45f), contrastColor(mix(customInputColor, customBackgroundColor, 0.45f)));
            default -> {
                Color window = getUiColor("Panel.background", new Color(240, 240, 240));
                Color input = getUiColor("TextField.background", Color.WHITE);
                Color text = getUiColor("Label.foreground", Color.BLACK);
                Color border = mix(window, text, 0.18f);
                Color button = getUiColor("Button.background", mix(window, text, 0.12f));
                yield new ThemePalette(window, mix(window, Color.WHITE, isDark(window) ? 0.08f : 0.35f), input, text, mix(text, window, 0.42f), border, button, contrastColor(button));
            }
        };
    }

    private void applyThemeToComponent(Component component, ThemePalette palette) {
        if (component instanceof RoundedPanel roundedPanel) {
            roundedPanel.setThemeColors(palette.sectionBackground, palette.borderColor);
        }
        if (component instanceof JPanel panel) {
            boolean transparentPanel = isTransparentPanel(panel);
            panel.setOpaque(!transparentPanel && !(panel instanceof RoundedPanel));
            panel.setBackground(resolvePanelBackground(panel, palette));
        }
        if (component instanceof JViewport viewport) {
            viewport.setOpaque(true);
            viewport.setBackground(resolveViewportBackground(viewport, palette));
        }
        if (component instanceof JLabel label) {
            label.setForeground(palette.textColor);
        }
        if (component instanceof JCheckBox checkBox) {
            checkBox.setOpaque(isTransparentPanel(checkBox.getParent()));
            checkBox.setBackground(resolvePanelBackground(checkBox.getParent(), palette));
            checkBox.setForeground(palette.textColor);
        }
        if (component instanceof JTextComponent textComponent) {
            textComponent.setOpaque(true);
            textComponent.setBackground(palette.inputBackground);
            textComponent.setForeground(contrastColor(palette.inputBackground));
            textComponent.setCaretColor(contrastColor(palette.inputBackground));
            textComponent.setBorder(createRoundedInputBorder(palette.borderColor));
            if (textComponent instanceof JTextArea textArea) {
                textArea.setSelectionColor(mix(palette.buttonBackground, palette.inputBackground, 0.35f));
                textArea.setSelectedTextColor(contrastColor(textArea.getSelectionColor()));
            }
        }
        if (component instanceof JComboBox<?> comboBox) {
            comboBox.setOpaque(true);
            comboBox.setBackground(palette.inputBackground);
            comboBox.setForeground(contrastColor(palette.inputBackground));
            comboBox.setBorder(createRoundedInputBorder(palette.borderColor));
        }
        if (component instanceof JSpinner spinner) {
            spinner.setOpaque(true);
            spinner.setBackground(palette.inputBackground);
            spinner.setBorder(createRoundedInputBorder(palette.borderColor));
            Component editor = spinner.getEditor();
            if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
                defaultEditor.getTextField().setOpaque(true);
                defaultEditor.getTextField().setBackground(palette.inputBackground);
                defaultEditor.getTextField().setForeground(contrastColor(palette.inputBackground));
                defaultEditor.getTextField().setCaretColor(contrastColor(palette.inputBackground));
                defaultEditor.getTextField().setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            }
        }
        if (component instanceof JScrollPane scrollPane) {
            Color scrollBackground = resolveScrollPaneBackground(scrollPane, palette);
            scrollPane.setOpaque(true);
            scrollPane.setBackground(scrollBackground);
            scrollPane.getViewport().setBackground(scrollBackground);
            scrollPane.setBorder(isInputScrollPane(scrollPane) ? createRoundedInputBorder(palette.borderColor) : BorderFactory.createEmptyBorder());
        }
        if (component instanceof RoundedButton button) {
            button.setThemeColors(palette.buttonBackground, palette.buttonText, palette.borderColor);
        }
        if (component instanceof JComponent jComponent) {
            jComponent.setFont(jComponent.getFont().deriveFont(Font.PLAIN, 13f));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyThemeToComponent(child, palette);
            }
        }
    }

    private Border createRoundedInputBorder(Color borderColor) {
        return new CompoundBorder(new RoundedBorder(borderColor, 12), new EmptyBorder(6, 10, 6, 10));
    }

    private void markPanelTransparent(JPanel panel) {
        panel.putClientProperty(TRANSPARENT_PANEL_PROPERTY, Boolean.TRUE);
        panel.setOpaque(false);
    }

    private boolean isTransparentPanel(Component component) {
        return component instanceof JComponent jComponent
                && Boolean.TRUE.equals(jComponent.getClientProperty(TRANSPARENT_PANEL_PROPERTY));
    }

    private Color resolvePanelBackground(Component component, ThemePalette palette) {
        if (component instanceof RoundedPanel) {
            return palette.sectionBackground;
        }
        return isTransparentPanel(component) ? palette.windowBackground : palette.windowBackground;
    }

    private Color resolveViewportBackground(JViewport viewport, ThemePalette palette) {
        Component view = viewport.getView();
        if (view instanceof JTextComponent || view instanceof JTable || view instanceof JList<?>) {
            return palette.inputBackground;
        }
        return palette.windowBackground;
    }

    private boolean isInputScrollPane(JScrollPane scrollPane) {
        Component view = scrollPane.getViewport().getView();
        return view instanceof JTextComponent || view instanceof JTable || view instanceof JList<?>;
    }

    private Color resolveScrollPaneBackground(JScrollPane scrollPane, ThemePalette palette) {
        return isInputScrollPane(scrollPane) ? palette.inputBackground : palette.windowBackground;
    }

    private Color getUiColor(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }

    private void doStart() {
        configFromUi();
        if (!validateConfig()) {
            return;
        }
        ensurePlayitBeforeStart();
        saveConfig(true);
        try {
            serverManager.start(config);
        } catch (Exception ex) {
            appendLog("Failed to start server: " + ex.getMessage());
            JOptionPane.showMessageDialog(frame, "Could not start the server.\n\n" + ex.getMessage(), "Start Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void ensurePlayitBeforeStart() {
        if (!playitCheckBox.isSelected()) {
            return;
        }
        if (isPlayitRunning()) {
            appendLog("Playit.gg detected as already running.");
            return;
        }
        if (!playitStartBox.isSelected()) {
            appendLog("Playit.gg is not running.");
            return;
        }
        String playitPath = playitPathField.getText().trim();
        if (playitPath.isBlank()) {
            appendLog("Playit.gg start requested, but executable path is not configured.");
            return;
        }
        try {
            appendLog("Playit.gg not running; starting it now.");
            new ProcessBuilder(playitPath).start();
        } catch (IOException e) {
            appendLog("Could not start Playit.gg: " + e.getMessage());
        }
    }

    private boolean isPlayitRunning() {
        return ProcessHandle.allProcesses()
                .map(ProcessHandle::info)
                .flatMap(info -> info.command().stream())
                .map(command -> command.toLowerCase(Locale.ROOT))
                .anyMatch(command -> command.contains("playit"));
    }

    private void cancelPendingRestart() {
        if (serverManager.cancelPendingRestart()) {
            appendLog("Restart cancelled by user.");
            updatePerformanceStats();
        } else {
            appendLog("No pending restart to cancel.");
        }
    }

    private String getLastConsoleLines(int count) {
        int fromIndex = Math.max(0, recentConsoleLines.size() - count);
        return String.join(System.lineSeparator(), recentConsoleLines.subList(fromIndex, recentConsoleLines.size()));
    }

    private void startPerformanceTimer() {
        configFromUi();
        if (performanceTimer != null) {
            performanceTimer.stop();
        }
        if (!shouldRunMonitoring()) {
            updatePerformanceStats();
            return;
        }
        int delayMillis = Math.max(1000, config.monitoringPollingSeconds * 1000);
        performanceTimer = new Timer(delayMillis, e -> updatePerformanceStats());
        performanceTimer.start();
        updatePerformanceStats();
    }

    private void updatePerformanceStats() {
        if (perfStatusLabel == null) {
            return;
        }
        if (!config.monitoringEnabled) {
            setMonitoringDisabledMessage("Monitoring is disabled in Options.");
            return;
        }
        if (monitoringPausedForVisibility) {
            setMonitoringDisabledMessage("Monitoring is paused while the app is minimized.");
            return;
        }

        perfStatusLabel.setText("Status: " + serverManager.getState().name());
        perfUptimeLabel.setText(config.monitorUptime ? "Uptime: " + formatDuration(serverManager.getUptimeMillis()) : "Uptime: Disabled");
        perfRestartLabel.setText(config.monitorRestartState
                ? (serverManager.isRestartPending()
                ? "Restart: Crash restart scheduled in " + serverManager.getPendingRestartSeconds() + " sec"
                : "Restart: " + serverManager.getLastRestartReason())
                : "Restart: Disabled");
        perfTypeLabel.setText(config.monitorServerType ? "Server Type: " + detectedServerType.displayName() : "Server Type: Disabled");
        perfJavaLabel.setText(config.monitorJavaStatus ? "Java: " + detectedJavaInfo.shortDisplay() : "Java: Disabled");
        perfServerMemoryLabel.setText(config.monitorServerMemory
                ? "Server Memory: Xms " + formatMemoryValue(xmsField != null ? xmsField.getText().trim() : "")
                + " / Xmx " + formatMemoryValue(xmxField != null ? xmxField.getText().trim() : "")
                : "Server Memory: Disabled");

        if (config.monitorTps || config.monitorPlayerCount || config.monitorLatency) {
            List<String> parts = new ArrayList<>();
            if (config.monitorTps) {
                parts.add("TPS unavailable");
            }
            if (config.monitorPlayerCount) {
                parts.add("players unavailable");
            }
            if (config.monitorLatency) {
                parts.add("latency unavailable");
            }
            perfPlayersLabel.setText("Players/TPS: " + String.join(", ", parts));
        } else {
            perfPlayersLabel.setText("Players/TPS: Disabled");
        }

        if (config.monitorSystemCpu || config.monitorSystemMemory) {
            try {
                com.sun.management.OperatingSystemMXBean osBean =
                        (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                if (config.monitorSystemCpu) {
                    double cpuLoad = osBean.getCpuLoad();
                    perfSystemCpuLabel.setText("System CPU: " + (cpuLoad >= 0 ? Math.round(cpuLoad * 100) + "%" : "Unavailable"));
                } else {
                    perfSystemCpuLabel.setText("System CPU: Disabled");
                }
                if (config.monitorSystemMemory) {
                    double usedMemory = 1d - ((double) osBean.getFreeMemorySize() / (double) osBean.getTotalMemorySize());
                    perfSystemMemoryLabel.setText("System Memory: " + Math.round(usedMemory * 100) + "% used");
                } else {
                    perfSystemMemoryLabel.setText("System Memory: Disabled");
                }
            } catch (Exception e) {
                perfSystemCpuLabel.setText(config.monitorSystemCpu ? "System CPU: Unavailable" : "System CPU: Disabled");
                perfSystemMemoryLabel.setText(config.monitorSystemMemory ? "System Memory: Unavailable" : "System Memory: Disabled");
            }
        } else {
            perfSystemCpuLabel.setText("System CPU: Disabled");
            perfSystemMemoryLabel.setText("System Memory: Disabled");
        }

        long serverCpuMillis = serverManager.getProcessCpuMillis();
        perfServerCpuLabel.setText(config.monitorSystemCpu || config.monitorServerMemory
                ? (serverCpuMillis >= 0 ? "Server CPU Time: " + formatDuration(serverCpuMillis) : "Server CPU Time: Unavailable")
                : "Server CPU Time: Disabled");
    }

    private void setMonitoringDisabledMessage(String message) {
        perfStatusLabel.setText("Status: " + serverManager.getState().name());
        perfUptimeLabel.setText(message);
        perfRestartLabel.setText("Restart: " + (config.monitorRestartState ? serverManager.getLastRestartReason() : "Disabled"));
        perfSystemCpuLabel.setText("System CPU: Paused");
        perfSystemMemoryLabel.setText("System Memory: Paused");
        perfServerMemoryLabel.setText("Server Memory: Paused");
        perfServerCpuLabel.setText("Server CPU Time: Paused");
        perfJavaLabel.setText("Java: " + (config.monitorJavaStatus ? detectedJavaInfo.shortDisplay() : "Disabled"));
        perfTypeLabel.setText("Server Type: " + (config.monitorServerType ? detectedServerType.displayName() : "Disabled"));
        perfPlayersLabel.setText("Players/TPS: Paused");
    }

    private boolean shouldRunMonitoring() {
        return config.monitoringEnabled && !monitoringPausedForVisibility;
    }

    private void pauseMonitoringForHiddenUi(String logMessage) {
        if (monitoringPausedForVisibility) {
            return;
        }
        monitoringPausedForVisibility = true;
        if (performanceTimer != null) {
            performanceTimer.stop();
        }
        if (logMessage != null) {
            appendLog(logMessage);
        }
        updatePerformanceStats();
    }

    private void resumeMonitoringIfAllowed(String logMessage) {
        if (!monitoringPausedForVisibility) {
            return;
        }
        monitoringPausedForVisibility = false;
        if (config.monitoringEnabled) {
            if (logMessage != null) {
                appendLog(logMessage);
            }
            startPerformanceTimer();
        } else {
            updatePerformanceStats();
        }
    }

    private void updateMonitoringControls() {
        boolean enabled = monitoringEnabledBox != null && monitoringEnabledBox.isSelected();
        for (JComponent component : List.of(
                monitorSystemCpuBox, monitorSystemMemoryBox, monitorServerMemoryBox, monitorUptimeBox,
                monitorTpsBox, monitorPlayerCountBox, monitorLatencyBox, monitorJavaStatusBox,
                monitorServerTypeBox, monitorRestartStateBox, monitoringRateCombo)) {
            if (component != null) {
                component.setEnabled(enabled);
            }
        }
    }

    private String formatDuration(long millis) {
        if (millis <= 0) {
            return "00:00:00";
        }
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String formatMemoryValue(String value) {
        return value == null || value.isBlank() ? "not set" : value;
    }

    private boolean validateConfig() {
        if (config.serverFolder == null || config.serverFolder.isBlank()) {
            showError("Pick your server folder first.");
            return false;
        }
        if (config.jarFile == null || config.jarFile.isBlank()) {
            showError("Pick your server jar first.");
            return false;
        }
        Path folder = Paths.get(config.serverFolder);
        if (!Files.isDirectory(folder)) {
            showError("Server folder does not exist.");
            return false;
        }
        if (!Files.exists(folder.resolve(config.jarFile))) {
            showError("Jar file was not found inside that folder.\n\nLooking for: " + folder.resolve(config.jarFile));
            return false;
        }
        return true;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "Check Settings", JOptionPane.WARNING_MESSAGE);
    }

    private void sendCommandFromBox() {
        String cmd = commandField.getText().trim();
        if (cmd.isEmpty()) {
            return;
        }
        serverManager.sendCommand(cmd);
        commandField.setText("");
    }

    private void openServerFolder() {
        try {
            if (folderField.getText().isBlank()) {
                showError("No server folder is set.");
                return;
            }
            Desktop.getDesktop().open(Paths.get(folderField.getText()).toFile());
        } catch (Exception e) {
            appendLog("Could not open folder: " + e.getMessage());
        }
    }

    private void saveConfig(boolean syncLaunchFiles) {
        try {
            ConfigService.save(config);
            if (syncLaunchFiles) {
                Path serverFolder = safePath(config.serverFolder);
                ServerSetupDetector.DetectionResult detection = ServerSetupDetector.detect(
                        serverFolder,
                        serverFolder == null || config.jarFile == null || config.jarFile.isBlank() ? null : serverFolder.resolve(config.jarFile)
                );
                LaunchFileService.SyncResult syncResult = LaunchFileService.applySettingsToLaunchFile(serverFolder, config, detection);
                appendLog(syncResult.message());
            }
        } catch (IOException e) {
            appendLog("Failed to save config: " + e.getMessage());
        }
    }

    private void saveConfigSilently() {
        if (config == null || themeModeCombo == null || closeBehaviorCombo == null) {
            return;
        }
        configFromUi();
        saveConfig(false);
        startPerformanceTimer();
    }

    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            recentConsoleLines.add(line);
            if (recentConsoleLines.size() > 200) {
                recentConsoleLines.remove(0);
            }
            logArea.append(line + System.lineSeparator());
            logArea.setCaretPosition(logArea.getDocument().getLength());
            if (consolePreviewArea != null) {
                consolePreviewArea.setText(getLastConsoleLines(3));
                consolePreviewArea.setCaretPosition(consolePreviewArea.getDocument().getLength());
            }
        });
    }

    private void updateStatus(ServerManager.State state) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Status: " + state.name());
            updatePerformanceStats();
            if (trayIcon != null) {
                trayIcon.setToolTip("MC Server Manager - " + state.name());
            }
            if (state == ServerManager.State.CRASHED) {
                showTrayMessage("Minecraft Server Crashed", "Crash detected. Check the manager logs.");
            } else if (state == ServerManager.State.ONLINE) {
                showTrayMessage("Minecraft Server Online", "Server finished starting.");
            }
        });
    }
    private void installTray() {
        if (!SystemTray.isSupported()) {
            appendLog("System tray is not supported on this PC. Tray mode disabled.");
            return;
        }
        PopupMenu menu = new PopupMenu();
        MenuItem showItem = new MenuItem("Show Window");
        MenuItem startItem = new MenuItem("Start Server");
        MenuItem stopItem = new MenuItem("Safe Stop");
        MenuItem restartItem = new MenuItem("Restart Server");
        MenuItem exitItem = new MenuItem("Exit App");
        showItem.addActionListener(e -> SwingUtilities.invokeLater(this::showMainWindow));
        startItem.addActionListener(e -> SwingUtilities.invokeLater(this::doStart));
        stopItem.addActionListener(e -> serverManager.safeStop(selectedDelay(safeStopCombo, 15)));
        restartItem.addActionListener(e -> serverManager.restart(selectedDelay(safeStopCombo, 15)));
        exitItem.addActionListener(e -> SwingUtilities.invokeLater(this::shutdownAndExit));
        menu.add(showItem);
        menu.add(startItem);
        menu.add(stopItem);
        menu.add(restartItem);
        menu.addSeparator();
        menu.add(exitItem);
        Image image = createTrayImage();
        trayIcon = new TrayIcon(image, "MC Server Manager", menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> SwingUtilities.invokeLater(this::showMainWindow));
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            appendLog("Could not add tray icon: " + e.getMessage());
        }
    }

    private Image createTrayImage() {
        int size = 16;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(40, 40, 40));
        g.fillRect(0, 0, size, size);
        g.setColor(new Color(88, 166, 57));
        g.fillRoundRect(2, 2, 12, 12, 5, 5);
        g.setColor(Color.BLACK);
        g.drawRoundRect(2, 2, 12, 12, 5, 5);
        g.dispose();
        return img;
    }

    private void showTrayMessage(String title, String message) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
    }

    private void shutdownAndExit() {
        if (serverManager.isRunning()) {
            int result = JOptionPane.showConfirmDialog(frame,
                    "The Minecraft server is still running.\n\nYes = Exit app and leave server running\nNo = Cancel\n",
                    "Server Still Running",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }
        System.exit(0);
    }

    private void showMainWindow() {
        frame.setVisible(true);
        frame.setState(Frame.NORMAL);
        resumeMonitoringIfAllowed("Monitoring resumed after restoring the window.");
    }

    private boolean shouldCloseToTray() {
        return CLOSE_TO_TRAY.equals(getSelectedCloseBehavior());
    }

    private boolean shouldCloseToTaskbar() {
        return CLOSE_TO_TASKBAR.equals(getSelectedCloseBehavior());
    }

    private String resolveThemeMode() {
        return config.themeMode == null || config.themeMode.isBlank() ? THEME_MATCH_WINDOWS : config.themeMode;
    }

    private String resolveCloseBehavior() {
        if (config.closeBehavior == null || config.closeBehavior.isBlank()) {
            return config.minimizeToTray ? CLOSE_TO_TRAY : CLOSE_EXIT;
        }
        return config.closeBehavior;
    }

    private String getSelectedThemeMode() {
        if (themeModeCombo == null || themeModeCombo.getSelectedItem() == null) {
            return resolveThemeMode();
        }
        return switch (themeModeCombo.getSelectedItem().toString()) {
            case "Dark" -> THEME_DARK;
            case "Light" -> THEME_LIGHT;
            case "Custom" -> THEME_CUSTOM;
            default -> THEME_MATCH_WINDOWS;
        };
    }

    private String getSelectedCloseBehavior() {
        if (closeBehaviorCombo == null || closeBehaviorCombo.getSelectedItem() == null) {
            return resolveCloseBehavior();
        }
        return switch (closeBehaviorCombo.getSelectedItem().toString()) {
            case "Close to taskbar" -> CLOSE_TO_TASKBAR;
            case "Exit application" -> CLOSE_EXIT;
            default -> CLOSE_TO_TRAY;
        };
    }

    private String labelForThemeMode(String mode) {
        return switch (mode) {
            case THEME_DARK -> "Dark";
            case THEME_LIGHT -> "Light";
            case THEME_CUSTOM -> "Custom";
            default -> "Match Windows";
        };
    }

    private String labelForCloseBehavior(String mode) {
        return switch (mode) {
            case CLOSE_TO_TASKBAR -> "Close to taskbar";
            case CLOSE_EXIT -> "Exit application";
            default -> "Close to tray icon";
        };
    }

    private Color parseColor(String value, Color fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Color.decode(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String toHex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private boolean isDark(Color color) {
        double luminance = (0.299 * color.getRed()) + (0.587 * color.getGreen()) + (0.114 * color.getBlue());
        return luminance < 140;
    }

    private Color contrastColor(Color color) {
        return isDark(color) ? new Color(245, 247, 250) : new Color(30, 36, 44);
    }

    private Color mix(Color a, Color b, float ratio) {
        float clampedRatio = Math.max(0f, Math.min(1f, ratio));
        float inverse = 1f - clampedRatio;
        return new Color(
                Math.min(255, Math.round((a.getRed() * inverse) + (b.getRed() * clampedRatio))),
                Math.min(255, Math.round((a.getGreen() * inverse) + (b.getGreen() * clampedRatio))),
                Math.min(255, Math.round((a.getBlue() * inverse) + (b.getBlue() * clampedRatio)))
        );
    }

    private record ThemePalette(Color windowBackground, Color sectionBackground, Color inputBackground, Color textColor, Color mutedTextColor, Color borderColor, Color buttonBackground, Color buttonText) {}

    private static class RoundedPanel extends JPanel {
        private Color fillColor = new Color(250, 250, 250);
        private Color borderColor = new Color(210, 210, 210);
        RoundedPanel(LayoutManager layout) { super(layout); setOpaque(false); }
        void setThemeColors(Color fillColor, Color borderColor) { this.fillColor = fillColor; this.borderColor = borderColor; }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fillColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 28, 28);
            g2.setColor(borderColor);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 28, 28);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class CollapsibleSection extends RoundedPanel {
        private final JLabel titleLabel;
        private final RoundedButton toggleButton;
        private final JComponent content;
        private final JComponent collapsedPreview;
        private final boolean collapsible;
        private boolean collapsed;

        CollapsibleSection(String title, JComponent content, boolean collapsible, JComponent collapsedPreview) {
            super(new BorderLayout(0, 12));
            this.content = content;
            this.collapsedPreview = collapsedPreview;
            this.collapsible = collapsible;
            setBorder(new EmptyBorder(16, 16, 16, 16));

            JPanel header = new JPanel(new BorderLayout(8, 8));
            header.setOpaque(false);
            titleLabel = new JLabel(title);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
            header.add(titleLabel, BorderLayout.WEST);

            toggleButton = new RoundedButton(collapsible ? "Collapse" : "Open");
            toggleButton.setVisible(collapsible);
            toggleButton.addActionListener(e -> setCollapsed(!collapsed));
            header.add(toggleButton, BorderLayout.EAST);

            content.setOpaque(false);
            add(header, BorderLayout.NORTH);
            add(content, BorderLayout.CENTER);
            if (collapsedPreview != null) {
                collapsedPreview.setVisible(false);
                add(collapsedPreview, BorderLayout.SOUTH);
            }
            updateToggleText();
        }

        boolean isCollapsed() {
            return collapsed;
        }

        void setCollapsed(boolean collapsed) {
            if (!collapsible) {
                return;
            }
            this.collapsed = collapsed;
            content.setVisible(!collapsed);
            if (collapsedPreview != null) {
                collapsedPreview.setVisible(collapsed);
            }
            updateToggleText();
            revalidate();
            repaint();
        }

        private void updateToggleText() {
            if (collapsible) {
                toggleButton.setText(collapsed ? "Expand" : "Collapse");
            }
        }
    }

    private static class RoundedBorder extends AbstractBorder {
        private final Color color;
        private final int radius;
        RoundedBorder(Color color, int radius) { this.color = color; this.radius = radius; }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(1, 1, 1, 1);
            return insets;
        }
    }

    private static class RoundedButton extends JButton {
        private Color fillColor = new Color(67, 131, 214);
        private Color textColor = Color.WHITE;
        private Color borderColor = new Color(74, 88, 103);
        RoundedButton(String text) {
            super(text);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMargin(new Insets(10, 14, 10, 14));
        }
        void setThemeColors(Color fillColor, Color textColor, Color borderColor) {
            this.fillColor = fillColor;
            this.textColor = textColor;
            this.borderColor = borderColor;
            setForeground(textColor);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color paint = fillColor;
            if (!isEnabled()) paint = new Color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), 120);
            else if (getModel().isPressed()) paint = fillColor.darker();
            else if (getModel().isRollover()) paint = fillColor.brighter();
            g2.setColor(paint);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g2.setColor(borderColor);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
