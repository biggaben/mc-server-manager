# MC Server Manager (IntelliJ / Java)

A simple desktop app for running a Minecraft dedicated server without the normal built-in GUI.

## What it does

- Start the server with saved Java and JVM settings
- Safe stop with `save-all` then `stop`
- Restart button
- Force kill button for hung servers
- Live console window
- Send commands to the server
- Save settings to `%USERPROFILE%/.mc-server-manager/config.json`
- Auto-restart after crashes
- Minimize to tray / run in the background
- Start minimized option

## What you need

- Windows recommended
- Java 17+ installed
- IntelliJ IDEA
- Maven support in IntelliJ (built in)
- A working Minecraft server folder already created

## How to open it in IntelliJ

1. Extract the zip.
2. Open IntelliJ IDEA.
3. Click **Open**.
4. Pick the `mc-server-manager-intellij` folder.
5. Let IntelliJ import the Maven project.
6. Open `src/main/java/com/otosanx/mcmanager/Main.java`.
7. Click the green run button next to `main`.

## How to build a jar

Inside IntelliJ terminal, run:

```bash
mvn package
```

The runnable jar will be created in:

```text
target/mc-server-manager-1.0.0.jar
```

Run it with:

```bash
java -jar target/mc-server-manager-1.0.0.jar
```

## First setup in the app

- **Server Folder**: the folder with your server files
- **Server Jar**: usually something like `fabric-server-launch.jar`, `server.jar`, or `forge-...jar`
- **Java Path**: usually just `java`
- **Xms / Xmx**: example `4G` and `8G`
- **Server Args**: usually `nogui`
- **JVM Args**: defaults are already included

Then click **Save Settings** and **Start Server**.

## Important note about closing the app

If you close the window and tray mode is enabled, the app hides to tray.

If you fully exit the app while the server is still running, the Java server process will usually keep running, but the manager app will no longer control it. So for normal use, either:

- leave the app in tray mode, or
- safe stop the server before exiting the app

## Good next upgrades

If you want to keep building this, the next smart upgrades would be:

- multiple server profiles
- scheduled restarts
- backup button
- Discord webhook alerts
- TPS / player count parsing
- startup presets for Fabric / Forge / Paper
- Windows startup shortcut or installer
