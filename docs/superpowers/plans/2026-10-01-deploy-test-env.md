# Test-Environment Deploy Script Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One command, `scripts\Deploy-TestEnv.ps1`, that runs the commit-gate checks, then the server GameTests, then launches the dev client, stopping stale or conflicting running instances only after a `y`.

**Architecture:** A PowerShell module, `scripts/DeployTestEnv.psm1`, holds small functions: classify a `java.exe` command line, compute the build stamp, give the up-to-date/stale verdict, pick which instances must stop, prompt, stop, and launch. Pester 3.4 unit-tests the pure ones; process discovery is tested with a mocked `Get-CimInstance`. The ordering and exit codes live in the module's `Invoke-DeployFlow`, driven by injected steps so Pester can test all five exit codes. The script `scripts/Deploy-TestEnv.ps1` is a thin wrapper (step 0, `-CheckOnly`, the real Gradle steps). Task 1 checks the launch facts on the real machine before any code is written.

**Tech Stack:** Windows PowerShell 5.1 (`powershell.exe`), Pester 3.4.0 (built in), CIM (`Win32_Process`), Gradle wrapper (`gradlew.bat`, Gradle 9.2.1), ModDevGradle 2.0.147, NeoForge 26.3.0.23-beta, Java 25.

**Spec:** [`docs/superpowers/specs/2026-10-01-deploy-test-env-design.md`](../specs/2026-10-01-deploy-test-env-design.md)

## Global Constraints

- **Windows PowerShell 5.1**, not PowerShell 7: no `&&`/`||` pipeline chains, no `?:`, `??` or `?.`. No global `$ErrorActionPreference = 'Stop'` in the script (5.1 turns redirected native stderr into errors); check `$LASTEXITCODE`.
- **ASCII only** in every `.ps1`/`.psm1` file: 5.1 reads BOM-less files as ANSI, so an em dash or curly quote corrupts the script. Check with `grep -n '[^ -~[:space:]]' <files> || echo ascii-ok` before each commit. Do not use `grep -P`: in Git Bash with no UTF-8 locale it errors (exit 2), and the `|| echo ascii-ok` then prints success even when a non-ASCII character is present.
- **Every module function carries comment-based help** (`.SYNOPSIS`, `.PARAMETER`, `.OUTPUTS`; ASCII only), as `C:\dev\notes-manager\scripts\Repair-DockerStaleSockets.ps1` does.
- **Pester 3.4 syntax**: `Should Be`, `Should BeNullOrEmpty`, `Should Match`, `Assert-MockCalled ... -Times N -Exactly`. Never `Should -Be` (Pester 4+).
- Run the unit tests with: `powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester -Path scripts/tests -EnableExit"`. They are **not** wired into pre-commit or CI (CI is Linux with Pester 5; spec: CI integration out of scope).
- Paths: script `scripts/Deploy-TestEnv.ps1`; module `scripts/DeployTestEnv.psm1`; tests `scripts/tests/DeployTestEnv.Tests.ps1`; client log `build/deploy/runClient.log`.
- Only `gradlew.bat`, never a system Gradle.
- **Exit codes:** 0 ok (client launched or already current); 1 not in a repo or step 1 failed; 2 GameTests failed; 3 an instance that must stop was found and the user declined, could not be asked, or it did not exit; 4 the client launch process died within 15 s.
- Instance kinds are exactly the strings **`Client`**, **`GameTestServer`**, **`Server`**. Verdicts are exactly **`UpToDate`** and **`Stale`**. Up to date means start time **strictly later** than the build stamp; no stamp means `Stale`.
- All time comparisons in **UTC** (`LastWriteTimeUtc`, `CreationDate.ToUniversalTime()`); display in local time as `yyyy-MM-dd HH:mm:ss`.
- **Nothing is ever stopped without a `y`.** Non-interactive = `N`. Client stop: `CloseMainWindow()`, wait **20 s**, then `Stop-Process -Force`. Server stop: `Stop-Process -Force`.
- Do **not** accept the Minecraft EULA or run `runServer`: accepting terms is Will's call.
- Do not `taskkill /F` or force-stop anything outside this repo's instances (force-killing has already caused real problems on this machine; see `C:\dev\CLAUDE.md`'s Docker Desktop note).
- Visual checks belong to Will. The script proving a process exists says nothing about whether the game renders.
- Commit messages: conventional commits, title <= 100 chars, no attribution lines. The commit gate runs `spotlessCheck` and `build` on every commit.
- Every command runs from the repo root. In the Bash tool use `./gradlew`; in PowerShell use `.\gradlew.bat`.

## Deviations from the spec

None. The spec was reviewed and fixed on 2026-10-01 before this plan was written. The review changed: GameTests always run (an up-to-date server no longer skips them); the command-line match is anchored on `<repo root>\build\moddev\` so `serverRun` cannot match inside `gameTestServerRun`; the client log uses `cmd /c ... >> log 2>&1` because `Start-Process` cannot merge or append; multiple instances of one kind are handled; the non-interactive test and the launch grace period (15 s) are exact; the shared `run/` directory is verified first; and Pester unit tests were added.

## File Structure

```text
scripts/
├── Deploy-TestEnv.ps1           NEW  thin wrapper: step 0, -CheckOnly, real Gradle steps
├── DeployTestEnv.psm1           NEW  helpers and the flow (classification, stamp, verdict, selection, prompt, stop, launch, Invoke-DeployFlow)
└── tests/
    └── DeployTestEnv.Tests.ps1  NEW  Pester 3.4 unit tests for the module
docs/superpowers/specs/2026-10-01-deploy-test-env-design.md   MODIFY  Verified launch facts (Task 1)
CLAUDE.md                                                     MODIFY  Commands table (Task 6)
docs/playtest-checklist.md                                    MODIFY  how to launch (Task 6)
```

Module functions, by task:

| Function | Task | Tested by |
|---|---|---|
| `Get-InstanceKind` | 2 | Pester |
| `Get-BuildStamp` | 2 | Pester (TestDrive) |
| `Get-Verdict` | 2 | Pester |
| `Get-Instances` | 3 | Pester (mocked CIM) |
| `Select-InstancesToStop` | 3 | Pester |
| `Test-HasCurrentClient` | 3 | Pester |
| `Format-InstanceLine`, `Format-BuildStamp` | 3 | Pester |
| `Test-CanPrompt` | 4 | Task 6 (non-interactive run) |
| `Confirm-StopInstances` | 4 | Pester (mocked prompt) |
| `Stop-Instance` | 4 | Pester (mocked `Get-Process`/`Stop-Process`), Task 6 (real client) |
| `Invoke-StopStep` | 4 | Pester (mocked confirm/stop) |
| `Write-InstanceList` | 5 | Task 6 (output only) |
| `Start-DevClient` | 5 | Pester (`-Command` override), Task 6 (real launch) |
| `Invoke-DeployFlow` | 5 | Pester (mocked steps: ordering and all five exit codes) |

---

### Task 1: Verify the launch facts on this machine

No code. The design relies on facts that can only be seen on a live run. If step 5 or step 6 fails, **stop and report to Will**. The flow would have to change, and that is his decision.

**Files:**

- Modify: `docs/superpowers/specs/2026-10-01-deploy-test-env-design.md` (section "Verified launch facts")

**Interfaces:**

- Produces: the exact client and GameTest-server command-line shapes (path separator, quoting, `@` argument files), which Task 2 uses verbatim as test fixtures.

- [x] **Step 0: Commit the pending spec fixes first**

`git status --short docs/superpowers/specs/2026-10-01-deploy-test-env-design.md`. If it shows a modified file (status `M` in the second column), the spec carries an earlier review rewrite that is not Task 1's. Commit it on its own so step 10's commit holds only the recorded facts:

```bash
git add docs/superpowers/specs/2026-10-01-deploy-test-env-design.md
git commit -m "docs: apply review fixes to the deploy-test-env spec"
```

If the file is clean, skip this step.

- [x] **Step 1: Make sure nothing is running and the build is warm**

PowerShell:

```powershell
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*\build\moddev\*' } | Select-Object ProcessId, CreationDate
```

Expected: no output. If a client is running, ask Will to quit it from the game menu. Do not kill it.

Then, in Bash: `./gradlew build --console=plain` with `run_in_background: true` (a cold build takes 5-15 min; see `CLAUDE.md`). Expected: `BUILD SUCCESSFUL`.

- [x] **Step 2: Record the GameTest server's command line**

In Bash, start `./gradlew runGameTestServer --console=plain` with `run_in_background: true`. Then, in PowerShell:

```powershell
$deadline = (Get-Date).AddMinutes(5)
do {
    $p = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*\build\moddev\gameTestServerRun*' })
    if ($p.Count -gt 0) { break }
    Start-Sleep -Milliseconds 500
} while ((Get-Date) -lt $deadline)
foreach ($x in $p) {
    "PID $($x.ProcessId) created $($x.CreationDate) length $($x.CommandLine.Length)"
    $x.CommandLine.Substring(0, [Math]::Min(300, $x.CommandLine.Length))
    [regex]::Matches($x.CommandLine, '@\S+') | ForEach-Object { $_.Value }
    [regex]::Matches($x.CommandLine, 'net\.neoforged\.\S+') | ForEach-Object { $_.Value }
}
```

Expected: one process with `@C:\dev\spellcraft\build\moddev\gameTestServerRunVmArgs.txt` (or similar) in its arguments. If nothing matches within 5 min, print every `java.exe` command line (`Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Select-Object -Expand CommandLine`) and record what the game process actually looks like. Wait for the background GameTest run to finish; expected exit 0.

- [x] **Step 3: Check the build stamp is not moved by a run**

PowerShell, before and after another `./gradlew runGameTestServer --console=plain`:

```powershell
Get-ChildItem build\classes\java\main, build\resources\main -Recurse -File | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1 FullName, LastWriteTimeUtc
```

Expected: the same file and time both times. If the run moves the stamp, stop and report: every instance would always look stale.

- [x] **Step 4: Record the client's command line**

In Bash, start `./gradlew runClient --console=plain` with `run_in_background: true` (a game window opens on Will's screen). Run the step 2 snippet with `gameTestServerRun` replaced by `clientRun`. Expected: `@...\build\moddev\clientRunVmArgs.txt`. Then confirm the client's start time is later than the stamp from step 3:

```powershell
$c = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*\build\moddev\clientRun*' }
$c.CreationDate.ToUniversalTime()
```

- [x] **Step 5: GameTests while the client runs**

With the client from step 4 still running: `./gradlew runGameTestServer --console=plain`. Expected: exit 0, `BUILD SUCCESSFUL`. Both write `run/logs/latest.log`; a log4j rollover warning is acceptable, a failed test or crash is not. If it fails, **stop and report**.

- [x] **Step 6: Rebuild while the client runs**

With the client still running, add a comment line at the top of `src/main/java/io/github/willrieske/spellcraft/gametest/UpsideDownGameTests.java` (after the `package` line: `// deploy-script spike`), then:

