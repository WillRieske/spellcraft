# Helpers for Deploy-TestEnv.ps1.
# Spec: docs/superpowers/specs/2026-10-01-deploy-test-env-design.md
# Tests: scripts/tests/DeployTestEnv.Tests.ps1 (Pester 3.4).
# Windows PowerShell 5.1; ASCII only (5.1 reads BOM-less files as ANSI).

Set-StrictMode -Version 2.0

# The dev runs read classes and resources from these directories, not from the jar.
$script:BuildOutputDirs = @('build\classes\java\main', 'build\resources\main')

# ModDevGradle passes '@<repo>\build\moddev\<run>RunVmArgs.txt' (and ProgramArgs) to java.exe, with
# every backslash DOUBLED in these tokens (spec: "Verified launch facts"). Matching is on the '@'
# token only: the -cp classpath contains '<repo>\build\moddev\artifacts\' for every instance and
# cannot tell kinds apart. The separator before the run name keeps 'serverRun' from matching inside
# 'gameTestServerRun'; the '<repo>' anchor ignores other checkouts and nested worktrees.
$script:RunKinds = @{
    'clientrun'         = 'Client'
    'gametestserverrun' = 'GameTestServer'
    'serverrun'         = 'Server'
}

# One '\' or '/', and a run of them. Built from [char]92 so the source never holds a literal
# doubled backslash that an editor or shell could silently collapse.
$script:SepChar = '[/' + [regex]::Escape([string][char]92) + ']'
$script:SepPattern = $script:SepChar + '+'

<#
.SYNOPSIS
    A regex for a path in which any run of '\' or '/' matches any other run.
.DESCRIPTION
    So doubled backslashes, forward slashes and a trailing separator all compare equal. A UNC
    path (two leading separators) must still start with two or more.
.PARAMETER Path
    The path.
.OUTPUTS
    [string] regex, without anchors.
#>
function ConvertTo-PathPattern {
    param([Parameter(Mandatory)][string] $Path)
    $parts = @($Path -split $script:SepPattern | Where-Object { $_ -ne '' } | ForEach-Object { [regex]::Escape($_) })
    $prefix = if ($Path -match ('^' + $script:SepChar + '{2}')) { $script:SepChar + '{2,}' } else { '' }
    $prefix + ($parts -join $script:SepPattern)
}

<#
.SYNOPSIS
    Classify a java.exe command line as a dev run of this repo.
.PARAMETER CommandLine
    The process command line; null or empty (CIM hides some) yields $null.
.PARAMETER RepoRoot
    The repo root, or several spellings of it (see Get-RepoRootForms); only runs whose '@'
    argument files sit under '<RepoRoot>\build\moddev\' match.
.OUTPUTS
    'Client', 'GameTestServer', 'Server' or $null.
#>
function Get-InstanceKind {
    param(
        [AllowNull()][AllowEmptyString()][string] $CommandLine,
        [Parameter(Mandatory)][string[]] $RepoRoot
    )
    if ([string]::IsNullOrEmpty($CommandLine)) { return $null }
    foreach ($root in $RepoRoot) {
        $pattern = '@' + (ConvertTo-PathPattern -Path $root) + $script:SepPattern + 'build' +
            $script:SepPattern + 'moddev' + $script:SepPattern +
            '(clientRun|gameTestServerRun|serverRun)(VmArgs|ProgramArgs)\.txt'
        $m = [regex]::Match($CommandLine, $pattern, 'IgnoreCase')
        if ($m.Success) { return $script:RunKinds[$m.Groups[1].Value.ToLowerInvariant()] }
    }
    $null
}

<#
.SYNOPSIS
    The spellings of the repo root a dev run's command line may use.
.DESCRIPTION
    Gradle may write the root in a different form from the one this script was started with:
    long instead of 8.3 short names, or the real location behind a junction or subst drive.
    Returns the given path, its long-name form and its final (link-resolved) form, without
    duplicates. A form Windows cannot produce is left out.
.PARAMETER RepoRoot
    The repo root.
.OUTPUTS
    [string[]], the given path first.
#>
function Get-RepoRootForms {
    param([Parameter(Mandatory)][string] $RepoRoot)
    if (-not ('DeployTestEnv.NativePath' -as [type])) {
        Add-Type -TypeDefinition $script:NativePathSource
    }
    $forms = New-Object System.Collections.Generic.List[string]
    $forms.Add($RepoRoot)
    $final = [DeployTestEnv.NativePath]::FinalPath($RepoRoot)
    if ($final) {
        # Drop the long-path prefix: '<sep><sep>?<sep>UNC<sep>server' becomes '<sep><sep>server',
        # '<sep><sep>?<sep>C:' becomes 'C:'.
        $bs = [string][char]92
        $longPrefix = $bs + $bs + '?' + $bs
        if ($final.StartsWith($longPrefix + 'UNC' + $bs)) { $final = $bs + $bs + $final.Substring(8) }
        elseif ($final.StartsWith($longPrefix)) { $final = $final.Substring(4) }
    }
    foreach ($form in @([DeployTestEnv.NativePath]::LongPath($RepoRoot), $final)) {
        if ($form -and -not ($forms -contains $form)) { $forms.Add($form) }
    }
    $forms.ToArray()
}

