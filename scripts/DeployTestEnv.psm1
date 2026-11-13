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

# What a verified run depends on: the files Gradle compiles, tests and packages, the build setup, and
# the one file outside src/ that a test reads (FunctionalSpecReferencesTest fails the build on a
# functional-spec change), and .gitattributes (Spotless, run by spotlessCheck, takes its line endings
# from it by default). Other docs, the scripts and build output are deliberately not here: changing
# them cannot change the build or the tests. Paths use '/', as the Java tests write them: a Pester test
# checks that every path a Java test reads is covered by this list.
$script:FingerprintInputs = @(
    'src', 'build.gradle', 'settings.gradle', 'gradle.properties', 'gradle', 'gradlew', 'gradlew.bat',
    '.gitattributes', 'docs/superpowers/specs/evoking/evoking-functional-spec.md')

<#
.SYNOPSIS
    The files the build and the GameTests depend on, as the disk has them now.
.DESCRIPTION
    The one place that decides which files are inputs, shared by Get-InputFingerprint and
    Get-InputWriteSignature so the two cannot disagree. Files are listed from the disk, not by git:
    Gradle packages a file under src/ whether or not git ignores it, so every file counts. Throws when
    a file cannot be listed; callers catch it. A link (junction or symlink) at an input or anywhere
    under an input directory makes it print "Could not verify the sources: <path> is a link" and give
    up: Windows PowerShell 5.1 does not follow links when listing, Gradle does, so the files behind
    the link would be in no hash.
.PARAMETER RepoRoot
    The repo root.
.OUTPUTS
    $null when no input exists, a link is found or a file lies outside the root. Otherwise an object with Root (the
    resolved root, no trailing separator) and Files (System.IO.FileInfo, sorted by relative name
    ordinally) and Names (the relative names with '/', in the same order).