```bash
./gradlew classes --console=plain
```

Expected: `BUILD SUCCESSFUL` (no "file in use" / `AccessDeniedException` on `build/classes`). Then restore the file and rebuild:

```bash
git checkout -- src/main/java/io/github/willrieske/spellcraft/gametest/UpsideDownGameTests.java
./gradlew classes --console=plain
```

Re-run the step 3 stamp snippet: it must now be later than the client's start time from step 4 (the client is stale). If the build failed on a locked file, **stop and report**.

- [x] **Step 7: Find the dedicated server's argument-file names without launching it**

```bash
./gradlew tasks --all --console=plain | grep -i '^prepare'
```

Run the task that prepares the `server` run (expected name `prepareServerRun`), then `ls build/moddev`. Expected: `serverRunVmArgs.txt` and `serverRunProgramArgs.txt` next to the client ones. Do not run `runServer` (it needs the EULA accepted).

- [x] **Step 8: Close the client the way the script will**

```powershell
$c = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*\build\moddev\clientRun*' }
$proc = Get-Process -Id $c.ProcessId
$sw = [Diagnostics.Stopwatch]::StartNew()
"CloseMainWindow returned $($proc.CloseMainWindow())"
"Exited: $($proc.WaitForExit(20000)) after $([int]$sw.Elapsed.TotalSeconds) s"
```

Expected: `True` and `Exited: True` within 20 s. If it does not exit, record that. Do not force it; ask Will to quit from the game menu.

- [x] **Step 9: Write the facts into the spec**

Replace the line `Filled in by the plan's Task 1.` under `## Verified launch facts` with a dated list (`Verified 2026-10-..:`), one bullet each:

- The client command line: the java path's quoting, every `@` token and the main class, with paths exactly as seen (single `\`, `/`, or doubled `\\`).
- The same for the GameTest server.
- The dedicated server: the argument-file names from step 7, inferred (not launched, EULA).
- A run does not move the build stamp (step 3).
- GameTests pass while the client runs (step 5).
- Rebuilding while the client runs works and makes it stale (step 6).
- `CloseMainWindow()` result and seconds to exit (step 8).

- [x] **Step 10: Commit**

```bash
git add docs/superpowers/specs/2026-10-01-deploy-test-env-design.md
git commit -m "docs: record verified dev-run facts in the deploy-script spec"
```

> **As built (`0f56792`):** diverged — the `@` argument-file tokens in the real process command lines use DOUBLED backslashes (`@C:\\dev\\...\\build\\moddev\\clientRunVmArgs.txt`), so the plan's `*\build\moddev\clientRun*` match would not match; the java path is the only quoted token; reason: observed on the live run, recorded in the spec's Verified launch facts (matching in Tasks 2-3 must normalize `\\` to `\` or match the doubled form).

---

### Task 2: Classify instances and compute the stamp and verdict

**Files:**

- Create: `scripts/DeployTestEnv.psm1`
- Create: `scripts/tests/DeployTestEnv.Tests.ps1`

**Interfaces:**

- Consumes: the command-line shapes recorded in the spec's "Verified launch facts" (Task 1).
- Produces:
  - `Get-InstanceKind -CommandLine <string|null> -RepoRoot <string>` -> `'Client'`, `'GameTestServer'`, `'Server'` or `$null`.
  - `Get-BuildStamp -RepoRoot <string>` -> `[datetime]` (UTC) or `$null`.
  - `Get-Verdict -StartTimeUtc <datetime> -BuildStampUtc <datetime|null>` -> `'UpToDate'` or `'Stale'`.

- [x] **Step 1: Write the failing tests**

Create `scripts/tests/DeployTestEnv.Tests.ps1`. The fixture lines below assume the shape `java.exe @<root>\build\moddev\<run>RunVmArgs.txt net.neoforged.devlaunch.Main @<root>\build\moddev\<run>RunProgramArgs.txt`. **Before writing them, compare with the spec's "Verified launch facts" and copy the real shape** (quoting, separators, extra tokens), keeping `$Root` substituted in.

```powershell
# Pester 3.4 (built into Windows PowerShell 5.1). Run:
#   powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester -Path scripts/tests -EnableExit"
# ASCII only.

Import-Module (Join-Path $PSScriptRoot '..\DeployTestEnv.psm1') -Force

$Root = 'C:\dev\spellcraft'
$Java = '"C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot\bin\java.exe"'

function New-RunLine([string] $RepoRoot, [string] $Run) {
    "$Java @$RepoRoot\build\moddev\${Run}VmArgs.txt net.neoforged.devlaunch.Main @$RepoRoot\build\moddev\${Run}ProgramArgs.txt"
}

$ClientLine = New-RunLine $Root 'clientRun'
$GameTestLine = New-RunLine $Root 'gameTestServerRun'
$ServerLine = New-RunLine $Root 'serverRun'
$DaemonLine = "$Java -Xmx2g -cp C:\Users\will\.gradle\wrapper\dists\gradle-9.2.1-bin\abc\gradle-9.2.1\lib\gradle-daemon-main-9.2.1.jar org.gradle.launcher.daemon.bootstrap.GradleDaemon 9.2.1"

Describe 'Get-InstanceKind' {
    It 'classifies the dev client' {
        Get-InstanceKind -CommandLine $ClientLine -RepoRoot $Root | Should Be 'Client'
    }
    It 'classifies the GameTest server, not as the dedicated server' {
        Get-InstanceKind -CommandLine $GameTestLine -RepoRoot $Root | Should Be 'GameTestServer'
    }
    It 'classifies the dedicated server' {
        Get-InstanceKind -CommandLine $ServerLine -RepoRoot $Root | Should Be 'Server'
    }
    It 'ignores the Gradle daemon' {
        Get-InstanceKind -CommandLine $DaemonLine -RepoRoot $Root | Should BeNullOrEmpty
    }
    It 'ignores a null or empty command line (CIM hides some processes)' {
        Get-InstanceKind -CommandLine $null -RepoRoot $Root | Should BeNullOrEmpty
        Get-InstanceKind -CommandLine '' -RepoRoot $Root | Should BeNullOrEmpty
    }
    It 'ignores a sibling checkout whose name starts with the repo name' {
        $line = New-RunLine 'C:\dev\spellcraft2' 'clientRun'
        Get-InstanceKind -CommandLine $line -RepoRoot $Root | Should BeNullOrEmpty
    }
    It 'ignores a worktree inside the repo, but sees it from its own root' {
        $wt = 'C:\dev\spellcraft\.claude\worktrees\feature'
        $line = New-RunLine $wt 'clientRun'
        Get-InstanceKind -CommandLine $line -RepoRoot $Root | Should BeNullOrEmpty
        Get-InstanceKind -CommandLine $line -RepoRoot $wt | Should Be 'Client'
    }
    It 'matches regardless of case' {
        $line = New-RunLine 'c:\DEV\Spellcraft' 'clientRun'
        Get-InstanceKind -CommandLine $line -RepoRoot $Root | Should Be 'Client'
    }
    It 'accepts a repo root with a trailing backslash' {
        Get-InstanceKind -CommandLine $ClientLine -RepoRoot 'C:\dev\spellcraft\' | Should Be 'Client'
    }
}

Describe 'Get-BuildStamp' {
    function New-Repo([string] $Name) {
        $repo = Join-Path $TestDrive $Name
        New-Item -ItemType Directory -Path $repo | Out-Null
        $repo
    }
    function New-File([string] $Path, [datetime] $WriteTimeUtc) {
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Path) | Out-Null
        Set-Content -LiteralPath $Path -Value 'x' -Encoding ASCII
        (Get-Item -LiteralPath $Path).LastWriteTimeUtc = $WriteTimeUtc
    }
    $old = [datetime]::SpecifyKind([datetime]'2026-10-01 10:00:00', 'Utc')
    $new = [datetime]::SpecifyKind([datetime]'2026-10-01 11:30:00', 'Utc')

    It 'is null when nothing has been built' {
        Get-BuildStamp -RepoRoot (New-Repo 'unbuilt') | Should BeNullOrEmpty
    }
    It 'is null when the output directories are empty' {
        $repo = New-Repo 'empty'
        New-Item -ItemType Directory -Force -Path (Join-Path $repo 'build\classes\java\main') | Out-Null
        New-Item -ItemType Directory -Force -Path (Join-Path $repo 'build\resources\main') | Out-Null
        Get-BuildStamp -RepoRoot $repo | Should BeNullOrEmpty
    }
    It 'is the newest file across classes and resources, at any depth' {
        $repo = New-Repo 'built'
        New-File (Join-Path $repo 'build\classes\java\main\io\A.class') $old
        New-File (Join-Path $repo 'build\resources\main\assets\spellcraft\lang\en_us.json') $new
        Get-BuildStamp -RepoRoot $repo | Should Be $new
    }
    It 'ignores the jar and test outputs' {
        $repo = New-Repo 'jar'
        New-File (Join-Path $repo 'build\classes\java\main\A.class') $old
        New-File (Join-Path $repo 'build\libs\spellcraft-1.0.0.jar') $new
        New-File (Join-Path $repo 'build\classes\java\test\ATest.class') $new
        Get-BuildStamp -RepoRoot $repo | Should Be $old
    }
    It 'works with only one of the two directories present' {
        $repo = New-Repo 'classesonly'
        New-File (Join-Path $repo 'build\classes\java\main\A.class') $old
        Get-BuildStamp -RepoRoot $repo | Should Be $old
    }
}