$script:NativePathSource = @'
using System;
using System.Runtime.InteropServices;
using System.Text;
using Microsoft.Win32.SafeHandles;

namespace DeployTestEnv {
    public static class NativePath {
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern uint GetLongPathNameW(string shortPath, StringBuilder longPath, uint size);

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern SafeFileHandle CreateFileW(string name, uint access, uint share, IntPtr security,
            uint disposition, uint flags, IntPtr template);

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        static extern uint GetFinalPathNameByHandleW(SafeFileHandle file, StringBuilder path, uint size, uint flags);

        // Null when Windows cannot expand it.
        public static string LongPath(string path) {
            var sb = new StringBuilder(1024);
            uint n = GetLongPathNameW(path, sb, (uint)sb.Capacity);
            return (n == 0 || n >= sb.Capacity) ? null : sb.ToString();
        }

        // The path with junctions, symlinks and subst drives resolved, still carrying the
        // long-path prefix (see Get-RepoRootForms); null when Windows cannot.
        public static string FinalPath(string path) {
            const uint OPEN_EXISTING = 3, FILE_FLAG_BACKUP_SEMANTICS = 0x02000000, SHARE_ALL = 7;
            using (var h = CreateFileW(path, 0, SHARE_ALL, IntPtr.Zero, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS, IntPtr.Zero)) {
                if (h.IsInvalid) return null;
                var sb = new StringBuilder(1024);
                uint n = GetFinalPathNameByHandleW(h, sb, (uint)sb.Capacity, 0);
                if (n == 0 || n >= sb.Capacity) return null;
                return sb.ToString();
            }
        }
    }
}
'@

<#
.SYNOPSIS
    Time of the newest change to the compiled classes or processed resources.
.DESCRIPTION
    The newest write time of any file or directory in the output directories, the directories
    included. Directories count because deleting a file (an incremental compile removing a
    class) changes only its directory's write time.
.PARAMETER RepoRoot
    The repo root.
.OUTPUTS
    [datetime] (UTC), or $null when nothing has been built (no files).
