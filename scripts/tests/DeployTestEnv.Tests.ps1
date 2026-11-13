# Pester 3.4 (built into Windows PowerShell 5.1). Run:
#   powershell -NoProfile -ExecutionPolicy Bypass -Command "Import-Module Pester -RequiredVersion 3.4.0; Invoke-Pester -Path scripts/tests -EnableExit"
# ASCII only.

Import-Module (Join-Path $PSScriptRoot '..\DeployTestEnv.psm1') -Force

$Root = 'C:\dev\spellcraft'
$Java = '"C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe"'

# The real shape (spec, "Verified launch facts"): the two '@' argument-file tokens carry DOUBLED
# backslashes; -Dfml.modFolders and -cp carry single ones, and the classpath contains
# '<root>\build\moddev\artifacts\' for every instance. The doubled form is produced with
# .Replace so no editor or shell can silently collapse literal doubled backslashes in this file.
function ConvertTo-AtToken([string] $RepoRoot, [string] $Run, [string] $Suffix) {
    '@' + ("$RepoRoot\build\moddev\$Run$Suffix.txt").Replace('\', '\\')
}

function New-RunLine([string] $RepoRoot, [string] $Run) {
    $vm = ConvertTo-AtToken $RepoRoot $Run 'VmArgs'
    $prog = ConvertTo-AtToken $RepoRoot $Run 'ProgramArgs'
    $fml = "-Dfml.modFolders=spellcraft%%$RepoRoot\build\classes\java\main;spellcraft%%$RepoRoot\build\resources\main"
    $cp = "-cp $RepoRoot\build\moddev\artifacts\minecraft-patched-26.3.jar;C:\Users\will\.gradle\caches\lib\neoforge-26.3.0.23-beta.jar"
    "$Java $fml $vm -Dfile.encoding=UTF-8 $cp net.neoforged.devlaunch.Main $prog"
}

# A fake System.Diagnostics.Process for Get-Process mocks. Global, because Mock bodies run in the
# module's scope and cannot see this file's functions. WaitForExit records its argument in
# $global:WaitMs and Kill counts in $global:Kills.
function global:New-FakeProcess {
    param(
        [datetime] $StartUtc = [datetime]::MinValue,
        [switch] $StartUnreadable,
        [scriptblock] $OnClose = { $false },
        [bool] $Exits = $true,
        [scriptblock] $OnKill = { $global:Kills++ }
    )
    $p = New-Object PSObject
    $p | Add-Member -MemberType NoteProperty -Name Handle -Value ([IntPtr]1)
    if ($StartUnreadable) {
        $p | Add-Member -MemberType ScriptProperty -Name StartTime -Value { throw (New-Object System.ComponentModel.Win32Exception 5) }
    } else {
        $p | Add-Member -MemberType NoteProperty -Name StartTime -Value $StartUtc.ToLocalTime()
    }
    $p | Add-Member -MemberType ScriptMethod -Name CloseMainWindow -Value $OnClose
    $p | Add-Member -MemberType ScriptMethod -Name WaitForExit -Value ({ param($ms) $global:WaitMs += $ms; $Exits }.GetNewClosure())
    $p | Add-Member -MemberType ScriptMethod -Name Kill -Value $OnKill
    $p
}

# A fixed UTC instant for tests that need a build stamp or a write time. Mock bodies and
# -ParameterFilter blocks run in the module's scope and cannot see it; they keep the literal.
$Utc20261001 = [datetime]::SpecifyKind([datetime]'2026-10-01 12:00:00', 'Utc')

# Writes a text file under a repo, creating its directories.
function Set-Text([string] $Repo, [string] $Relative, [string] $Text) {
    $path = Join-Path $Repo $Relative
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $path) | Out-Null
    Set-Content -LiteralPath $path -Value $Text -Encoding ASCII
}

# Returns the path of a new repo under $TestDrive holding a few inputs and one ignored doc;
# -Built adds a compiled class.
function New-InputRepo([string] $Name, [switch] $Built) {
    $repo = Join-Path $TestDrive $Name
    New-Item -ItemType Directory -Path $repo | Out-Null
    Set-Text $repo 'src\main\A.java' 'class A {}'
    Set-Text $repo 'build.gradle' 'plugins {}'
    Set-Text $repo 'gradle.properties' 'a=1'
    Set-Text $repo 'docs\notes.md' 'notes'
    if ($Built) { Set-Text $repo 'build\classes\java\main\A.class' 'a' }
    $repo
}

# Creates a directory junction (needs no privilege). Remove it with Remove-Junction before the test
# ends: rmdir on a junction removes only the link, so $TestDrive cleanup never follows it.
function New-Junction([string] $Link, [string] $Target) {
    cmd /c mklink /J "$Link" "$Target" | Out-Null
}
function Remove-Junction([string] $Link) {
    cmd /c rmdir "$Link" | Out-Null
}

# Classifies Java string literals (as written in the source, escapes included) that may name a file
# outside what the build is known to read. Returns the ones to worry about:
#   (a) a parent-directory segment ('../', '..\\' or exactly '..'), or
#   (b) a '/' or a backslash, and a first path segment that names something at the repo root
#       ($RootNames), and the path is neither an input nor under an input directory ($Inputs, '/' form).
# Conservative on purpose: a false positive is cheap (an allow-list entry), a miss is a stale skip.
function Get-UncoveredJavaPaths([string[]] $Literals, [string[]] $RootNames, [string[]] $Inputs) {
    foreach ($literal in $Literals) {
        if ($literal.IndexOf('/') -lt 0 -and $literal.IndexOf([char]92) -lt 0 -and $literal -ne '..') { continue }
        if ($literal -match '(^|[/\\])\.\.([/\\]|$)') { $literal; continue }
        $segments = @($literal -split '[/\\]+' | Where-Object { $_ })
        if ($segments.Count -eq 0) { continue }
        $first = $segments[0]
        if (-not ($RootNames | Where-Object { $first -ieq $_ })) { continue }
        $normal = $segments -join '/'
        if (-not ($Inputs | Where-Object { $normal -ceq $_ -or $normal.StartsWith($_ + '/', [StringComparison]::Ordinal) })) { $literal }
    }
}

$ClientLine = New-RunLine $Root 'clientRun'
$GameTestLine = New-RunLine $Root 'gameTestServerRun'
$ServerLine = New-RunLine $Root 'serverRun'
$DaemonLine = "$Java -Xmx2g -cp C:\Users\will\.gradle\wrapper\dists\gradle-9.2.1-bin\abc\gradle-9.2.1\lib\gradle-daemon-main-9.2.1.jar org.gradle.launcher.daemon.bootstrap.GradleDaemon 9.2.1"
$WrapperLine = "$Java -Dorg.gradle.appname=gradlew -classpath $Root\gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain runClient"

