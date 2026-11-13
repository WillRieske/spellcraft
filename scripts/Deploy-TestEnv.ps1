<#
.SYNOPSIS
    Run the commit-gate checks and the GameTests, then launch the Spellcraft dev client.
.DESCRIPTION
    Spec: docs/superpowers/specs/2026-10-01-deploy-test-env-design.md
    The flow lives in Invoke-DeployFlow (scripts/DeployTestEnv.psm1); this script supplies the real
    Gradle steps. Running instances that must stop are stopped only after a y; non-interactive runs answer N.
    Exit codes: 0 ok (client launched or already current); 1 not in a repo, the build failed, or the
    verified-run record could not be cleared; 2 GameTests failed; 3 an instance had to stop and the
    user declined, could not be asked, or it did not exit or could not be stopped; 4 the client launch
    process died within 15 s.
    The build and the GameTests are skipped when the sources (src/, the Gradle files and the Evoking
    functional spec) and the compiled classes are identical to the last run whose checks passed; the
    record is build\deploy\verified.txt.
.PARAMETER CheckOnly
    Print the build stamp, whether the sources match the last verified run, and the running
    instances with their verdicts, then exit 0. Builds, stops and launches nothing.
.PARAMETER Force
    Run the build and the GameTests even when the sources match the last verified run.
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\Deploy-TestEnv.ps1
#>
[CmdletBinding()]
param([switch] $CheckOnly, [switch] $Force)

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

# Taken before any build; the flow takes it again after the GameTests and records the run only if
# the two are equal, so an edit made while the checks run is never recorded as verified.
$fingerprint = Get-InputFingerprint -RepoRoot $repoRoot
$verifiedPath = Join-Path $repoRoot 'build\deploy\verified.txt'

if ($CheckOnly) {
    $stamp = Get-BuildStamp -RepoRoot $repoRoot
    Write-Host (Format-BuildStamp -BuildStampUtc $stamp)
    Write-Host (Format-VerifiedLine -State (Get-VerifiedState -Fingerprint $fingerprint -BuildStampUtc $stamp -VerifiedPath $verifiedPath))
    Write-InstanceList -Instances @(Get-Instances -RepoRoot $repoRoot) -BuildStampUtc $stamp -What 'instances'
    exit 0
}

# Out-Host keeps Gradle's output on screen and out of the returned value, which is only the exit code.
$exitCode = Invoke-DeployFlow -RepoRoot $repoRoot `
    -Build { & .\gradlew.bat spotlessCheck build | Out-Host; $LASTEXITCODE } `
    -RunGameTests { & .\gradlew.bat runGameTestServer | Out-Host; $LASTEXITCODE } `
    -LaunchClient ({ Start-DevClient -RepoRoot $repoRoot }.GetNewClosure()) `
    -Fingerprint $fingerprint -VerifiedPath $verifiedPath -Force:$Force
exit $exitCode