Describe 'Get-Verdict' {
    $stamp = [datetime]::SpecifyKind([datetime]'2026-10-01 12:00:00', 'Utc')

    It 'is UpToDate when the instance started after the build' {
        Get-Verdict -StartTimeUtc $stamp.AddSeconds(1) -BuildStampUtc $stamp | Should Be 'UpToDate'
    }
    It 'is Stale when the instance started before the build' {
        Get-Verdict -StartTimeUtc $stamp.AddSeconds(-1) -BuildStampUtc $stamp | Should Be 'Stale'
    }
    It 'is Stale on an exact tie' {
        Get-Verdict -StartTimeUtc $stamp -BuildStampUtc $stamp | Should Be 'Stale'
    }
    It 'is Stale when there is no build stamp' {
        Get-Verdict -StartTimeUtc $stamp -BuildStampUtc $null | Should Be 'Stale'
    }
}
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester -Path scripts/tests -EnableExit"`
Expected: FAIL. `Import-Module` reports the module was not loaded, and the tests fail with `The term 'Get-InstanceKind' is not recognized`.

- [x] **Step 3: Write the module**

Create `scripts/DeployTestEnv.psm1`:

```powershell
# Helpers for Deploy-TestEnv.ps1.
# Spec: docs/superpowers/specs/2026-10-01-deploy-test-env-design.md
# Tests: scripts/tests/DeployTestEnv.Tests.ps1 (Pester 3.4).
# Windows PowerShell 5.1; ASCII only (5.1 reads BOM-less files as ANSI).

Set-StrictMode -Version 2.0

# The dev runs read classes and resources from these directories, not from the jar.
$script:BuildOutputDirs = @('build\classes\java\main', 'build\resources\main')

# ModDevGradle passes '@<repo>\build\moddev\<run>RunVmArgs.txt' (and ProgramArgs) to java.exe.
# Anchoring on '<repo>\build\moddev\' keeps 'serverRun' from matching inside 'gameTestServerRun'
# and ignores other checkouts and worktrees.
$script:RunKinds = @{
    'clientrun'         = 'Client'
    'gametestserverrun' = 'GameTestServer'
    'serverrun'         = 'Server'
}

<#
.SYNOPSIS
    Classify a java.exe command line as a dev run of this repo.
.PARAMETER CommandLine
    The process command line; null or empty (CIM hides some) yields $null.
.PARAMETER RepoRoot
    The repo root; only runs under '<RepoRoot>\build\moddev\' match.
.OUTPUTS
    'Client', 'GameTestServer', 'Server' or $null.
#>
function Get-InstanceKind {
    param(
        [AllowNull()][AllowEmptyString()][string] $CommandLine,
        [Parameter(Mandatory)][string] $RepoRoot
    )
    if ([string]::IsNullOrEmpty($CommandLine)) { return $null }
    $pattern = [regex]::Escape($RepoRoot.TrimEnd('\')) + '\\build\\moddev\\(clientRun|gameTestServerRun|serverRun)'
    $m = [regex]::Match($CommandLine, $pattern, 'IgnoreCase')
    if (-not $m.Success) { return $null }
    $script:RunKinds[$m.Groups[1].Value.ToLowerInvariant()]
}

<#
.SYNOPSIS
    Time of the newest compiled class or processed resource.
.PARAMETER RepoRoot
    The repo root.
.OUTPUTS
    [datetime] (UTC), or $null when nothing has been built.
#>
function Get-BuildStamp {
    param([Parameter(Mandatory)][string] $RepoRoot)
    $dirs = @($script:BuildOutputDirs |
        ForEach-Object { Join-Path $RepoRoot $_ } |
        Where-Object { Test-Path -LiteralPath $_ -PathType Container })
    if ($dirs.Count -eq 0) { return $null }
    $newest = Get-ChildItem -LiteralPath $dirs -Recurse -File |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if ($null -eq $newest) { return $null }
    $newest.LastWriteTimeUtc
}

<#
.SYNOPSIS
    Whether an instance started strictly after the build stamp.
.PARAMETER StartTimeUtc
    The instance start time (UTC).
.PARAMETER BuildStampUtc
    The build stamp (UTC); $null means nothing built, so always 'Stale'.
.OUTPUTS
    'UpToDate' or 'Stale'.
#>
function Get-Verdict {
    param(
        [Parameter(Mandatory)][datetime] $StartTimeUtc,
        [Nullable[datetime]] $BuildStampUtc
    )
    if ($null -eq $BuildStampUtc) { return 'Stale' }
    if ($StartTimeUtc -gt $BuildStampUtc) { 'UpToDate' } else { 'Stale' }
}
```

If Task 1 recorded `/` or doubled `\\` separators in the command line, normalise first in `Get-InstanceKind`: add `$CommandLine = $CommandLine -replace '/', '\' -replace '\\\\', '\'` after the null check (and the same for `$RepoRoot`), and add a test with the recorded form.

- [x] **Step 4: Run the tests to verify they pass**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester -Path scripts/tests -EnableExit"`
Expected: `Tests Passed: 18, Failed: 0`, exit 0.

- [x] **Step 5: Check ASCII and commit**

```bash
grep -n '[^ -~[:space:]]' scripts/DeployTestEnv.psm1 scripts/tests/DeployTestEnv.Tests.ps1 || echo ascii-ok
git add scripts/DeployTestEnv.psm1 scripts/tests/DeployTestEnv.Tests.ps1
git commit -m "feat(scripts): classify dev-run instances and compute the build stamp"
```

Expected: `ascii-ok`, then the commit gate passes.

> **As built (`843ea82`):** diverged — `Get-InstanceKind` collapses doubled backslashes and matches `@<root>\build\moddev\<run>(VmArgs|ProgramArgs).txt` (anchored on the root), and the fixtures use the doubled-backslash `@` tokens recorded in the spec; reason: Task 1's live-run finding (see Task 1's note).

---

### Task 3: Discover instances, choose which must stop, format status

**Files:**

- Modify: `scripts/DeployTestEnv.psm1` (append)
- Modify: `scripts/tests/DeployTestEnv.Tests.ps1` (append)

**Interfaces:**

- Consumes: `Get-InstanceKind`, `Get-Verdict` (Task 2).
- Produces:
  - `Get-Instances -RepoRoot <string>` -> zero or more `[pscustomobject]@{ Kind = <string>; ProcessId = <int>; StartTimeUtc = <datetime, Kind Utc> }`. Callers wrap it in `@(...)`.
  - `Select-InstancesToStop -Instances <object[]> -BuildStampUtc <datetime|null> -Step <'Servers'|'Client'>` -> the instances that must stop: for `Servers`, every `GameTestServer` plus stale `Server`s; for `Client`, stale `Client`s.
  - `Test-HasCurrentClient -Instances <object[]> -BuildStampUtc <datetime|null>` -> `[bool]`.
  - `Format-InstanceLine -Instance <object> -BuildStampUtc <datetime|null>` -> `'<Kind padded 15> PID <pid padded 6> started yyyy-MM-dd HH:mm:ss  up to date|stale'` (local time).
  - `Format-BuildStamp -BuildStampUtc <datetime|null>` -> `'Build stamp: yyyy-MM-dd HH:mm:ss'` (local) or `'Build stamp: none (nothing built yet)'`.

- [ ] **Step 1: Write the failing tests**

Append to `scripts/tests/DeployTestEnv.Tests.ps1`. Mock bodies run in the module's scope and cannot see this file's variables, so they use literal strings.

