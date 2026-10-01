# Spellcraft: test-environment deploy script (design)

**Date:** 2026-10-01
**Status:** Reviewed 2026-10-01 (fixes applied); plan: [`2026-10-01-deploy-test-env.md`](../plans/2026-10-01-deploy-test-env.md)
**Target:** Windows 11, Windows PowerShell 5.1, Java 25 (Temurin), Gradle wrapper

## Summary

One script, `scripts/Deploy-TestEnv.ps1`, takes the repo from "whatever is on disk" to a running
dev client with the automated checks green. It runs the commit-gate checks, then the server
GameTests, then launches the dev client. Before each launch step it checks for running instances
that must stop first, and whether a running client is up to date with the fresh build.

```bash
powershell -ExecutionPolicy Bypass -File scripts\Deploy-TestEnv.ps1
```

PowerShell, not `.bat`: process inspection (CIM) and timestamp comparison are painful in batch.
The script derives the repo root from its own location (`$PSScriptRoot\..`), which is the same
place as `cd C:\dev\spellcraft` without hard-coding the path. It uses only `gradlew.bat`, never
a system Gradle. Java 25 is assumed installed; the Gradle toolchain resolves it.

The helpers live in a module, `scripts/DeployTestEnv.psm1`, so they can be unit-tested with
Pester 3.4 (built into Windows PowerShell 5.1). The script itself is the thin flow.

## Glossary

| Term | Meaning |
|---|---|
| **Build stamp** | The newest file modification time under `build/classes/java/main` and `build/resources/main`. None if neither has files. |
| **Instance** | A `java.exe` process running the dev client, the GameTest server or the dedicated server **from this checkout** (not the Gradle daemon, not another checkout or worktree). |
| **Up to date** | The instance's start time is strictly later than the build stamp. |
| **Stale** | Not up to date: started at or before the build stamp, or there is no build stamp. It may be running older code than what is on disk. |

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

GameTests always run. A running server is not evidence that the tests pass, so an up-to-date
instance never skips them.

1. Find server instances and print them.
2. Instances that must stop first (an up-to-date dedicated server is left running):
   - every **GameTest server**, whatever its verdict: it is another test run in progress and
     shares `run/gametestserver`;
   - every **stale dedicated server**.
3. If any must stop: print PID, start time and verdict for each, ask `y/N` once. On `y`, stop
   them. On `N`, exit 3.
4. Run `.\gradlew.bat runGameTestServer`. It starts a headless server and exits non-zero on
   failure. A non-zero exit stops the whole script (exit 2); the client never launches over a
   failing build.

### Step 3: dev client

1. Find client instances and print them.
2. Stale clients must stop. If any: print PID, start time and verdict, ask `y/N` once. On `N`,
   exit 3. On `y`, stop them (see below).
3. If an up-to-date client is still running: skip, print that it already has the current
   build, exit 0.
4. Otherwise launch `.\gradlew.bat runClient --console=plain` detached (a hidden
   `cmd.exe /c ... >> build\deploy\runClient.log 2>&1`, because PowerShell 5.1's
   `Start-Process` cannot send stdout and stderr to one file and cannot append). Wait 15 s; if
   that process has already exited, print the log tail and exit 4. Otherwise print its PID and
   the log path, and exit 0.

## Detecting and stopping instances