Describe 'Get-InstanceKind' {
    It 'builds fixtures with doubled backslashes in the at tokens only' {
        $ClientLine | Should Match ([regex]::Escape('@C:\\dev\\spellcraft\\build\\moddev\\clientRunVmArgs.txt'))
        $ClientLine | Should Match ([regex]::Escape('-cp C:\dev\spellcraft\build\moddev\artifacts\'))
    }
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
    It 'ignores the Gradle wrapper process' {
        Get-InstanceKind -CommandLine $WrapperLine -RepoRoot $Root | Should BeNullOrEmpty
    }
    It 'is not misled by the moddev artifacts classpath or modFolders alone' {
        $line = "$Java -Dfml.modFolders=spellcraft%%$Root\build\classes\java\main -cp $Root\build\moddev\artifacts\a.jar net.neoforged.devlaunch.Main"
        Get-InstanceKind -CommandLine $line -RepoRoot $Root | Should BeNullOrEmpty
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
    It 'also accepts single-backslash at tokens' {
        $line = "$Java @$Root\build\moddev\clientRunVmArgs.txt net.neoforged.devlaunch.Main @$Root\build\moddev\clientRunProgramArgs.txt"
        Get-InstanceKind -CommandLine $line -RepoRoot $Root | Should Be 'Client'
    }
    It 'accepts a repo root with forward slashes or a trailing slash' {
        Get-InstanceKind -CommandLine $ClientLine -RepoRoot 'C:/dev/spellcraft' | Should Be 'Client'
        Get-InstanceKind -CommandLine $ClientLine -RepoRoot 'C:/dev/spellcraft/' | Should Be 'Client'
    }
    # Built from [char]92 so no literal doubled backslash lives in this file.
    $Unc = [string][char]92 + '\server\share\spellcraft'
    It 'matches a repo on a UNC share' {
        Get-InstanceKind -CommandLine (New-RunLine $Unc 'clientRun') -RepoRoot $Unc | Should Be 'Client'
    }
    It 'does not let a UNC root match a drive path with the same folders' {
        Get-InstanceKind -CommandLine (New-RunLine 'C:\server\share\spellcraft' 'clientRun') -RepoRoot $Unc | Should BeNullOrEmpty
    }
    It 'matches any of several root forms' {
        $line = New-RunLine 'D:\real\spellcraft' 'gameTestServerRun'
        Get-InstanceKind -CommandLine $line -RepoRoot @($Root, 'D:\real\spellcraft') | Should Be 'GameTestServer'
    }
    It 'only counts the path as an at token, not as a plain argument' {
        $line = "$Java -Dspellcraft.args=$Root\build\moddev\clientRunVmArgs.txt net.neoforged.devlaunch.Main"
        Get-InstanceKind -CommandLine $line -RepoRoot $Root | Should BeNullOrEmpty
    }
}

Describe 'Get-RepoRootForms' {
    It 'returns the given path first, and nothing else for a missing one' {
        $missing = Join-Path $TestDrive 'missing'
        $forms = @(Get-RepoRootForms -RepoRoot $missing)
        $forms.Count | Should Be 1
        $forms[0] | Should Be $missing
    }
    It 'adds the real location behind a junction' {
        $real = Join-Path $TestDrive 'real'
        New-Item -ItemType Directory -Path $real | Out-Null
        $link = Join-Path $TestDrive 'link'
        cmd /c mklink /J "$link" "$real" | Out-Null
        $forms = @(Get-RepoRootForms -RepoRoot $link)
        $forms[0] | Should Be $link
        # TestDrive may itself sit under a short (8.3) or linked temp path, so compare final forms.
        $forms -contains (@(Get-RepoRootForms -RepoRoot $real) | Select-Object -Last 1) | Should Be $true
    }
    $longDir = Join-Path $TestDrive 'a long directory name'
    New-Item -ItemType Directory -Path $longDir | Out-Null
    $shortDir = (New-Object -ComObject Scripting.FileSystemObject).GetFolder($longDir).ShortPath
    It 'adds the long form of an 8.3 short path' -Skip:($shortDir -eq $longDir -or $shortDir -notmatch '~') {
        $forms = @(Get-RepoRootForms -RepoRoot $shortDir)
        $forms[0] | Should Be $shortDir
        @($forms | Where-Object { $_ -like '*a long directory name' }).Count | Should BeGreaterThan 0
    }
    # Java's canonical path (which Gradle writes) expands 8.3 names but keeps a junction, so the
    # long-but-unresolved form is needed on its own, not just the final one.
    $longLink = Join-Path $TestDrive 'a long link name'
    cmd /c mklink /J "$longLink" "$longDir" | Out-Null
    $shortLink = (New-Object -ComObject Scripting.FileSystemObject).GetFolder($longLink).ShortPath
    It 'keeps the junction in the long form of a short path through it' -Skip:($shortLink -eq $longLink -or $shortLink -notmatch '~') {
        $forms = @(Get-RepoRootForms -RepoRoot $shortLink)
        @($forms | Where-Object { $_ -like '*a long link name' }).Count | Should Be 1
        @($forms | Where-Object { $_ -like '*a long directory name' }).Count | Should Be 1
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
    # Directories count toward the stamp, and creating files sets their write time to now, so
    # fixtures set every directory under build\ to a known time after creating the files.
    function Set-DirTimes([string] $Repo, [datetime] $WriteTimeUtc) {
        Get-ChildItem -LiteralPath (Join-Path $Repo 'build') -Recurse -Directory | ForEach-Object { $_.LastWriteTimeUtc = $WriteTimeUtc }
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
        Set-DirTimes $repo $old
        Get-BuildStamp -RepoRoot $repo | Should Be $new
    }
    It 'ignores the jar and test outputs' {
        $repo = New-Repo 'jar'
        New-File (Join-Path $repo 'build\classes\java\main\A.class') $old
        New-File (Join-Path $repo 'build\libs\spellcraft-1.0.0.jar') $new
        New-File (Join-Path $repo 'build\classes\java\test\ATest.class') $new
        Set-DirTimes $repo $old
        (Get-Item -LiteralPath (Join-Path $repo 'build\libs')).LastWriteTimeUtc = $new
        (Get-Item -LiteralPath (Join-Path $repo 'build\classes\java\test')).LastWriteTimeUtc = $new
        Get-BuildStamp -RepoRoot $repo | Should Be $old
    }
    It 'works with only one of the two directories present' {
        $repo = New-Repo 'classesonly'
        New-File (Join-Path $repo 'build\classes\java\main\A.class') $old
        Set-DirTimes $repo $old
        Get-BuildStamp -RepoRoot $repo | Should Be $old
    }
    It 'counts a directory written after the newest file, at any depth' {
        $repo = New-Repo 'dirnewer'
        New-File (Join-Path $repo 'build\classes\java\main\io\A.class') $old
        Set-DirTimes $repo $old
        (Get-Item -LiteralPath (Join-Path $repo 'build\classes\java\main\io')).LastWriteTimeUtc = $new
        Get-BuildStamp -RepoRoot $repo | Should Be $new
    }
    It 'counts an output directory itself' {
        $repo = New-Repo 'topdir'
        New-File (Join-Path $repo 'build\resources\main\a.json') $old
        Set-DirTimes $repo $old
        (Get-Item -LiteralPath (Join-Path $repo 'build\resources\main')).LastWriteTimeUtc = $new
        Get-BuildStamp -RepoRoot $repo | Should Be $new
    }
    It 'moves forward, not back, when the newest class is deleted' {
        $repo = New-Repo 'deleted'
        New-File (Join-Path $repo 'build\classes\java\main\A.class') $old
        New-File (Join-Path $repo 'build\classes\java\main\B.class') $new
        Set-DirTimes $repo $old
        $before = Get-BuildStamp -RepoRoot $repo
        Remove-Item -LiteralPath (Join-Path $repo 'build\classes\java\main\B.class')
        Get-BuildStamp -RepoRoot $repo | Should BeGreaterThan $before
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

function New-Instance([string] $Kind, [datetime] $StartTimeUtc, [int] $ProcessId = 1) {
    [pscustomobject]@{ Kind = $Kind; ProcessId = $ProcessId; StartTimeUtc = $StartTimeUtc }
}

$Stamp = [datetime]::SpecifyKind([datetime]'2026-10-01 12:00:00', 'Utc')
$Before = $Stamp.AddMinutes(-5)
$After = $Stamp.AddMinutes(5)

Describe 'Get-Instances' {
    # Mock bodies run in the module's scope and cannot see this file's variables or functions, so the
    # real-shaped command lines (doubled backslashes in the at tokens) are built inline by doubling each backslash (via [char]92, so no literal doubled
    # backslashes live in this file).
    Mock -ModuleName DeployTestEnv Get-RepoRootForms { , @($RepoRoot) }
    Mock -ModuleName DeployTestEnv Get-Process { New-FakeProcess -StartUtc ([datetime]'2026-10-01 14:05:01').ToUniversalTime() }
    Mock -ModuleName DeployTestEnv Get-CimInstance {
        $java = '"C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe"'
        $bs = [string][char]92
        $client = '@' + 'C:\dev\spellcraft\build\moddev\clientRunVmArgs.txt'.Replace($bs, $bs + $bs)
        $gametest = '@' + 'C:\dev\spellcraft\build\moddev\gameTestServerRunVmArgs.txt'.Replace($bs, $bs + $bs)
        if (-not ($client.Contains($bs + $bs) -and $gametest.Contains($bs + $bs))) { throw 'fixture lost its doubled backslashes' }
        @(
            [pscustomobject]@{ ProcessId = 101; CreationDate = [datetime]'2026-10-01 14:05:01'
                CommandLine = "$java -cp C:\dev\spellcraft\build\moddev\artifacts\a.jar $client net.neoforged.devlaunch.Main" }
            [pscustomobject]@{ ProcessId = 102; CreationDate = [datetime]'2026-10-01 14:06:00'
                CommandLine = "$java -cp gradle-daemon-main.jar org.gradle.launcher.daemon.bootstrap.GradleDaemon 9.2.1" }
            [pscustomobject]@{ ProcessId = 103; CreationDate = [datetime]'2026-10-01 14:07:00'; CommandLine = $null }
            [pscustomobject]@{ ProcessId = 104; CreationDate = [datetime]'2026-10-01 14:08:00'
                CommandLine = "$java -cp C:\dev\spellcraft\build\moddev\artifacts\a.jar $gametest net.neoforged.devlaunch.Main" }
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
    It 'takes the start time from the process, in UTC' {
        $result[0].StartTimeUtc | Should Be ([datetime]'2026-10-01 14:05:01').ToUniversalTime()
        $result[0].StartTimeUtc.Kind | Should Be 'Utc'
        Assert-MockCalled Get-Process -ModuleName DeployTestEnv -Times 1 -ParameterFilter { $Id -eq 101 }
    }
    It 'matches against every form of the repo root' {
        Assert-MockCalled Get-RepoRootForms -ModuleName DeployTestEnv -Times 1 -Exactly -ParameterFilter { $RepoRoot -eq 'C:\dev\spellcraft' }
    }
}

Describe 'Get-Instances with an alternative root form' {
    Mock -ModuleName DeployTestEnv Get-RepoRootForms { , @('S:\', 'C:\dev\spellcraft') }
    Mock -ModuleName DeployTestEnv Get-Process { New-FakeProcess -StartUtc ([datetime]'2026-10-01 14:05:01').ToUniversalTime() }
    Mock -ModuleName DeployTestEnv Get-CimInstance {
        $bs = [string][char]92
        $client = '@' + 'C:\dev\spellcraft\build\moddev\clientRunVmArgs.txt'.Replace($bs, $bs + $bs)
        [pscustomobject]@{ ProcessId = 106; CommandLine = "java.exe $client net.neoforged.devlaunch.Main" }
    }

    It 'finds a run that names the root in its other form' {
        $result = @(Get-Instances -RepoRoot 'S:\')
        $result.Count | Should Be 1
        $result[0].ProcessId | Should Be 106
    }
}

Describe 'Get-Instances when the start time cannot be read' {
    Mock -ModuleName DeployTestEnv Write-Host { }
    Mock -ModuleName DeployTestEnv Get-RepoRootForms { , @($RepoRoot) }
    Mock -ModuleName DeployTestEnv Get-Process { New-FakeProcess -StartUnreadable }
    Mock -ModuleName DeployTestEnv Get-CimInstance {
        $bs = [string][char]92
        $client = '@' + 'C:\dev\spellcraft\build\moddev\clientRunVmArgs.txt'.Replace($bs, $bs + $bs)
        [pscustomobject]@{ ProcessId = 105; CommandLine = "java.exe -cp C:\dev\spellcraft\build\moddev\artifacts\a.jar $client net.neoforged.devlaunch.Main" }
    }

    $result = @(Get-Instances -RepoRoot 'C:\dev\spellcraft')

    It 'skips the instance instead of throwing' {
        $result.Count | Should Be 0
    }
    It 'says which instance it skipped' {
        Assert-MockCalled Write-Host -ModuleName DeployTestEnv -Times 1 -Exactly -ParameterFilter { "$Object" -match 'Client PID 105' }
    }
}

Describe 'Get-Instances when the process exits before its start time is read' {
    Mock -ModuleName DeployTestEnv Write-Host { }
    Mock -ModuleName DeployTestEnv Get-RepoRootForms { , @($RepoRoot) }
    Mock -ModuleName DeployTestEnv Get-Process { }
    Mock -ModuleName DeployTestEnv Get-CimInstance {
        $bs = [string][char]92
        $client = '@' + 'C:\dev\spellcraft\build\moddev\clientRunVmArgs.txt'.Replace($bs, $bs + $bs)
        [pscustomobject]@{ ProcessId = 107; CommandLine = "java.exe $client net.neoforged.devlaunch.Main" }
    }

    It 'leaves it out silently' {
        @(Get-Instances -RepoRoot 'C:\dev\spellcraft').Count | Should Be 0
        Assert-MockCalled Write-Host -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
    }
}

Describe 'Get-Instances across the end of daylight saving' {
    # Two UTC instants one hour apart that show the same local wall-clock time: the repeated hour
    # when clocks go back. Found by search, so the test follows this machine's time zone.
    $ambiguous = $null
    $t = [datetime]::SpecifyKind([datetime]'2026-01-01', 'Utc')
    while ($t.Year -eq 2026 -and $null -eq $ambiguous) {
        if ([datetime]::FromFileTime($t.ToFileTimeUtc()) -eq [datetime]::FromFileTime($t.AddHours(1).ToFileTimeUtc())) { $ambiguous = $t }
        $t = $t.AddHours(1)
    }
    $global:DstFirst = $ambiguous

    Mock -ModuleName DeployTestEnv Get-RepoRootForms { , @($RepoRoot) }
    # Process.StartTime is DateTime.FromFileTime(creation time): local, and it remembers which of
    # the two repeated hours it is.
    Mock -ModuleName DeployTestEnv Get-Process {
        $p = New-Object PSObject
        $p | Add-Member -MemberType NoteProperty -Name StartTime -Value ([datetime]::FromFileTime($global:DstFirst.AddHours($Id[0] - 201).ToFileTimeUtc()))
        $p
    }
    Mock -ModuleName DeployTestEnv Get-CimInstance {
        $bs = [string][char]92
        $client = '@' + 'C:\dev\spellcraft\build\moddev\clientRunVmArgs.txt'.Replace($bs, $bs + $bs)
        [pscustomobject]@{ ProcessId = 201; CommandLine = "java.exe $client x" }
        [pscustomobject]@{ ProcessId = 202; CommandLine = "java.exe $client x" }
    }

    It 'keeps the two repeated hours apart' -Skip:($null -eq $ambiguous) {
        $result = @(Get-Instances -RepoRoot 'C:\dev\spellcraft')
        $result[0].StartTimeUtc | Should Be $global:DstFirst
        $result[1].StartTimeUtc | Should Be $global:DstFirst.AddHours(1)
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
        # Only the whole answer counts: these pin both anchors of the match.
        foreach ($no in @('yep', 'nay', 'okay', ' y', 'y ', 'yes please')) {
            It "treats '$no' as no (only y or yes)" {
                $global:Answer = $no
                Mock -ModuleName DeployTestEnv Read-Host { $global:Answer }
                Confirm-StopInstances -Instances $one -BuildStampUtc $Stamp | Should Be $false
            }
        }
        It 'treats a prompt the host refuses as no, without throwing' {
            Mock -ModuleName DeployTestEnv Read-Host { throw 'Windows PowerShell is in NonInteractive mode.' }
            Confirm-StopInstances -Instances $one -BuildStampUtc $Stamp | Should Be $false
            Assert-MockCalled Write-Host -ModuleName DeployTestEnv -Times 1 -Scope It -ParameterFilter { "$Object" -match 'treating the answer as N' }
        }
    }
}

Describe 'Test-CanPromptWith' {
    $plain = @('powershell.exe', '-ExecutionPolicy', 'Bypass', '-File', 'scripts\Deploy-TestEnv.ps1')

    It 'is true for an interactive console with no -NonInteractive' {
        Test-CanPromptWith -UserInteractive $true -InputRedirected $false -CommandLineArgs $plain | Should Be $true
    }
    It 'is false for a non-interactive host' {
        Test-CanPromptWith -UserInteractive $false -InputRedirected $false -CommandLineArgs $plain | Should Be $false
    }
    It 'is false when stdin is redirected' {
        Test-CanPromptWith -UserInteractive $true -InputRedirected $true -CommandLineArgs $plain | Should Be $false
    }
    # Every spelling powershell.exe accepts (checked on Windows PowerShell 5.1).
    foreach ($flag in @('-NonInteractive', '-noni', '-NONI', '-nonin', '/noni', '/NonInteractive')) {
        It "is false under $flag" {
            Test-CanPromptWith -UserInteractive $true -InputRedirected $false -CommandLineArgs (@('powershell.exe', $flag) + $plain[1..4]) | Should Be $false
        }
    }
    It 'is not fooled by -NoProfile or -NoLogo' {
        Test-CanPromptWith -UserInteractive $true -InputRedirected $false -CommandLineArgs @('powershell.exe', '-NoProfile', '-NoLogo', '-File', 'x.ps1') | Should Be $true
    }
}

Describe 'Test-CanPrompt' {
    It 'passes the real host state to Test-CanPromptWith' {
        Mock -ModuleName DeployTestEnv Test-CanPromptWith { $true }
        Test-CanPrompt | Should Be $true
        Assert-MockCalled Test-CanPromptWith -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It -ParameterFilter {
            $UserInteractive -eq [Environment]::UserInteractive -and
            $InputRedirected -eq [Console]::IsInputRedirected -and
            ($CommandLineArgs -join ' ') -eq ([Environment]::GetCommandLineArgs() -join ' ')
        }
    }
    It 'is false end to end when stdin is redirected' {
        # A child powershell, so the real host state is exercised without touching this session.
        $modulePath = (Resolve-Path (Join-Path $PSScriptRoot '..\DeployTestEnv.psm1')).Path
        $out = '' | & powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Import-Module '$modulePath' -Force; [string](Test-CanPrompt)"
        ($out | Select-Object -Last 1) | Should Be 'False'
    }
}

Describe 'Stop-Instance' {
    Mock -ModuleName DeployTestEnv Write-Host { }
    Mock -ModuleName DeployTestEnv Stop-Process { }
    # Fake processes come from New-FakeProcess. Mock bodies cannot see this file's variables, so
    # each Context writes its start time literally: 11:55:00 UTC is $Before, the instances' start.
    $client = New-Instance 'Client' $Before 5
    $server = New-Instance 'Server' $Before 6
    function Reset-Counters { $global:WaitMs = @(); $global:Kills = 0 }

    Context 'when the process has already gone' {
        Mock -ModuleName DeployTestEnv Get-Process { $null }

        It 'does nothing and reports it gone' {
            Reset-Counters
            Stop-Instance -Instance $client | Should Be $true
            $global:Kills | Should Be 0
        }
    }

    Context 'when the PID now belongs to a process started 2 h later (PID reuse)' {
        Mock -ModuleName DeployTestEnv Get-Process {
            New-FakeProcess -StartUtc ([datetime]::SpecifyKind([datetime]'2026-10-01 14:00:00', 'Utc')) -OnClose { throw 'CloseMainWindow on a reused PID' }
        }

        It 'treats the instance as gone and touches nothing' {
            Reset-Counters
            Stop-Instance -Instance $client | Should Be $true
            $global:Kills | Should Be 0
        }
    }

    Context 'when the PID now belongs to a process started 6 s later' {
        Mock -ModuleName DeployTestEnv Get-Process {
            New-FakeProcess -StartUtc ([datetime]::SpecifyKind([datetime]'2026-10-01 11:55:06', 'Utc')) -OnClose { throw 'CloseMainWindow on a reused PID' }
        }

        It 'is just past the 5 s tolerance: treated as gone, not touched' {
            Reset-Counters
            Stop-Instance -Instance $client | Should Be $true
            $global:Kills | Should Be 0
        }
    }

    Context 'when the start time is exactly 5 s from the instance start' {
        Mock -ModuleName DeployTestEnv Get-Process { New-FakeProcess -StartUtc ([datetime]::SpecifyKind([datetime]'2026-10-01 11:54:55', 'Utc')) }

        It 'is still the same process and gets stopped' {
            Reset-Counters
            Stop-Instance -Instance $client | Should Be $true
            $global:Kills | Should Be 1
        }
    }

    Context 'when the start time is within the tolerance of the instance start' {
        Mock -ModuleName DeployTestEnv Get-Process { New-FakeProcess -StartUtc ([datetime]::SpecifyKind([datetime]'2026-10-01 11:55:03', 'Utc')) }

        It 'is the same process and gets stopped' {
            Reset-Counters
            Stop-Instance -Instance $client | Should Be $true
            $global:Kills | Should Be 1
        }
    }

    Context 'when the start time cannot be read and the process is still there' {
        Mock -ModuleName DeployTestEnv Get-Process { New-FakeProcess -StartUnreadable }

        It 'stops nothing and reports it not gone' {
            Reset-Counters
            Stop-Instance -Instance $server | Should Be $false
            $global:Kills | Should Be 0
            Assert-MockCalled Stop-Process -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
            Assert-MockCalled Write-Host -ModuleName DeployTestEnv -Times 1 -Scope It -ParameterFilter { "$Object" -match 'not stopping it' }
        }
    }

    Context 'when the start time cannot be read because the process just exited' {
        Mock -ModuleName DeployTestEnv Get-Process {
            $global:GetProcessCalls++
            if ($global:GetProcessCalls -eq 1) { New-FakeProcess -StartUnreadable }
        }

        It 'reports it gone and stops nothing' {
            Reset-Counters
            $global:GetProcessCalls = 0
            Stop-Instance -Instance $server | Should Be $true
            $global:Kills | Should Be 0
        }
    }

    Context 'a client that closes when asked' {
        Mock -ModuleName DeployTestEnv Get-Process { New-FakeProcess -StartUtc ([datetime]::SpecifyKind([datetime]'2026-10-01 11:55:00', 'Utc')) -OnClose { $true } }

        It 'is not killed and waits 20 s by default' {
            Reset-Counters
            Stop-Instance -Instance $client | Should Be $true
            $global:Kills | Should Be 0
            ($global:WaitMs -join ',') | Should Be '20000'
        }
        It 'waits GraceSeconds * 1000 ms for a custom grace' {
            Reset-Counters
            Stop-Instance -Instance $client -GraceSeconds 7 | Should Be $true
            ($global:WaitMs -join ',') | Should Be '7000'
        }
    }

    Context 'a client that is asked to close but does not exit in time' {
        Mock -ModuleName DeployTestEnv Get-Process { New-FakeProcess -StartUtc ([datetime]::SpecifyKind([datetime]'2026-10-01 11:55:00', 'Utc')) -OnClose { $true } -Exits $false }

        It 'is killed once through its handle, and reported not gone if it never exits' {
            Reset-Counters
            Stop-Instance -Instance $client -GraceSeconds 1 | Should Be $false
            $global:Kills | Should Be 1
            Assert-MockCalled Stop-Process -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
            ($global:WaitMs -join ',') | Should Be '1000,10000'
        }
    }

    Context 'a client with no main window to close' {
        Mock -ModuleName DeployTestEnv Get-Process { New-FakeProcess -StartUtc ([datetime]::SpecifyKind([datetime]'2026-10-01 11:55:00', 'Utc')) }

        It 'is killed straight away and waits 10 s for it to go' {
            Reset-Counters
            Stop-Instance -Instance $client | Should Be $true
            $global:Kills | Should Be 1
            ($global:WaitMs -join ',') | Should Be '10000'
        }
    }

    Context 'a client that exits between Get-Process and CloseMainWindow' {
        Mock -ModuleName DeployTestEnv Get-Process {
            New-FakeProcess -StartUtc ([datetime]::SpecifyKind([datetime]'2026-10-01 11:55:00', 'Utc')) -OnClose { throw (New-Object System.InvalidOperationException 'exited') }
        }

        It 'counts as gone' {
            Reset-Counters
            Stop-Instance -Instance $client | Should Be $true
            $global:Kills | Should Be 0
        }
    }

    Context 'a server that exits just before the kill' {
        Mock -ModuleName DeployTestEnv Get-Process {
            New-FakeProcess -StartUtc ([datetime]::SpecifyKind([datetime]'2026-10-01 11:55:00', 'Utc')) -OnKill { throw (New-Object System.InvalidOperationException 'exited') }
        }

        It 'counts as gone' {
            Reset-Counters
            Stop-Instance -Instance $server | Should Be $true
        }
    }

    Context 'a server that cannot be killed (access denied)' {
        Mock -ModuleName DeployTestEnv Get-Process {
            New-FakeProcess -StartUtc ([datetime]::SpecifyKind([datetime]'2026-10-01 11:55:00', 'Utc')) -OnKill { throw (New-Object System.ComponentModel.Win32Exception 5) }
        }

        It 'throws, for Invoke-StopStep to report' {
            Reset-Counters
            { Stop-Instance -Instance $server } | Should Throw
        }
    }

    Context 'a server' {
        Mock -ModuleName DeployTestEnv Get-Process {
            # A server must never be sent CloseMainWindow: it throws if called.
            New-FakeProcess -StartUtc ([datetime]::SpecifyKind([datetime]'2026-10-01 11:55:00', 'Utc')) -OnClose { throw 'CloseMainWindow on a server' }
        }

        It 'is killed without CloseMainWindow' {
            Reset-Counters
            Stop-Instance -Instance $server | Should Be $true
            $global:Kills | Should Be 1
            ($global:WaitMs -join ',') | Should Be '10000'
        }
    }
}

Describe 'Stop-Instance on real processes' {
    Mock -ModuleName DeployTestEnv Write-Host { }
    function Start-Victim {
        $p = Start-Process -FilePath 'ping.exe' -ArgumentList '-n 60 127.0.0.1' -WindowStyle Hidden -PassThru
        New-Instance 'Server' $p.StartTime.ToUniversalTime() $p.Id
    }

    It 'kills the process it was given' {
        $victim = Start-Victim
        try {
            Stop-Instance -Instance $victim | Should Be $true
            Get-Process -Id $victim.ProcessId -ErrorAction SilentlyContinue | Should BeNullOrEmpty
        } finally { Stop-Process -Id $victim.ProcessId -Force -ErrorAction SilentlyContinue }
    }
    It 'leaves alone a process whose start time is 6 s off' {
        $victim = Start-Victim
        try {
            $victim.StartTimeUtc = $victim.StartTimeUtc.AddSeconds(-6)
            Stop-Instance -Instance $victim | Should Be $true
            Get-Process -Id $victim.ProcessId -ErrorAction SilentlyContinue | Should Not BeNullOrEmpty
        } finally { Stop-Process -Id $victim.ProcessId -Force -ErrorAction SilentlyContinue }
    }
}

Describe 'Invoke-StopStep' {
    Mock -ModuleName DeployTestEnv Write-Host { }
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
    It 'fails, without throwing, when stopping an instance throws, and still tries the others' {
        Mock -ModuleName DeployTestEnv Confirm-StopInstances { $true }
        Mock -ModuleName DeployTestEnv Stop-Instance { if ($Instance.ProcessId -eq 1) { throw 'Access is denied' } else { $true } }
        $two = @((New-Instance 'GameTestServer' $After 1), (New-Instance 'Server' $Before 2))
        Invoke-StopStep -ToStop $two -BuildStampUtc $Stamp | Should Be $false
        Assert-MockCalled Stop-Instance -ModuleName DeployTestEnv -Times 2 -Exactly -Scope It
        Assert-MockCalled Write-Host -ModuleName DeployTestEnv -Times 1 -Scope It -ParameterFilter { "$Object" -match 'Could not stop GameTestServer PID 1: Access is denied' }
    }
}

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
        # /T: the cmd.exe wrapper's ping child would otherwise outlive it and hold the log open.
        finally { taskkill /PID $r.ProcessId /T /F 2>&1 | Out-Null }
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
            Mock -ModuleName DeployTestEnv Stop-Instance { $true }
            $f = New-Flow
            Invoke-Flow $f | Should Be 3
            ($f.Calls -join ',') | Should Be 'build'
            Assert-MockCalled Stop-Instance -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
        }
        It 'exits 3 before the GameTests when it will not exit' {
            Mock -ModuleName DeployTestEnv Confirm-StopInstances { $true }
            Mock -ModuleName DeployTestEnv Stop-Instance { $false }
            $f = New-Flow
            Invoke-Flow $f | Should Be 3
            ($f.Calls -join ',') | Should Be 'build'
            Assert-MockCalled Stop-Instance -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It
        }
        It 'exits 3, not 1, when stopping it throws' {
            Mock -ModuleName DeployTestEnv Confirm-StopInstances { $true }
            Mock -ModuleName DeployTestEnv Stop-Instance { throw 'Access is denied' }
            $f = New-Flow
            Invoke-Flow $f | Should Be 3
            ($f.Calls -join ',') | Should Be 'build'
        }
        It 'stops it and goes on after y' {
            Mock -ModuleName DeployTestEnv Confirm-StopInstances { $true }
            Mock -ModuleName DeployTestEnv Stop-Instance { $true }
            $f = New-Flow
            Invoke-Flow $f | Should Be 0
            ($f.Calls -join ',') | Should Be 'build,tests,launch'
            Assert-MockCalled Stop-Instance -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It
        }
    }

    Context 'a stale dedicated server is running' {
        Mock -ModuleName DeployTestEnv Get-Instances {
            [pscustomobject]@{ Kind = 'Server'; ProcessId = 7; StartTimeUtc = [datetime]::SpecifyKind([datetime]'2026-10-01 11:00:00', 'Utc') }
        }

        It 'exits 3 before the GameTests when the user declines' {
            Mock -ModuleName DeployTestEnv Confirm-StopInstances { $false }
            Mock -ModuleName DeployTestEnv Stop-Instance { $true }
            $f = New-Flow
            Invoke-Flow $f | Should Be 3
            ($f.Calls -join ',') | Should Be 'build'
            Assert-MockCalled Stop-Instance -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
        }
        It 'stops it before the GameTests after y' {
            Mock -ModuleName DeployTestEnv Confirm-StopInstances { $true }
            Mock -ModuleName DeployTestEnv Stop-Instance { [void]$global:DeployTestCalls.Add('stop'); $true }
            $f = New-Flow
            $global:DeployTestCalls = $f.Calls
            Invoke-Flow $f | Should Be 0
            ($f.Calls -join ',') | Should Be 'build,stop,tests,launch'
            Assert-MockCalled Stop-Instance -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It -ParameterFilter { $Instance.ProcessId -eq 7 }
        }
    }

    Context 'a stale client is running' {
        Mock -ModuleName DeployTestEnv Get-Instances {
            [pscustomobject]@{ Kind = 'Client'; ProcessId = 9; StartTimeUtc = [datetime]::SpecifyKind([datetime]'2026-10-01 11:00:00', 'Utc') }
        }

        It 'runs the GameTests beside it, then exits 3 when the user declines' {
            Mock -ModuleName DeployTestEnv Confirm-StopInstances { $false }
            Mock -ModuleName DeployTestEnv Stop-Instance { $true }
            $f = New-Flow
            Invoke-Flow $f | Should Be 3
            ($f.Calls -join ',') | Should Be 'build,tests'
            Assert-MockCalled Stop-Instance -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
        }
        It 'exits 3 when the client will not exit, without launching' {
            Mock -ModuleName DeployTestEnv Confirm-StopInstances { $true }
            Mock -ModuleName DeployTestEnv Stop-Instance { $false }
            $f = New-Flow
            Invoke-Flow $f | Should Be 3
            ($f.Calls -join ',') | Should Be 'build,tests'
            Assert-MockCalled Stop-Instance -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It
        }
        It 'stops it after y, then launches a fresh client' {
            Mock -ModuleName DeployTestEnv Confirm-StopInstances { $true }
            Mock -ModuleName DeployTestEnv Stop-Instance { $true }
            $f = New-Flow
            Invoke-Flow $f | Should Be 0
            ($f.Calls -join ',') | Should Be 'build,tests,launch'
            Assert-MockCalled Stop-Instance -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It -ParameterFilter { $Instance.ProcessId -eq 9 }
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

Describe 'Get-InputFingerprint' {
    $FunctionalSpec = 'docs\superpowers\specs\evoking\evoking-functional-spec.md'
    # Java string literals the path scan flags that are not file reads. Each entry needs a comment saying why.
    $JavaPathAllowList = @()

    It 'is the same for the same contents' {
        $repo = New-InputRepo 'same'
        Get-InputFingerprint -RepoRoot $repo | Should Be (Get-InputFingerprint -RepoRoot $repo)
        Get-InputFingerprint -RepoRoot $repo | Should Not BeNullOrEmpty
    }
    It 'changes when a source file changes' {
        $repo = New-InputRepo 'src-edit'
        $before = Get-InputFingerprint -RepoRoot $repo
        Set-Text $repo 'src\main\A.java' 'class A { int x; }'
        Get-InputFingerprint -RepoRoot $repo | Should Not Be $before
    }
    It 'changes when a build file changes' {
        $repo = New-InputRepo 'build-edit'
        $before = Get-InputFingerprint -RepoRoot $repo
        Set-Text $repo 'gradle.properties' 'a=2'
        Get-InputFingerprint -RepoRoot $repo | Should Not Be $before
    }
    It 'changes when a Gradle wrapper file changes' {
        $repo = New-InputRepo 'wrapper-edit'
        Set-Text $repo 'gradle\wrapper\gradle-wrapper.properties' 'v=1'
        $before = Get-InputFingerprint -RepoRoot $repo
        Set-Text $repo 'gradle\wrapper\gradle-wrapper.properties' 'v=2'
        Get-InputFingerprint -RepoRoot $repo | Should Not Be $before
    }
    It 'changes when a new file appears under src, tracked or not' {
        $repo = New-InputRepo 'src-new'
        $before = Get-InputFingerprint -RepoRoot $repo
        Set-Text $repo 'src\main\B.java' 'class B {}'
        Get-InputFingerprint -RepoRoot $repo | Should Not Be $before
    }
    It 'changes when a source file is deleted' {
        $repo = New-InputRepo 'src-delete'
        Set-Text $repo 'src\main\B.java' 'class B {}'
        $before = Get-InputFingerprint -RepoRoot $repo
        Remove-Item -LiteralPath (Join-Path $repo 'src\main\B.java')
        Get-InputFingerprint -RepoRoot $repo | Should Not Be $before
    }
    It 'changes when a file moves without changing its contents' {
        $repo = New-InputRepo 'src-move'
        $before = Get-InputFingerprint -RepoRoot $repo
        New-Item -ItemType Directory -Force -Path (Join-Path $repo 'src\other') | Out-Null
        Move-Item -LiteralPath (Join-Path $repo 'src\main\A.java') -Destination (Join-Path $repo 'src\other\A.java')
        Get-InputFingerprint -RepoRoot $repo | Should Not Be $before
    }
    foreach ($file in 'settings.gradle', 'gradlew', 'gradlew.bat', '.gitattributes') {
        It "changes when $file changes" {
            $repo = New-InputRepo "edit-$file"
            Set-Text $repo $file 'one'
            $before = Get-InputFingerprint -RepoRoot $repo
            Set-Text $repo $file 'two'
            Get-InputFingerprint -RepoRoot $repo | Should Not Be $before
        }
    }
    It 'changes when the Evoking functional spec changes (FunctionalSpecReferencesTest reads it in the build)' {
        $repo = New-InputRepo 'spec-edit'
        Set-Text $repo $FunctionalSpec '3.4 one'
        $before = Get-InputFingerprint -RepoRoot $repo
        Set-Text $repo $FunctionalSpec '3.4 one 3.5 two'
        Get-InputFingerprint -RepoRoot $repo | Should Not Be $before
    }
    It 'ignores other docs, scripts and build output' {
        $repo = New-InputRepo 'ignored'
        Set-Text $repo 'scripts\tool.ps1' 'a'
        $before = Get-InputFingerprint -RepoRoot $repo
        Set-Text $repo 'docs\notes.md' 'other notes'
        Set-Text $repo 'scripts\tool.ps1' 'b'
        Set-Text $repo 'build\classes\X.class' 'x'
        Get-InputFingerprint -RepoRoot $repo | Should Be $before
    }
    It 'counts a git-ignored file under src (Gradle packages it whatever git thinks)' {
        $repo = New-InputRepo 'git-ignored'
        git -C $repo init -q 2>$null | Out-Null
        Set-Text $repo '.gitignore' '*.tmp'
        $before = Get-InputFingerprint -RepoRoot $repo
        Set-Text $repo 'src\main\resources\scratch.tmp' 'scratch'
        Get-InputFingerprint -RepoRoot $repo | Should Not Be $before
    }
    It 'sees an edit to a file with a non-ASCII name' {
        $repo = New-InputRepo 'non-ascii'
        $name = 'src\main\caf' + [char]0xE9 + '.txt'
        Set-Text $repo $name 'one'
        $before = Get-InputFingerprint -RepoRoot $repo
        $before | Should Not BeNullOrEmpty
        Set-Text $repo $name 'two'
        Get-InputFingerprint -RepoRoot $repo | Should Not Be $before
    }
    It 'does not depend on the form of the root (trailing separator)' {
        $repo = New-InputRepo 'root-form'
        Get-InputFingerprint -RepoRoot ($repo + '\') | Should Be (Get-InputFingerprint -RepoRoot $repo)
    }
    It 'is null when a source file cannot be read, and says why' {
        Mock -ModuleName DeployTestEnv Write-Host { }
        $repo = New-InputRepo 'locked'
        $lock = [System.IO.File]::Open((Join-Path $repo 'src\main\A.java'), 'Open', 'ReadWrite', 'None')
        try {
            Get-InputFingerprint -RepoRoot $repo | Should BeNullOrEmpty
            Assert-MockCalled Write-Host -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It -ParameterFilter { "$Object" -like '*Could not read the sources*A.java*' }
        } finally {
            $lock.Dispose()
        }
    }
    It 'is null when none of the inputs exist' {
        $dir = Join-Path $TestDrive 'no-inputs'
        New-Item -ItemType Directory -Path $dir | Out-Null
        Get-InputFingerprint -RepoRoot $dir | Should BeNullOrEmpty
    }
    It 'is null when the root does not exist' {
        Get-InputFingerprint -RepoRoot (Join-Path $TestDrive 'missing-root') | Should BeNullOrEmpty
    }
    It 'is null when a directory under src is denied to the lister, and says why' {
        # A mock cannot tell -ErrorAction Stop from SilentlyContinue, so this one uses a real denied directory.
        Mock -ModuleName DeployTestEnv Write-Host { }
        $repo = New-InputRepo 'denied-real'
        Set-Text $repo 'src\main\sub\B.java' 'class B {}'
        $dir = Join-Path $repo 'src\main\sub'
        icacls $dir /deny '*S-1-1-0:(RD)' | Out-Null
        try {
            Get-InputFingerprint -RepoRoot $repo | Should BeNullOrEmpty
            Get-InputWriteSignature -RepoRoot $repo | Should BeNullOrEmpty
            Assert-MockCalled Write-Host -ModuleName DeployTestEnv -Times 2 -Exactly -Scope It -ParameterFilter { "$Object" -like '*Could not read the sources*' }
        } finally {
            icacls $dir /remove:d '*S-1-1-0' | Out-Null
        }
    }
    Context 'the listing of a directory throws' {
        Mock -ModuleName DeployTestEnv Write-Host { }
        Mock -ModuleName DeployTestEnv Get-ChildItem { throw (New-Object System.UnauthorizedAccessException 'Access to the path is denied.') }

        It 'is null, and says the sources could not be read' {
            $repo = New-InputRepo 'denied-mock'
            Get-InputFingerprint -RepoRoot $repo | Should BeNullOrEmpty
            Assert-MockCalled Write-Host -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It -ParameterFilter { "$Object" -like '*Could not read the sources*denied*' }
        }
    }
    Context 'a listed file lies outside the root' {
        Mock -ModuleName DeployTestEnv Get-ChildItem { New-Object System.IO.FileInfo (Join-Path $env:windir 'win.ini') }

        It 'is null' {
            $repo = New-InputRepo 'outside-root'
            Get-InputFingerprint -RepoRoot $repo | Should BeNullOrEmpty
        }
    }
    It 'is null for a junction under src, and names it, because Gradle follows links and the lister does not' {
        Mock -ModuleName DeployTestEnv Write-Host { }
        $repo = New-InputRepo 'junction'
        $outside = Join-Path $TestDrive 'junction-outside'
        Set-Text $outside 'X.java' 'class X {}'
        $link = Join-Path $repo 'src\linked'
        New-Junction $link $outside
        try {
            Get-InputFingerprint -RepoRoot $repo | Should BeNullOrEmpty
            Assert-MockCalled Write-Host -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It -ParameterFilter { "$Object" -eq 'Could not verify the sources: src/linked is a link (junction or symlink).' }
        } finally {
            Remove-Junction $link
        }
    }
    It 'is null for a junction deeper under src' {
        $repo = New-InputRepo 'junction-deep'
        $outside = Join-Path $TestDrive 'junction-deep-outside'
        Set-Text $outside 'X.java' 'class X {}'
        $link = Join-Path $repo 'src\main\linked'
        New-Junction $link $outside
        try {
            Get-InputFingerprint -RepoRoot $repo | Should BeNullOrEmpty
        } finally {
            Remove-Junction $link
        }
    }
    It 'is null when an input directory is itself a junction' {
        $repo = New-InputRepo 'junction-input'
        $outside = Join-Path $TestDrive 'junction-input-outside'
        Set-Text $outside 'wrapper.properties' 'v=1'
        $link = Join-Path $repo 'gradle'
        New-Junction $link $outside
        try {
            Get-InputFingerprint -RepoRoot $repo | Should BeNullOrEmpty
        } finally {
            Remove-Junction $link
        }
    }
    It 'classifies Java string literals: flags paths that may leave the inputs, ignores the rest' {
        $rootNames = 'docs', 'scripts', 'run', 'build', 'gradle', 'config', '.claude', 'src'
        $inputs = 'src', 'gradle', 'docs/superpowers/specs/evoking/evoking-functional-spec.md'
        $literals = 'docs/api-notes.md', '../docs/x.md', 'run/config/x.toml', 'scripts\\x.ps1', '..\\docs\\x.md', '..',
        'src/main/resources/x.json', 'docs/superpowers/specs/evoking/evoking-functional-spec.md',
        'textures/entity/vex.png', 'spellcraft:mirror', 'run', 'a/b'
        $flagged = @(Get-UncoveredJavaPaths $literals $rootNames $inputs)
        ($flagged -join '|') | Should Be 'docs/api-notes.md|../docs/x.md|run/config/x.toml|scripts\\x.ps1|..\\docs\\x.md|..'
    }
    It 'finds no path in any Java string under src that may be read from outside the build inputs' {
        # A test that reads a file is a build input: FunctionalSpecReferencesTest fails the build when the
        # functional spec changes. A new path of that kind must join $script:FingerprintInputs. Every
        # string literal is checked, not only the first argument of Path.of, Paths.get or new File, so a
        # path held in a constant, resolve("../docs/x") or new FileReader("...") is seen too.
        $repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
        $inputs = InModuleScope DeployTestEnv { $script:FingerprintInputs }
        $rootNames = @(Get-ChildItem -LiteralPath $repoRoot -Force | ForEach-Object { $_.Name })
        $texts = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'src') -Recurse -File -Filter '*.java' |
                ForEach-Object { [System.IO.File]::ReadAllText($_.FullName) })
        $texts | Should Not BeNullOrEmpty
        $firstArgument = $texts | ForEach-Object { [regex]::Matches($_, '(?:Path\.of|Paths\.get|new File)\(\s*"([^"]+)"') } |
            ForEach-Object { $_.Groups[1].Value }
        $everyLiteral = $texts | ForEach-Object { [regex]::Matches($_, '"((?:[^"\\\r\n]|\\.)*)"') } |
            ForEach-Object { $_.Groups[1].Value }
        $notInputs = @($firstArgument | Where-Object {
                $path = $_
                -not ($inputs | Where-Object { $path -ceq $_ -or $path.StartsWith($_ + '/', [StringComparison]::Ordinal) })
            })
        $found = $notInputs + @(Get-UncoveredJavaPaths @($everyLiteral) $rootNames $inputs)
        $uncovered = @($found | Sort-Object -Unique | Where-Object { $JavaPathAllowList -notcontains $_ })
        ($uncovered -join ', ') | Should Be ''
    }
}

Describe 'Get-InputWriteSignature' {
    $FunctionalSpec = 'docs\superpowers\specs\evoking\evoking-functional-spec.md'

    It 'is the same for an untouched tree' {
        $repo = New-InputRepo 'ws-same'
        Get-InputWriteSignature -RepoRoot $repo | Should Not BeNullOrEmpty
        Get-InputWriteSignature -RepoRoot $repo | Should Be (Get-InputWriteSignature -RepoRoot $repo)
    }
    It 'does not depend on the form of the root (trailing separator)' {
        $repo = New-InputRepo 'ws-root-form'
        Get-InputWriteSignature -RepoRoot ($repo + '\') | Should Be (Get-InputWriteSignature -RepoRoot $repo)
    }
    It 'changes when a file changes content' {
        $repo = New-InputRepo 'ws-edit'
        $before = Get-InputWriteSignature -RepoRoot $repo
        Set-Text $repo 'src\main\A.java' 'class A { int x; }'
        Get-InputWriteSignature -RepoRoot $repo | Should Not Be $before
    }
    It 'changes when a file is rewritten with the same content and a new write time' {
        $repo = New-InputRepo 'ws-rewrite'
        $file = Join-Path $repo 'src\main\A.java'
        (Get-Item -LiteralPath $file).LastWriteTimeUtc = $Utc20261001
        $before = Get-InputWriteSignature -RepoRoot $repo
        $content = [System.IO.File]::ReadAllBytes($file)
        [System.IO.File]::WriteAllBytes($file, $content)
        (Get-Item -LiteralPath $file).LastWriteTimeUtc = $Utc20261001.AddMinutes(7)
        Get-InputWriteSignature -RepoRoot $repo | Should Not Be $before
    }
    It 'changes when a file is added' {
        $repo = New-InputRepo 'ws-add'
        $before = Get-InputWriteSignature -RepoRoot $repo
        Set-Text $repo 'src\main\B.java' 'class B {}'
        Get-InputWriteSignature -RepoRoot $repo | Should Not Be $before
    }
    It 'changes when a file is deleted' {
        $repo = New-InputRepo 'ws-delete'
        Set-Text $repo 'src\main\B.java' 'class B {}'
        $before = Get-InputWriteSignature -RepoRoot $repo
        Remove-Item -LiteralPath (Join-Path $repo 'src\main\B.java')
        Get-InputWriteSignature -RepoRoot $repo | Should Not Be $before
    }
    It 'changes when a file moves without changing' {
        $repo = New-InputRepo 'ws-move'
        $before = Get-InputWriteSignature -RepoRoot $repo
        New-Item -ItemType Directory -Force -Path (Join-Path $repo 'src\other') | Out-Null
        Move-Item -LiteralPath (Join-Path $repo 'src\main\A.java') -Destination (Join-Path $repo 'src\other\A.java')
        Get-InputWriteSignature -RepoRoot $repo | Should Not Be $before
    }
    It 'changes when the Evoking functional spec changes' {
        $repo = New-InputRepo 'ws-spec'
        Set-Text $repo $FunctionalSpec '3.4 one'
        $before = Get-InputWriteSignature -RepoRoot $repo
        Set-Text $repo $FunctionalSpec '3.4 one 3.5 two'
        Get-InputWriteSignature -RepoRoot $repo | Should Not Be $before
    }
    It 'ignores other docs, scripts and build output' {
        $repo = New-InputRepo 'ws-ignored'
        Set-Text $repo 'scripts\tool.ps1' 'a'
        $before = Get-InputWriteSignature -RepoRoot $repo
        Set-Text $repo 'docs\notes.md' 'other notes'
        Set-Text $repo 'scripts\tool.ps1' 'b'
        Set-Text $repo 'build\classes\X.class' 'x'
        Get-InputWriteSignature -RepoRoot $repo | Should Be $before
    }
    It 'is null when none of the inputs exist' {
        $dir = Join-Path $TestDrive 'ws-no-inputs'
        New-Item -ItemType Directory -Path $dir | Out-Null
        Get-InputWriteSignature -RepoRoot $dir | Should BeNullOrEmpty
    }
    It 'is null when the root does not exist' {
        Get-InputWriteSignature -RepoRoot (Join-Path $TestDrive 'ws-missing-root') | Should BeNullOrEmpty
    }
    Context 'the listing of a directory throws' {
        Mock -ModuleName DeployTestEnv Write-Host { }
        Mock -ModuleName DeployTestEnv Get-ChildItem { throw (New-Object System.UnauthorizedAccessException 'Access to the path is denied.') }

        It 'is null, and says the sources could not be read' {
            $repo = New-InputRepo 'ws-denied-mock'
            Get-InputWriteSignature -RepoRoot $repo | Should BeNullOrEmpty
            Assert-MockCalled Write-Host -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It -ParameterFilter { "$Object" -like '*Could not read the sources*denied*' }
        }
    }
    It 'is null for a junction under src, and names it' {
        Mock -ModuleName DeployTestEnv Write-Host { }
        $repo = New-InputRepo 'ws-junction'
        $outside = Join-Path $TestDrive 'ws-junction-outside'
        Set-Text $outside 'X.java' 'class X {}'
        $link = Join-Path $repo 'src\linked'
        New-Junction $link $outside
        try {
            Get-InputWriteSignature -RepoRoot $repo | Should BeNullOrEmpty
            Assert-MockCalled Write-Host -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It -ParameterFilter { "$Object" -eq 'Could not verify the sources: src/linked is a link (junction or symlink).' }
        } finally {
            Remove-Junction $link
        }
    }
}

# Runs $Body with the given environment variables set (a $null value removes the variable), then
# restores the old values.
function Invoke-WithEnv([hashtable] $Vars, [scriptblock] $Body) {
    $old = @{}
    foreach ($name in $Vars.Keys) { $old[$name] = [Environment]::GetEnvironmentVariable($name) }
    try {
        foreach ($name in $Vars.Keys) { [Environment]::SetEnvironmentVariable($name, $Vars[$name]) }
        & $Body
    } finally {
        foreach ($name in $old.Keys) { [Environment]::SetEnvironmentVariable($name, $old[$name]) }
    }
}

Describe 'Get-GameTestSelectorSource' {
    $Clear = @{ ORG_GRADLE_PROJECT_gameTests = $null; GRADLE_OPTS = $null; JAVA_OPTS = $null; GRADLE_USER_HOME = $null }

    # A repo root and a Gradle user home, each a directory under $TestDrive, with no properties files.
    function New-Dirs([string] $Name) {
        $repo = Join-Path $TestDrive "$Name-repo"
        $home1 = Join-Path $TestDrive "$Name-home"
        New-Item -ItemType Directory -Path $repo, $home1 | Out-Null
        @{ Repo = $repo; Home = $home1 }
    }

    It 'is null when nothing is set' {
        $d = New-Dirs 'none'
        Set-Text $d.Repo 'gradle.properties' "org.gradle.jvmargs=-Xmx2g`r`nfoo=bar"
        Set-Text $d.Home 'gradle.properties' 'org.gradle.daemon=true'
        $r = Invoke-WithEnv $Clear { Get-GameTestSelectorSource -RepoRoot $d.Repo -GradleUserHome $d.Home }
        $r | Should BeNullOrEmpty
    }
    It 'is null when neither properties file exists' {
        $d = New-Dirs 'nofiles'
        $r = Invoke-WithEnv $Clear { Get-GameTestSelectorSource -RepoRoot $d.Repo -GradleUserHome $d.Home }
        $r | Should BeNullOrEmpty
    }
    It 'names the environment variable ORG_GRADLE_PROJECT_gameTests' {
        $d = New-Dirs 'env-project'
        $vars = $Clear.Clone()
        $vars.ORG_GRADLE_PROJECT_gameTests = 'spellcraft:x'
        $r = Invoke-WithEnv $vars { Get-GameTestSelectorSource -RepoRoot $d.Repo -GradleUserHome $d.Home }
        $r | Should Match 'ORG_GRADLE_PROJECT_gameTests'
    }
    foreach ($name in 'GRADLE_OPTS', 'JAVA_OPTS') {
        It "names $name when it carries -Dorg.gradle.project.gameTests" {
            $d = New-Dirs "env-$name"
            $vars = $Clear.Clone()
            $vars[$name] = '-Xmx1g -Dorg.gradle.project.gameTests=spellcraft:x -Dfoo=bar'
            $r = Invoke-WithEnv $vars { Get-GameTestSelectorSource -RepoRoot $d.Repo -GradleUserHome $d.Home }
            $r | Should Match $name
        }
        It "ignores $name without the property" {
            $d = New-Dirs "env-other-$name"
            $vars = $Clear.Clone()
            $vars[$name] = '-Xmx1g -Dorg.gradle.project.otherGameTests=1 -DgameTests=1'
            $r = Invoke-WithEnv $vars { Get-GameTestSelectorSource -RepoRoot $d.Repo -GradleUserHome $d.Home }
            $r | Should BeNullOrEmpty
        }
    }
    foreach ($line in 'gameTests=spellcraft:x', 'gameTests = spellcraft:x', 'gameTests:spellcraft:x', 'gameTests spellcraft:x',
        'org.gradle.project.gameTests=spellcraft:x', 'systemProp.org.gradle.project.gameTests=spellcraft:x', '  gameTests=x') {
        It "finds '$line' in the repo's gradle.properties and names the file" {
            $d = New-Dirs "repo-$($line.GetHashCode())"
            Set-Text $d.Repo 'gradle.properties' "a=1`r`n$line`r`nb=2"
            $r = Invoke-WithEnv $Clear { Get-GameTestSelectorSource -RepoRoot $d.Repo -GradleUserHome $d.Home }
            $r | Should Match ([regex]::Escape((Join-Path $d.Repo 'gradle.properties')))
        }
        It "finds '$line' in the Gradle user home's gradle.properties and names the file" {
            $d = New-Dirs "home-$($line.GetHashCode())"
            Set-Text $d.Home 'gradle.properties' "a=1`r`n$line`r`nb=2"
            $r = Invoke-WithEnv $Clear { Get-GameTestSelectorSource -RepoRoot $d.Repo -GradleUserHome $d.Home }
            $r | Should Match ([regex]::Escape((Join-Path $d.Home 'gradle.properties')))
        }
    }
    foreach ($line in '#gameTests=spellcraft:x', '  # gameTests=x', '!gameTests=x', 'myGameTestsFlag=1', 'gameTestsExtra=1',
        'gameTests.other=1', 'org.gradle.project.gameTestsExtra=1', 'xgameTests=1', 'a=gameTests=1') {
        It "does not take '$line' for a selector" {
            $d = New-Dirs "not-$($line.GetHashCode())"
            Set-Text $d.Repo 'gradle.properties' "a=1`r`n$line"
            Set-Text $d.Home 'gradle.properties' $line
            $r = Invoke-WithEnv $Clear { Get-GameTestSelectorSource -RepoRoot $d.Repo -GradleUserHome $d.Home }
            $r | Should BeNullOrEmpty
        }
    }
    It 'reads the Gradle user home from GRADLE_USER_HOME when none is passed' {
        $d = New-Dirs 'userhome-env'
        Set-Text $d.Home 'gradle.properties' 'gameTests=x'
        $vars = $Clear.Clone()
        $vars.GRADLE_USER_HOME = $d.Home
        $r = Invoke-WithEnv $vars { Get-GameTestSelectorSource -RepoRoot $d.Repo }
        $r | Should Match ([regex]::Escape((Join-Path $d.Home 'gradle.properties')))
    }
    It 'counts a properties file it cannot read as set, and says so' {
        $d = New-Dirs 'locked'
        Set-Text $d.Repo 'gradle.properties' 'a=1'
        $path = Join-Path $d.Repo 'gradle.properties'
        $lock = [System.IO.File]::Open($path, 'Open', 'ReadWrite', 'None')
        try {
            $r = Invoke-WithEnv $Clear { Get-GameTestSelectorSource -RepoRoot $d.Repo -GradleUserHome $d.Home }
        } finally {
            $lock.Dispose()
        }
        $r | Should Match ([regex]::Escape($path))
        $r | Should Match 'could not be read'
    }
}

Describe 'Get-VerifiedState, Save-VerifiedFingerprint and Clear-VerifiedFingerprint' {
    $Stamp = $Utc20261001

    It 'is None when nothing has been recorded' {
        Get-VerifiedState -Fingerprint 'abc' -BuildStampUtc $Stamp -VerifiedPath (Join-Path $TestDrive 'v1\verified.txt') | Should Be 'None'
    }
    It 'is None when the record is empty' {
        $path = Join-Path $TestDrive 'v2\verified.txt'
        New-Item -ItemType Directory -Path (Split-Path -Parent $path) | Out-Null
        Set-Content -LiteralPath $path -Value '' -Encoding ASCII
        Get-VerifiedState -Fingerprint 'abc' -BuildStampUtc $Stamp -VerifiedPath $path | Should Be 'None'
    }
    It 'is Current after saving the same fingerprint and build stamp, creating the directory' {
        $path = Join-Path $TestDrive 'v3\build\deploy\verified.txt'
        Save-VerifiedFingerprint -Fingerprint 'abc' -BuildStampUtc $Stamp -VerifiedPath $path
        Get-VerifiedState -Fingerprint 'abc' -BuildStampUtc $Stamp -VerifiedPath $path | Should Be 'Current'
    }
    It 'is Changed for a different fingerprint, and Current again once saved over' {
        $path = Join-Path $TestDrive 'v4\verified.txt'
        Save-VerifiedFingerprint -Fingerprint 'abc' -BuildStampUtc $Stamp -VerifiedPath $path
        Get-VerifiedState -Fingerprint 'def' -BuildStampUtc $Stamp -VerifiedPath $path | Should Be 'Changed'
        Save-VerifiedFingerprint -Fingerprint 'def' -BuildStampUtc $Stamp -VerifiedPath $path
        Get-VerifiedState -Fingerprint 'def' -BuildStampUtc $Stamp -VerifiedPath $path | Should Be 'Current'
    }
    It 'is Unknown when the fingerprint could not be computed, even with a record' {
        $path = Join-Path $TestDrive 'v5\verified.txt'
        Save-VerifiedFingerprint -Fingerprint 'abc' -BuildStampUtc $Stamp -VerifiedPath $path
        Get-VerifiedState -Fingerprint $null -BuildStampUtc $Stamp -VerifiedPath $path | Should Be 'Unknown'
    }
    It 'compares exactly, not by prefix or case' {
        $path = Join-Path $TestDrive 'v6\verified.txt'
        Save-VerifiedFingerprint -Fingerprint 'abc123' -BuildStampUtc $Stamp -VerifiedPath $path
        Get-VerifiedState -Fingerprint 'abc' -BuildStampUtc $Stamp -VerifiedPath $path | Should Be 'Changed'
        Get-VerifiedState -Fingerprint 'ABC123' -BuildStampUtc $Stamp -VerifiedPath $path | Should Be 'Changed'
    }
    It 'is ClassesDiffer when the sources match but the classes were built since' {
        $path = Join-Path $TestDrive 'v7\verified.txt'
        Save-VerifiedFingerprint -Fingerprint 'abc' -BuildStampUtc $Stamp -VerifiedPath $path
        Get-VerifiedState -Fingerprint 'abc' -BuildStampUtc $Stamp.AddTicks(1) -VerifiedPath $path | Should Be 'ClassesDiffer'
    }
    It 'is ClassesDiffer when the sources match but nothing is built' {
        $path = Join-Path $TestDrive 'v8\verified.txt'
        Save-VerifiedFingerprint -Fingerprint 'abc' -BuildStampUtc $Stamp -VerifiedPath $path
        Get-VerifiedState -Fingerprint 'abc' -BuildStampUtc $null -VerifiedPath $path | Should Be 'ClassesDiffer'
    }
    It 'is ClassesDiffer for a record without a build stamp (the first format)' {
        $path = Join-Path $TestDrive 'v9\verified.txt'
        New-Item -ItemType Directory -Path (Split-Path -Parent $path) | Out-Null
        Set-Content -LiteralPath $path -Value 'abc' -Encoding ASCII
        Get-VerifiedState -Fingerprint 'abc' -BuildStampUtc $Stamp -VerifiedPath $path | Should Be 'ClassesDiffer'
    }
    It 'is None after clearing, and clearing a missing record is quiet' {
        $path = Join-Path $TestDrive 'v10\verified.txt'
        Save-VerifiedFingerprint -Fingerprint 'abc' -BuildStampUtc $Stamp -VerifiedPath $path
        Clear-VerifiedFingerprint -VerifiedPath $path
        Get-VerifiedState -Fingerprint 'abc' -BuildStampUtc $Stamp -VerifiedPath $path | Should Be 'None'
        { Clear-VerifiedFingerprint -VerifiedPath $path } | Should Not Throw
    }

    Context 'the record is locked' {
        Mock -ModuleName DeployTestEnv Write-Host { }

        It 'is None, and says the record could not be read' {
            $path = Join-Path $TestDrive 'v11\verified.txt'
            Save-VerifiedFingerprint -Fingerprint 'abc' -BuildStampUtc $Stamp -VerifiedPath $path
            $lock = [System.IO.File]::Open($path, 'Open', 'ReadWrite', 'None')
            try {
                Get-VerifiedState -Fingerprint 'abc' -BuildStampUtc $Stamp -VerifiedPath $path | Should Be 'None'
                Assert-MockCalled Write-Host -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It -ParameterFilter { "$Object" -like '*Could not read the verified-run record*verified.txt*' }
            } finally {
                $lock.Dispose()
            }
        }
    }
}

Describe 'Invoke-DeployFlow with a verified record' {
    Mock -ModuleName DeployTestEnv Write-Host { }
    Mock -ModuleName DeployTestEnv Get-Content { 'last log line' }
    Mock -ModuleName DeployTestEnv Get-BuildStamp { [datetime]::SpecifyKind([datetime]'2026-10-01 12:00:00', 'Utc') }
    Mock -ModuleName DeployTestEnv Stop-Instance { $true }
    Mock -ModuleName DeployTestEnv Confirm-StopInstances { $true }
    Mock -ModuleName DeployTestEnv Get-Instances { }
    Mock -ModuleName DeployTestEnv Save-VerifiedFingerprint { }
    Mock -ModuleName DeployTestEnv Clear-VerifiedFingerprint { $true }
    Mock -ModuleName DeployTestEnv Get-InputFingerprint { 'fp1' }
    Mock -ModuleName DeployTestEnv Get-InputWriteSignature { 'ws1' }
    Mock -ModuleName DeployTestEnv Get-GameTestSelectorSource { $null }

    # Returns a hashtable: Calls (the steps that ran) plus the Build, RunGameTests and LaunchClient blocks.
    function New-Flow([int] $BuildExit = 0, [int] $TestsExit = 0) {
        $calls = New-Object System.Collections.ArrayList
        @{
            Calls        = $calls
            Build        = { [void]$calls.Add('build'); $BuildExit }.GetNewClosure()
            RunGameTests = { [void]$calls.Add('tests'); $TestsExit }.GetNewClosure()
            LaunchClient = { [void]$calls.Add('launch'); [pscustomobject]@{ Started = $true; ProcessId = 1; Log = 'x' } }.GetNewClosure()
        }
    }
    # Runs the flow with the fingerprint 'fp1' and the write signature 'ws1' and returns its exit code.
    function Invoke-Flow($Flow, [string] $Fingerprint = 'fp1', [switch] $NoWriteSignature, [switch] $Force) {
        $extra = @{}
        if (-not $NoWriteSignature) { $extra.WriteSignature = 'ws1' }
        Invoke-DeployFlow -RepoRoot 'C:\dev\spellcraft' -Build $Flow.Build -RunGameTests $Flow.RunGameTests `
            -LaunchClient $Flow.LaunchClient -Fingerprint $Fingerprint -VerifiedPath 'C:\v\verified.txt' -Force:$Force @extra
    }

    Context 'the sources match the last verified run' {
        Mock -ModuleName DeployTestEnv Get-VerifiedState { 'Current' }

        It 'skips the build and the GameTests and launches' {
            $f = New-Flow
            Invoke-Flow $f | Should Be 0
            ($f.Calls -join ',') | Should Be 'launch'
            Assert-MockCalled Save-VerifiedFingerprint -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
            Assert-MockCalled Clear-VerifiedFingerprint -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
        }
        It 'asks for the state of these sources and this build stamp' {
            Invoke-Flow (New-Flow) | Out-Null
            Assert-MockCalled Get-VerifiedState -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It -ParameterFilter {
                $Fingerprint -eq 'fp1' -and $VerifiedPath -eq 'C:\v\verified.txt' -and
                $BuildStampUtc -eq [datetime]::SpecifyKind([datetime]'2026-10-01 12:00:00', 'Utc')
            }
        }
        It 'says why it skipped' {
            $f = New-Flow
            Invoke-Flow $f | Out-Null
            Assert-MockCalled Write-Host -ModuleName DeployTestEnv -Scope It -ParameterFilter { "$Object" -like '*verified*' }
        }
        It 'runs everything under -Force and records the result' {
            $f = New-Flow
            Invoke-Flow $f -Force | Should Be 0
            ($f.Calls -join ',') | Should Be 'build,tests,launch'
            Assert-MockCalled Save-VerifiedFingerprint -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It -ParameterFilter { $Fingerprint -eq 'fp1' }
        }
        It 'still stops a stale client after y, then launches' {
            Mock -ModuleName DeployTestEnv Get-Instances {
                [pscustomobject]@{ Kind = 'Client'; ProcessId = 9; StartTimeUtc = [datetime]::SpecifyKind([datetime]'2026-10-01 11:00:00', 'Utc') }
            }
            $f = New-Flow
            Invoke-Flow $f | Should Be 0
            ($f.Calls -join ',') | Should Be 'launch'
            Assert-MockCalled Stop-Instance -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It -ParameterFilter { $Instance.ProcessId -eq 9 }
        }
        It 'exits 3 without launching when the stale client is not stopped' {
            Mock -ModuleName DeployTestEnv Confirm-StopInstances { $false }
            Mock -ModuleName DeployTestEnv Get-Instances {
                [pscustomobject]@{ Kind = 'Client'; ProcessId = 9; StartTimeUtc = [datetime]::SpecifyKind([datetime]'2026-10-01 11:00:00', 'Utc') }
            }
            $f = New-Flow
            Invoke-Flow $f | Should Be 3
            ($f.Calls -join ',') | Should Be ''
        }
    }

    Context 'the sources match but nothing has been built (no build stamp)' {
        Mock -ModuleName DeployTestEnv Get-VerifiedState { 'Current' }
        Mock -ModuleName DeployTestEnv Get-BuildStamp { $null }

        It 'runs everything' {
            $f = New-Flow
            Invoke-Flow $f | Should Be 0
            ($f.Calls -join ',') | Should Be 'build,tests,launch'
        }
    }
    foreach ($state in 'Changed', 'None', 'ClassesDiffer') {
        Context "the verified state is $state" {
            Mock -ModuleName DeployTestEnv Get-VerifiedState { $state }.GetNewClosure()

            It 'runs everything and records the fingerprint after the GameTests pass' {
                $f = New-Flow
                Invoke-Flow $f | Should Be 0
                ($f.Calls -join ',') | Should Be 'build,tests,launch'
                Assert-MockCalled Save-VerifiedFingerprint -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It -ParameterFilter { $Fingerprint -eq 'fp1' -and $VerifiedPath -eq 'C:\v\verified.txt' }
            }
        }
    }
    Context 'the verified state is Unknown (no fingerprint)' {
        Mock -ModuleName DeployTestEnv Get-VerifiedState { 'Unknown' }

        It 'runs everything and records nothing, and says the sources could not be read before the checks' {
            $f = New-Flow
            Invoke-Flow $f -Fingerprint '' | Should Be 0
            ($f.Calls -join ',') | Should Be 'build,tests,launch'
            Assert-MockCalled Save-VerifiedFingerprint -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
            Assert-MockCalled Write-Host -ModuleName DeployTestEnv -Scope It -ParameterFilter { "$Object" -like '*could not be read before the checks*not recorded*' }
        }
    }

    Context 'a run that does not pass' {
        Mock -ModuleName DeployTestEnv Get-VerifiedState { 'Changed' }

        It 'records nothing after a failed build, and the old record is gone' {
            Invoke-Flow (New-Flow -BuildExit 1) | Should Be 1
            Assert-MockCalled Save-VerifiedFingerprint -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
            Assert-MockCalled Clear-VerifiedFingerprint -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It -ParameterFilter { $VerifiedPath -eq 'C:\v\verified.txt' }
        }
        It 'records nothing after failed GameTests' {
            Invoke-Flow (New-Flow -TestsExit 1) | Should Be 2
            Assert-MockCalled Save-VerifiedFingerprint -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
        }
        It 'records nothing when a GameTest server was not stopped' {
            Mock -ModuleName DeployTestEnv Confirm-StopInstances { $false }
            Mock -ModuleName DeployTestEnv Get-Instances {
                [pscustomobject]@{ Kind = 'GameTestServer'; ProcessId = 8; StartTimeUtc = [datetime]::SpecifyKind([datetime]'2026-10-01 13:00:00', 'Utc') }
            }
            Invoke-Flow (New-Flow) | Should Be 3
            Assert-MockCalled Save-VerifiedFingerprint -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
        }
    }

    # Pester 3.4 keeps a Mock made in an It for the rest of its Context, so each of these has its own.
    Context 'the old record cannot be cleared' {
        Mock -ModuleName DeployTestEnv Get-VerifiedState { 'Changed' }
        Mock -ModuleName DeployTestEnv Clear-VerifiedFingerprint { $false }

        It 'exits 1 without building' {
            $f = New-Flow
            Invoke-Flow $f | Should Be 1
            ($f.Calls -join ',') | Should Be ''
        }
    }
    Context 'the sources changed while the checks ran' {
        Mock -ModuleName DeployTestEnv Get-VerifiedState { 'Changed' }
        Mock -ModuleName DeployTestEnv Get-InputFingerprint { 'fp2' }

        It 'launches but records nothing' {
            $f = New-Flow
            Invoke-Flow $f | Should Be 0
            ($f.Calls -join ',') | Should Be 'build,tests,launch'
            Assert-MockCalled Save-VerifiedFingerprint -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
        }
    }
    Context 'the sources cannot be read after the checks' {
        Mock -ModuleName DeployTestEnv Get-VerifiedState { 'Changed' }
        Mock -ModuleName DeployTestEnv Get-InputFingerprint { $null }

        It 'launches but records nothing' {
            Invoke-Flow (New-Flow) | Should Be 0
            Assert-MockCalled Save-VerifiedFingerprint -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
        }
    }
    Context 'a source file was written while the checks ran (the write signature changed)' {
        Mock -ModuleName DeployTestEnv Get-VerifiedState { 'Changed' }
        Mock -ModuleName DeployTestEnv Get-InputWriteSignature { 'ws2' }

        It 'launches but records nothing, and says why' {
            $f = New-Flow
            Invoke-Flow $f | Should Be 0
            ($f.Calls -join ',') | Should Be 'build,tests,launch'
            Assert-MockCalled Save-VerifiedFingerprint -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
            Assert-MockCalled Write-Host -ModuleName DeployTestEnv -Scope It -ParameterFilter { "$Object" -like '*written while the checks ran*not recorded*' }
        }
    }
    Context 'the write signature cannot be read after the checks' {
        Mock -ModuleName DeployTestEnv Get-VerifiedState { 'Changed' }
        Mock -ModuleName DeployTestEnv Get-InputWriteSignature { $null }

        It 'launches but records nothing' {
            Invoke-Flow (New-Flow) | Should Be 0
            Assert-MockCalled Save-VerifiedFingerprint -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
        }
    }
    Context 'no write signature is given' {
        Mock -ModuleName DeployTestEnv Get-VerifiedState { 'Changed' }

        It 'launches but records nothing, and says the sources could not be read before the checks' {
            $f = New-Flow
            Invoke-Flow $f -NoWriteSignature | Should Be 0
            ($f.Calls -join ',') | Should Be 'build,tests,launch'
            Assert-MockCalled Save-VerifiedFingerprint -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
            Assert-MockCalled Write-Host -ModuleName DeployTestEnv -Scope It -ParameterFilter { "$Object" -like '*could not be read before the checks*not recorded*' }
        }
    }
    Context 'a GameTest selector is set outside the command line' {
        Mock -ModuleName DeployTestEnv Get-VerifiedState { 'Changed' }
        Mock -ModuleName DeployTestEnv Get-GameTestSelectorSource { 'the environment variable ORG_GRADLE_PROJECT_gameTests' }

        It 'launches but records nothing, and says where the selector is set' {
            $f = New-Flow
            Invoke-Flow $f | Should Be 0
            ($f.Calls -join ',') | Should Be 'build,tests,launch'
            Assert-MockCalled Save-VerifiedFingerprint -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
            Assert-MockCalled Write-Host -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It -ParameterFilter {
                "$Object" -eq 'A GameTest selector is set in the environment variable ORG_GRADLE_PROJECT_gameTests, so only some GameTests ran. This run is not recorded; the next run redoes the checks.'
            }
        }
        It 'asks about this repo root' {
            Invoke-Flow (New-Flow) | Out-Null
            Assert-MockCalled Get-GameTestSelectorSource -ModuleName DeployTestEnv -Times 1 -Exactly -Scope It -ParameterFilter { $RepoRoot -eq 'C:\dev\spellcraft' }
        }
    }
    Context 'no compiled classes are found after the checks' {
        Mock -ModuleName DeployTestEnv Get-VerifiedState { 'Changed' }
        Mock -ModuleName DeployTestEnv Get-BuildStamp { $null }

        It 'launches but records nothing, and says why' {
            Invoke-Flow (New-Flow) | Should Be 0
            Assert-MockCalled Save-VerifiedFingerprint -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
            Assert-MockCalled Write-Host -ModuleName DeployTestEnv -Scope It -ParameterFilter { "$Object" -like '*no compiled classes*not recorded*' }
        }
    }

    Context 'no verified path is given (the feature is off)' {
        It 'runs everything and records nothing' {
            Mock -ModuleName DeployTestEnv Get-VerifiedState { 'Current' }
            $f = New-Flow
            Invoke-DeployFlow -RepoRoot 'C:\dev\spellcraft' -Build $f.Build -RunGameTests $f.RunGameTests -LaunchClient $f.LaunchClient | Should Be 0
            ($f.Calls -join ',') | Should Be 'build,tests,launch'
            Assert-MockCalled Save-VerifiedFingerprint -ModuleName DeployTestEnv -Times 0 -Exactly -Scope It
        }
    }
}

Describe 'Invoke-DeployFlow with a real record, real sources and real class files' {
    Mock -ModuleName DeployTestEnv Write-Host { }
    Mock -ModuleName DeployTestEnv Get-Instances { }
    Mock -ModuleName DeployTestEnv Get-GameTestSelectorSource { $null }

    # Runs one deploy run and returns "<exit code>:<steps that ran>". The write signature is taken first,
    # then the fingerprint, as the script does.
    function Invoke-Run([string] $Repo, [int] $BuildExit = 0, [int] $TestsExit = 0, [scriptblock] $DuringTests = { }, [switch] $Force) {
        $calls = New-Object System.Collections.ArrayList
        $writeSignature = Get-InputWriteSignature -RepoRoot $Repo
        $code = Invoke-DeployFlow -RepoRoot $Repo `
            -Build { [void]$calls.Add('build'); $BuildExit }.GetNewClosure() `
            -RunGameTests { [void]$calls.Add('tests'); & $DuringTests; $TestsExit }.GetNewClosure() `
            -LaunchClient { [void]$calls.Add('launch'); [pscustomobject]@{ Started = $true; ProcessId = 1; Log = 'x' } }.GetNewClosure() `
            -WriteSignature $writeSignature -Fingerprint (Get-InputFingerprint -RepoRoot $Repo) `
            -VerifiedPath (Join-Path $Repo 'build\deploy\verified.txt') -Force:$Force
        "$code`:" + ($calls -join ',')
    }

    It 'runs everything once, then only launches while nothing changes' {
        $repo = New-InputRepo 'round-trip' -Built
        Invoke-Run $repo | Should Be '0:build,tests,launch'
        Invoke-Run $repo | Should Be '0:launch'
        Invoke-Run $repo | Should Be '0:launch'
    }
    It 'runs everything again after a source edit, and skips again after that passes' {
        $repo = New-InputRepo 'edit' -Built
        Invoke-Run $repo | Out-Null
        Set-Text $repo 'src\main\A.java' 'class A { int x; }'
        Invoke-Run $repo | Should Be '0:build,tests,launch'
        Invoke-Run $repo | Should Be '0:launch'
    }
    It 'runs everything again when the classes were rebuilt since the verified run' {
        $repo = New-InputRepo 'rebuilt' -Built
        Invoke-Run $repo | Out-Null
        Set-Text $repo 'build\classes\java\main\B.class' 'b'
        (Get-Item -LiteralPath (Join-Path $repo 'build\classes\java\main\B.class')).LastWriteTimeUtc = [datetime]::UtcNow.AddMinutes(5)
        Invoke-Run $repo | Should Be '0:build,tests,launch'
    }
    It 'forgets the record when a forced run fails the build, so the next run does not skip' {
        $repo = New-InputRepo 'forced-build-fail' -Built
        Invoke-Run $repo | Out-Null
        Invoke-Run $repo -Force -BuildExit 1 | Should Be '1:build'
        Invoke-Run $repo | Should Be '0:build,tests,launch'
    }
    It 'forgets the record when a forced run fails the GameTests, so the next run does not skip' {
        $repo = New-InputRepo 'forced-tests-fail' -Built
        Invoke-Run $repo | Out-Null
        Invoke-Run $repo -Force -TestsExit 1 | Should Be '2:build,tests'
        Invoke-Run $repo | Should Be '0:build,tests,launch'
    }
    It 'does not record a run whose sources were edited while the checks ran, even after the edit is reverted' {
        $repo = New-InputRepo 'edit-during' -Built
        $file = Join-Path $repo 'src\main\A.java'
        Invoke-Run $repo -DuringTests { Set-Content -LiteralPath $file -Value 'class A { int y; }' -Encoding ASCII }.GetNewClosure() |
            Should Be '0:build,tests,launch'
        Set-Content -LiteralPath $file -Value 'class A {}' -Encoding ASCII
        Invoke-Run $repo | Should Be '0:build,tests,launch'
    }
    It 'does not record a run whose sources were edited and reverted while the checks ran' {
        $repo = New-InputRepo 'edit-revert-during' -Built
        $file = Join-Path $repo 'src\main\A.java'
        $later = $Utc20261001.AddDays(30)
        $during = {
            Set-Content -LiteralPath $file -Value 'class A { int y; }' -Encoding ASCII
            Set-Content -LiteralPath $file -Value 'class A {}' -Encoding ASCII
            (Get-Item -LiteralPath $file).LastWriteTimeUtc = $later
        }.GetNewClosure()
        Invoke-Run $repo -DuringTests $during | Should Be '0:build,tests,launch'
        Invoke-Run $repo | Should Be '0:build,tests,launch'
        Invoke-Run $repo | Should Be '0:launch'
    }
}

Describe 'Format-VerifiedLine' {
    It 'says what the next run will do for each state' {
        Format-VerifiedLine -State 'Current' | Should Match 'skips'
        Format-VerifiedLine -State 'Changed' | Should Match 'changed'
        Format-VerifiedLine -State 'None' | Should Match 'no passing run'
        Format-VerifiedLine -State 'Unknown' | Should Match 'cannot tell'
        Format-VerifiedLine -State 'ClassesDiffer' | Should Match 'classes'
    }
    It 'promises a skip only for Current' {
        foreach ($state in 'Changed', 'None', 'Unknown', 'ClassesDiffer') {
            Format-VerifiedLine -State $state | Should Not Match 'skips'
        }
    }
}

Describe 'Deploy-TestEnv.ps1 -CheckOnly (the real script on this checkout; read-only)' {
    It 'exits 0 and prints the verified line' {
        $script = Join-Path $PSScriptRoot '..\Deploy-TestEnv.ps1'
        $out = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $script -CheckOnly
        $LASTEXITCODE | Should Be 0
        (@($out) -join "`n") | Should Match 'Verified: '
    }
}