```powershell
function New-Instance([string] $Kind, [datetime] $StartTimeUtc, [int] $ProcessId = 1) {
    [pscustomobject]@{ Kind = $Kind; ProcessId = $ProcessId; StartTimeUtc = $StartTimeUtc }
}

$Stamp = [datetime]::SpecifyKind([datetime]'2026-10-01 12:00:00', 'Utc')
$Before = $Stamp.AddMinutes(-5)
$After = $Stamp.AddMinutes(5)

Describe 'Get-Instances' {
    Mock -ModuleName DeployTestEnv Get-CimInstance {
        @(
            [pscustomobject]@{ ProcessId = 101; CreationDate = [datetime]'2026-10-01 14:05:01'
                CommandLine = 'java.exe @C:\dev\spellcraft\build\moddev\clientRunVmArgs.txt net.neoforged.devlaunch.Main' }
            [pscustomobject]@{ ProcessId = 102; CreationDate = [datetime]'2026-10-01 14:06:00'
                CommandLine = 'java.exe -cp gradle-daemon-main.jar org.gradle.launcher.daemon.bootstrap.GradleDaemon 9.2.1' }
            [pscustomobject]@{ ProcessId = 103; CreationDate = [datetime]'2026-10-01 14:07:00'; CommandLine = $null }
            [pscustomobject]@{ ProcessId = 104; CreationDate = [datetime]'2026-10-01 14:08:00'
                CommandLine = 'java.exe @C:\dev\spellcraft\build\moddev\gameTestServerRunVmArgs.txt' }
        )
    }

    $result = @(Get-Instances -RepoRoot 'C:\dev\spellcraft')

    It 'asks CIM for java.exe processes only' {
        Assert-MockCalled Get-CimInstance -ModuleName DeployTestEnv -Times 1 -Exactly -ParameterFilter { $Filter -eq "Name='java.exe'" }
    }
    It 'keeps only this repo''s dev runs' {
        $result.Count | Should Be 2
        $result[0].Kind | Should Be 'Client'
        $result[0].ProcessId | Should Be 101
        $result[1].Kind | Should Be 'GameTestServer'
        $result[1].ProcessId | Should Be 104
    }
    It 'converts the start time to UTC' {
        $result[0].StartTimeUtc | Should Be ([datetime]'2026-10-01 14:05:01').ToUniversalTime()
        $result[0].StartTimeUtc.Kind | Should Be 'Utc'
    }
}

Describe 'Get-Instances with nothing running' {
    Mock -ModuleName DeployTestEnv Get-CimInstance { }

    It 'returns nothing' {
        @(Get-Instances -RepoRoot 'C:\dev\spellcraft').Count | Should Be 0
    }
}

Describe 'Select-InstancesToStop' {
    It 'stops every GameTest server before the tests, even an up-to-date one' {
        $r = @(Select-InstancesToStop -Instances @(New-Instance 'GameTestServer' $After 7) -BuildStampUtc $Stamp -Step Servers)
        $r.Count | Should Be 1
        $r[0].ProcessId | Should Be 7
    }
    It 'stops a stale dedicated server but leaves an up-to-date one' {
        $all = @((New-Instance 'Server' $Before 1), (New-Instance 'Server' $After 2))
        $r = @(Select-InstancesToStop -Instances $all -BuildStampUtc $Stamp -Step Servers)
        $r.Count | Should Be 1
        $r[0].ProcessId | Should Be 1
    }
    It 'leaves clients alone in the server step' {
        @(Select-InstancesToStop -Instances @(New-Instance 'Client' $Before) -BuildStampUtc $Stamp -Step Servers).Count | Should Be 0
    }
    It 'stops stale clients only in the client step' {
        $all = @((New-Instance 'Client' $Before 1), (New-Instance 'Client' $After 2), (New-Instance 'GameTestServer' $Before 3))
        $r = @(Select-InstancesToStop -Instances $all -BuildStampUtc $Stamp -Step Client)
        $r.Count | Should Be 1
        $r[0].ProcessId | Should Be 1
    }
    It 'treats every client as stale when there is no build stamp' {
        @(Select-InstancesToStop -Instances @(New-Instance 'Client' $After) -BuildStampUtc $null -Step Client).Count | Should Be 1
    }
    It 'returns nothing for no instances' {
        @(Select-InstancesToStop -Instances @() -BuildStampUtc $Stamp -Step Client).Count | Should Be 0
    }
}

Describe 'Test-HasCurrentClient' {
    It 'is true when an up-to-date client runs' {
        Test-HasCurrentClient -Instances @(New-Instance 'Client' $After) -BuildStampUtc $Stamp | Should Be $true
    }
    It 'is false when only a stale client runs' {
        Test-HasCurrentClient -Instances @(New-Instance 'Client' $Before) -BuildStampUtc $Stamp | Should Be $false
    }
    It 'is false when only an up-to-date server runs' {
        Test-HasCurrentClient -Instances @(New-Instance 'Server' $After) -BuildStampUtc $Stamp | Should Be $false
    }
    It 'is false when nothing runs' {
        Test-HasCurrentClient -Instances @() -BuildStampUtc $Stamp | Should Be $false
    }
}

Describe 'Format-InstanceLine and Format-BuildStamp' {
    $startUtc = ([datetime]'2026-10-01 14:05:01').ToUniversalTime()

    It 'shows kind, PID, local start time and up to date' {
        $line = Format-InstanceLine -Instance (New-Instance 'Client' $startUtc 4242) -BuildStampUtc $startUtc.AddMinutes(-1)
        $line | Should Match '^Client\s+PID 4242\s+started 2026-10-01 14:05:01  up to date$'
    }
    It 'shows stale' {
        $line = Format-InstanceLine -Instance (New-Instance 'GameTestServer' $startUtc 7) -BuildStampUtc $startUtc.AddMinutes(1)
        $line | Should Match '^GameTestServer\s+PID 7\s+started 2026-10-01 14:05:01  stale$'
    }
    It 'shows the stamp in local time' {
        Format-BuildStamp -BuildStampUtc $startUtc | Should Be 'Build stamp: 2026-10-01 14:05:01'
    }
    It 'says when nothing has been built' {
        Format-BuildStamp -BuildStampUtc $null | Should Be 'Build stamp: none (nothing built yet)'
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester -Path scripts/tests -EnableExit"`
Expected: the Task 2 tests pass. The new ones fail with `The term 'Get-Instances' is not recognized` (Pester 3.4 also refuses to mock in a module whose function is missing), and similar for the other new functions.

- [ ] **Step 3: Implement**

Append to `scripts/DeployTestEnv.psm1`:

```powershell
<#
.SYNOPSIS
    List this repo's running dev instances (client, GameTest server, dedicated server).
.PARAMETER RepoRoot
    The repo root.
.OUTPUTS
    Zero or more objects with Kind, ProcessId and StartTimeUtc. Wrap the call in @(...).
#>
function Get-Instances {
    param([Parameter(Mandatory)][string] $RepoRoot)
    foreach ($p in @(Get-CimInstance -ClassName Win32_Process -Filter "Name='java.exe'")) {
        $kind = Get-InstanceKind -CommandLine $p.CommandLine -RepoRoot $RepoRoot
        if ($null -eq $kind) { continue }
        [pscustomobject]@{
            Kind         = $kind
            ProcessId    = [int]$p.ProcessId
            StartTimeUtc = $p.CreationDate.ToUniversalTime()
        }
    }
}

<#
.SYNOPSIS
    Pick the instances that must stop before the given step.
.PARAMETER Instances
    Instances from Get-Instances.
.PARAMETER BuildStampUtc
    The build stamp (UTC) or $null.
.PARAMETER Step
    'Servers': every GameTestServer plus stale Servers. 'Client': stale Clients.
.OUTPUTS
    The instances to stop; may be none.
#>
function Select-InstancesToStop {
    param(
        [AllowEmptyCollection()][object[]] $Instances = @(),
        [AllowNull()] $BuildStampUtc,
        [Parameter(Mandatory)][ValidateSet('Servers', 'Client')][string] $Step
    )
    foreach ($i in $Instances) {
        $stale = (Get-Verdict -StartTimeUtc $i.StartTimeUtc -BuildStampUtc $BuildStampUtc) -eq 'Stale'
        $mustStop = switch ($Step) {
            # A running GameTest server is another test run in progress; it shares run/gametestserver.
            'Servers' { ($i.Kind -eq 'GameTestServer') -or ($i.Kind -eq 'Server' -and $stale) }
            'Client' { $i.Kind -eq 'Client' -and $stale }
        }
        if ($mustStop) { $i }
    }
}

<#
.SYNOPSIS
    Whether an up-to-date client is already running.
.PARAMETER Instances
    Instances from Get-Instances.
.PARAMETER BuildStampUtc
    The build stamp (UTC) or $null.
.OUTPUTS
    [bool]
#>
function Test-HasCurrentClient {
    param(
        [AllowEmptyCollection()][object[]] $Instances = @(),
        [AllowNull()] $BuildStampUtc
    )
    foreach ($i in $Instances) {
        if ($i.Kind -eq 'Client' -and (Get-Verdict -StartTimeUtc $i.StartTimeUtc -BuildStampUtc $BuildStampUtc) -eq 'UpToDate') {
            return $true
        }
    }
    $false
}

<#
.SYNOPSIS
    One status line for an instance: kind, PID, local start time, verdict.
.PARAMETER Instance
    An instance from Get-Instances.
.PARAMETER BuildStampUtc
    The build stamp (UTC) or $null.
.OUTPUTS
    [string]
#>
function Format-InstanceLine {
    param(
        [Parameter(Mandatory)] $Instance,
        [AllowNull()] $BuildStampUtc
    )
    $verdict = if ((Get-Verdict -StartTimeUtc $Instance.StartTimeUtc -BuildStampUtc $BuildStampUtc) -eq 'UpToDate') { 'up to date' } else { 'stale' }
    '{0,-15} PID {1,-6} started {2:yyyy-MM-dd HH:mm:ss}  {3}' -f $Instance.Kind, $Instance.ProcessId, $Instance.StartTimeUtc.ToLocalTime(), $verdict
}

<#
.SYNOPSIS
    The build stamp as a status line, in local time.
.PARAMETER BuildStampUtc
    The build stamp (UTC) or $null.
.OUTPUTS
    [string]
#>
function Format-BuildStamp {
    param([AllowNull()] $BuildStampUtc)
    if ($null -eq $BuildStampUtc) { return 'Build stamp: none (nothing built yet)' }
    'Build stamp: {0:yyyy-MM-dd HH:mm:ss}' -f $BuildStampUtc.ToLocalTime()
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester -Path scripts/tests -EnableExit"`
Expected: `Failed: 0`, exit 0 (36 tests in total).

- [ ] **Step 5: Check ASCII and commit**

```bash
grep -n '[^ -~[:space:]]' scripts/DeployTestEnv.psm1 scripts/tests/DeployTestEnv.Tests.ps1 || echo ascii-ok
git add scripts/DeployTestEnv.psm1 scripts/tests/DeployTestEnv.Tests.ps1
git commit -m "feat(scripts): find running instances and choose which must stop"
```

