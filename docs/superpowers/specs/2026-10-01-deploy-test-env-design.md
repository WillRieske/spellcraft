# Spellcraft: test-environment deploy script (design)

**Date:** 2026-10-01
**Status:** Approved in brainstorming; awaiting written-spec review
**Target:** Windows 11, Windows PowerShell 5.1, Java 25 (Temurin), Gradle wrapper

## Summary

One script, `scripts/Deploy-TestEnv.ps1`, takes the repo from "whatever is on disk" to a running
dev client with the automated checks green. It runs the commit-gate checks, then the server
GameTests, then launches the dev client. Before each launch step it checks whether an instance
is already running and whether that instance is up to date with the fresh build.

```bash
powershell -ExecutionPolicy Bypass -File scripts\Deploy-TestEnv.ps1
```

PowerShell, not `.bat`: process inspection (CIM) and timestamp comparison are painful in batch.
The script derives the repo root from its own location (`$PSScriptRoot\..`), which is the same
place as `cd C:\dev\spellcraft` without hard-coding the path. It uses only `gradlew.bat`, never
a system Gradle. Java 25 is assumed installed; the Gradle toolchain resolves it.

## Glossary

| Term | Meaning |
|---|---|
| **Build stamp** | The newest file modification time under `build/classes/java/main` and `build/resources/main`. |
| **Instance** | A `java.exe` process running the dev client or the GameTest/dedicated server for this repo (not the Gradle daemon). |
| **Up to date** | The instance's start time is later than the build stamp. |
| **Stale** | The instance's start time is earlier than the build stamp: it is running older code than what is on disk. |

## Why the build stamp is not the jar

ModDevGradle dev runs declare `mods { spellcraft { sourceSet(sourceSets.main) } }` in
`build.gradle`, so the client and servers load the class and resource directories directly.
`build/libs/spellcraft-1.0.0.jar` is only a build output and nothing running reads it.
Comparing against the jar would be wrong: it could be newer than the running code (rebuilt but
not reloaded) or equally old (an unchanged jar next to changed classes). The directories are
what the running JVM actually loaded from.

Gradle's incremental build leaves unchanged outputs alone, so rebuilding an unchanged tree does
not move the stamp, and a running instance stays up to date.

## Flow

The stamp is computed after step 1, because the build is what changes it.

### Step 0: locate the repo

`Set-Location` to the repo root. Fail with exit code 1 and a message if `gradlew.bat` is not
there.

### Step 1: automated checks

Run `.\gradlew.bat spotlessCheck build` with live console output. It is the same gate that runs
on commit. A cold run downloads and decompiles Minecraft (5-15 min), so there is no timeout.
Non-zero exit: stop, exit 1.

### Step 2: server GameTests

1. Find server instances (GameTest or dedicated).
2. None: run the tests.
3. Up to date: skip, print that the running server already has the current build.
4. Stale: print PID, start time and build stamp, ask `y/N`. On `y`, stop it and run the tests.
   On `N`, exit 3.
5. Run `.\gradlew.bat runGameTestServer`. It starts a headless server and exits non-zero on
   failure. A non-zero exit stops the whole script (exit 2); the client never launches over a
   failing build.

### Step 3: dev client

1. Find client instances.
2. None: launch.
3. Up to date: skip, print that the running client already has the current build, exit 0.
4. Stale: print PID, start time and build stamp, ask `y/N`. On `N`, exit 3. On `y`, stop it
   (see below), then launch.
5. Launch `.\gradlew.bat runClient` detached, with stdout and stderr appended to
   `build/deploy/runClient.log`. Wait a few seconds; if the Gradle process has already exited,
   print the log tail and exit 4. Otherwise print the PID and log path, and exit 0.

## Detecting and stopping instances

- **Detect:** `Get-CimInstance Win32_Process -Filter "Name='java.exe'"`, matching the command
  line. ModDevGradle starts the game with argument files under `build\moddev\`, named
  `clientRun*`, `gameTestServerRun*` and `serverRun*`. Matching those filenames identifies the
  run type and excludes the Gradle daemon. This is verified against a real launch during
  implementation, not assumed.
- **Stop:** `CloseMainWindow()` on the client, then wait up to 20 s for exit, then
  `Stop-Process -Force` only because the user has already answered `y`. Servers have no window:
  `Stop-Process`. Nothing is ever stopped without the prompt.
- **Non-interactive runs:** if stdin is not interactive, a stale instance is treated as `N`
  (exit 3) rather than hanging on the prompt.

## `-CheckOnly`

Prints each running instance (type, PID, start time), the build stamp and the up-to-date or
stale verdict, then exits 0 without building, stopping or launching anything. This is how
detection is tested without a 15-minute build, and it is a useful read-only status command.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | Everything passed; client launched or already current |
| 1 | Not in a repo, or step 1 failed |
| 2 | GameTests failed |
| 3 | A stale instance was found and the user declined (or could not be asked) |
| 4 | The client Gradle process died right after launch |

## Out of scope

- Playtesting. The script launches the client; whether the game looks and feels right is
  Will's, via `docs/playtest-checklist.md`.
- Build caching, retries, parallelism and any CI integration.
- Stopping Gradle daemons.
- A `.bat` wrapper.

## Verification plan

1. `-CheckOnly` with nothing running: reports none, exits 0.
2. Full run steps 0-2: build and GameTests pass, exits as expected at each failure path where
   it can be provoked cheaply (for example a deliberately failing check on a scratch branch).
3. Launch the client once through the script; confirm via `-CheckOnly` that the detection
   matches the real command line and reports up to date.
4. Touch a file under `build/classes/java/main` and confirm the same client now reports stale.
5. Re-run the script: the prompt appears, `N` exits 3, `y` relaunches.

Item 3 confirms the process exists, not that the game renders. Visuals stay with Will.
