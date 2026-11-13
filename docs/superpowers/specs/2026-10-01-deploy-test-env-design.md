# Spellcraft: test-environment deploy script (design)

**Date:** 2026-10-01
**Status:** Reviewed 2026-10-01 (fixes applied); skip-if-unchanged added and reviewed 2026-11-12 (fixes applied); plan: [`2026-10-01-deploy-test-env.md`](../plans/2026-10-01-deploy-test-env.md)
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
| **Build stamp** | The newest write time of any file or directory under `build/classes/java/main` and `build/resources/main`, the two directories included. Directories count because deleting a file (an incremental compile removing a class) changes only its directory's time. None if neither has files. |
| **Instance** | A `java.exe` process running the dev client, the GameTest server or the dedicated server **from this checkout** (not the Gradle daemon, not another checkout or worktree). |
| **Up to date** | The instance's start time is strictly later than the build stamp. |
| **Stale** | Not up to date: started at or before the build stamp, or there is no build stamp. It may be running older code than what is on disk. |
| **Input fingerprint** | A SHA-256 over the path and contents of every file on disk under `src/`, `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/`, `gradlew`, `gradlew.bat` and `docs/superpowers/specs/evoking/evoking-functional-spec.md`, as the working tree has them now, committed or not, git-ignored or not. Other docs, the scripts and build output are not inputs. |
| **Verified run** | A run whose build and GameTests passed with the sources unchanged from start to end. `build\deploy\verified.txt` holds that run's input fingerprint and, on a second line, the build stamp (in ticks) as the GameTests left it. |

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

### Skipping unchanged runs (steps 1 and 2)

The GameTests take minutes and never count as up to date to Gradle, so the script keeps its own
record. Before anything runs it takes the input fingerprint and compares it with
`build\deploy\verified.txt`:

- **Current** (the fingerprint and the build stamp both equal the record) and `-Force` not given:
  steps 1 and 2 are skipped, with a line saying so. Step 3 runs as usual: stale clients still need
  the prompt, and `runClient` itself recompiles anything missing.
- Anything else (**Changed**; **ClassesDiffer** because the sources match but the classes are
  missing or were built since; **None** because no record exists or it is empty; **Unknown**
  because the sources could not be listed or read; `-Force`): steps 1 and 2 run.
- Before step 1 runs, the record is deleted, so a failed or interrupted run never leaves an older
  pass standing for the same sources (a forced run that fails, a flaky GameTest). If it can be
  neither deleted nor emptied, the run stops with exit code 1 and a line naming the file.
- After step 2 passes, the fingerprint is taken again. If it equals the one taken at the start,
  it is written to the record with the build stamp read at that moment. If it differs, an edit
  was made while the checks ran and nobody knows which version Gradle checked: nothing is
  written, a line says so, and the next run redoes the checks. Nothing is written after a failed
  build, failed GameTests or a declined stop, or when the fingerprint is unknown. A write failure
  only prints a line.

Why the build stamp is in the record: step 3 calls a running client current when it started after
the stamp. Without the stamp in the record, this sequence would pass unverified code: a verified
run at sources A; an edit to B and a `gradlew build` (the commit gate does one); a client started
on B's classes; the edit reverted. The fingerprint is A again, but the classes and the client are
B's. With the stamp, that is **ClassesDiffer**: the build runs, the stamp moves, and the client is
stale. A no-op build does not move the stamp (see above), so commit-gate builds of unchanged
sources do not defeat the skip.

Why the files are listed from the disk and not by git: Gradle packages every file under
`src/main/resources` whether or not git ignores it, and git's file listing depends on the console
code page for non-ASCII names in Windows PowerShell 5.1. An editor's scratch file under `src/`
therefore costs a full run, which is the safe direction.

Why one doc is an input: `FunctionalSpecReferencesTest` reads the Evoking functional spec during
`build` and fails on a paragraph number that code and spec do not share. A Pester test reads every
path the Java tests open (`Path.of`, `Paths.get`, `new File` with a literal) and fails when one is
outside the input list, so the next such file cannot be forgotten.

What the fingerprint does not cover, where `-Force` is the remedy: anything outside the repo and
the inputs (the JDK, `~/.gradle/gradle.properties`, Gradle's caches, a dependency whose published
artifact changed) and the state under `run/` that the GameTest server reads.