---

### Task 4: Prompt and stop

**Files:**

- Modify: `scripts/DeployTestEnv.psm1` (append)
- Modify: `scripts/tests/DeployTestEnv.Tests.ps1` (append)

**Interfaces:**

- Consumes: `Format-InstanceLine`, `Format-BuildStamp` (Task 3).
- Produces:
  - `Test-CanPrompt` -> `[bool]`: false if `[Environment]::UserInteractive` is false, stdin is redirected, or the process was started with `-NonInteractive`.
  - `Confirm-StopInstances -Instances <object[]> -BuildStampUtc <datetime|null>` -> `[bool]`: prints the instances, asks `Stop them? [y/N]`; true only for `y`/`yes` (any case); false without asking when `Test-CanPrompt` is false.
  - `Stop-Instance -Instance <object> [-GraceSeconds 20]` -> `[bool]`, true once the process is confirmed gone: client -> `CloseMainWindow()`, wait, then `Stop-Process -Force`; server -> `Stop-Process -Force`. Already gone -> true, nothing called. A process still alive after the forced stop and a 10 s wait -> false.
  - `Invoke-StopStep -ToStop <object[]> -BuildStampUtc <datetime|null>` -> `[bool]`: true if nothing to stop or all confirmed gone after `y`; false if declined or any instance did not exit (the caller must not go on: all runs share `run/`).

- [ ] **Step 1: Write the failing tests**

Append to `scripts/tests/DeployTestEnv.Tests.ps1`:

```powershell
Describe 'Confirm-StopInstances' {
    Mock -ModuleName DeployTestEnv Write-Host { }
    $one = @(New-Instance 'Client' $Before 9)

    Context 'when it cannot prompt' {
        Mock -ModuleName DeployTestEnv Test-CanPrompt { $false }
        Mock -ModuleName DeployTestEnv Read-Host { 'y' }

        It 'declines without asking' {
            Confirm-StopInstances -Instances $one -BuildStampUtc $Stamp | Should Be $false
            Assert-MockCalled Read-Host -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
        }
    }

    Context 'when it can prompt' {
        Mock -ModuleName DeployTestEnv Test-CanPrompt { $true }

        It "treats 'y' as yes" {
            Mock -ModuleName DeployTestEnv Read-Host { 'y' }
            Confirm-StopInstances -Instances $one -BuildStampUtc $Stamp | Should Be $true
        }
        It "treats 'Y' as yes" {
            Mock -ModuleName DeployTestEnv Read-Host { 'Y' }
            Confirm-StopInstances -Instances $one -BuildStampUtc $Stamp | Should Be $true
        }
        It "treats 'yes' as yes" {
            Mock -ModuleName DeployTestEnv Read-Host { 'yes' }
            Confirm-StopInstances -Instances $one -BuildStampUtc $Stamp | Should Be $true
        }
        It 'treats Enter (empty) as no' {
            Mock -ModuleName DeployTestEnv Read-Host { '' }
            Confirm-StopInstances -Instances $one -BuildStampUtc $Stamp | Should Be $false
        }
        It "treats 'n' as no" {
            Mock -ModuleName DeployTestEnv Read-Host { 'n' }
            Confirm-StopInstances -Instances $one -BuildStampUtc $Stamp | Should Be $false
        }
        It "treats 'yep' as no (only y or yes)" {
            Mock -ModuleName DeployTestEnv Read-Host { 'yep' }
            Confirm-StopInstances -Instances $one -BuildStampUtc $Stamp | Should Be $false
        }
    }
}

Describe 'Stop-Instance' {
    Mock -ModuleName DeployTestEnv Write-Host { }
    Mock -ModuleName DeployTestEnv Stop-Process { }
    # Fake processes: Mock bodies run in the module's scope and cannot see this file's variables,
    # so each Context builds its own with literal return values.
    $client = New-Instance 'Client' $Before 5
    $server = New-Instance 'Server' $Before 6

    Context 'when the process has already gone' {
        Mock -ModuleName DeployTestEnv Get-Process { $null }

        It 'does nothing and reports it gone' {
            Stop-Instance -Instance $client | Should Be $true
            Assert-MockCalled Stop-Process -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
        }
    }

    Context 'a client that closes when asked' {
        Mock -ModuleName DeployTestEnv Get-Process {
            $p = New-Object PSObject
            $p | Add-Member -MemberType ScriptMethod -Name CloseMainWindow -Value { $true }
            $p | Add-Member -MemberType ScriptMethod -Name WaitForExit -Value { param($ms) $true }
            $p
        }

        It 'is not force-killed' {
            Stop-Instance -Instance $client | Should Be $true
            Assert-MockCalled Stop-Process -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
        }
    }

    Context 'a client that is asked to close but does not exit in time' {
        Mock -ModuleName DeployTestEnv Get-Process {
            $p = New-Object PSObject
            $p | Add-Member -MemberType ScriptMethod -Name CloseMainWindow -Value { $true }
            $p | Add-Member -MemberType ScriptMethod -Name WaitForExit -Value { param($ms) $false }
            $p
        }

        It 'is force-killed once, and reported not gone if it never exits' {
            Stop-Instance -Instance $client -GraceSeconds 1 | Should Be $false
            Assert-MockCalled Stop-Process -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It
        }
    }

    Context 'a client with no main window to close' {
        Mock -ModuleName DeployTestEnv Get-Process {
            $p = New-Object PSObject
            $p | Add-Member -MemberType ScriptMethod -Name CloseMainWindow -Value { $false }
            $p | Add-Member -MemberType ScriptMethod -Name WaitForExit -Value { param($ms) $true }
            $p
        }

        It 'is force-killed straight away' {
            Stop-Instance -Instance $client | Should Be $true
            Assert-MockCalled Stop-Process -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It
        }
    }

    Context 'a server' {
        Mock -ModuleName DeployTestEnv Get-Process {
            $p = New-Object PSObject
            # A server must never be sent CloseMainWindow: it throws if called.
            $p | Add-Member -MemberType ScriptMethod -Name CloseMainWindow -Value { throw 'CloseMainWindow on a server' }
            $p | Add-Member -MemberType ScriptMethod -Name WaitForExit -Value { param($ms) $true }
            $p
        }

        It 'is force-killed without CloseMainWindow' {
            Stop-Instance -Instance $server | Should Be $true
            Assert-MockCalled Stop-Process -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It
        }
    }
}

Describe 'Invoke-StopStep' {
    Mock -ModuleName DeployTestEnv Stop-Instance { $true }

    It 'succeeds without asking when nothing must stop' {
        Mock -ModuleName DeployTestEnv Confirm-StopInstances { $true }
        Invoke-StopStep -ToStop @() -BuildStampUtc $Stamp | Should Be $true
        Assert-MockCalled Confirm-StopInstances -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
    }
    It 'stops nothing and fails when declined' {
        Mock -ModuleName DeployTestEnv Confirm-StopInstances { $false }
        Invoke-StopStep -ToStop @(New-Instance 'Client' $Before) -BuildStampUtc $Stamp | Should Be $false
        Assert-MockCalled Stop-Instance -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
    }
    It 'stops each instance once after y' {
        Mock -ModuleName DeployTestEnv Confirm-StopInstances { $true }
        $two = @((New-Instance 'GameTestServer' $After 1), (New-Instance 'Server' $Before 2))
        Invoke-StopStep -ToStop $two -BuildStampUtc $Stamp | Should Be $true
        Assert-MockCalled Stop-Instance -ModuleName DeployTestEnv -Times 2 -Exactly -Scope It
    }
    It 'fails when an instance does not exit after y' {
        Mock -ModuleName DeployTestEnv Confirm-StopInstances { $true }
        Mock -ModuleName DeployTestEnv Stop-Instance { $false }
        Invoke-StopStep -ToStop @(New-Instance 'GameTestServer' $After 1) -BuildStampUtc $Stamp | Should Be $false
    }
}
```

`Assert-MockCalled` counts across the whole `Describe`/`Context` by default in Pester 3.4; `-Scope It` keeps each count to its own test.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester -Path scripts/tests -EnableExit"`
Expected: earlier tests pass. The new ones fail with `Could not find Command Test-CanPrompt` (or `Confirm-StopInstances`, `Stop-Instance`).

- [ ] **Step 3: Implement**

Append to `scripts/DeployTestEnv.psm1`:

