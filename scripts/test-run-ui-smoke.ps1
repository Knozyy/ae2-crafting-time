$ErrorActionPreference = "Stop"
$temp = Join-Path ([IO.Path]::GetTempPath()) ("ae2ct ui smoke " + [guid]::NewGuid().ToString("N"))
$scripts = Join-Path $temp "scripts"
$source = Join-Path $temp "versions\1.20.1-forge\run\saves\ae2-crafting-time"
New-Item -ItemType Directory -Path $scripts, $source -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "run-ui-smoke.ps1") -Destination (Join-Path $scripts "run-ui-smoke.ps1")
$runnerAst = [System.Management.Automation.Language.Parser]::ParseFile(
    (Join-Path $scripts 'run-ui-smoke.ps1'), [ref]$null, [ref]$null)
$timeoutAssignments = @($runnerAst.FindAll({ param($node)
    $node -is [System.Management.Automation.Language.AssignmentStatementAst] -and
        $node.Left.Extent.Text -ceq '$timeout'
}, $true))
if ($timeoutAssignments.Count -ne 1) { throw 'Smoke runner timeout selection was not unique' }
$selectTimeout = [scriptblock]::Create($timeoutAssignments[0].Extent.Text + "`n" + '$timeout.TotalMinutes')
foreach ($case in @(
        @{ scenario='crafting-suspension'; interactive=$false; minutes=15 },
        @{ scenario='craft-plan'; interactive=$false; minutes=8 },
        @{ scenario='suite'; interactive=$false; minutes=40 },
        @{ scenario='crafting-suspension'; interactive=$true; minutes=30 }
    )) {
    $Scenario = $case.scenario; $Interactive = $case.interactive
    if ((& $selectTimeout) -ne $case.minutes) {
        throw "Unexpected smoke timeout for $Scenario interactive=$Interactive"
    }
}
$progressGates = @($runnerAst.FindAll({ param($node)
    $node -is [System.Management.Automation.Language.IfStatementAst] -and
        $node.Clauses[0].Item1.Extent.Text -match '\$Scenario -in' -and
        $node.Extent.Text -match 'Get-UiSmokeProgressDecision'
}, $true))
if ($progressGates.Count -ne 1 -or
        $progressGates[0].Extent.Text -cnotmatch '-CallbackTimeoutSeconds\s+\$CallbackTimeoutSeconds' -or
        $progressGates[0].Extent.Text -cnotmatch '-CheckpointTimeoutSeconds\s+\$CheckpointTimeoutSeconds') {
    throw 'Smoke progress watchdog was not bound to its configured callback/checkpoint limits'
}
$selectWatchdog = [scriptblock]::Create($progressGates[0].Clauses[0].Item1.Extent.Text)
$selectedCases = @()
foreach ($case in @(
        @{ scenario='crafting-suspension'; expected=$true },
        @{ scenario='cpu-list-total-ttc'; expected=$true },
        @{ scenario='craft-plan'; expected=$false }
    )) {
    $Scenario = $case.scenario
    if ((& $selectWatchdog) -ne $case.expected) {
        throw "Unexpected smoke progress watchdog selection for $Scenario"
    }
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "prepare-ui-smoke-suite.ps1"), (Join-Path $PSScriptRoot "ui-smoke-forge-suite.json"), (Join-Path $PSScriptRoot "ui-smoke-fabric-suite.json"), (Join-Path $PSScriptRoot "ui-smoke-neoforge-suite.json"), (Join-Path $PSScriptRoot "ui-smoke-neoforge-26.1.2-suite.json") -Destination $scripts
foreach ($file in @('expand-ui-smoke-groups.ps1','release-matrix.json','ui-smoke-coverage.json','ui-smoke-groups.json',
        'prepare-ui-smoke-resume.ps1','ui-smoke-dependency-identity.ps1','ui-smoke-progress.ps1',
        'ui-smoke-scheduled-java.ps1','resource-fixture-contract.ps1')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination $scripts
}
# This synthetic launcher exercises the single-process runner contract. The
# native two-process CPU-list and status-amount contracts have separate checks.
$fixtureGroupsPath = Join-Path $scripts 'ui-smoke-groups.json'
$fixtureGroups = Get-Content -LiteralPath $fixtureGroupsPath -Raw | ConvertFrom-Json
$fixtureGroups.groups.'standard-ae2' = @($fixtureGroups.groups.'standard-ae2' |
    Where-Object { $_ -notin @('cpu-list-total-ttc', 'standard-status-controls') })