The record sits under `build\`, so `gradlew clean` deletes it and the next run is a full one. The
comparison is exact (case-sensitive), and uses file contents, not times, so touching a source file
without changing it does not defeat the skip and a reverted edit matches the old record again
(unless something was compiled in between, as above). A record in the first format (one line) reads
as **ClassesDiffer**, so the first run after this change is a full one.

The dev client still runs from the class directories, not the jar (see the section above), so a
saved jar would not be what is launched; the fingerprint stands in for it.

### Step 0: locate the repo

`Set-Location` to the repo root. Fail with exit code 1 and a message if `gradlew.bat` is not
there.

### Step 1: automated checks

Run `.\gradlew.bat spotlessCheck build` with live console output. It is the same gate that runs
on commit. A cold run downloads and decompiles Minecraft (5-15 min), so there is no timeout.
Non-zero exit: stop, exit 1.

### Step 2: server GameTests

Unless skipped (see above), the GameTests run. A running server is not evidence that the tests
pass, so an up-to-date instance never skips them; only a matching verified run does.

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
  `@<repo root>\build\moddev\<name>` and is case-insensitive, so `serverRun` never matches inside
  `gameTestServerRun`, and runs from another checkout or worktree are ignored. Only the `@`
  argument-file token counts. Any run of `\` or `/` matches any other, so the doubled backslashes
  of the real tokens, forward slashes and trailing separators all compare equal; a UNC root still
  needs its two leading separators. The root is tried in each form Gradle may have written it:
  as given, with 8.3 short names expanded, and with junctions and `subst` drives resolved. The
  real command lines are recorded under [Verified launch facts](#verified-launch-facts).
- **Start time:** from `Process.StartTime`, not CIM's `CreationDate`. `Process.StartTime`
  converts back to the right UTC instant even in the repeated hour when daylight saving ends,
  and it is the same clock the PID-reuse check reads. An instance whose start time cannot be
  read is skipped with a message.
- **Stop:** first pin the process by opening its handle (Windows cannot reuse a PID while a
  handle is open), then check it is still the same process: its start time must be within 5 s
  of the instance's. A different start time means the PID now belongs to another process, which
  is left alone and the instance counts as gone. If the start time cannot be read and the process
  still exists, it is not stopped (exit 3). Then `CloseMainWindow()` on the client and wait up
  to 20 s; anything still running is killed through the pinned handle. This happens only after
  the user has answered `y`. Servers have no window, so they are killed straight away. An error
  while stopping (e.g. access denied) counts as "did not exit" (exit 3), not a crash. Nothing is
  ever stopped without the prompt.
- **Non-interactive runs:** if `[Console]::IsInputRedirected`, the process was started with
  `-NonInteractive` (in any spelling `powershell.exe` accepts: `-noni` to `-NonInteractive`, or
  the same with `/`), or `[Environment]::UserInteractive` is false, an instance that must stop is
  treated as `N` (exit 3) rather than hanging on the prompt. If the host still refuses the
  prompt, that is also `N`.

## Shared run directory

All runs use `run/` as the game directory and log to `run/logs/latest.log`. Step 2 runs the
GameTests while a client may still be running (the client decision comes in step 3), and step 1
rewrites class files that a running client loaded. Both must work on Windows; implementation
verifies them before writing code. If either fails, the flow changes (stop the client before
step 1) and goes back to Will.

## `-CheckOnly`

Prints the build stamp, whether the sources and the build stamp match the last verified run (and
so whether the next run skips steps 1 and 2; it uses the same state function as the flow, so the
line cannot promise a skip the flow would not make), and each running instance (type, PID, start time, up-to-date or stale
verdict), then exits 0 without building, stopping or launching anything. This is how detection
is tested without a 15-minute build, and it is a useful read-only status command.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | Everything passed; client launched or already current |
| 1 | Not in a repo, step 1 failed, or the verified-run record could not be cleared |
| 2 | GameTests failed |
| 3 | An instance that must stop was found and the user declined (or could not be asked), or it did not exit after being stopped, or it could not be stopped (an error, or its identity could not be checked) |
| 4 | The client launch process died within 15 s |

## Out of scope

- Playtesting. The script launches the client; whether the game looks and feels right is
  Will's, via `docs/playtest-checklist.md`.
- Retries, parallelism and any CI integration (the Pester tests run by hand; CI is Linux).
- Caching the build itself: Gradle does that. Only the skip of a whole passing run is the script's
  (see "Skipping unchanged runs").
- Launching from a saved jar (the dev client loads class directories).
- Stopping Gradle daemons.
- Launching a dedicated server.
- A `.bat` wrapper.

## Testing

Pester 3.4 unit tests (`scripts/tests/DeployTestEnv.Tests.ps1`) cover the pure parts:
command-line classification, the build stamp, the verdict, which instances must stop, the
prompt decision and the status formatting. The input fingerprint is tested on real throwaway
directories (edit, add, delete, move, each Gradle file, the functional spec, a git-ignored file,
a non-ASCII name, a locked file, no inputs); the skip decision with a mocked verified state; the
record with real files; and the whole flow against a real record, real sources and real class
files (run, skip, edit, rebuilt classes, a forced run that fails, an edit during the checks). One
test runs the real script with `-CheckOnly`. Each skip guard (exact compare, build stamp, `-Force`,
path in the hash, save only after the GameTests pass) has a test that fails when it is broken
(hand-made mutations, 2026-11-12). Process discovery is tested with mocked
`Get-CimInstance` and `Get-Process`; the repo-root forms use real junctions and 8.3 names; and
the stop guard is also run against real throwaway `ping.exe` processes. Each guard on the stop
path has a test that fails when the guard is broken (checked by hand-made mutations,
2026-10-02).

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
  `@C:\\dev\\spellcraft\\.claude\\worktrees\\deploy-test-env\\build\\moddev\\clientRunVmArgs.txt`
  and `@C:\\dev\\spellcraft\\.claude\\worktrees\\deploy-test-env\\build\\moddev\\clientRunProgramArgs.txt`
  (generally `@<root with every \ doubled>\\build\\moddev\\clientRunVmArgs.txt`). The command line
  ends with the program-args `@` token. The program-args file names the real main class
  (`net.neoforged.fml.startup.Client`).
- **GameTest server command line**: the same shape with `@...\\build\\moddev\\gameTestServerRunVmArgs.txt`
  and `@...\\build\\moddev\\gameTestServerRunProgramArgs.txt`; main class `net.neoforged.devlaunch.Main`
  (the program-args file names `net.neoforged.fml.startup.GameTestServer`).
- **Matching trap (Task 2 must handle):** a plain match on `\build\moddev\gameTestServerRun` or
  `<root>\build\moddev\clientRun` does **not** match, because the `@` tokens use doubled backslashes
  (`\\build\\moddev\\`). Only the classpath and `-Dfml.modFolders` use single backslashes, and neither
  of those contains `clientRun...` or `gameTestServerRun...`. The match must normalize `\\` to `\`
  (or match `\\build\\moddev\\clientRunProgramArgs`) and anchor on `<root>` the same way. The
  classpath substring `<root>\build\moddev\artifacts\` also appears in every instance, so
  `\build\moddev\` alone cannot tell the kinds apart. `Select-String` for `serverRunProgramArgs`
  also matches inside `gameTestServerRunProgramArgs`, so anchor on the preceding `\`.
  The Gradle wrapper process (`gradle-wrapper.jar runClient ...`) and the Gradle daemon are also
  `java.exe`; neither contains `@...moddev...` tokens, so they are not instances.
- **Dedicated server (inferred, not launched: EULA)**: `prepareServerRun` writes
  `build/moddev/serverRunVmArgs.txt` and `build/moddev/serverRunProgramArgs.txt` (main class
  `net.neoforged.fml.startup.Server`, argument `--nogui`), so its command line should carry
  `@...\\build\\moddev\\serverRunVmArgs.txt` and `@...\\build\\moddev\\serverRunProgramArgs.txt`.
- **A run does not move the build stamp.** Newest file under `build\classes\java\main` and
  `build\resources\main` stayed `Spellcraft.class` at 2026-10-01 23:04:57 UTC across two
  `runGameTestServer` runs (and the client launch). Re-checked 2026-10-02 with directories
  counted: the stamp stayed at 00:19:46.887 UTC across a no-op `spotlessCheck build` and a
  `runGameTestServer` run.
- **`-NonInteractive` spellings**: `powershell.exe` accepts `-noni`, `-nonin`, ... `-NonInteractive`
  in any case, and the same with `/` (`/noni`, `/NonInteractive`); `-non` is rejected. Under each
  accepted one, `Read-Host` throws.
- **Daylight-saving end**: `Process.StartTime` (`DateTime.FromFileTime`) keeps which of the two
  repeated local hours it is: 05:30 and 06:30 UTC on 2026-11-01 both show as 01:30 Eastern, and
  `.ToUniversalTime()` returns each correctly.
- **GameTests pass while the client runs**: `runGameTestServer` exited 0 with the client open
  ("All 10 required tests passed", `BUILD SUCCESSFUL`); no rollover warning or crash.
- **Rebuilding while the client runs works and makes it stale**: `./gradlew classes` after a source
  edit and again after restoring it both ended `BUILD SUCCESSFUL`, with no file-in-use error. The
  stamp moved to 2026-10-01 23:15:33 UTC, later than the client's start time of 23:14:24 UTC.
- **`CloseMainWindow()` closes the client**: it returned `True` and the process exited within 1 s
  (`WaitForExit` returned `True`).