```powershell
<#
.SYNOPSIS
    Whether the script can ask the user a question.
.OUTPUTS
    [bool]: false for a non-interactive host, redirected stdin or -NonInteractive.
#>
function Test-CanPrompt {
    if (-not [Environment]::UserInteractive) { return $false }
    if ([Console]::IsInputRedirected) { return $false }
    $nonInteractive = @([Environment]::GetCommandLineArgs() | Where-Object { $_ -match '^-noni' })
    $nonInteractive.Count -eq 0
}

<#
.SYNOPSIS
    List the instances that must stop and ask 'Stop them? [y/N]'.
.PARAMETER Instances
    The instances that must stop.
.PARAMETER BuildStampUtc
    The build stamp (UTC) or $null.
.OUTPUTS
    [bool]: true only for y or yes; false (without asking) when it cannot prompt.
#>
function Confirm-StopInstances {
    param(
        [AllowEmptyCollection()][object[]] $Instances = @(),
        [AllowNull()] $BuildStampUtc
    )
    Write-Host 'These must stop before continuing (GameTest servers always; others because they are stale):'
    Write-Host ('  ' + (Format-BuildStamp -BuildStampUtc $BuildStampUtc))
    foreach ($i in $Instances) {
        Write-Host ('  ' + (Format-InstanceLine -Instance $i -BuildStampUtc $BuildStampUtc))
    }
    if (-not (Test-CanPrompt)) {
        Write-Host 'Cannot prompt (non-interactive run); treating the answer as N.'
        return $false
    }
    $answer = Read-Host 'Stop them? [y/N]'
    $answer -match '^(y|yes)$'
}

<#
.SYNOPSIS
    Stop one instance: a client is asked to close first, anything else (or a client that will not) is force-stopped.
.DESCRIPTION
    Only call after the user answered y (see Invoke-StopStep).
.PARAMETER Instance
    An instance from Get-Instances.
.PARAMETER GraceSeconds
    How long a client gets to close after CloseMainWindow() before it is forced.
.OUTPUTS
    [bool]: true once the process is confirmed gone, false if it is still alive after the forced stop.
#>
function Stop-Instance {
    param(
        [Parameter(Mandatory)] $Instance,
        [int] $GraceSeconds = 20
    )
    $proc = Get-Process -Id $Instance.ProcessId -ErrorAction SilentlyContinue
    if ($null -eq $proc) { return $true }
    if ($Instance.Kind -eq 'Client' -and $proc.CloseMainWindow()) {
        Write-Host "Asked client PID $($Instance.ProcessId) to close; waiting up to $GraceSeconds s."
        if ($proc.WaitForExit($GraceSeconds * 1000)) { return $true }
        Write-Host "Client PID $($Instance.ProcessId) did not exit in $GraceSeconds s; forcing it."
    }
    Stop-Process -Id $Instance.ProcessId -Force -ErrorAction SilentlyContinue
    $proc.WaitForExit(10000)
}

<#
.SYNOPSIS
    Ask, then stop the instances that must stop.
.PARAMETER ToStop
    The instances that must stop; may be empty.
.PARAMETER BuildStampUtc
    The build stamp (UTC) or $null.
.OUTPUTS
    [bool]: true if nothing had to stop or all are confirmed gone; false if declined or any instance did not exit.
#>
function Invoke-StopStep {
    param(
        [AllowEmptyCollection()][object[]] $ToStop = @(),
        [AllowNull()] $BuildStampUtc
    )
    if ($ToStop.Count -eq 0) { return $true }
    if (-not (Confirm-StopInstances -Instances $ToStop -BuildStampUtc $BuildStampUtc)) { return $false }
    $allGone = $true
    foreach ($i in $ToStop) {
        if (-not (Stop-Instance -Instance $i)) {
            Write-Host "$($i.Kind) PID $($i.ProcessId) did not exit."
            $allGone = $false
        }
    }
    $allGone
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester -Path scripts/tests -EnableExit"`
Expected: `Failed: 0`, exit 0.

- [ ] **Step 5: Check ASCII and commit**

```bash
grep -n '[^ -~[:space:]]' scripts/DeployTestEnv.psm1 scripts/tests/DeployTestEnv.Tests.ps1 || echo ascii-ok
git add scripts/DeployTestEnv.psm1 scripts/tests/DeployTestEnv.Tests.ps1
git commit -m "feat(scripts): prompt before stopping instances, never stop without y"
```

---

### Task 5: The flow, the script, `-CheckOnly` and the client launch

**Files:**

- Create: `scripts/Deploy-TestEnv.ps1`
- Modify: `scripts/DeployTestEnv.psm1` (append `Start-DevClient`, `Write-InstanceList`, `Invoke-DeployFlow`)
- Modify: `scripts/tests/DeployTestEnv.Tests.ps1` (append)

**Interfaces:**

- Consumes: every module function from Tasks 2-4.
- Produces:
  - `Start-DevClient -RepoRoot <string> [-GraceSeconds 15] [-Command '.\gradlew.bat runClient --console=plain']` -> `[pscustomobject]@{ Started = <bool>; ProcessId = <int>; Log = <string> }`. `Started` is false if the launch process exited within the grace period. `-Command` exists so a test can substitute a command that dies or lives without touching Gradle.
  - `Write-InstanceList -Instances <object[]> -BuildStampUtc <datetime|null> -What <string>` -> prints one line per instance, or `No running <What>.`
  - `Invoke-DeployFlow -RepoRoot <string> -Build <scriptblock> -RunGameTests <scriptblock> -LaunchClient <scriptblock>` -> `[int]` exit code (0-4, Global Constraints). `Build` and `RunGameTests` each return only a Gradle exit code; `LaunchClient` returns a `Start-DevClient` result. The delegates are what lets Pester drive the whole flow.
  - `scripts/Deploy-TestEnv.ps1 [-CheckOnly]`: a thin wrapper that supplies the real delegates and exits with the flow's code.

- [ ] **Step 1: Write the failing tests**

Append to `scripts/tests/DeployTestEnv.Tests.ps1`. Delegates are closed over their results (`GetNewClosure()`) because they run inside the module.

```powershell
Describe 'Start-DevClient' {
    function New-RepoDir([string] $Name) {
        $d = Join-Path $TestDrive $Name
        New-Item -ItemType Directory -Path $d | Out-Null
        $d
    }

    It 'reports a launch that dies within the grace period, and logs a header' {
        $r = Start-DevClient -RepoRoot (New-RepoDir 'died') -GraceSeconds 10 -Command 'exit 1'
        $r.Started | Should Be $false
        (Get-Content -LiteralPath $r.Log -Raw) | Should Match 'Deploy-TestEnv launch'
    }
    It 'reports a launch that is still running after the grace period' {
        $r = Start-DevClient -RepoRoot (New-RepoDir 'alive') -GraceSeconds 1 -Command 'ping -n 6 127.0.0.1'
        try { $r.Started | Should Be $true }
        finally { Stop-Process -Id $r.ProcessId -Force -ErrorAction SilentlyContinue }
    }
}

Describe 'Invoke-DeployFlow' {
    Mock -ModuleName DeployTestEnv Write-Host { }
    Mock -ModuleName DeployTestEnv Get-Content { 'last log line' }
    Mock -ModuleName DeployTestEnv Get-BuildStamp { [datetime]::SpecifyKind([datetime]'2026-10-01 12:00:00', 'Utc') }
    Mock -ModuleName DeployTestEnv Stop-Instance { $true }
    Mock -ModuleName DeployTestEnv Confirm-StopInstances { $true }
    Mock -ModuleName DeployTestEnv Get-Instances { }

    function New-Flow([int] $BuildExit = 0, [int] $TestsExit = 0, [bool] $Started = $true) {
        $calls = New-Object System.Collections.ArrayList
        @{
            Calls        = $calls
            Build        = { [void]$calls.Add('build'); $BuildExit }.GetNewClosure()
            RunGameTests = { [void]$calls.Add('tests'); $TestsExit }.GetNewClosure()
            LaunchClient = { [void]$calls.Add('launch'); [pscustomobject]@{ Started = $Started; ProcessId = 1; Log = 'x' } }.GetNewClosure()
        }
    }
    function Invoke-Flow($Flow) {
        Invoke-DeployFlow -RepoRoot 'C:\dev\spellcraft' -Build $Flow.Build -RunGameTests $Flow.RunGameTests -LaunchClient $Flow.LaunchClient
    }

    It 'runs build, GameTests, launch in order and exits 0' {
        $f = New-Flow
        Invoke-Flow $f | Should Be 0
        ($f.Calls -join ',') | Should Be 'build,tests,launch'
    }
    It 'exits 1 after a failed build and runs nothing else' {
        $f = New-Flow -BuildExit 1
        Invoke-Flow $f | Should Be 1
        ($f.Calls -join ',') | Should Be 'build'
    }
    It 'exits 2 after failed GameTests and does not launch' {
        $f = New-Flow -TestsExit 1
        Invoke-Flow $f | Should Be 2
        ($f.Calls -join ',') | Should Be 'build,tests'
    }
    It 'exits 4 when the launch process died' {
        $f = New-Flow -Started $false
        Invoke-Flow $f | Should Be 4
        ($f.Calls -join ',') | Should Be 'build,tests,launch'
    }

    Context 'a GameTest server is running' {
        Mock -ModuleName DeployTestEnv Get-Instances {
            [pscustomobject]@{ Kind = 'GameTestServer'; ProcessId = 8; StartTimeUtc = [datetime]::SpecifyKind([datetime]'2026-10-01 13:00:00', 'Utc') }
        }

        It 'exits 3 before the GameTests when the user declines' {
            Mock -ModuleName DeployTestEnv Confirm-StopInstances { $false }
            $f = New-Flow
            Invoke-Flow $f | Should Be 3
            ($f.Calls -join ',') | Should Be 'build'
        }
        It 'exits 3 before the GameTests when it will not exit' {
            Mock -ModuleName DeployTestEnv Stop-Instance { $false }
            $f = New-Flow
            Invoke-Flow $f | Should Be 3
            ($f.Calls -join ',') | Should Be 'build'
        }
        It 'stops it and goes on after y' {
            $f = New-Flow
            Invoke-Flow $f | Should Be 0
            ($f.Calls -join ',') | Should Be 'build,tests,launch'
        }
    }

    Context 'a stale client is running' {
        Mock -ModuleName DeployTestEnv Get-Instances {
            [pscustomobject]@{ Kind = 'Client'; ProcessId = 9; StartTimeUtc = [datetime]::SpecifyKind([datetime]'2026-10-01 11:00:00', 'Utc') }
        }

        It 'runs the GameTests beside it, then exits 3 when the user declines' {
            Mock -ModuleName DeployTestEnv Confirm-StopInstances { $false }
            $f = New-Flow
            Invoke-Flow $f | Should Be 3
            ($f.Calls -join ',') | Should Be 'build,tests'
        }
    }

    Context 'a current client is running' {
        Mock -ModuleName DeployTestEnv Get-Instances {
            [pscustomobject]@{ Kind = 'Client'; ProcessId = 9; StartTimeUtc = [datetime]::SpecifyKind([datetime]'2026-10-01 13:00:00', 'Utc') }
        }

        It 'exits 0 without launching another' {
            $f = New-Flow
            Invoke-Flow $f | Should Be 0
            ($f.Calls -join ',') | Should Be 'build,tests'
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester -Path scripts/tests -EnableExit"`
Expected: earlier tests pass; the new ones fail with `The term 'Start-DevClient' is not recognized` (and `Invoke-DeployFlow`).

