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
