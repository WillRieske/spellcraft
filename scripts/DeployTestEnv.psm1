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
