$ErrorActionPreference = "Stop"
$temp = Join-Path ([IO.Path]::GetTempPath()) ("ae2ct-codexvm-smoke-" + [guid]::NewGuid().ToString("N"))
$source = Join-Path $temp "source"
$stage = Join-Path $temp "stage"
$scripts = Join-Path $source "scripts"
New-Item -ItemType Directory -Path $scripts -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $source 'bundle') -Force | Out-Null
'{"loader":"1.20.1-47.4.23"}' | Set-Content (Join-Path $source 'bundle/profile.json')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "run-ui-smoke-codexvm.ps1") -Destination $scripts
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'get-java-home.ps1') -Destination $scripts
[IO.File]::WriteAllText((Join-Path $scripts "run-ui-smoke.ps1"), @'
param([string]$CasesBase64,[string]$BundleDirectory, [string]$PreparedLaunch, [string]$Target, [string]$ReportDirectory, [string]$Scenario, [string[]]$ProjectId, [switch]$Latest, [switch]$Interactive, [switch]$ScheduledJava, [string]$InteractiveUser, [int]$StartupTimeoutSeconds, [int]$CallbackTimeoutSeconds, [int]$CheckpointTimeoutSeconds, [string]$HeadSha)
New-Item -ItemType Directory -Path $ReportDirectory -Force | Out-Null
[ordered]@{ casesBase64=$CasesBase64; preparedLaunch=$PreparedLaunch; target=$Target; scenario=$Scenario; projectId=@($ProjectId); latest=$Latest.IsPresent; interactive=$Interactive.IsPresent; javaHome=$env:JAVA_HOME; scheduledJava=$ScheduledJava.IsPresent; interactiveUser=$InteractiveUser; localReport=$ReportDirectory; startupTimeoutSeconds=$StartupTimeoutSeconds; callbackTimeoutSeconds=$CallbackTimeoutSeconds; checkpointTimeoutSeconds=$CheckpointTimeoutSeconds; headSha=$HeadSha } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $ReportDirectory "wrapper-result.json") -Encoding UTF8
if ($env:AE2CT_UI_SMOKE_TEST_INNER_EXIT) {
    [ordered]@{ phase='failed'; exitCode=[int]$env:AE2CT_UI_SMOKE_TEST_INNER_EXIT; message='fixture failure' } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $ReportDirectory 'status.json') -Encoding UTF8
    exit ([int]$env:AE2CT_UI_SMOKE_TEST_INNER_EXIT)
}
'@, [Text.UTF8Encoding]::new($false))