- **Detect:** `Get-CimInstance Win32_Process -Filter "Name='java.exe'"`, matching the command
  line. ModDevGradle starts the game with argument files under `build\moddev\`, named
  `clientRun*`, `gameTestServerRun*` and `serverRun*`. The match is anchored on
  `<repo root>\build\moddev\<name>` and is case-insensitive, so `serverRun` never matches inside
  `gameTestServerRun`, and runs from another checkout or worktree are ignored. The real command
  lines are recorded under [Verified launch facts](#verified-launch-facts).
- **Stop:** `CloseMainWindow()` on the client, then wait up to 20 s for exit, then
  `Stop-Process -Force` only because the user has already answered `y`. Servers have no window:
  `Stop-Process -Force`. Nothing is ever stopped without the prompt.
- **Non-interactive runs:** if `[Console]::IsInputRedirected`, the process was started with
  `-NonInteractive`, or `[Environment]::UserInteractive` is false, an instance that must stop is
  treated as `N` (exit 3) rather than hanging on the prompt.

## Shared run directory

All runs use `run/` as the game directory and log to `run/logs/latest.log`. Step 2 runs the
GameTests while a client may still be running (the client decision comes in step 3), and step 1
rewrites class files that a running client loaded. Both must work on Windows; implementation
verifies them before writing code. If either fails, the flow changes (stop the client before
step 1) and goes back to Will.

## `-CheckOnly`

Prints the build stamp and each running instance (type, PID, start time, up-to-date or stale
verdict), then exits 0 without building, stopping or launching anything. This is how detection
is tested without a 15-minute build, and it is a useful read-only status command.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | Everything passed; client launched or already current |
| 1 | Not in a repo, or step 1 failed |
| 2 | GameTests failed |
| 3 | An instance that must stop was found and the user declined (or could not be asked), or it did not exit after being stopped |
| 4 | The client launch process died within 15 s |

## Out of scope

- Playtesting. The script launches the client; whether the game looks and feels right is
  Will's, via `docs/playtest-checklist.md`.
- Build caching, retries, parallelism and any CI integration (the Pester tests run by hand; CI
  is Linux).
- Stopping Gradle daemons.
- Launching a dedicated server.
- A `.bat` wrapper.

## Testing

Pester 3.4 unit tests (`scripts/tests/DeployTestEnv.Tests.ps1`) cover the pure parts:
command-line classification, the build stamp, the verdict, which instances must stop, the
prompt decision and the status formatting. Process discovery is tested with a mocked
`Get-CimInstance`.

## Verification plan

1. `-CheckOnly` with nothing running: reports none, exits 0.
2. Failure paths that can be provoked cheaply, without committing: a formatting break exits 1;
   a deliberately failing GameTest exits 2 and no client launches.
3. Launch the client once through the script; confirm via `-CheckOnly` that the detection
   matches the real command line and reports up to date.
4. Touch a file under `build/classes/java/main` and confirm the same client now reports stale.
5. Re-run the script non-interactively: GameTests pass with the client running, then exit 3.
6. Will, in a console: the prompt appears, `N` exits 3, `y` closes the client and relaunches.

Item 3 confirms the process exists, not that the game renders. Visuals stay with Will.

## Verified launch facts

Verified 2026-10-01 on this machine (Windows 11, Windows PowerShell 5.1, Java 25 Temurin). Paths
were observed in a git worktree, so `<root>` below is `C:\dev\spellcraft\.claude\worktrees\deploy-test-env`;
the shape matters, not the root. In the main checkout `<root>` is `C:\dev\spellcraft`.

- **Client command line** (`Win32_Process.CommandLine`, one java.exe, about 16.9k characters).
  The java path is double-quoted; everything after it is unquoted. In order:
  `"C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe"`
  `-Dfml.modFolders=spellcraft%%<root>\build\classes\java\main;spellcraft%%<root>\build\resources\main`
  (single backslashes), then the VM-args file, then `-Dfile.encoding=UTF-8 ...`, then
  `-cp <long classpath>` (single backslashes; it contains `<root>\build\moddev\artifacts\minecraft-patched-...jar`),
  then the main class `net.neoforged.devlaunch.Main`, then the program-args file. The two `@` tokens
  have **doubled backslashes**, unquoted, with no spaces:
  `@C:\dev\spellcraft\.claude\worktrees\deploy-test-env\build\moddev\clientRunVmArgs.txt`
  and `@C:\dev\spellcraft\.claude\worktrees\deploy-test-env\build\moddev\clientRunProgramArgs.txt`
  (generally `@<root with every \ doubled>\build\moddev\clientRunVmArgs.txt`). The command line
  ends with the program-args `@` token. The program-args file names the real main class
  (`net.neoforged.fml.startup.Client`).
- **GameTest server command line**: the same shape with `@...\build\moddev\gameTestServerRunVmArgs.txt`
  and `@...\build\moddev\gameTestServerRunProgramArgs.txt`; main class `net.neoforged.devlaunch.Main`
  (the program-args file names `net.neoforged.fml.startup.GameTestServer`).
- **Matching trap (Task 2 must handle):** a plain match on `\build\moddev\gameTestServerRun` or
  `<root>\build\moddev\clientRun` does **not** match, because the `@` tokens use doubled backslashes
  (`\build\moddev\`). Only the classpath and `-Dfml.modFolders` use single backslashes, and neither
  of those contains `clientRun...` or `gameTestServerRun...`. The match must normalize `\` to `\`
  (or match `\build\moddev\clientRunProgramArgs`) and anchor on `<root>` the same way. The
  classpath substring `<root>\build\moddev\artifacts\` also appears in every instance, so
  `\build\moddev\` alone cannot tell the kinds apart. `Select-String` for `serverRunProgramArgs`
  also matches inside `gameTestServerRunProgramArgs`, so anchor on the preceding `\`.
  The Gradle wrapper process (`gradle-wrapper.jar runClient ...`) and the Gradle daemon are also
  `java.exe`; neither contains `@...moddev...` tokens, so they are not instances.
- **Dedicated server (inferred, not launched: EULA)**: `prepareServerRun` writes
  `build/moddev/serverRunVmArgs.txt` and `build/moddev/serverRunProgramArgs.txt` (main class
  `net.neoforged.fml.startup.Server`, argument `--nogui`), so its command line should carry
  `@...\build\moddev\serverRunVmArgs.txt` and `@...\build\moddev\serverRunProgramArgs.txt`.
- **A run does not move the build stamp.** Newest file under `build\classes\java\main` and
  `build\resources\main` stayed `Spellcraft.class` at 2026-10-01 23:04:57 UTC across two
  `runGameTestServer` runs (and the client launch).
- **GameTests pass while the client runs**: `runGameTestServer` exited 0 with the client open
  ("All 10 required tests passed", `BUILD SUCCESSFUL`); no rollover warning or crash.
- **Rebuilding while the client runs works and makes it stale**: `./gradlew classes` after a source
  edit and again after restoring it both ended `BUILD SUCCESSFUL`, with no file-in-use error. The
  stamp moved to 2026-10-01 23:15:33 UTC, later than the client's start time of 23:14:24 UTC.
- **`CloseMainWindow()` closes the client**: it returned `True` and the process exited within 1 s
  (`WaitForExit` returned `True`).
