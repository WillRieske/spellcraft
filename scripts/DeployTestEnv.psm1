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
# cannot tell kinds apart. The '\' before the run name keeps 'serverRun' from matching inside
# 'gameTestServerRun'; the '<repo>' anchor ignores other checkouts and nested worktrees.
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
    The repo root; only runs whose '@' argument files sit under '<RepoRoot>\build\moddev\' match.
.OUTPUTS
    'Client', 'GameTestServer', 'Server' or $null.
#>
function Get-InstanceKind {
    param(
        [AllowNull()][AllowEmptyString()][string] $CommandLine,
        [Parameter(Mandatory)][string] $RepoRoot
    )
    if ([string]::IsNullOrEmpty($CommandLine)) { return $null }
    # Collapse doubled backslashes to single ones. Built from [char]92 so the source never holds a
    # literal doubled backslash that an editor or shell could silently collapse.
    $bs = [string][char]92
    $line = $CommandLine.Replace($bs + $bs, $bs)
    $root = $RepoRoot.TrimEnd([char]92).Replace($bs + $bs, $bs)
    $pattern = '@' + [regex]::Escape($root + $bs + 'build' + $bs + 'moddev' + $bs) +
        '(clientRun|gameTestServerRun|serverRun)(VmArgs|ProgramArgs)\.txt'
    $m = [regex]::Match($line, $pattern, 'IgnoreCase')
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

<#
.SYNOPSIS
    List this repo's running dev instances (client, GameTest server, dedicated server).
.PARAMETER RepoRoot
    The repo root.
.OUTPUTS
    Zero or more objects with Kind, ProcessId and StartTimeUtc. Wrap the call in @(...).
    A dev run whose start time CIM does not report is skipped, with a line saying so.
#>
function Get-Instances {
    param([Parameter(Mandatory)][string] $RepoRoot)
    foreach ($p in @(Get-CimInstance -ClassName Win32_Process -Filter "Name='java.exe'")) {
        $kind = Get-InstanceKind -CommandLine $p.CommandLine -RepoRoot $RepoRoot
        if ($null -eq $kind) { continue }
        # Without a start time neither the verdict nor Stop-Instance's PID-reuse guard can work.
        if ($null -eq $p.CreationDate) {
            Write-Host "$kind PID $($p.ProcessId) has no start time (CIM hid it); skipping it. Stop it by hand if it gets in the way."
            continue
        }
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
    # PID reuse guard: the PID may now belong to an unrelated process. Never stop one whose start
    # time differs from the instance's by more than 5 s.
    try {
        $drift = [math]::Abs(($proc.StartTime.ToUniversalTime() - $Instance.StartTimeUtc).TotalSeconds)
        if ($drift -gt 5) {
            Write-Host "PID $($Instance.ProcessId) now belongs to a different process (start time differs); treating $($Instance.Kind) as gone."
            return $true
        }
    } catch {
        # StartTime unreadable (access denied or exited meanwhile): fall through to the normal path.
        Write-Verbose "Could not read the start time of PID $($Instance.ProcessId): $_"
    }
    $closeRequested = $false
    if ($Instance.Kind -eq 'Client') {
        try {
            $closeRequested = $proc.CloseMainWindow()
        } catch {
            # PowerShell wraps exceptions from .NET method calls, so look at the inner one too.
            # InvalidOperationException means the process exited meanwhile: it is gone.
            for ($ex = $_.Exception; $null -ne $ex; $ex = $ex.InnerException) {
                if ($ex -is [System.InvalidOperationException]) { return $true }
            }
            throw
        }
    }
    if ($closeRequested) {
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