- [ ] **Step 3: Implement**

Append to `scripts/DeployTestEnv.psm1`:

```powershell
$script:ExitOk = 0
$script:ExitBuildFailed = 1
$script:ExitGameTestsFailed = 2
$script:ExitDeclined = 3
$script:ExitClientDied = 4

<#
.SYNOPSIS
    Start the dev client in a hidden Gradle launcher, logging to build\deploy\runClient.log.
.PARAMETER RepoRoot
    The repo root; the launcher runs there.
.PARAMETER GraceSeconds
    How long the launcher must stay alive to count as started.
.PARAMETER Command
    The command run inside cmd.exe; tests substitute a short-lived one.
.OUTPUTS
    An object with Started (false if the launcher exited within the grace period), ProcessId (of the cmd.exe wrapper, not the game JVM) and Log.
#>
function Start-DevClient {
    param(
        [Parameter(Mandatory)][string] $RepoRoot,
        [int] $GraceSeconds = 15,
        [string] $Command = '.\gradlew.bat runClient --console=plain'
    )
    $logDir = Join-Path $RepoRoot 'build\deploy'
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $log = Join-Path $logDir 'runClient.log'
    Add-Content -LiteralPath $log -Value "===== Deploy-TestEnv launch $(Get-Date -Format s) =====" -Encoding ASCII
    # Start-Process (5.1) cannot send stdout and stderr to one file, nor append; cmd can.
    # The hidden cmd has its own console, so closing this terminal does not kill the client.
    $proc = Start-Process -FilePath 'cmd.exe' `
        -ArgumentList "/d /c $Command >> build\deploy\runClient.log 2>&1" `
        -WorkingDirectory $RepoRoot -WindowStyle Hidden -PassThru
    $died = $proc.WaitForExit($GraceSeconds * 1000)
    [pscustomobject]@{ Started = -not $died; ProcessId = $proc.Id; Log = $log }
}

<#
.SYNOPSIS
    Print one status line per instance, or 'No running <What>.'
.PARAMETER Instances
    Instances from Get-Instances.
.PARAMETER BuildStampUtc
    The build stamp (UTC) or $null.
.PARAMETER What
    Plural noun for the empty message, e.g. 'servers'.
#>
function Write-InstanceList {
    param(
        [AllowEmptyCollection()][object[]] $Instances = @(),
        [AllowNull()] $BuildStampUtc,
        [Parameter(Mandatory)][string] $What
    )
    if ($Instances.Count -eq 0) {
        Write-Host "  No running $What."
        return
    }
    foreach ($i in $Instances) {
        Write-Host ('  ' + (Format-InstanceLine -Instance $i -BuildStampUtc $BuildStampUtc))
    }
}

<#
.SYNOPSIS
    The deploy flow: build checks, server GameTests, then the dev client.
.DESCRIPTION
    Step 1 runs the commit-gate checks. Step 2 always runs the GameTests (stopping any GameTest
    server and any stale dedicated server first, after y). Step 3 stops stale clients (after y)
    and launches a client unless an up-to-date one already runs. Stopping is all-or-nothing: if
    an instance is declined or does not exit, the flow stops, because all runs share run/.
.PARAMETER RepoRoot
    The repo root.
.PARAMETER Build
    Runs the build checks; returns only the Gradle exit code.
.PARAMETER RunGameTests
    Runs the GameTests; returns only the Gradle exit code.
.PARAMETER LaunchClient
    Starts the client; returns a Start-DevClient result.
.OUTPUTS
    [int] exit code: 0 ok, 1 build failed, 2 GameTests failed, 3 stop declined or failed, 4 client died.
#>
function Invoke-DeployFlow {
    param(
        [Parameter(Mandatory)][string] $RepoRoot,
        [Parameter(Mandatory)][scriptblock] $Build,
        [Parameter(Mandatory)][scriptblock] $RunGameTests,
        [Parameter(Mandatory)][scriptblock] $LaunchClient
    )
    # Step 1: automated checks (the commit gate). No timeout: a cold run takes 5-15 min.
    Write-Host '== Step 1: spotlessCheck build'
    $code = & $Build
    if ($code -ne 0) {
        Write-Host "Build failed (gradlew exit $code)."
        return $script:ExitBuildFailed
    }
    $stamp = Get-BuildStamp -RepoRoot $RepoRoot
    Write-Host (Format-BuildStamp -BuildStampUtc $stamp)

    # Step 2: server GameTests. Always run; a running server is not a passing test run.
    Write-Host '== Step 2: server GameTests'
    $servers = @(Get-Instances -RepoRoot $RepoRoot | Where-Object { $_.Kind -ne 'Client' })
    Write-InstanceList -Instances $servers -BuildStampUtc $stamp -What 'servers'
    $toStop = @(Select-InstancesToStop -Instances $servers -BuildStampUtc $stamp -Step Servers)
    if (-not (Invoke-StopStep -ToStop $toStop -BuildStampUtc $stamp)) {
        Write-Host 'Stopping here: an instance was declined or did not exit.'
        return $script:ExitDeclined
    }
    $code = & $RunGameTests
    if ($code -ne 0) {
        Write-Host "GameTests failed (gradlew exit $code). Not launching the client."
        return $script:ExitGameTestsFailed
    }

    # Step 3: dev client.
    Write-Host '== Step 3: dev client'
    $clients = @(Get-Instances -RepoRoot $RepoRoot | Where-Object { $_.Kind -eq 'Client' })
    Write-InstanceList -Instances $clients -BuildStampUtc $stamp -What 'clients'
    $toStop = @(Select-InstancesToStop -Instances $clients -BuildStampUtc $stamp -Step Client)
    if (-not (Invoke-StopStep -ToStop $toStop -BuildStampUtc $stamp)) {
        Write-Host 'Stopping here: an instance was declined or did not exit.'
        return $script:ExitDeclined
    }
    if (Test-HasCurrentClient -Instances @(Get-Instances -RepoRoot $RepoRoot) -BuildStampUtc $stamp) {
        Write-Host 'The running client already has the current build; not launching another.'
        return $script:ExitOk
    }
    $launch = & $LaunchClient
    if (-not $launch.Started) {
        Write-Host "runClient exited within the grace period. Last lines of $($launch.Log):"
        Get-Content -LiteralPath $launch.Log -Tail 40 | ForEach-Object { Write-Host $_ }
        return $script:ExitClientDied
    }
    Write-Host "Client launching (wrapper PID $($launch.ProcessId); run -CheckOnly in a few seconds for the game process). Log: $($launch.Log)"
    $script:ExitOk
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester -Path scripts/tests -EnableExit"`
Expected: `Failed: 0`, exit 0. If an `Invoke-DeployFlow` test fails only because a mock body cannot see something, give the mock body literals (it runs in module scope).

- [ ] **Step 5: Write the script**

Create `scripts/Deploy-TestEnv.ps1`:

```powershell
<#
.SYNOPSIS
    Run the commit-gate checks and the GameTests, then launch the Spellcraft dev client.
.DESCRIPTION
    Spec: docs/superpowers/specs/2026-10-01-deploy-test-env-design.md
    The flow lives in Invoke-DeployFlow (scripts/DeployTestEnv.psm1); this script supplies the real
    Gradle steps. Running instances that must stop are stopped only after a y; non-interactive runs answer N.
    Exit codes: 0 ok (client launched or already current); 1 not in a repo or the build failed;
    2 GameTests failed; 3 an instance had to stop and the user declined, could not be asked, or it did
    not exit; 4 the client launch process died within 15 s.
.PARAMETER CheckOnly
    Print the build stamp and the running instances with their verdicts, then exit 0.
    Builds, stops and launches nothing.
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\Deploy-TestEnv.ps1
#>
[CmdletBinding()]
param([switch] $CheckOnly)

Set-StrictMode -Version 2.0
# No global $ErrorActionPreference = 'Stop': when this script's output is redirected, Windows
# PowerShell 5.1 turns a native command's stderr (Gradle warnings) into error records, and Stop
# would abort on them. Gradle failures are caught by $LASTEXITCODE instead.
Import-Module (Join-Path $PSScriptRoot 'DeployTestEnv.psm1') -Force -ErrorAction Stop

# Step 0: locate the repo.
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..') -ErrorAction Stop).Path
if (-not (Test-Path -LiteralPath (Join-Path $repoRoot 'gradlew.bat') -PathType Leaf)) {
    Write-Host "gradlew.bat not found in $repoRoot; is this script inside the spellcraft repo?"
    exit 1
}
Set-Location -LiteralPath $repoRoot

if ($CheckOnly) {
    $stamp = Get-BuildStamp -RepoRoot $repoRoot
    Write-Host (Format-BuildStamp -BuildStampUtc $stamp)
    Write-InstanceList -Instances @(Get-Instances -RepoRoot $repoRoot) -BuildStampUtc $stamp -What 'instances'
    exit 0
}

# Out-Host keeps Gradle's output on screen and out of the returned value, which is only the exit code.
$exitCode = Invoke-DeployFlow -RepoRoot $repoRoot `
    -Build { & .\gradlew.bat spotlessCheck build | Out-Host; $LASTEXITCODE } `
    -RunGameTests { & .\gradlew.bat runGameTestServer | Out-Host; $LASTEXITCODE } `
    -LaunchClient ({ Start-DevClient -RepoRoot $repoRoot }.GetNewClosure())
