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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    private JSpinner restartDelaySpinner;
    private JSpinner safeStopSpinner;
    private JTextArea jvmArgsArea;
    private JTextArea logArea;
    private JTextField commandField;
    private JLabel statusLabel;
    private JComboBox<String> themeModeCombo;
    private JComboBox<String> closeBehaviorCombo;
    private JButton backgroundColorButton;
    private JButton inputColorButton;
    private TrayIcon trayIcon;
    private Color customBackgroundColor = Color.decode("#1F2430");
    private Color customInputColor = Color.decode("#2A3140");

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
        if (config.startMinimized) {
            if (shouldCloseToTray() && SystemTray.isSupported()) {
                frame.setVisible(false);
                showTrayMessage("MC Server Manager", "Started minimized. App is in the system tray.");
            } else {
                frame.setVisible(true);
                frame.setState(Frame.ICONIFIED);
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
        root.add(buildMainPanel(), BorderLayout.CENTER);
        root.add(buildBottomPanel(), BorderLayout.SOUTH);

        installTray();
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                configFromUi();
                config.windowWidth = frame.getWidth();
                config.windowHeight = frame.getHeight();
                saveConfig();
                if (shouldCloseToTray() && SystemTray.isSupported()) {
                    frame.setVisible(false);
                    showTrayMessage("MC Server Manager", "App minimized to tray.");
                } else if (shouldCloseToTaskbar()) {
                    frame.setState(Frame.ICONIFIED);
                } else {
                    shutdownAndExit();
                }
            }
        });
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
        JPanel sidebar = new JPanel();
        markPanelTransparent(sidebar);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.add(buildQuickActionsPanel());
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(buildOptionsPanel());
        sidebar.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(sidebar);
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

        int row = 0;
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
        return createSectionPanel("Server Settings", content);
    }

    private JPanel buildQuickActionsPanel() {
        JPanel content = new JPanel();
        markPanelTransparent(content);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        RoundedButton saveButton = createActionButton("Save Settings", e -> {
            configFromUi();
            saveConfig();
            appendLog("Settings saved to: " + ConfigService.getConfigFile());
        });
        RoundedButton startButton = createActionButton("Start Server", e -> doStart());
        RoundedButton stopButton = createActionButton("Safe Stop", e -> serverManager.safeStop((Integer) safeStopSpinner.getValue()));
        RoundedButton restartButton = createActionButton("Restart Server", e -> serverManager.restart((Integer) safeStopSpinner.getValue()));
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

        statusLabel = new JLabel("Status: OFFLINE");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 15f));
        statusLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, statusLabel.getPreferredSize().height));
        autoRestartBox = new JCheckBox("Auto-restart on crash");
        restartDelaySpinner = new JSpinner(new SpinnerNumberModel(15, 1, 3600, 1));
        safeStopSpinner = new JSpinner(new SpinnerNumberModel(10, 3, 300, 1));

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
        content.add(killButton);
        content.add(Box.createVerticalStrut(12));
        content.add(autoRestartBox);
        content.add(Box.createVerticalStrut(10));
        content.add(createSectionLabel("Restart Delay (sec)"));
        content.add(restartDelaySpinner);
        content.add(Box.createVerticalStrut(10));
        content.add(createSectionLabel("Safe Stop Wait (sec)"));
        content.add(safeStopSpinner);

        alignSidebarComponents(content);
        return createSectionPanel("Quick Actions", content);
    }

    private JPanel buildOptionsPanel() {
        JPanel content = new JPanel(new GridBagLayout());
        markPanelTransparent(content);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        startMinimizedBox = new JCheckBox("Start minimized");
        themeModeCombo = new JComboBox<>(new String[]{"Match Windows", "Dark", "Light", "Custom"});
        closeBehaviorCombo = new JComboBox<>(new String[]{"Close to tray icon", "Close to taskbar", "Exit application"});
        backgroundColorButton = createActionButton("Choose Background Color", e -> chooseCustomBackgroundColor());
        inputColorButton = createActionButton("Choose Input Color", e -> chooseCustomInputColor());

        themeModeCombo.addActionListener(e -> {
            updateThemeControlState();
            applyTheme();
        });
        closeBehaviorCombo.addActionListener(e -> saveConfigSilently());

        addOptionRow(content, gbc, "Theme", themeModeCombo);
        addOptionRow(content, gbc, "Close Button", closeBehaviorCombo);
        addOptionRow(content, gbc, "Custom Background", backgroundColorButton);
        addOptionRow(content, gbc, "Custom Inputs", inputColorButton);

        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        content.add(startMinimizedBox, gbc);
        gbc.gridy++;
        JLabel hintLabel = new JLabel("<html>Custom theme uses two colors:<br>one for the app background and one for text boxes, lists, and console areas.</html>");
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, 12f));
        content.add(hintLabel, gbc);
        return createSectionPanel("Options", content);
    }

    private JPanel buildCenterPanel() {
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return createSectionPanel("Live Console", createWrappedScrollPane(logArea));
    }

    private JPanel buildBottomPanel() {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        markPanelTransparent(content);
        commandField = new JTextField();
        RoundedButton sendButton = createActionButton("Send", e -> sendCommandFromBox());
        commandField.addActionListener(e -> sendCommandFromBox());
        RoundedButton openFolderButton = createActionButton("Open Server Folder", e -> openServerFolder());
        JPanel left = new JPanel(new BorderLayout(8, 8));
        markPanelTransparent(left);
        left.add(commandField, BorderLayout.CENTER);
        left.add(sendButton, BorderLayout.EAST);
        content.add(left, BorderLayout.CENTER);
        content.add(openFolderButton, BorderLayout.EAST);
        return createSectionPanel("Send Command", content);
    }

    private JPanel createSectionPanel(String title, JComponent content) {
        RoundedPanel panel = new RoundedPanel(new BorderLayout(0, 12));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(titleLabel, BorderLayout.NORTH);
        content.setOpaque(false);
        panel.add(content, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return panel;
    }

    private JScrollPane createWrappedScrollPane(JComponent component) {
        JScrollPane scrollPane = new JScrollPane(component);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    private void addLabeledRow(JPanel panel, GridBagConstraints gbc, int row, String label, JTextField field, JButton button) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.gridwidth = 1;
        panel.add(createSectionLabel(label), gbc);
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
        panel.add(createSectionLabel(label), gbc);
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

    private void alignSidebarComponents(JPanel panel) {
        for (Component component : panel.getComponents()) {
            if (component instanceof JComponent jComponent) {
                jComponent.setAlignmentX(Component.LEFT_ALIGNMENT);
                if (jComponent instanceof JButton button) {
                    button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
                } else if (jComponent instanceof JSpinner spinner) {
                    spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, spinner.getPreferredSize().height));
                }
            }
        }
    }
    private RoundedButton browseFolderButton() {
        return createActionButton("Browse", e -> {
            JFileChooser chooser = new JFileChooser(folderField.getText().isBlank() ? null : new java.io.File(folderField.getText()));
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                folderField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
    }

    private RoundedButton browseJarButton() {
        return createActionButton("Browse", e -> {
            JFileChooser chooser = new JFileChooser(folderField.getText().isBlank() ? null : new java.io.File(folderField.getText()));
            chooser.setFileFilter(new FileNameExtensionFilter("Java Archive (*.jar)", "jar"));
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                Path selected = chooser.getSelectedFile().toPath();
                jarField.setText(selected.getFileName().toString());
                if (folderField.getText().isBlank() && selected.getParent() != null) {
                    folderField.setText(selected.getParent().toString());
                }
            }
        });
    }

    private RoundedButton createActionButton(String text, ActionListener listener) {
        RoundedButton button = new RoundedButton(text);
        button.addActionListener(listener);
        return button;
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
        startMinimizedBox.setSelected(config.startMinimized);
        restartDelaySpinner.setValue(config.autoRestartDelaySeconds);
        safeStopSpinner.setValue(config.safeStopWaitSeconds);
        themeModeCombo.setSelectedItem(labelForThemeMode(resolveThemeMode()));
        closeBehaviorCombo.setSelectedItem(labelForCloseBehavior(resolveCloseBehavior()));
        customBackgroundColor = parseColor(config.customBackgroundColor, customBackgroundColor);
        customInputColor = parseColor(config.customInputColor, customInputColor);
        updateColorButtonLabel(backgroundColorButton, customBackgroundColor);
        updateColorButtonLabel(inputColorButton, customInputColor);
        updateThemeControlState();
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
        config.startMinimized = startMinimizedBox.isSelected();
        config.autoRestartDelaySeconds = (Integer) restartDelaySpinner.getValue();
        config.safeStopWaitSeconds = (Integer) safeStopSpinner.getValue();
        config.themeMode = getSelectedThemeMode();
        config.closeBehavior = getSelectedCloseBehavior();
        config.customBackgroundColor = toHex(customBackgroundColor);
        config.customInputColor = toHex(customInputColor);
        config.minimizeToTray = CLOSE_TO_TRAY.equals(config.closeBehavior);
    }

    private void updateThemeControlState() {
        boolean customEnabled = THEME_CUSTOM.equals(getSelectedThemeMode());
        backgroundColorButton.setEnabled(customEnabled);
        inputColorButton.setEnabled(customEnabled);
    }

    private void applyTheme() {
        if (frame == null) {
            return;
        }
        configFromUi();
        ThemePalette palette = createThemePalette();
        applyThemeToComponent(frame.getContentPane(), palette);
        frame.getContentPane().setBackground(palette.windowBackground);
        frame.repaint();
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
        saveConfig();
        try {
            serverManager.start(config);
        } catch (Exception ex) {
            appendLog("Failed to start server: " + ex.getMessage());
            JOptionPane.showMessageDialog(frame, "Could not start the server.\n\n" + ex.getMessage(), "Start Failed", JOptionPane.ERROR_MESSAGE);
        }
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

    private void saveConfig() {
        try {
            ConfigService.save(config);
        } catch (IOException e) {
            appendLog("Failed to save config: " + e.getMessage());
        }
    }

    private void saveConfigSilently() {
        if (config == null || themeModeCombo == null || closeBehaviorCombo == null) {
            return;
        }
        configFromUi();
        saveConfig();
    }

    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + System.lineSeparator());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void updateStatus(ServerManager.State state) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Status: " + state.name());
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
        showItem.addActionListener(e -> SwingUtilities.invokeLater(() -> {
            frame.setVisible(true);
            frame.setState(Frame.NORMAL);
        }));
        startItem.addActionListener(e -> SwingUtilities.invokeLater(this::doStart));
        stopItem.addActionListener(e -> serverManager.safeStop((Integer) safeStopSpinner.getValue()));
        restartItem.addActionListener(e -> serverManager.restart((Integer) safeStopSpinner.getValue()));
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
        trayIcon.addActionListener(e -> SwingUtilities.invokeLater(() -> {
            frame.setVisible(true);
            frame.setState(Frame.NORMAL);
        }));
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