try {
    $head = ('1' * 40) -join ''
    & (Join-Path $scripts "run-ui-smoke-codexvm.ps1") -HeadSha $head -BundleDirectory (Join-Path $source "bundle") -LocalRoot $stage -Latest -Interactive `
        -Scenario aeinfinitybooster-terminal -ProjectId VQhDBNs8
    $resultPath = Join-Path $source "build\ui-smoke\1.20.1-forge\latest\aeinfinitybooster-terminal\wrapper-result.json"
    $result = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
    if (-not $result.latest -or -not $result.interactive -or $result.scenario -ne "aeinfinitybooster-terminal" -or
            @($result.projectId).Count -ne 1 -or $result.projectId[0] -ne "VQhDBNs8") {
        throw "CodexVM wrapper dropped smoke arguments"
    }

    $prepared = Join-Path $temp 'prepared'
    $versioned = Join-Path $prepared '1.20.1-forge/1.20.1-47.4.23/launch.json'
    New-Item -ItemType Directory -Path (Split-Path -Parent $versioned) -Force | Out-Null
    '{}' | Set-Content $versioned
    & (Join-Path $scripts 'run-ui-smoke-codexvm.ps1') -HeadSha $head -BundleDirectory (Join-Path $source 'bundle') -LocalRoot $stage -PreparedLaunchRoot $prepared -Latest -Scenario aeinfinitybooster-terminal
    $result = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
    if ($result.preparedLaunch -ne $versioned) { throw 'Resolved loader did not select its versioned manifest' }
    Remove-Item -LiteralPath $versioned
    & (Join-Path $scripts 'run-ui-smoke-codexvm.ps1') -HeadSha $head -BundleDirectory (Join-Path $source 'bundle') -LocalRoot $stage -PreparedLaunchRoot $prepared -Latest -Scenario aeinfinitybooster-terminal
    $result = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
    if ($result.preparedLaunch -ne (Join-Path $prepared '1.20.1-forge/launch.json')) { throw 'Existing default manifest route changed' }
    '{"loader":"../escape"}' | Set-Content (Join-Path $source 'bundle/profile.json')
    try {
        & (Join-Path $scripts 'run-ui-smoke-codexvm.ps1') -HeadSha $head -BundleDirectory (Join-Path $source 'bundle') -LocalRoot $stage
        throw 'Accepted loader path traversal'
    } catch { if ($_.Exception.Message -ne 'Invalid prepared loader version') { throw } }
    '{"loader":"1.20.1-47.4.23"}' | Set-Content (Join-Path $source 'bundle/profile.json')
    $cacheMarker = Join-Path $stage "build\cache-marker.txt"
    New-Item -ItemType Directory -Path (Split-Path -Parent $cacheMarker) -Force | Out-Null
    Set-Content -LiteralPath $cacheMarker -Value "keep"
    $preservedMarkers=@('reports/retained-ledger.txt','runtime/server/retained-world.txt','connected-bundle-retained/identity.txt')|ForEach-Object {
        $marker=Join-Path $stage $_;New-Item -ItemType Directory -Path (Split-Path $marker) -Force|Out-Null
        Set-Content -LiteralPath $marker -Value 'preserve';$marker
    }
    & (Join-Path $scripts "run-ui-smoke-codexvm.ps1") -HeadSha $head -BundleDirectory (Join-Path $source "bundle") -LocalRoot $stage
    foreach($marker in $preservedMarkers){if(!(Test-Path -LiteralPath $marker) -or (Get-Content -LiteralPath $marker) -ne 'preserve'){throw 'Staging mirror destroyed runtime or report evidence'}}
    if (-not (Test-Path -LiteralPath $cacheMarker)) { throw "Stable staging discarded the guest build cache" }
    if (-not (Test-Path -LiteralPath (Join-Path $source "build\ui-smoke\1.20.1-forge\compatible\craft-plan\wrapper-result.json"))) {
        throw "Default scenario report was not separated"
    }
    & (Join-Path $scripts "run-ui-smoke-codexvm.ps1") -HeadSha $head -BundleDirectory (Join-Path $source "bundle") -LocalRoot $stage -Scenario suite
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('["waiting-status","delayed-status"]'))
    & (Join-Path $scripts 'run-ui-smoke-codexvm.ps1') -HeadSha $head -BundleDirectory (Join-Path $source 'bundle') -LocalRoot $stage -Scenario suite -CasesBase64 $encoded
    $suiteResult = Get-Content (Join-Path $source 'build\ui-smoke\1.20.1-forge\compatible\suite\wrapper-result.json') -Raw | ConvertFrom-Json
    if ($suiteResult.scenario -ne 'suite' -or $suiteResult.casesBase64 -cne $encoded) { throw 'Wrapper dropped suite selection' }
    & (Join-Path $scripts "run-ui-smoke-codexvm.ps1") -HeadSha $head -BundleDirectory (Join-Path $source "bundle") -LocalRoot $stage -Target 1.20.1-fabric -Scenario suite
    $fabricResult = Get-Content (Join-Path $source 'build\ui-smoke\1.20.1-fabric\compatible\suite\wrapper-result.json') -Raw | ConvertFrom-Json
    if ($fabricResult.target -ne '1.20.1-fabric' -or $fabricResult.scenario -ne 'suite') { throw 'Wrapper dropped Fabric target' }
    & (Join-Path $scripts "run-ui-smoke-codexvm.ps1") -HeadSha $head -BundleDirectory (Join-Path $source "bundle") -LocalRoot $stage -Target 1.21.1-neoforge -Scenario suite
    $neoResult = Get-Content (Join-Path $source 'build\ui-smoke\1.21.1-neoforge\compatible\suite\wrapper-result.json') -Raw | ConvertFrom-Json
    if ($neoResult.target -ne '1.21.1-neoforge' -or $neoResult.scenario -ne 'suite' -or $neoResult.javaHome -notmatch '21') {
        throw 'Wrapper dropped NeoForge target or JDK 21'
    }
    $previousJava = $env:JAVA_HOME_21
    $env:JAVA_HOME_21 = $fabricResult.javaHome
    try {
        & (Join-Path $scripts "run-ui-smoke-codexvm.ps1") -HeadSha $head -BundleDirectory (Join-Path $source "bundle") -LocalRoot $stage -Target 1.21.1-neoforge
        throw 'Accepted Java 17 for NeoForge'
    } catch {
        if ($_.Exception.Message -notlike '*requires JDK 21*') { throw }
    } finally { $env:JAVA_HOME_21 = $previousJava }
    & (Join-Path $scripts "run-ui-smoke-codexvm.ps1") -HeadSha $head -BundleDirectory (Join-Path $source "bundle") -LocalRoot $stage -Target 26.1.2-neoforge -Scenario suite
    $neoResult = Get-Content (Join-Path $source 'build\ui-smoke\26.1.2-neoforge\compatible\suite\wrapper-result.json') -Raw | ConvertFrom-Json
    if ($neoResult.target -ne '26.1.2-neoforge' -or $neoResult.scenario -ne 'suite' -or $neoResult.javaHome -notmatch '25') {
        throw 'Wrapper dropped NeoForge target or JDK 25'
    }
    $previousJava = $env:JAVA_HOME_25
    $env:JAVA_HOME_25 = $fabricResult.javaHome
    try {
        & (Join-Path $scripts "run-ui-smoke-codexvm.ps1") -HeadSha $head -BundleDirectory (Join-Path $source "bundle") -LocalRoot $stage -Target 26.1.2-neoforge
        throw 'Accepted Java 17 for NeoForge'
    } catch {
        if ($_.Exception.Message -notlike '*requires JDK 25*') { throw }
    } finally { $env:JAVA_HOME_25 = $previousJava }
    & (Join-Path $scripts "run-ui-smoke-codexvm.ps1") -HeadSha $head -BundleDirectory (Join-Path $source "bundle") -LocalRoot $stage -Scenario no-space-status
    $focused = Get-Content (Join-Path $source 'build/ui-smoke/1.20.1-forge/compatible/no-space-status/wrapper-result.json') -Raw | ConvertFrom-Json
    if ($focused.scenario -ne 'no-space-status') { throw 'Wrapper dropped no-space scenario' }
    & (Join-Path $scripts "run-ui-smoke-codexvm.ps1") -HeadSha $head -BundleDirectory (Join-Path $source "bundle") -LocalRoot $stage -Scenario no-provider-status
    $focused = Get-Content (Join-Path $source 'build/ui-smoke/1.20.1-forge/compatible/no-provider-status/wrapper-result.json') -Raw | ConvertFrom-Json
    if ($focused.scenario -ne 'no-provider-status') { throw 'Wrapper dropped no-provider scenario' }
    function New-ScheduledTaskAction { throw 'CodexVM wrapper created a redundant scheduled PowerShell task' }
    function Register-ScheduledTask { throw 'CodexVM wrapper registered a redundant scheduled PowerShell task' }
    function Set-ScheduledTask { throw 'CodexVM wrapper updated a redundant scheduled PowerShell task' }
    function Start-ScheduledTask { throw 'CodexVM wrapper started a redundant scheduled PowerShell task' }
    & (Join-Path $scripts 'run-ui-smoke-codexvm.ps1') -HeadSha $head -BundleDirectory (Join-Path $source 'bundle') -LocalRoot $stage -Scheduled -InteractiveUser Codex
    $scheduled = Get-Content (Join-Path $source 'build/ui-smoke/1.20.1-forge/compatible/craft-plan/wrapper-result.json') -Raw | ConvertFrom-Json
    if (!$scheduled.scheduledJava -or $scheduled.interactiveUser -ne 'Codex') {
        throw 'CodexVM runner did not delegate direct scheduled-Java ownership to the main smoke runner'
    }
    if (!$scheduled.localReport.StartsWith($stage, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'CodexVM runner wrote live Java evidence through the shared-folder boundary'
    }
    if ($scheduled.startupTimeoutSeconds -ne 300 -or $scheduled.callbackTimeoutSeconds -ne 20 -or
            $scheduled.checkpointTimeoutSeconds -ne 60 -or $scheduled.headSha -ne $head) {
        throw 'CodexVM runner dropped watchdog or immutable-head parameters'
    }
    $previousInnerExit = $env:AE2CT_UI_SMOKE_TEST_INNER_EXIT
    $env:AE2CT_UI_SMOKE_TEST_INNER_EXIT = '7'
    try {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $scripts 'run-ui-smoke-codexvm.ps1') `
            -HeadSha $head -BundleDirectory (Join-Path $source 'bundle') -LocalRoot $stage -Scenario no-space-status
        if ($LASTEXITCODE -ne 7) { throw "CodexVM runner changed inner exit code: $LASTEXITCODE" }
        $failedStatus = Get-Content -LiteralPath (Join-Path $source 'build/ui-smoke/1.20.1-forge/compatible/no-space-status/status.json') -Raw | ConvertFrom-Json
        if ($failedStatus.phase -ne 'failed' -or $failedStatus.exitCode -ne 7) {
            throw 'CodexVM runner did not retain the failing inner report'
        }
    } finally {
        $env:AE2CT_UI_SMOKE_TEST_INNER_EXIT = $previousInnerExit
    }
    [IO.File]::WriteAllText((Join-Path $scripts 'run-connected-dedicated-ui-smoke.ps1'), @'
param([string]$ReportDirectory)
New-Item -ItemType Directory -Path $ReportDirectory -Force | Out-Null
@{localReport=$ReportDirectory} | ConvertTo-Json | Set-Content (Join-Path $ReportDirectory 'wrapper-result.json')
'@, [Text.UTF8Encoding]::new($false))
    $connectedDestination = Join-Path $source 'build/connected-path-check'
    $connectedPaths = @()
    foreach ($launch in 1..2) {
        & (Join-Path $scripts 'run-ui-smoke-codexvm.ps1') -HeadSha $head -LocalRoot $stage `
            -Scenario crafting-suspension -ServerDirectory (Join-Path $temp 'sealed-source') `
            -BundleDirectory (Join-Path $source 'bundle') -ReportDirectory $connectedDestination -AcceptMinecraftEula
        $result = Get-Content (Join-Path $connectedDestination 'wrapper-result.json') -Raw | ConvertFrom-Json
        $connectedPaths += $result.localReport
        if ((Split-Path $result.localReport) -ne (Join-Path $stage 'runtime') -or
                (Split-Path $result.localReport -Leaf) -cnotmatch '^c-[a-f0-9]{32}$') {
            throw 'Connected runtime path leaves insufficient space for nested Forge libraries'
        }
    }
    if ($connectedPaths[0] -eq $connectedPaths[1] -or !(Test-Path $connectedPaths[0])) {
        throw 'Connected launch reused or discarded an earlier runtime report'
    }
    # Connected Stop resolves the stable campaign record, not a new random report.
    $connectedReport=Join-Path $stage 'reports/1.20.1-forge/compatible/delayed-resource-icons-connected-existing'
    $attempt=Join-Path $connectedReport 'client-attempt-1'
    New-Item -ItemType Directory -Path $attempt -Force | Out-Null
    $connectedStatusPath=Join-Path (Split-Path $connectedReport) 'delayed-resource-icons-connected-status.json'
    $script:stopStarted=[DateTime]::UtcNow.AddMinutes(-1)
    $script:stopScript=Join-Path $scripts 'run-ui-smoke-codexvm.ps1'
    $serverDirectory=Join-Path $temp 'sealed-source'
    $connectedStatus=[ordered]@{phase='running';pid=41230;commandScript=$script:stopScript;report=$connectedReport
        processStartedAt=$script:stopStarted.ToString('o');headSha=$head;serverDirectory=$serverDirectory}
    $connectedStatus|ConvertTo-Json|Set-Content -LiteralPath $connectedStatusPath
    $ledgerHash=(Get-FileHash -LiteralPath $connectedStatusPath).Hash
    $runtimeMarker=Join-Path $connectedReport 'runtime-server/level.dat'
    New-Item -ItemType Directory -Path (Split-Path $runtimeMarker) -Force|Out-Null
    Set-Content -LiteralPath $runtimeMarker -Value 'owned running world'
    function robocopy.exe { throw 'Active campaign reached staging mirror' }
    function Copy-Item { throw 'Active campaign reached bundle copy' }
    try {
        & (Join-Path $scripts 'run-ui-smoke-codexvm.ps1') -HeadSha $head -LocalRoot $stage `
            -Scenario delayed-resource-icons -ServerDirectory $serverDirectory -BundleDirectory (Join-Path $source 'bundle') -AcceptMinecraftEula
        throw 'Second connected launch accepted active campaign'
    } catch { if($_.Exception.Message -ne 'A connected campaign status is still active; stop or resolve it before starting another'){throw} }
    finally { Remove-Item Function:robocopy.exe;Remove-Item Function:Copy-Item }
    if((Get-FileHash -LiteralPath $connectedStatusPath).Hash -ne $ledgerHash -or (Get-Content -LiteralPath $runtimeMarker) -ne 'owned running world') {
        throw 'Second connected launch changed the active ledger or runtime'
    }
    @{phase='running';pid=41231;argumentFile='owned-client.args';processStartedAt=$script:stopStarted.ToString('o')}|
        ConvertTo-Json|Set-Content -LiteralPath (Join-Path $attempt 'status.json')
    $script:killed=@()
    function Get-CimInstance {
        param($ClassName,$Filter)
        [pscustomobject]@{CommandLine=$(if($Filter -like '*41231'){'java @owned-client.args'}else{$stopScript});CreationDate=$stopStarted}
    }
    $script:killed=[Collections.Generic.List[string]]::new()
    function taskkill.exe { $killed.Add(($args -join ' '));$global:LASTEXITCODE=0 }
    & (Join-Path $scripts 'run-ui-smoke-codexvm.ps1') -Stop -HeadSha $head -LocalRoot $stage `
        -Scenario delayed-resource-icons -ServerDirectory $serverDirectory
    if ($script:killed.Count -ne 2 -or $script:killed[0] -notlike '*41231*' -or $script:killed[1] -notlike '*41230*') {
        throw 'Connected Stop did not stop the recorded scheduled client and wrapper/server tree'
    }
    if ((Get-Content -LiteralPath $connectedStatusPath -Raw|ConvertFrom-Json).phase -ne 'stopped') { throw 'Connected Stop left an active campaign record' }
    $connectedStatus.phase='running';$connectedStatus.processStartedAt=$script:stopStarted.AddHours(-1).ToString('o')
    $connectedStatus|ConvertTo-Json|Set-Content -LiteralPath $connectedStatusPath
    try {
        & (Join-Path $scripts 'run-ui-smoke-codexvm.ps1') -Stop -HeadSha $head -LocalRoot $stage `
            -Scenario delayed-resource-icons -ServerDirectory $serverDirectory
        throw 'Connected Stop accepted reused PID'
    } catch { if ($_.Exception.Message -notlike '*does not match the recorded UI-smoke command*') { throw } }
    if ($script:killed.Count -ne 2) { throw 'Stale campaign stop killed a process' }
    try {
        & (Join-Path $scripts 'run-ui-smoke-codexvm.ps1') -Stop -HeadSha ('2'*40) -LocalRoot $stage `
            -Scenario delayed-resource-icons -ServerDirectory $serverDirectory
        throw 'Connected Stop accepted different campaign head'
    } catch { if ($_.Exception.Message -ne 'Connected UI-smoke stop identity does not match the requested campaign') { throw } }
    Write-Host "run-ui-smoke-codexvm checks passed"
} finally {
    if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
}
