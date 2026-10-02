# Pester 3.4 (built into Windows PowerShell 5.1). Run:
#   powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Pester -Path scripts/tests -EnableExit"
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
