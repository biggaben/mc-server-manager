# Changelog

All notable changes to this project will be documented in this file.

## [1.0.1] - 2026-03-15

### Fixed

- **EDT Freezing**: UI no longer freezes during performance monitoring polls. Metric polling is now offloaded from the Swing Event Dispatch Thread (EDT) using `SwingWorker` (`Main.java`).
- **Lock Contention**: `getProcessMemoryBytes` no longer holds the `ServerManager` instance lock while fetching OS command output (`ServerManager.java`).
- **Synchronous Thread Blocking**: `forceKill` no longer halts the entire manager thread waiting for process termination. The wait and force-destroy fallback runs asynchronously via an inner executor (`ServerManager.java`).
- **Unclosed Resource Leaks**: All OS-level file handle leaks from `Files.list()` and `Files.walk()` have been fixed with `try-with-resources` blocks across `ServerSetupDetector.java` and `LaunchFileService.java`.
- **Silent JVM Argument Wiping**: Launch script patching correctly injects custom JVM arguments before `-Xms` and `-Xmx` sizes without stripping out the existing runtime arguments if the `-jar` identifier is missing (`LaunchFileService.java`).