#>
function Get-InputFiles {
    param([Parameter(Mandatory)][string] $RepoRoot)
    $root = (Resolve-Path -LiteralPath $RepoRoot -ErrorAction Stop).ProviderPath.TrimEnd('\')
    $prefix = $root + '\'
    $link = [System.IO.FileAttributes]::ReparsePoint
    $files = New-Object System.Collections.Generic.List[System.IO.FileInfo]
    foreach ($entry in $script:FingerprintInputs) {
        $path = Join-Path $root $entry
        if (-not (Test-Path -LiteralPath $path)) { continue }
        # Windows PowerShell 5.1 does not descend into a junction or symlink, but Gradle does, so
        # files behind a link would be in neither hash. A link anywhere under the inputs: give up.
        $item = Get-Item -LiteralPath $path -Force -ErrorAction Stop
        if ($item.Attributes -band $link) {
            Write-Host "Could not verify the sources: $entry is a link (junction or symlink)."
            return $null
        }
        if (-not $item.PSIsContainer) {
            $files.Add($item)
            continue
        }
        foreach ($child in @(Get-ChildItem -LiteralPath $path -Recurse -Force -ErrorAction Stop)) {
            if ($child.Attributes -band $link) {
                $shown = if ($child.FullName.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
                    $child.FullName.Substring($prefix.Length).Replace('\', '/')
                } else { $child.FullName }
                Write-Host "Could not verify the sources: $shown is a link (junction or symlink)."
                return $null
            }
            if (-not $child.PSIsContainer) { $files.Add($child) }
        }
    }
    if ($files.Count -eq 0) { return $null }
    $names = New-Object System.Collections.Generic.List[string]
    foreach ($file in $files) {
        # A path that is not under the root cannot be named relative to it; give up, do not guess.
        if (-not $file.FullName.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { return $null }
        $names.Add($file.FullName.Substring($prefix.Length).Replace('\', '/'))
    }
    $nameArray = $names.ToArray()
    $fileArray = $files.ToArray()
    [Array]::Sort($nameArray, $fileArray, [StringComparer]::Ordinal)
    [pscustomobject]@{ Root = $root; Files = $fileArray; Names = $nameArray }
}

<#
.SYNOPSIS
    A hash of everything the build and the GameTests depend on, as the working tree has it now.
.DESCRIPTION
    Hashes the contents of every file on disk under the build inputs, with its path, so an edit, an
    addition, a deletion and a move each change the result, committed or not (the files come from
    Get-InputFiles).
.PARAMETER RepoRoot
    The repo root.
.OUTPUTS
    [string] (hex), or $null when the root or every input is missing, a file cannot be listed or
    read, or a link (junction or symlink) lies under the inputs: callers then treat the sources as
    unverifiable. A read failure or a link prints a line with the cause.
#>
function Get-InputFingerprint {
    param([Parameter(Mandatory)][string] $RepoRoot)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $inputs = Get-InputFiles -RepoRoot $RepoRoot
        if ($null -eq $inputs) { return $null }
        $text = New-Object System.Text.StringBuilder
        for ($i = 0; $i -lt $inputs.Names.Count; $i++) {
            $bytes = [System.IO.File]::ReadAllBytes($inputs.Files[$i].FullName)
            $hash = [BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-', '')
            [void]$text.Append($inputs.Names[$i]).Append(':').Append($hash).Append("`n")
        }
        [BitConverter]::ToString($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($text.ToString()))).Replace('-', '')
    } catch {
        Write-Host "Could not read the sources ($($_.Exception.Message))."
        $null
    } finally {
        $sha.Dispose()
    }
}

<#
.SYNOPSIS
    A hash of the size and write time of every build input, without reading any file.
.DESCRIPTION
    One line per file, "<relative name>:<Length>:<LastWriteTimeUtc ticks>", names sorted ordinally, then
    SHA-256 over the lines. Taken before and after the checks it catches a file that was written in
    between even when its content ended up the same (an edit that was reverted), which the content
    fingerprint cannot see. The files come from Get-InputFiles.
.PARAMETER RepoRoot
    The repo root.
.OUTPUTS
    [string] (hex), or $null in the cases where Get-InputFingerprint returns $null (a listing failure
    or a link under the inputs prints a line with the cause).
#>
function Get-InputWriteSignature {
    param([Parameter(Mandatory)][string] $RepoRoot)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $inputs = Get-InputFiles -RepoRoot $RepoRoot
        if ($null -eq $inputs) { return $null }
        $text = New-Object System.Text.StringBuilder
        for ($i = 0; $i -lt $inputs.Names.Count; $i++) {
            $file = $inputs.Files[$i]
            [void]$text.Append($inputs.Names[$i]).Append(':').Append($file.Length.ToString([System.Globalization.CultureInfo]::InvariantCulture)).Append(':').Append($file.LastWriteTimeUtc.Ticks.ToString([System.Globalization.CultureInfo]::InvariantCulture)).Append("`n")
        }
        [BitConverter]::ToString($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($text.ToString()))).Replace('-', '')
    } catch {
        Write-Host "Could not read the sources ($($_.Exception.Message))."
        $null
    } finally {
        $sha.Dispose()
    }
}

<#
.SYNOPSIS
    Where a GameTest selector is set outside the command line, if anywhere.
.DESCRIPTION
    build.gradle runs only the GameTests that match when the Gradle project property gameTests exists,
    so such a run must not be recorded as a full pass. Gradle reads project properties from the
    environment variable ORG_GRADLE_PROJECT_gameTests, from -Dorg.gradle.project.gameTests in
    GRADLE_OPTS or JAVA_OPTS, and from gradle.properties in the repo and in the Gradle user home
    (gameTests, org.gradle.project.gameTests or systemProp.org.gradle.project.gameTests; a line
    starting with # or ! is a comment). A properties file that exists but cannot be read counts as
    set: this function never answers "none" when it could not look.
.PARAMETER RepoRoot
    The repo root.
.PARAMETER GradleUserHome
    The Gradle user home; default $env:GRADLE_USER_HOME, else ~\.gradle.
.OUTPUTS
    [string] naming the place (an environment variable, or the full path of a properties file), or
    $null when no selector is set.
#>
function Get-GameTestSelectorSource {
    param(
        [Parameter(Mandatory)][string] $RepoRoot,
        [string] $GradleUserHome = ''
    )
    if (-not $GradleUserHome) {
        $GradleUserHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $HOME '.gradle' }
    }
    if ([Environment]::GetEnvironmentVariable('ORG_GRADLE_PROJECT_gameTests')) {
        return 'the environment variable ORG_GRADLE_PROJECT_gameTests'
    }
    foreach ($name in 'GRADLE_OPTS', 'JAVA_OPTS') {
        $value = [Environment]::GetEnvironmentVariable($name)
        if ($value -and $value -match '(?<![\w.])-Dorg\.gradle\.project\.gameTests(?![\w.])') {
            return "the environment variable $name"
        }
    }
    foreach ($path in (Join-Path $RepoRoot 'gradle.properties'), (Join-Path $GradleUserHome 'gradle.properties')) {
        if (-not (Test-Path -LiteralPath $path)) { continue }
        try {
            $lines = [System.IO.File]::ReadAllLines($path)
        } catch {
            return "$path (it could not be read: $($_.Exception.Message))"
        }
        foreach ($line in $lines) {
            if ($line -match '^\s*[#!]') { continue }
            # A properties file also allows whitespace as the separator; the name must end there.
            if ($line -match '^\s*(systemProp\.)?(org\.gradle\.project\.)?gameTests(\s*[=:]|\s|$)') { return $path }
        }
    }
    $null
}

<#
.SYNOPSIS
    The build stamp as the record stores it: ticks, so the comparison is exact and culture-free.
.PARAMETER BuildStampUtc
    The build stamp (UTC).
.OUTPUTS
    [string]
#>
function ConvertTo-StampToken {
    param([Parameter(Mandatory)][datetime] $BuildStampUtc)
    $BuildStampUtc.Ticks.ToString([System.Globalization.CultureInfo]::InvariantCulture)
}

<#
.SYNOPSIS
    Whether the sources and the compiled classes match the last run whose build and GameTests passed.
.PARAMETER Fingerprint
    Get-InputFingerprint's result for the sources now; $null or empty means it could not be computed.
.PARAMETER BuildStampUtc
    Get-BuildStamp's result now; $null means nothing is built.
.PARAMETER VerifiedPath
    The file Save-VerifiedFingerprint writes.
.OUTPUTS
    'Current' (sources and build stamp identical), 'Changed' (a record exists and the sources differ),
    'ClassesDiffer' (the sources match, but the classes on disk are missing or are not the ones that
    run checked: something compiled other sources since), 'None' (no usable record) or 'Unknown' (no
    fingerprint to compare).
#>
function Get-VerifiedState {
    param(
        [AllowNull()][AllowEmptyString()][string] $Fingerprint,
        [Nullable[datetime]] $BuildStampUtc,
        [Parameter(Mandatory)][string] $VerifiedPath
    )
    if ([string]::IsNullOrEmpty($Fingerprint)) { return 'Unknown' }
    $lines = @()
    try {
        if (Test-Path -LiteralPath $VerifiedPath -PathType Leaf) {
            $lines = @([System.IO.File]::ReadAllText($VerifiedPath) -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
        }
    } catch {
        # Safe direction: an unreadable record never allows a skip. But say so, because 'no passing
        # run is recorded' (the line printed for None) would be false.
        Write-Host "Could not read the verified-run record $VerifiedPath ($($_.Exception.Message)); treating it as absent."
        $lines = @()
    }
    if ($lines.Count -eq 0) { return 'None' }
    if ($lines[0] -cne $Fingerprint) { return 'Changed' }
    if ($null -eq $BuildStampUtc -or $lines.Count -lt 2 -or $lines[1] -cne (ConvertTo-StampToken -BuildStampUtc $BuildStampUtc)) {
        return 'ClassesDiffer'
    }
    'Current'
}

<#
.SYNOPSIS
    Record that the build and the GameTests passed for these sources and these compiled classes.
.DESCRIPTION
    Two lines: the fingerprint, then the build stamp in ticks. A write failure only prints a line: the
    record is an optimization, and its absence makes the next run do the full work.
.PARAMETER Fingerprint
    Get-InputFingerprint's result, taken before the build and unchanged after the GameTests.
.PARAMETER BuildStampUtc
    Get-BuildStamp's result after the GameTests.
.PARAMETER VerifiedPath
    The file to write; its directory is created.
#>
function Save-VerifiedFingerprint {
    param(
        [Parameter(Mandatory)][string] $Fingerprint,
        [Parameter(Mandatory)][datetime] $BuildStampUtc,
        [Parameter(Mandatory)][string] $VerifiedPath
    )
    try {
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $VerifiedPath) | Out-Null
        $text = $Fingerprint + "`n" + (ConvertTo-StampToken -BuildStampUtc $BuildStampUtc) + "`n"
        [System.IO.File]::WriteAllText($VerifiedPath, $text, [System.Text.Encoding]::ASCII)
    } catch {
        Write-Host "Could not record the verified run ($($_.Exception.Message)); the next run will redo the checks."
    }
}

<#
.SYNOPSIS
    Forget the verified run, before a run that may fail.
.DESCRIPTION
    A failed or interrupted run must not leave an older pass standing for the same sources. If the file
    cannot be deleted it is emptied; if that fails too a line says so.
.PARAMETER VerifiedPath
    The file Save-VerifiedFingerprint writes.
.OUTPUTS
    [bool] $true when no record is left.
#>
function Clear-VerifiedFingerprint {
    param([Parameter(Mandatory)][string] $VerifiedPath)
    if (-not (Test-Path -LiteralPath $VerifiedPath)) { return $true }
    try {
        Remove-Item -LiteralPath $VerifiedPath -Force -ErrorAction Stop
        return $true
    } catch {
        try {
            [System.IO.File]::WriteAllText($VerifiedPath, '', [System.Text.Encoding]::ASCII)
            return $true
        } catch {
            Write-Host "Could not clear the verified-run record $VerifiedPath ($($_.Exception.Message)). Delete it by hand."
            return $false
        }
    }
}

<#
.SYNOPSIS
    One status line for a Get-VerifiedState result.
.PARAMETER State
    'Current', 'Changed', 'ClassesDiffer', 'None' or 'Unknown'.
.OUTPUTS
    [string]
#>
function Format-VerifiedLine {
    param([Parameter(Mandatory)][ValidateSet('Current', 'Changed', 'ClassesDiffer', 'None', 'Unknown')][string] $State)
    switch ($State) {
        'Current' { 'Verified: the sources and the compiled classes match the last passing build and GameTests; the next run skips them (unless -Force).' }
        'Changed' { 'Verified: the sources changed since the last passing run; the next run builds and runs the GameTests.' }
        'ClassesDiffer' { 'Verified: the sources match the last passing run, but the compiled classes are missing or were built since; the next run builds and runs the GameTests.' }
        'None' { 'Verified: no passing run is recorded; the next run builds and runs the GameTests.' }
        'Unknown' { 'Verified: cannot tell (the sources could not be listed or read); the next run builds and runs the GameTests.' }
    }
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
    Step 1 runs the commit-gate checks. Step 2 runs the GameTests (stopping any GameTest
    server and any stale dedicated server first, after y). Both are skipped when the sources and
    the compiled classes match the last run whose checks passed (see Get-VerifiedState); otherwise
    the record is cleared before the build and written again only after the GameTests pass with
    the sources unchanged. Step 3 stops stale clients (after y)
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
.PARAMETER Fingerprint
    Get-InputFingerprint's result, taken before the flow starts; $null when it could not be computed.
.PARAMETER WriteSignature
    Get-InputWriteSignature's result, taken before the fingerprint and before the flow starts; $null
    when it could not be computed. The flow takes both again after the GameTests and records the run
    only if neither changed. When -VerifiedPath is given but this is empty, nothing is ever recorded.
.PARAMETER VerifiedPath
    Where the verified-run record lives. Empty turns the skip-if-unchanged feature off: steps 1-2 always
    run and nothing is recorded.
.PARAMETER Force
    Run steps 1-2 even when the sources match the record.
.OUTPUTS
    [int] exit code: 0 ok, 1 build failed (or the verified-run record could not be cleared), 2 GameTests
    failed, 3 stop declined or failed, 4 client died.
#>
function Invoke-DeployFlow {
    param(
        [Parameter(Mandatory)][string] $RepoRoot,
        [Parameter(Mandatory)][scriptblock] $Build,
        [Parameter(Mandatory)][scriptblock] $RunGameTests,
        [Parameter(Mandatory)][scriptblock] $LaunchClient,
        [AllowNull()][AllowEmptyString()][string] $Fingerprint = $null,
        [AllowNull()][AllowEmptyString()][string] $WriteSignature = $null,
        [string] $VerifiedPath = '',
        [switch] $Force
    )
    # Steps 1-2 are skipped only when the sources are byte-for-byte what the last passing run checked
    # and the classes on disk are the ones it left (same build stamp): step 3 judges a running client
    # by the stamp, so classes compiled from other sources since must not pass as verified. A running
    # server is never evidence that tests pass, so nothing else skips the GameTests.
    $stamp = Get-BuildStamp -RepoRoot $RepoRoot
    $verified = if ($VerifiedPath) {
        Get-VerifiedState -Fingerprint $Fingerprint -BuildStampUtc $stamp -VerifiedPath $VerifiedPath
    } else { 'Off' }
    if ($verified -eq 'Current' -and -not $Force -and $null -ne $stamp) {
        Write-Host '== Steps 1-2 skipped: the sources match the last verified run (build and GameTests passed). Use -Force to run them anyway.'
        Write-Host (Format-BuildStamp -BuildStampUtc $stamp)
    } else {
        # Forget the old pass first: if this run fails or is interrupted, the newest evidence about
        # these sources is not a pass, and the next run must not skip on the older one.
        # A record that cannot be cleared stops the run (exit 1): it could outlive a failure.
        if ($VerifiedPath -and -not (Clear-VerifiedFingerprint -VerifiedPath $VerifiedPath)) {
            return $script:ExitBuildFailed
        }
        # Step 1: automated checks (the commit gate). No timeout: a cold run takes 5-15 min.
        Write-Host '== Step 1: spotlessCheck build'
        $code = & $Build
        if ($code -ne 0) {
            Write-Host "Build failed (gradlew exit $code)."
            return $script:ExitBuildFailed
        }
        $stamp = Get-BuildStamp -RepoRoot $RepoRoot
        Write-Host (Format-BuildStamp -BuildStampUtc $stamp)

        # Step 2: server GameTests.
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
        # Record the pass only if the sources are still what they were before the build: after an
        # edit made while the checks ran, nobody knows which version Gradle checked. The content
        # fingerprint cannot see an edit that was reverted, so the write signature (sizes and write
        # times) must match too. Fingerprint first, signature second: the two signatures then bracket
        # the two fingerprints, so a write during the second hash shows up in the second signature.
        # The stamp is read again because it is the classes as the GameTests left them that the
        # record vouches for.
        if ($VerifiedPath) {
            $stamp = Get-BuildStamp -RepoRoot $RepoRoot
            $afterFingerprint = Get-InputFingerprint -RepoRoot $RepoRoot
            $afterSignature = Get-InputWriteSignature -RepoRoot $RepoRoot
            $notRecorded = 'This run is not recorded; the next run redoes the checks.'
            $selectorSource = Get-GameTestSelectorSource -RepoRoot $RepoRoot
            if ($selectorSource) {
                Write-Host "A GameTest selector is set in $selectorSource, so only some GameTests ran. $notRecorded"
            } elseif (-not $Fingerprint -or -not $WriteSignature) {
                Write-Host "The sources could not be read before the checks. $notRecorded"
            } elseif (-not $afterFingerprint -or -not $afterSignature) {
                Write-Host "The sources could not be read after the checks. $notRecorded"
            } elseif ($afterFingerprint -cne $Fingerprint) {
                Write-Host "The sources changed while the checks ran. $notRecorded"
            } elseif ($afterSignature -cne $WriteSignature) {
                Write-Host "A source file was written while the checks ran (an edit that was reverted, or a tool that rewrote it). $notRecorded"
            } elseif ($null -eq $stamp) {
                Write-Host "No compiled classes were found after the checks. $notRecorded"
            } else {
                Save-VerifiedFingerprint -Fingerprint $Fingerprint -BuildStampUtc $stamp -VerifiedPath $VerifiedPath
            }
        }
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
