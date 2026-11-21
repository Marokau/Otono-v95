# Otono v95 launcher

Open **Otono.cmd** at the repository root. The window uses the existing Initial-Server-Setup, Start-Server and Start-Game CMD files. No absolute machine/drive paths are embedded.

## First use

1. Install a Java 21 JDK. The launcher checks both java and javac (21 or newer), using JAVA_HOME before PATH just like the scripts. Reopen after changing environment variables.
2. Install a complete compatible v95 game under game. The installer-created game/MapleStory subfolder is supported. Open game folder shows where to install it; the launcher does not download the game.
3. Choose Initial Setup. The existing Maven Wrapper builds the server and copies the bundled official Kinoko.exe/Kinoko.dll from client/bin into the detected game directory. Those files are provided by setup, not a separate user prerequisite. Internet is needed for Maven and dependencies on the first build.
4. Choose Start Server. Its console stays visible. The launcher waits for the local login and first channel ports before enabling Start Game. SQLite initializes during the first successful server startup, not merely when setup finishes.
5. Choose Start Game. This invokes the existing Kinoko startup script; any Windows elevation behavior remains with that client.

The scan checks required filenames, not WZ contents or a cryptographically verified client version. Missing/incorrect versions can still fail during actual startup. Bundled files missing from client/bin require restoring the Otono download. A Java 21 JDK remains the tested baseline.

## Everyday use and safety

- The checklist refreshes automatically; Refresh checks also reprobes Java.
- Repair Setup skips an existing server build and restores the two bundled client files. It does not reset the SQLite database. It will overwrite locally customized Kinoko.exe/Kinoko.dll with the bundled release, just as initial setup does.
- Source rebuilding remains an intentional developer action: stop v95, then run launcher/Initial-Server-Setup.cmd --rebuild. Repair Setup is not a source rebuild button.
- Server/setup run in their own console windows. Closing the launcher does not stop those processes. Reopening recognizes its own still-running process using a local PID/start-time record; it does not identify an unrelated server as Otono merely because a port is open.
- A busy 8484 or 8585 blocks another server launch and setup repair. Close the conflicting server first. v83 and v95 use overlapping default ports.
- Stop the server through its console. No force-kill or database-delete button is provided.
- Config and Documentation open the existing local file/folder. Config changes still require a server restart.

## Source and verification

Otono.cmd runs launcher/Start-Launcher.ps1, which compiles launcher/OtonoLauncher.cs with the Windows .NET Framework 4.x compiler on first use or source changes. Compiled output and local session records are ignored under launcher/bin. No separate .NET SDK, Visual Studio or installed Maven is required. If Windows lacks the framework/compiler or blocks PowerShell, the original CMD setup/start paths remain usable. The execution-policy flag applies to this invocation; it does not change the machine's policy setting.

The generated launcher supports --self-test for isolated checks: empty install, nested/direct game-folder precedence, missing assets/client, prepared install, and script invocation with spaces/ampersands in paths. A separate isolated setup fixture verified repairing missing client files twice while leaving its existing jar and saved-database sentinel unchanged. The interface was rendered and visually reviewed. These checks do not repeat the full game installation/login audit or launch the real game.

## Wallpaper appearance



Start Game now closes the launcher when launcher/Start-Game.cmd reports a successful launch request. The server console/process remains open and is recognized when the launcher is reopened. Launch errors leave the launcher open with a message. This checks dispatch success, not whether a full game login completes.

## Release updates

Close the game and server, then choose **Check for Updates**. The launcher checks the latest stable Otono v95 release on GitHub and asks for confirmation. It verifies the release asset SHA-256 and individual payload files before replacing changed release files. The patch includes the built server JAR. Updated bundled client binaries are also installed in the detected game folder.

For the first update from v0.2.1, extract the release's **Otono-v95-Updater.zip** into the existing Otono folder and run **launcher/Check-for-Updates.cmd**. Future installations have the button. SQLite saves, config.yaml, installed game assets and personal additions are outside the patch. A conflicting local edit stops the update before installation. If interrupted, run the same updater to recover before playing.

For maintainers, run **python launcher/packaging/build-patch.py v0.2.1 v0.3.0 OUTPUT_FOLDER** in the publication checkout after tagging the reviewed release. It builds the JAR from the target tag and creates the patch and bootstrap ZIPs. Attach both assets before marking the release latest. Generate a separate patch for each supported starting version.