$fixtureGroups | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $fixtureGroupsPath -Encoding UTF8
[IO.File]::WriteAllText((Join-Path $temp "gradle.properties"), "modVersion=1.1.0`n", [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $source ".ae2-crafting-time-test-fixture.json"), @'
{"schema":1,"scenario":"craft-plan","sourceFixtureId":"ae2-crafting-time","disposableWorldId":"SOURCE_ONLY",
 "terminal":{"x":1,"y":2,"z":3,"face":"SOUTH"},"outputId":"minecraft:furnace"}
'@, [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $source "level.dat"), "fixture", [Text.UTF8Encoding]::new($false))
$neoFixture = Join-Path $temp "versions\1.21.1-neoforge\run\saves\ae2-crafting-time"
New-Item -ItemType Directory -Path (Split-Path $neoFixture) -Force | Out-Null
Copy-Item $source $neoFixture -Recurse
$newFixture = Join-Path $temp "versions\26.1.2-neoforge\run\saves\ae2-crafting-time"
New-Item -ItemType Directory -Path (Split-Path $newFixture) -Force | Out-Null
Copy-Item $source $newFixture -Recurse
$fabricFixture = Join-Path $temp 'versions/1.20.1-fabric/run/saves/ae2-crafting-time'
New-Item -ItemType Directory -Path $fabricFixture -Force | Out-Null
Set-Content -LiteralPath (Join-Path $fabricFixture 'level.dat') -Value 'native Fabric metadata'
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'copy-ui-smoke-fixture.ps1') -Destination $scripts
[IO.File]::WriteAllText((Join-Path $scripts "run-client.ps1"), @'
param(
    [string]$Target, [string]$RuntimeDirectory, [string]$DriverScenario,
    [string]$DriverOutputDirectory, [string]$DriverWorld, [switch]$Latest,
    [string[]]$ProjectId,
    [switch]$Interactive, [Parameter(ValueFromRemainingArguments = $true)][string[]]$Rest
)
if ([IO.Path]::GetFullPath((Get-Location).Path) -ne [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))) { exit 8 }
$optionsPath = Join-Path $RuntimeDirectory 'options.txt'
switch ($env:AE2CT_UI_SMOKE_TEST_MODE) {
    'fixed-scale' { (Get-Content $optionsPath) -replace '^guiScale:0$', 'guiScale:2' | Set-Content $optionsPath }
    'missing-scale' { Set-Content $optionsPath @(Get-Content $optionsPath | Where-Object { $_ -cnotmatch '^guiScale:' }) }
    'duplicate-scale' { Add-Content $optionsPath ([Environment]::NewLine + 'guiScale:0') }
    'malformed-scale' { (Get-Content $optionsPath) -replace '^guiScale:0$', 'guiScale:auto' | Set-Content $optionsPath }
}
$options = Get-Content $optionsPath
if (($options -join [Environment]::NewLine) -notmatch '(?m)^onboardAccessibility:false\r?$') { exit 9 }
if (@($options | Where-Object { $_ -ceq 'guiScale:0' }).Count -ne 1 -or @($options | Where-Object { $_ -cmatch '^guiScale:' }).Count -ne 1) { exit 11 }
if ($Target -eq '1.20.1-fabric' -and (Get-Content (Join-Path $RuntimeDirectory "saves/$DriverWorld/level.dat") -Raw).Trim() -ne 'native Fabric metadata') { exit 10 }
$profile = if ($Latest) { "latest" } else { "compatible" }
$loader = $Target.Split("-", 2)[1]
$modsDirectory = if ($Target -eq "1.20.1-forge") { "resolved-mods" } else { "mods" }
$driver = "ae2-crafting-time-1.1.0-$loader-$($Target.Split("-")[0])-test-driver.jar"
New-Item -ItemType Directory -Path $DriverOutputDirectory, (Join-Path $RuntimeDirectory $modsDirectory),
    (Join-Path $RuntimeDirectory "logs") -Force | Out-Null
