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

function New-Instance([string] $Kind, [datetime] $StartTimeUtc, [int] $ProcessId = 1) {
    [pscustomobject]@{ Kind = $Kind; ProcessId = $ProcessId; StartTimeUtc = $StartTimeUtc }
}

$Stamp = [datetime]::SpecifyKind([datetime]'2026-10-01 12:00:00', 'Utc')
$Before = $Stamp.AddMinutes(-5)
$After = $Stamp.AddMinutes(5)

Describe 'Get-Instances' {
    # Mock bodies run in the module's scope and cannot see this file's variables or functions, so the
    # real-shaped command lines (doubled backslashes in the at tokens) are built inline with .Replace.
    Mock -ModuleName DeployTestEnv Get-CimInstance {
        $java = '"C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe"'
        $client = '@' + 'C:\dev\spellcraft\build\moddev\clientRunVmArgs.txt'.Replace('\', '\')
        $gametest = '@' + 'C:\dev\spellcraft\build\moddev\gameTestServerRunVmArgs.txt'.Replace('\', '\')
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