#>
function Get-BuildStamp {
    param([Parameter(Mandatory)][string] $RepoRoot)
    $dirs = @($script:BuildOutputDirs |
        ForEach-Object { Join-Path $RepoRoot $_ } |
        Where-Object { Test-Path -LiteralPath $_ -PathType Container })
    if ($dirs.Count -eq 0) { return $null }
    $items = @(Get-Item -LiteralPath $dirs) + @(Get-ChildItem -LiteralPath $dirs -Recurse)
    if (-not ($items | Where-Object { -not $_.PSIsContainer })) { return $null }
    ($items | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1).LastWriteTimeUtc
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

<#
.SYNOPSIS
    List this repo's running dev instances (client, GameTest server, dedicated server).
.PARAMETER RepoRoot
    The repo root.
.OUTPUTS
    Zero or more objects with Kind, ProcessId and StartTimeUtc. Wrap the call in @(...).
    A dev run whose start time cannot be read is skipped, with a line saying so.
#>
function Get-Instances {
    param([Parameter(Mandatory)][string] $RepoRoot)
    $roots = Get-RepoRootForms -RepoRoot $RepoRoot
    foreach ($p in @(Get-CimInstance -ClassName Win32_Process -Filter "Name='java.exe'")) {
        $kind = Get-InstanceKind -CommandLine $p.CommandLine -RepoRoot $roots
        if ($null -eq $kind) { continue }
        # The start time comes from the process, not CIM's CreationDate: Process.StartTime keeps
        # the UTC instant, so a start in the repeated hour at the end of daylight saving converts
        # back correctly, and Stop-Instance's PID-reuse guard compares the same clock.
        $proc = Get-Process -Id $p.ProcessId -ErrorAction SilentlyContinue
        if ($null -eq $proc) { continue }
        try {
            $startUtc = $proc.StartTime.ToUniversalTime()
        } catch {
            if (Test-IsExitedError -ErrorRecord $_) { continue }
            # Without a start time neither the verdict nor Stop-Instance's PID-reuse guard can work.
            Write-Host "$kind PID $($p.ProcessId) has no readable start time ($($_.Exception.Message)); skipping it. Stop it by hand if it gets in the way."
            continue
        }
        [pscustomobject]@{
            Kind         = $kind
            ProcessId    = [int]$p.ProcessId
            StartTimeUtc = $startUtc
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

<#
.SYNOPSIS
    Whether a question can be asked, given the host state.
.PARAMETER UserInteractive
    [Environment]::UserInteractive.
.PARAMETER InputRedirected
    [Console]::IsInputRedirected.
.PARAMETER CommandLineArgs
    [Environment]::GetCommandLineArgs().
.OUTPUTS
    [bool]: false for a non-interactive host, redirected stdin, or -NonInteractive in any
    spelling powershell.exe accepts ('-noni' to '-NonInteractive', also with '/').
#>
function Test-CanPromptWith {
    param(
        [Parameter(Mandatory)][bool] $UserInteractive,
        [Parameter(Mandatory)][bool] $InputRedirected,
        [AllowEmptyCollection()][string[]] $CommandLineArgs = @()
    )
    if (-not $UserInteractive) { return $false }
    if ($InputRedirected) { return $false }
    $nonInteractive = @($CommandLineArgs | Where-Object { $_ -match '^[-/]noni' })
    $nonInteractive.Count -eq 0
}

<#
.SYNOPSIS
    Whether the script can ask the user a question.
.OUTPUTS
    [bool]: see Test-CanPromptWith.
#>
function Test-CanPrompt {
    Test-CanPromptWith -UserInteractive ([Environment]::UserInteractive) `
        -InputRedirected ([Console]::IsInputRedirected) `
        -CommandLineArgs ([Environment]::GetCommandLineArgs())
}

<#
.SYNOPSIS
    Whether an error says the process has already exited.
.DESCRIPTION
    PowerShell wraps exceptions from .NET method and property calls, so the inner ones are
    checked too. Process members throw InvalidOperationException once the process has exited.
.PARAMETER ErrorRecord
    The caught error ($_).
.OUTPUTS
    [bool]
#>
function Test-IsExitedError {
    param([Parameter(Mandatory)] $ErrorRecord)
    for ($ex = $ErrorRecord.Exception; $null -ne $ex; $ex = $ex.InnerException) {
        if ($ex -is [System.InvalidOperationException]) { return $true }
    }
    $false
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
    try {
        $answer = Read-Host 'Stop them? [y/N]'
    } catch {
        # Backstop for a host Test-CanPrompt did not recognise as non-interactive.
        Write-Host "Cannot prompt ($($_.Exception.Message)); treating the answer as N."
        return $false
    }
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
    [bool]: true once the process is confirmed gone (or the PID is now another process's), false
    if it is still alive after the forced stop or its identity cannot be checked.
#>
function Stop-Instance {
    param(
        [Parameter(Mandatory)] $Instance,
        [int] $GraceSeconds = 20
    )
    $proc = Get-Process -Id $Instance.ProcessId -ErrorAction SilentlyContinue
    if ($null -eq $proc) { return $true }
    try {
        # Pin the process: while this handle is open Windows cannot hand the PID to a new process,
        # so the identity check below and the kill act on the same process.
        try {
            [void]$proc.Handle
            $startUtc = $proc.StartTime.ToUniversalTime()
        } catch {
            if (Test-IsExitedError -ErrorRecord $_) { return $true }
            if ($null -eq (Get-Process -Id $Instance.ProcessId -ErrorAction SilentlyContinue)) { return $true }
            Write-Host "Cannot check that PID $($Instance.ProcessId) is still that $($Instance.Kind) ($($_.Exception.Message)); not stopping it."
            return $false
        }
        # PID reuse guard: the PID may now belong to an unrelated process. Never stop one whose
        # start time differs from the instance's by more than 5 s.
        $drift = [math]::Abs(($startUtc - $Instance.StartTimeUtc).TotalSeconds)
        if ($drift -gt 5) {
            Write-Host "PID $($Instance.ProcessId) now belongs to a different process (start time differs); treating $($Instance.Kind) as gone."
            return $true
        }
        $closeRequested = $false
        if ($Instance.Kind -eq 'Client') {
            try {
                $closeRequested = $proc.CloseMainWindow()
            } catch {
                if (Test-IsExitedError -ErrorRecord $_) { return $true }
                throw
            }
        }
        if ($closeRequested) {
            Write-Host "Asked client PID $($Instance.ProcessId) to close; waiting up to $GraceSeconds s."
            if ($proc.WaitForExit($GraceSeconds * 1000)) { return $true }
            Write-Host "Client PID $($Instance.ProcessId) did not exit in $GraceSeconds s; forcing it."
        }
        try {
            # Through the pinned handle, not Stop-Process -Id, which would look the PID up again.
            $proc.Kill()
        } catch {
            if (Test-IsExitedError -ErrorRecord $_) { return $true }
            throw
        }
        $proc.WaitForExit(10000)
    } finally {
        if ($proc -is [System.IDisposable]) { $proc.Dispose() }
    }
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
        try {
            $gone = Stop-Instance -Instance $i
        } catch {
            # E.g. access denied. Report it as a failed stop (exit 3), not a crash (exit 1).
            Write-Host "Could not stop $($i.Kind) PID $($i.ProcessId): $($_.Exception.Message)"
            $gone = $false
        }
        if (-not $gone) {
            Write-Host "$($i.Kind) PID $($i.ProcessId) did not exit."
            $allGone = $false
        }
    }
    $allGone
}

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