if ($Target -like '*-neoforge') {
    $fml = Join-Path $RuntimeDirectory 'config/fml.toml'
    New-Item -ItemType Directory -Path (Split-Path $fml) -Force | Out-Null
    if ($env:AE2CT_UI_SMOKE_TEST_MODE -eq 'missing-fml') { Remove-Item -LiteralPath $fml -Force -ErrorAction SilentlyContinue }
    else { Set-Content -LiteralPath $fml -Value 'earlyWindowSquir = false' }
}
if ($DriverScenario -eq "suite") {
    $plan = Get-Content (Join-Path $DriverOutputDirectory 'suite-plan.json') -Raw | ConvertFrom-Json
    $cases = foreach ($case in $plan.cases) {
        & $PSCommandPath -Target $Target -RuntimeDirectory $RuntimeDirectory -DriverScenario $case.scenario `
            -DriverOutputDirectory (Join-Path $DriverOutputDirectory $case.scenario) -DriverWorld $case.world -Latest:$Latest
        [ordered]@{scenario=$case.scenario; world=$case.world; result='PASS'; startedAt='2026-09-02T00:00:00Z'; finishedAt='2026-09-02T00:00:01Z'}
    }
    $summary = [ordered]@{schema=1;complete=$true;result='PASS';processId=$PID;cases=@($cases)}
    switch ($env:AE2CT_UI_SMOKE_TEST_MODE) {
        'suite-missing' { $summary.cases = @($cases | Select-Object -Skip 1) }
        'suite-order' { $summary.cases[0].scenario = 'wrong-cpu' }
        'suite-fail' { $summary.result = 'FAIL'; $summary.complete = $false }
        'suite-world' { $summary.cases[0].world = 'wrong-world' }
    }
    $summary | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $DriverOutputDirectory 'result.json') -Encoding UTF8
    return
}
$contracts = (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ui-smoke-groups.json') -Raw | ConvertFrom-Json).cases
$checks = if ($contracts.$DriverScenario) {
    $values = [ordered]@{}
    foreach ($key in $contracts.$DriverScenario.checks) { $values[$key] = $true }
    $values
} elseif ($DriverScenario -eq "no-space-status") {
    [ordered]@{ screen=$true; "external-machine"=$true; warning=$true; tooltip=$true; layout=$true; recovered=$true }
} elseif ($DriverScenario -eq "no-power-status") {
    [ordered]@{ 'screen'=$true; 'real-job'=$true; 'external-unpowered'=$true; 'active-network'=$true; 'mixed-row'=$true; 'tooltip'=$true; 'layout'=$true; 'power-restored'=$true; 'cancelled'=$true; 'inactive-cpu'=$true }
} elseif ($DriverScenario -eq "no-provider-status") {
    [ordered]@{ screen=$true; 'real-job'=$true; 'mixed-row'=$true; 'pattern-removed'=$true; tooltip=$true;
        layout=$true; 'pattern-restored'=$true; 'second-provider'=$true;
        'provider-removed'=$true; 'provider-restored'=$true; cancelled=$true }
} elseif ($DriverScenario -eq "crafting-tree-read-recovery") {
    [ordered]@{ screen=$true; 'host-content'=$true; 'overlay-absent'=$true; layout=$true;
        tooltip=$true; 'details-ignored'=$true; 'reset-ignored'=$true }
} elseif ($DriverScenario -eq "merequester-read-recovery") {
    [ordered]@{ screen=$true; 'host-content'=$true; 'overlay-absent'=$true; layout=$true }
} elseif ($DriverScenario -eq "crafting-tree-screen") {
    [ordered]@{ screen=$true; 'node-ttc'=$true; tooltip=$true; layout=$true; details=$true; reset=$true }
} elseif ($DriverScenario -eq "ae2networkanalyser-screen") {
    [ordered]@{ screen=$true; layout=$true }
} elseif ($DriverScenario -eq "merequester-screen") {
    [ordered]@{ screen=$true; 'ttc-row'=$true; 'total-ttc'=$true; layout=$true }
} elseif ($DriverScenario -eq "aeinfinitybooster-terminal") {
    [ordered]@{ screen=$true; 'plan-ttc'=$true }
} elseif ($DriverScenario -like "*-terminal") {
    [ordered]@{ screen=$true; 'ttc-tooltip'=$true; 'plan-ttc'=$true }
} elseif ($DriverScenario -ne "craft-plan") {
    [ordered]@{ 'cpu-selected'=$true; 'job-accepted'=$true; 'dispatch-amount'=$true; 'returned-amount'=$true; 'job-finished'=$true; 'profile-sample'=$true; 'ttc-after-sample'=$true }
} else {
    [ordered]@{ screen=$true; 'ttc-row'=$true; 'total-ttc'=$true; 'sort-cycle'=$true; tooltip=$true; layout=$true }
}
if ($env:AE2CT_UI_SMOKE_TEST_MODE -eq 'reordered-checks') {
    $reordered = [ordered]@{}
    foreach ($key in @($checks.Keys | Sort-Object -Descending)) { $reordered[$key] = $checks[$key] }
    $checks = $reordered
}
switch ($env:AE2CT_UI_SMOKE_TEST_MODE) {
    'missing-check' { $checks.Remove('screen') }
    'extra-check' { $checks['unexpected'] = $true }
    'wrong-check-case' { $checks.Remove('screen'); $checks['SCREEN'] = $true }
}
$screenshots = if ($contracts.$DriverScenario) {
    @($contracts.$DriverScenario.screenshots)
} elseif ($DriverScenario -eq "no-space-status") {
    @("no-space-before.png", "no-space-en-us.png", "no-space-recovered.png")
} elseif ($DriverScenario -eq "no-power-status") {
    @("no-power-external-unpowered.png", "no-power-en-us.png", "no-power-restored.png", "no-power-inactive.png")
} elseif ($DriverScenario -eq "no-provider-status") {
    @("no-provider-before.png", "no-provider-en-us.png", "no-provider-pattern-restored.png", "no-provider-redundant.png", "no-provider-block-removed.png",
        "no-provider-block-restored.png", "no-provider-cancelled.png")
} elseif ($DriverScenario -eq "crafting-tree-screen") {
    @("crafting-tree-screen.png", "crafting-tree-tooltip.png", "crafting-tree-details.png", "crafting-tree-reset.png")
} elseif ($DriverScenario -eq "crafting-tree-read-recovery") {
    @("read-recovery-screen.png", "read-recovery-tooltip.png", "read-recovery-details.png", "read-recovery-reset.png")
} elseif ($DriverScenario -eq "merequester-read-recovery") {
    @("read-recovery-screen.png")
} elseif ($DriverScenario -eq "ae2networkanalyser-screen") {
    @("ae2networkanalyser-screen.png")
} elseif ($DriverScenario -eq "merequester-screen") {
    @("merequester-screen.png")
} elseif ($DriverScenario -like "*-terminal") {
    $prefix = $DriverScenario -replace '-terminal$', ''
    @("$prefix-terminal.png", "$prefix-plan.png")
} elseif ($DriverScenario -ne "craft-plan") { @("$(($DriverScenario -replace '-cpu$', ''))-profiled-plan.png") } else { @("craft-plan.png", "craft-plan-sort-1.png", "craft-plan-sort-2.png", "craft-plan-sort-3.png", "craft-plan-tooltip.png") }
$result = [ordered]@{
    schema = $(if ($env:AE2CT_UI_SMOKE_TEST_MODE -eq "schema") { 2 } else { 1 })
    complete = $true; driver = $driver; target = $(if ($env:AE2CT_UI_SMOKE_TEST_MODE -eq "wrong-target") { "wrong" } else { $Target }); profile = $profile
    scenario = $DriverScenario; result = "PASS"; checks = $checks; language = $(if ($env:AE2CT_UI_SMOKE_TEST_MODE -eq "wrong-language") { "uk_ua" } else { "en_us" }); adapters = @{}
    screenshots = $screenshots
}
$result | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $DriverOutputDirectory "result.json") -Encoding UTF8
foreach ($screenshot in $screenshots) {
    if ($env:AE2CT_UI_SMOKE_TEST_MODE -ne "missing-screenshot" -or $screenshot -ne @($screenshots)[-1]) {
        Set-Content -LiteralPath (Join-Path $DriverOutputDirectory $screenshot) -Value "png"
        $snapshot = @{screen='fixture-screen';screenWidth=100;screenHeight=100;guiScale=2;gui=@{x=0;y=0;width=100;height=100}}
        switch ($env:AE2CT_UI_SMOKE_TEST_MODE) {
            'boolean-scale' { $snapshot.guiScale = $true }
            'invalid-dimensions' { $snapshot.screenWidth = 0 }
            'uncontained-gui' { $snapshot.gui.x = 1 }
        }
        $snapshot | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $DriverOutputDirectory $screenshot.Replace('.png','.json'))
    }
}
if ($Interactive -and $env:AE2CT_UI_SMOKE_TEST_MODE -eq "interactive-token" -and
        $env:AE2CT_TEST_DRIVER_TOKEN -ne ('b' * 64)) { exit 7 }
(@($driver) + @($ProjectId | ForEach-Object { "$_.jar" })) | ConvertTo-Json |
    Set-Content -LiteralPath (Join-Path $RuntimeDirectory "$modsDirectory\.ae2-crafting-time-run-mods.json") -Encoding UTF8
Set-Content -LiteralPath (Join-Path $RuntimeDirectory "logs\latest.log") -Value $(if ($env:AE2CT_UI_SMOKE_TEST_MODE -eq "fatal") { "Mixin apply failed ae2craftingtime.mixins.json" } elseif ($env:AE2CT_UI_SMOKE_TEST_MODE -eq "production-refmap") { "Reference map 'ae2craftingtime.refmap.json' could not be read" } else { "clean" })
'@, [Text.UTF8Encoding]::new($false))

function Invoke-Case([string]$mode, [switch]$Latest, [switch]$Interactive,
        [string]$Target = "1.20.1-forge", [string]$Scenario = "craft-plan", [string[]]$ProjectId,
        [string]$ReportDirectory, [string]$RuntimeDirectory, [bool]$shouldPass) {
    $env:AE2CT_UI_SMOKE_TEST_MODE = $mode
    $arguments = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $scripts "run-ui-smoke.ps1"),
        "-HeadSha", ('1' * 40))
    $arguments += @("-Target", $Target)
    if ($Latest) { $arguments += "-Latest" }
    if ($Interactive) { $arguments += "-Interactive" }
    if ($Scenario -ne "craft-plan") { $arguments += @("-Scenario", $Scenario) }
    if ($ProjectId) { $arguments += @("-ProjectId") + $ProjectId }
    if (-not $ReportDirectory) {
        $profile = if ($Latest) { 'latest' } else { 'compatible' }
        $ReportDirectory = Join-Path $temp "build/ui-smoke/$Target/$profile/$Scenario"
    }
    $arguments += @("-ReportDirectory", $ReportDirectory)
    if ($RuntimeDirectory) { $arguments += @('-RuntimeDirectory', $RuntimeDirectory) }
    $preference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & powershell.exe @arguments 2>&1
        $passed = $LASTEXITCODE -eq 0
    } finally { $ErrorActionPreference = $preference }
    if ($passed -ne $shouldPass) {
        throw "Unexpected runner result for '$mode' latest=$Latest`: $($output -join [Environment]::NewLine)"
    }
}