exit $exitCode
```

- [ ] **Step 6: Verify `-CheckOnly` and the not-a-repo path**

Make sure no instance is running (Task 1 step 1 snippet), then:

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Deploy-TestEnv.ps1 -CheckOnly < /dev/null; echo "exit=$?"
```

Expected: `Build stamp: <date time>`, `No running instances.`, `exit=0`.

Not-a-repo path: copy the script and module somewhere without a `gradlew.bat` one level up, and run the copy:

```bash
SCR="$(mktemp -d)" && mkdir -p "$SCR/scripts" && cp scripts/Deploy-TestEnv.ps1 scripts/DeployTestEnv.psm1 "$SCR/scripts/"
powershell -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$SCR/scripts/Deploy-TestEnv.ps1")" -CheckOnly < /dev/null; echo "exit=$?"
rm -rf "$SCR"
```

Expected: `gradlew.bat not found in ...`, `exit=1`.

- [ ] **Step 7: Run the unit tests and check ASCII**

```bash
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester -Path scripts/tests -EnableExit"
grep -n '[^ -~[:space:]]' scripts/*.ps1 scripts/*.psm1 scripts/tests/*.ps1 || echo ascii-ok
```

Expected: `Failed: 0`; `ascii-ok`.

- [ ] **Step 8: Commit**

```bash
git add scripts/Deploy-TestEnv.ps1 scripts/DeployTestEnv.psm1 scripts/tests/DeployTestEnv.Tests.ps1
git commit -m "feat(scripts): add Deploy-TestEnv script with -CheckOnly"
```

---

### Task 6: End-to-end verification and docs

Runs the spec's verification plan on the real machine, then documents the command. Every run below is non-interactive (`< /dev/null`), so a prompt is answered `N`. This also verifies `Test-CanPrompt`. The steps that build use a 600 s Bash timeout. Run them with `run_in_background: true` if the build is cold. The step 4 polling block needs an explicit 200000 ms timeout (the default is 120 s; its own deadline is 3 min).

**Files:**

- Modify: `CLAUDE.md` (Commands table)
- Modify: `docs/playtest-checklist.md` (launch line)

**Interfaces:**

- Consumes: `scripts/Deploy-TestEnv.ps1` (Task 5) and, directly in step 7, `Get-Instances` (Task 3) and `Stop-Instance` (Task 4).

- [ ] **Step 1: Nothing running (spec item 1)**

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Deploy-TestEnv.ps1 -CheckOnly < /dev/null; echo "exit=$?"
```

Expected: `No running instances.`, `exit=0`.

- [ ] **Step 2: Step 1 failure exits 1 (spec item 2)**

Break formatting without committing: in `src/main/java/io/github/willrieske/spellcraft/gametest/UpsideDownGameTests.java`, method `effectIsANeutralMarker`, indent `helper.succeed();` by 12 spaces instead of 8. Then:

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Deploy-TestEnv.ps1 < /dev/null; echo "exit=$?"
git checkout -- src/main/java/io/github/willrieske/spellcraft/gametest/UpsideDownGameTests.java
```

Expected: `spotlessCheck` fails, `Build failed`, `exit=1`; no step 2 header printed.

- [ ] **Step 3: GameTest failure exits 2 and no client launches (spec item 2)**

In `effectIsANeutralMarker`, replace `helper.succeed();` with `helper.fail("deliberate deploy-script check");` (correct indentation, so spotless passes). Then:

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Deploy-TestEnv.ps1 < /dev/null; echo "exit=$?"
git checkout -- src/main/java/io/github/willrieske/spellcraft/gametest/UpsideDownGameTests.java
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Deploy-TestEnv.ps1 -CheckOnly < /dev/null
```

Expected: build passes, `runGameTestServer` reports the failing test, `GameTests failed ... Not launching the client.`, `exit=2`. The `-CheckOnly` afterwards shows `No running instances.`

- [ ] **Step 4: Full run launches the client (spec item 3)**

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Deploy-TestEnv.ps1 < /dev/null; echo "exit=$?"
```

Expected: build and GameTests pass; `No running clients.`; `Client launching (wrapper PID N; run -CheckOnly in a few seconds for the game process). Log: ...\build\deploy\runClient.log`; `exit=0`. Then poll until the game JVM appears (up to 3 min):

```powershell
$deadline = (Get-Date).AddMinutes(3)
do {
    $out = powershell -NoProfile -ExecutionPolicy Bypass -File scripts\Deploy-TestEnv.ps1 -CheckOnly
    if ($out -match '^\s*Client') { break }
    Start-Sleep -Seconds 5
} while ((Get-Date) -lt $deadline)
$out
```

Expected: a `Client ... up to date` line. Also confirm the log has Gradle output: `Get-Content build\deploy\runClient.log -Tail 5`.

- [ ] **Step 5: Touching a class makes the client stale (spec item 4)**

```powershell
$f = Get-ChildItem build\classes\java\main -Recurse -File | Select-Object -First 1
$f.LastWriteTimeUtc = (Get-Date).ToUniversalTime()
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\Deploy-TestEnv.ps1 -CheckOnly
```

Expected: the same client PID, now `stale`.

- [ ] **Step 6: Re-run non-interactively: GameTests run beside the client, then exit 3 (spec item 5)**

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Deploy-TestEnv.ps1 < /dev/null; echo "exit=$?"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Deploy-TestEnv.ps1 -CheckOnly < /dev/null
```

Expected: the build passes. Whether or not Gradle rewrites the touched class, its time stays later than the client's start, so the client stays stale; GameTests pass with the client running; step 3 lists the client as stale, prints `Cannot prompt (non-interactive run); treating the answer as N.`, `exit=3`. The `-CheckOnly` afterwards still lists the client (same PID), proving the decline stopped nothing.

- [ ] **Step 7: Stop the client with `Stop-Instance`**

This is the code the `y` answer runs:

```powershell
Import-Module .\scripts\DeployTestEnv.psm1 -Force
$c = @(Get-Instances -RepoRoot (Get-Location).Path | Where-Object { $_.Kind -eq 'Client' })
$sw = [Diagnostics.Stopwatch]::StartNew()
foreach ($i in $c) { Stop-Instance -Instance $i }
"stopped in $([int]$sw.Elapsed.TotalSeconds) s"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\Deploy-TestEnv.ps1 -CheckOnly
```

Expected: `Asked client PID N to close`, no "forcing it" line, then `No running instances.`

- [ ] **Step 8: Document the command**

In `CLAUDE.md`, replace the `runGameTestServer` row (its "until Task 7" note is stale: GameTests are registered) and add two rows after the `runClient` row:

```markdown
| `./gradlew runGameTestServer` | GameTests (server rules); non-zero exit on failure |
```

```markdown
| `powershell -ExecutionPolicy Bypass -File scripts\Deploy-TestEnv.ps1` | Gate checks + GameTests, then launch the dev client (or keep a current one); asks before stopping a stale one. `-CheckOnly`: status only. Exit codes in the script header |
| `powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester -Path scripts/tests -EnableExit"` | Deploy-script unit tests (Pester 3.4; by hand, not in CI) |
```

In `docs/playtest-checklist.md`, change the first sentence (``Run `./gradlew runClient`.``) to:

```markdown
Run `powershell -ExecutionPolicy Bypass -File scripts\Deploy-TestEnv.ps1` (checks, GameTests, then the client), or `./gradlew runClient`.
```

Keep the rest of that paragraph's line wrapping as it is.

- [ ] **Step 9: Commit**

```bash
git add CLAUDE.md docs/playtest-checklist.md
git commit -m "docs: document the Deploy-TestEnv script"
```

- [ ] **Step 10: Leave Will a running, stale client**

Step 7 closed the client, so rebuild the state the handover needs. Run the full script as in step 4 (`< /dev/null`; expect `exit=0`) and poll for the client as in step 4. Then touch a class as in step 5 and confirm with `-CheckOnly` that the client is `stale`. Leave it running.

- [ ] **Step 11: Hand over to Will (spec item 6)**

Report, and do not claim it done: the interactive prompt needs a real console. Ask Will to run, in a PowerShell window (the client from step 10 is open and stale):

1. `powershell -ExecutionPolicy Bypass -File scripts\Deploy-TestEnv.ps1`, answer `N`: expect exit 3 (`echo $LASTEXITCODE`), client still open.
2. Same, answer `y`: expect the game window to close on its own (no force), then a new client to launch.
3. `-CheckOnly`: the new client is `up to date`.

Also report that exit 4 (client launch died within 15 s) is covered by Pester tests (`Start-DevClient -Command 'exit 1'` and the mocked flow), not by a real crashed Minecraft launch.

## Divergence index

<!-- generated by plan_map.py --write-index; do not edit -->

| Task | SHA | Divergence |
|---|---|---|
| 1 | `0f56792` | the `@` argument-file tokens in the real process command lines use DOUBLED backslashes (`@C:\\dev\\...\\build\\moddev\\clientRunVmArgs.txt`), so the plan's `*\build\moddev\clientRun*` match would not match; the java path is the only quoted token; reason: observed on the live run, recorded in the spec's Verified launch facts (matching in Tasks 2-3 must normalize `\\` to `\` or match the doubled form). |
| 2 | `843ea82` | `Get-InstanceKind` collapses doubled backslashes and matches `@<root>\build\moddev\<run>(VmArgs\|ProgramArgs).txt` (anchored on the root), and the fixtures use the doubled-backslash `@` tokens recorded in the spec; reason: Task 1's live-run finding (see Task 1's note). |
