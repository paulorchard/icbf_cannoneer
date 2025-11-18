# IslandCraft — Forge 1.20.1 Mod Workspace Setup

This workspace is prepared to build a Minecraft Forge mod for Minecraft 1.20.1.

## Overview
- Prerequisites: JDK 17, VS Code, Git (optional)
- Download the Forge 1.20.1 MDK and extract into this folder (see steps below)
- Use the `gradlew` wrapper included in the MDK to build and run the project

## Recommended VS Code extensions
- Language Support for Java(TM) by Red Hat (`redhat.java`)
- Java Extension Pack (`vscjava.vscode-java-pack`)
- Debugger for Java (`vscjava.vscode-java-debug`)
- Java Test Runner (`vscjava.vscode-java-test`)
- GitLens (`eamodio.gitlens`)

You can install them from the Extensions view in VS Code, or run `scripts\install-vscode-extensions.ps1` (requires `code` CLI in PATH).

## Quick setup steps (Windows / PowerShell)
1. Install JDK 17 (Temurin recommended). Example via `winget`:

```powershell
# Run as Administrator if winget requires elevation
winget install --id EclipseAdoptium.Temurin.17 -e
```

Or run the helper script in this repo:

```powershell
scripts\install-jdk.ps1
```

2. Download the Forge MDK for Minecraft 1.20.1 from the official Forge site: https://files.minecraftforge.net
   - Choose the `1.20.1` Forge version (the MDK package)
   - Extract the contents of the MDK ZIP into this `c:\Apps\IslandCraft` directory (so `build.gradle`, `gradlew`, `src`, etc. are at the workspace root).

3. Open the folder in VS Code: `File -> Open Folder` -> `c:\Apps\IslandCraft`
   - VS Code will prompt to import the Gradle project if the Java extensions are installed.

4. Use the Gradle wrapper to build the project (PowerShell):

```powershell
# From repository root after extracting MDK:
.\gradlew build
```

5. To run the client from Gradle (if available in the MDK), use:

```powershell
.\gradlew runClient
```

Note: exact run tasks depend on the ForgeGradle configuration in the MDK. If `runClient` is not present, import the Gradle project then run the `runClient`/`runServer` tasks from the Gradle Tasks view.

## VS Code tasks
- `Gradle: Build (wrapper)` — runs `.\gradlew build` (see `.vscode/tasks.json`).

## Next steps I can do for you
- I can run `scripts\install-vscode-extensions.ps1` to install the recommended extensions (requires `code` CLI available in PATH).
- I can try to download and extract the Forge MDK for you (requires internet access & permission).
- I can run the JDK install script (requires `winget` and admin privileges).

Which of these would you like me to run now?