try {
    Invoke-Case "pass" -Target "1.20.1-fabric" -Scenario suite -shouldPass $true
    Invoke-Case "pass" -Target "1.20.1-fabric" -Latest -shouldPass $true
    Invoke-Case "wrong-target" -Target "1.20.1-fabric" -shouldPass $false
    Invoke-Case "pass" -Target "26.1.2-neoforge" -Scenario suite -shouldPass $true
    Invoke-Case "pass" -Target "26.1.2-neoforge" -Latest -shouldPass $true
    Invoke-Case "wrong-target" -Target "26.1.2-neoforge" -shouldPass $false
    Invoke-Case "missing-screenshot" -Target "26.1.2-neoforge" -Scenario suite -shouldPass $false
    Invoke-Case "pass" -Target "1.21.1-neoforge" -Scenario suite -shouldPass $true
    Invoke-Case "pass" -Target "1.21.1-neoforge" -Latest -shouldPass $true
    $neoEvidence = Join-Path $temp 'build/ui-smoke/1.21.1-neoforge/latest/craft-plan/evidence'
    if ((Get-Content (Join-Path $neoEvidence 'fml-postlaunch.toml') -Raw) -notmatch 'earlyWindowSquir = false') {
        throw 'Post-launch FML config evidence was not retained'
    }
    Invoke-Case "missing-fml" -Target "1.21.1-neoforge" -Latest -shouldPass $true
    if (-not (Test-Path -LiteralPath (Join-Path $neoEvidence 'fml-postlaunch.absent')) -or
            (Test-Path -LiteralPath (Join-Path $neoEvidence 'fml-postlaunch.toml'))) { throw 'Missing post-launch FML config was not recorded' }
    Invoke-Case "wrong-target" -Target "1.21.1-neoforge" -shouldPass $false
    Invoke-Case "missing-screenshot" -Target "1.21.1-neoforge" -Scenario suite -shouldPass $false
    Invoke-Case "pass" -Target "unsupported" -shouldPass $false
    Invoke-Case 'reordered-checks' -Scenario no-target-status -shouldPass $true
    foreach ($mode in @('missing-check', 'extra-check', 'wrong-check-case')) {
        Invoke-Case $mode -Scenario no-target-status -shouldPass $false
    }
    Invoke-Case "pass" -shouldPass $true
    $externalRuntimeReport = Join-Path $temp 'external-runtime-report'
    $guestLocalRuntime = Join-Path $externalRuntimeReport 'runtime'
    Invoke-Case 'pass' -ReportDirectory $externalRuntimeReport `
        -RuntimeDirectory $guestLocalRuntime -shouldPass $true
    if (!(Test-Path -LiteralPath (Join-Path $guestLocalRuntime 'options.txt') -PathType Leaf)) {
        throw 'Explicit UI-smoke runtime did not stay in the selected guest-local directory'
    }
    Invoke-Case 'pass' -ReportDirectory (Join-Path $temp 'bounded-runtime-report') `
        -RuntimeDirectory (Join-Path $temp 'unowned-runtime') -shouldPass $false
    foreach ($mode in @('fixed-scale','missing-scale','duplicate-scale','malformed-scale')) {
        Invoke-Case $mode -shouldPass $false
    }
    foreach ($mode in @('boolean-scale','invalid-dimensions','uncontained-gui')) {
        Invoke-Case $mode -Scenario 'standard-plan-controls' -shouldPass $false
    }
    Invoke-Case 'boolean-scale' -Scenario 'no-space-status' -shouldPass $false
    $cacheMarker = Join-Path $temp "build\ui-smoke\1.20.1-forge\compatible\runtime\cache-marker.txt"
    Set-Content -LiteralPath $cacheMarker -Value "keep"
    Invoke-Case "pass" -shouldPass $true
    if (-not (Test-Path -LiteralPath $cacheMarker)) { throw "Smoke runtime cache was discarded" }
    $status = Get-Content -LiteralPath (Join-Path $temp "build\ui-smoke\1.20.1-forge\compatible\craft-plan\status.json") -Raw | ConvertFrom-Json
    if ($status.phase -ne "passed" -or -not $status.pid -or $status.target -ne "1.20.1-forge") {
        throw "Smoke status omitted the final phase, PID, or target"
    }
    $externalReport = Join-Path $temp "shared-report"
    Invoke-Case "pass" -ReportDirectory $externalReport -shouldPass $true
    if (-not (Test-Path -LiteralPath (Join-Path $externalReport "evidence\result.json"))) {
        throw "External smoke report omitted copied evidence"
    }
    Invoke-Case "pass" -Scenario suite -shouldPass $true
    $suiteSaves = Join-Path $temp 'build\ui-smoke\1.20.1-forge\compatible\runtime\saves'
    if (@(Get-ChildItem $suiteSaves -Directory).Count) { throw 'Suite left disposable worlds behind' }
    $hashes = Get-Content (Join-Path $temp 'build/ui-smoke/1.20.1-forge/compatible/suite/evidence/fixture-hashes.json') -Raw | ConvertFrom-Json
    if (!$hashes.unchanged -or $hashes.disposableWorlds.Count -ne 1 -or @($hashes.disposableWorlds | Where-Object { !$_.removed }).Count) {
        throw 'Suite omitted source integrity or disposable-world removal evidence'
    }
    foreach ($mode in @('suite-missing', 'suite-order', 'suite-fail', 'suite-world', 'schema', 'missing-screenshot', 'fatal')) {
        Invoke-Case $mode -Scenario suite -shouldPass $false
    }
    Invoke-Case "pass" -Scenario suite -Latest -shouldPass $true
    Invoke-Case "pass" -Scenario suite -Interactive -shouldPass $false
    Invoke-Case "pass" -Scenario suite -ProjectId E6BFl96N -shouldPass $false
    Invoke-Case "pass" -Latest -shouldPass $true
    Invoke-Case "pass" -Scenario "no-space-status" -shouldPass $true
    Invoke-Case "missing-screenshot" -Scenario "no-space-status" -shouldPass $false
    Invoke-Case "pass" -Scenario "no-power-status" -shouldPass $true
    Invoke-Case "missing-screenshot" -Scenario "no-power-status" -shouldPass $false
    Invoke-Case "pass" -Scenario "no-provider-status" -shouldPass $true
    Invoke-Case "missing-screenshot" -Scenario "no-provider-status" -shouldPass $false
    foreach ($scenario in @('no-channel-status','no-target-status','input-blocked-status','locked-status')) {
        Invoke-Case "pass" -Scenario $scenario -shouldPass $true
        Invoke-Case "missing-screenshot" -Scenario $scenario -shouldPass $false
    }
    Invoke-Case "pass" -Scenario "neoeco-cpu" -shouldPass $true
    Invoke-Case "pass" -Scenario "crafting-tree-screen" -shouldPass $true
    Invoke-Case "missing-screenshot" -Scenario "crafting-tree-screen" -shouldPass $false
    Invoke-Case "pass" -Scenario "merequester-screen" -ProjectId E6BFl96N -shouldPass $true
    Invoke-Case "pass" -Scenario "merequester-read-recovery" -ProjectId E6BFl96N -shouldPass $true
    Invoke-Case "missing-screenshot" -Scenario "merequester-read-recovery" -ProjectId E6BFl96N -shouldPass $false
    Invoke-Case "pass" -Scenario "crafting-tree-read-recovery" -shouldPass $true
    Invoke-Case "missing-screenshot" -Scenario "crafting-tree-read-recovery" -shouldPass $false
    Invoke-Case "pass" -Scenario "ae2importexportcard-terminal" -ProjectId qelfSMnn -shouldPass $true
    Invoke-Case "pass" -Scenario "ae2networkanalyser-screen" -ProjectId 961856 -shouldPass $true
    Invoke-Case "pass" -Scenario "aeinfinitybooster-terminal" -ProjectId VQhDBNs8 -shouldPass $true
    if (-not (Test-Path -LiteralPath $cacheMarker)) { throw "Scenario switch discarded the shared runtime" }
    Invoke-Case "pass" -Scenario "ae2wtlib-terminal" -ProjectId pNabrMMw -shouldPass $true
    $focused = Get-Content -LiteralPath (Join-Path $temp "build\ui-smoke\1.20.1-forge\compatible\ae2wtlib-terminal\evidence\resolved-mods.json") -Raw | ConvertFrom-Json
    if ("pNabrMMw.jar" -notin $focused) { throw "Smoke runner dropped the focused project" }
    Invoke-Case "pass" -Scenario "future-addon-cpu" -shouldPass $true
    if (-not (Test-Path -LiteralPath (Join-Path $temp "build\ui-smoke\1.20.1-forge\compatible\craft-plan\evidence\result.json")) -or
            -not (Test-Path -LiteralPath (Join-Path $temp "build\ui-smoke\1.20.1-forge\compatible\neoeco-cpu\evidence\result.json")) -or
            -not (Test-Path -LiteralPath (Join-Path $temp "build\ui-smoke\1.20.1-forge\latest\craft-plan\evidence\result.json"))) {
        throw "Compatible and latest evidence was not separated"
    }
    $lock = [IO.File]::Open((Join-Path ([IO.Path]::GetTempPath()) "ae2-crafting-time-smoke-client.lock"), "Open", "ReadWrite", "None")
    try { Invoke-Case "pass" -shouldPass $false } finally { $lock.Dispose() }
    Invoke-Case "pass" -Interactive -shouldPass $true
    $env:AE2CT_TEST_DRIVER_TOKEN = 'b' * 64
    Invoke-Case "interactive-token" -Interactive -shouldPass $true
    $env:AE2CT_TEST_DRIVER_TOKEN = 'invalid'
    Invoke-Case "pass" -Interactive -shouldPass $false
    Remove-Item Env:\AE2CT_TEST_DRIVER_TOKEN
    Invoke-Case "wrong-language" -shouldPass $false
    Invoke-Case "schema" -shouldPass $false
    Invoke-Case "missing-screenshot" -shouldPass $false
    Invoke-Case "production-refmap" -Target "1.20.1-fabric" -shouldPass $false
    Invoke-Case "fatal" -shouldPass $false
    $failedStatus = Get-Content -LiteralPath (Join-Path $temp "build\ui-smoke\1.20.1-forge\compatible\craft-plan\status.json") -Raw | ConvertFrom-Json
    if ($failedStatus.phase -ne "failed" -or -not $failedStatus.message) { throw "Smoke failure status was incomplete" }

    foreach ($leaf in @('standard-plan-controls','waiting-status','running-status','delayed-status','craft-lifecycle')) {
        Invoke-Case "pass" -Scenario $leaf -shouldPass $true
    }
    Invoke-Case "pass" -Scenario standard-status-controls -shouldPass $false
    $statusFont = Get-Content -LiteralPath (Join-Path $temp 'build/ui-smoke/1.20.1-forge/compatible/runtime/resourcepacks/ae2ct-status-wide/assets/minecraft/font/default.json') -Raw | ConvertFrom-Json
    if ($statusFont.providers[0].type -cne 'bitmap' -or $statusFont.providers[0].height -ne 9 -or
            $statusFont.providers[0].chars[3] -cne ('0123456789' + [string]::new([char]0, 6)) -or
            $statusFont.providers[0].chars[2][15] -cne '/' -or
            $statusFont.providers[1].id -cne 'minecraft:uniform') {
        throw 'Status font fixture does not widen the actual amount glyphs over the uniform fallback'
    }
    Invoke-Case "pass" -Scenario cpu-list-total-ttc -shouldPass $false
    Invoke-Case "pass" -Scenario crafting-suspension -shouldPass $true
    Invoke-Case "missing-screenshot" -Scenario crafting-suspension -shouldPass $false
    Invoke-Case "pass" -Scenario crafting-suspension -Target 1.20.1-fabric -shouldPass $false
    Invoke-Case "pass" -Scenario standard-ae2 -shouldPass $true
    Invoke-Case "missing-screenshot" -Scenario standard-ae2 -shouldPass $false
    $failedManifest = Join-Path $temp 'build/ui-smoke/1.20.1-forge/compatible/standard-ae2/evidence/resolved-mods.json'
    if (-not (Test-Path -LiteralPath $failedManifest)) { throw 'Failure omitted the resolved manifest' }
    $markerPath = Join-Path $source ".ae2-crafting-time-test-fixture.json"
    $marker = Get-Content -LiteralPath $markerPath -Raw | ConvertFrom-Json
    $marker.disposableWorldId = "ae2-crafting-time"
    $marker | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $markerPath -Encoding UTF8
    Invoke-Case "pass" -shouldPass $false
    Write-Host "run-ui-smoke checks passed"
} finally {
    Remove-Item Env:\AE2CT_UI_SMOKE_TEST_MODE -ErrorAction SilentlyContinue
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $resolved = [IO.Path]::GetFullPath($temp)
    if ($resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -and (Test-Path -LiteralPath $resolved)) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
