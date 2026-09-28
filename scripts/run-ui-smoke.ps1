param(
    [ValidateSet("1.20.1-forge", "1.20.1-fabric", "1.21.1-neoforge", "26.1.2-neoforge")][string]$Target,
    [switch]$Changed,
    [string]$BaseRef = 'origin/master',
    [switch]$PlanOnly,
    [string]$CasesBase64,
    [switch]$Latest,
    [switch]$Interactive,
    [ValidatePattern("^(suite|standard-ae2|provider-dispatch-statuses|recurrent-plan|stored-variant-plan|delayed-resource-icons|appmek-resource-icons|chance-output-status|standard-plan-controls|badge-background|standard-status-controls|waiting-status|running-status|delayed-status|craft-lifecycle|cpu-list-total-ttc|craft-plan|no-space-status|no-provider-status|no-power-status|no-channel-status|no-target-status|input-blocked-status|locked-status|crafting-tree-screen|merequester-screen|crafting-tree-read-recovery|merequester-read-recovery|ae2networkanalyser-screen|aeinfinitybooster-terminal|ae2importexportcard-terminal|ae2(?:wcwt|wtlib)-terminal|[a-z0-9]+(?:-[a-z0-9]+)*-cpu)$")][string]$Scenario = "craft-plan",
    [string[]]$ProjectId,
    [string]$ArchiveRoot,
    [string]$ReportDirectory,
    [string]$RuntimeDirectory,
    [string]$BundleDirectory,
    [string]$PreparedLaunch,
    [string]$DedicatedAddress,
    [string]$ControlDirectory,
    [string]$CampaignId,
    [string]$ResourceFixtureId,
    [ValidateSet('alpha')][string]$Role,
    [string]$OfflineName,
    [ValidatePattern('^[a-f0-9]{32}$')][string]$OfflineUuid,
    [string]$HeadSha,
    [string]$ResumeBundleDirectory,
    [switch]$CaptureResumeOnly,
    [switch]$PrepareOnly,
    [switch]$ScheduledJava,
    [string]$InteractiveUser = 'Codex',
    [int]$CallbackTimeoutSeconds = 20,
    [int]$CheckpointTimeoutSeconds = 60,
    [int]$StartupTimeoutSeconds = 300,
    [switch]$FailOnInitialDisconnect,
    [switch]$ResourceFixtureOnly,
    [switch]$Prewarm,
    [long]$PrewarmDeadline,
    [string]$PrewarmBundle,
    [int]$PrewarmServerProcessId,
    [DateTime]$PrewarmServerStartedAt
)

function Test-UiSnapshotBounds($snapshot) {
    if ($null -eq $snapshot.guiScale -or $snapshot.guiScale -is [bool] -or $snapshot.guiScale -is [string]) { return $false }
    try { $scale = [double]$snapshot.guiScale } catch { return $false }
    if ([double]::IsNaN($scale) -or $scale -eq [double]::PositiveInfinity -or $scale -eq [double]::NegativeInfinity -or $scale -le 0) { return $false }
    $values = @($snapshot.screenWidth, $snapshot.screenHeight, $snapshot.gui.x,
        $snapshot.gui.y, $snapshot.gui.width, $snapshot.gui.height)
    foreach ($value in $values) {
        if ($null -eq $value -or $value -is [bool] -or $value -is [string]) { return $false }
        try { $number = [double]$value } catch { return $false }
        if ($number -ne [math]::Truncate($number) -or $number -lt [int]::MinValue -or $number -gt [int]::MaxValue) { return $false }
    }
    $screenWidth, $screenHeight, $x, $y, $width, $height = $values | ForEach-Object { [long][double]$_ }
    return $screenWidth -gt 0 -and $screenHeight -gt 0 -and $x -ge 0 -and $y -ge 0 -and
        $width -gt 0 -and $height -gt 0 -and $x + $width -le $screenWidth -and $y + $height -le $screenHeight
}

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot 'resource-fixture-contract.ps1')
if ($Prewarm) { . (Join-Path $PSScriptRoot 'resource-prewarm-contract.ps1') }
$resourceScenario = $Scenario -in @('delayed-resource-icons','appmek-resource-icons')
if ($Prewarm -and (!$resourceScenario -or !$DedicatedAddress -or !$PreparedLaunch -or $PrewarmDeadline -le 0 -or
        $PrewarmServerProcessId -le 0 -or $PrewarmBundle -cnotmatch '^[a-fA-F0-9]{64}$')) { throw 'Incomplete connected prewarm launch' }
if ($ResourceFixtureOnly -and !$resourceScenario -and $Scenario -ne 'suite') {
    throw 'ResourceFixtureOnly requires a resource fixture scenario'
}
function Get-ResourceFixtureCases([string]$CaseScenario, [string]$CaseTarget) {
    if ($CaseScenario -eq 'appmek-resource-icons') { return @('OXYGEN','HYDROGEN','CHEMICAL_OVERLAP') }
    return @('ITEM','WATER','LAVA') + $(if ($CaseTarget -eq '1.20.1-forge') { @('BUCKETLESS') } else { @() }) + @('FLUID_OVERLAP')
}
function Get-ResourceFixtureScreenshots([string[]]$FixtureCases, [bool]$Connected, [bool]$Production = $false) {
    $values = foreach ($fixtureCase in $FixtureCases) {
        $prefix = $fixtureCase.ToLowerInvariant().Replace('_','-')
        $checkpoints = @('held') + $(if ($Production -and $fixtureCase -ceq 'WATER') { @('resource-reloaded','chunk-reloaded') }) +
            $(if ($Connected) { @('rejoined') } else { @() }) +
            $(if ($fixtureCase.EndsWith('OVERLAP')) { @('winner-promoted') } else { @() }) +
            @('completed','cancel-held') +
            $(if ($Production -and $fixtureCase.EndsWith('OVERLAP')) { @('provider-removed') }) + @('cancelled')
        foreach ($checkpoint in $checkpoints) { "$prefix-$checkpoint.png" }
    }
    $values += $FixtureCases[-1].ToLowerInvariant().Replace('_','-') + '-cleanup.png'
    return @($values)
}
if ($Scenario -eq 'appmek-resource-icons' -and $Target -notin @('1.20.1-forge','1.21.1-neoforge')) {
    throw 'AppMek resource fixtures are supported only on Forge 1.20.1 and NeoForge 1.21.1'
}
if (-not $PreparedLaunch -and -not $ReportDirectory) {
    if ($CasesBase64) { throw 'Case-list transport is internal to native execution' }
    if ($BundleDirectory) { throw 'A native bundle requires its prepared launch manifest' }
    $campaign = @{ Latest = $Latest; ProjectId = $ProjectId; Target = $Target; Interactive = $Interactive
        StartupTimeoutSeconds = $StartupTimeoutSeconds }
    if ($PSBoundParameters.ContainsKey('Scenario')) { $campaign.Scenario = $Scenario }
    if ($ArchiveRoot) { $campaign.ArchiveRoot = $ArchiveRoot }
    $campaign.Changed = $Changed; $campaign.BaseRef = $BaseRef; $campaign.PlanOnly = $PlanOnly
    if ($ResourceFixtureOnly) { $campaign.ResourceFixtureOnly = $true }
    & (Join-Path $PSScriptRoot 'run-ui-smoke-matrix.ps1') @campaign
    exit $LASTEXITCODE
}
if ($Changed -or $PlanOnly) { throw 'Planning is host-only' }
if ($CasesBase64) {
    $requestedCases = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($CasesBase64)) | ConvertFrom-Json
    $selectedCases = @(& (Join-Path $PSScriptRoot 'expand-ui-smoke-groups.ps1') -Target $Target -Scenarios $requestedCases)
    if (Compare-Object $requestedCases $selectedCases -SyncWindow 0) { throw 'Internal case list must be flat and unique' }
    if ($Scenario -ne 'suite' -and ($selectedCases.Count -ne 1 -or $selectedCases[0] -cne $Scenario)) { throw 'Case list does not match launch scenario' }
}
if ($Scenario -eq 'standard-ae2') {
    if ($Interactive -or $ProjectId) { throw 'Groups require non-interactive full graph execution' }
    $selectedCases = @(& (Join-Path $PSScriptRoot 'expand-ui-smoke-groups.ps1') -Target $Target -Scenarios $Scenario)
    $Scenario = 'suite'
}
if (-not $Target) { throw 'A native launch or explicit report requires a target' }
if ($Scenario -eq "suite" -and ($Interactive -or ($ProjectId -and !$CasesBase64))) {
    throw "The prepared suite requires the full compatible profile and non-interactive execution"
}
$root = Split-Path -Parent $PSScriptRoot
$fixtureTarget = if ($Target -like "*-neoforge") { $Target } else { "1.20.1-forge" }
$source = Join-Path $root "versions\$fixtureTarget\run\saves\ae2-crafting-time"
$game, $loader = $Target.Split("-", 2)
$modsDirectory = if ($Target -eq "1.20.1-forge" -and -not $PreparedLaunch) { "resolved-mods" } else { "mods" }
$profile = if ($Latest) { "latest" } else { "compatible" }
$base = Join-Path $root "build\ui-smoke\$Target\$profile"
$report = if ($ReportDirectory) { [IO.Path]::GetFullPath($ReportDirectory) } else { Join-Path $base $Scenario }
$runtime = if ($RuntimeDirectory) { [IO.Path]::GetFullPath($RuntimeDirectory) } else { Join-Path $base "runtime" }
if ($RuntimeDirectory -and
        -not $runtime.StartsWith($report.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Explicit UI-smoke runtime must stay inside the selected report directory'
}
$evidence = Join-Path $report "evidence"
$headSha = $HeadSha
if (!$headSha) {
    $headSha = (& git -C $root rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot bind UI smoke to Git HEAD' }
}
if ($headSha -cnotmatch '^[a-f0-9]{40}$') { throw 'Cannot bind UI smoke to Git HEAD' }
. (Join-Path $PSScriptRoot 'ui-smoke-dependency-identity.ps1')
$dependencyIdentity = if ($BundleDirectory) { Get-UiSmokeDependencyIdentity $BundleDirectory } else { $null }
$resumeState = if ($ResumeBundleDirectory) {
    if (!$PreparedLaunch -or !$BundleDirectory -or $Scenario -ne 'cpu-list-total-ttc' -or $CaptureResumeOnly) {
        throw 'Resume-only execution requires one prepared CPU-list launch and cannot capture simultaneously'
    }
    & (Join-Path $PSScriptRoot 'prepare-ui-smoke-resume.ps1') -Mode Restore -ResumeDirectory $ResumeBundleDirectory `
        -BundleDirectory $BundleDirectory -Target $Target -Profile $profile -Scenario $Scenario -HeadSha $headSha
} else { $null }
$world = if ($resumeState) { $resumeState.world } else { "ae2ct-$([guid]::NewGuid().ToString('N'))" }
$worldCopy = Join-Path $runtime "saves\$world"
$worldCopies = @($worldCopy)
$stdout = Join-Path $report "launcher.stdout.log"
$stderr = Join-Path $report "launcher.stderr.log"
$statusPath = Join-Path $report "status.json"
$runId = [guid]::NewGuid().ToString("N")
$campaignId = if ($resumeState) { $resumeState.campaignId } elseif ($CampaignId) { $CampaignId } else { [guid]::NewGuid().ToString('N') }
$resourceFixtureId = if ($resourceScenario -or @($selectedCases | Where-Object { $_ -in @('delayed-resource-icons','appmek-resource-icons') }).Count) {
    if ($ResourceFixtureId) { $ResourceFixtureId } else { [guid]::NewGuid().ToString('N') }
} else { '' }
if ($campaignId -cnotmatch '^[A-Za-z0-9._-]{1,128}$') { throw 'Invalid UI-smoke campaign identity' }
$startedAt = [DateTime]::UtcNow.ToString("o")
$process = $null
$processDisappeared = $false
$processExitObserved = $false
$observedExitCode = $null
$processes = @()
$scheduledTaskName = $null
$preservePreparedWorld = $false
. (Join-Path $PSScriptRoot 'ui-smoke-scheduled-java.ps1')
$plannedPhases = @(Get-UiSmokeJavaLaunchPhases -Scenario $Scenario `
    -ContainsCpuList:($selectedCases -contains 'cpu-list-total-ttc') `
    -ResumeOnly:([bool]$resumeState) -PrepareOnly:$PrepareOnly)

function Get-TreeHash([string]$path) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $text = (Get-ChildItem -LiteralPath $path -File -Recurse | Sort-Object FullName | ForEach-Object {
            "$($_.FullName.Substring($path.Length))|$((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash)"
        }) -join "`n"
        return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($text))) -replace '-', '')
    } finally { $sha.Dispose() }
}

function Write-Status([string]$phase, [string]$message = "", [Nullable[int]]$exitCode = $null) {
    $status = [ordered]@{
        schema = 2; runId = $runId; campaignId = $campaignId; target = $Target; profile = $profile; scenario = $Scenario
        phase = $phase; pid = $(if ($process) { $process.Id } else { $null }); exitCode = $exitCode
        startedAt = $startedAt; updatedAt = [DateTime]::UtcNow.ToString("o"); javaHome = $env:JAVA_HOME
        stagedRoot = $root; stdout = $stdout; stderr = $stderr; evidence = $evidence; message = $message
        processStartedAt = $(if ($process) { $process.StartTime.ToUniversalTime().ToString('o') } else { $null })
        processes = @($processes)
        world = $world
        dependencyMode = $(if($dependencyIdentity){$dependencyIdentity.mode}else{$null})
        dependencyCatalogueSha256 = $(if($dependencyIdentity){$dependencyIdentity.catalogueSha256}else{$null})
        watchdog = [ordered]@{ startupTimeoutSeconds=$StartupTimeoutSeconds; callbackTimeoutSeconds=$CallbackTimeoutSeconds
            checkpointTimeoutSeconds=$CheckpointTimeoutSeconds }
        argumentFile = $(if ($PreparedLaunch) { Join-Path $runtime 'ui-smoke-java.args' } else { $null })
    }
    $temporary = "$statusPath.$runId.tmp"
    [IO.File]::WriteAllText($temporary, ($status | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporary -Destination $statusPath -Force
}

New-Item -ItemType Directory -Path $report -Force | Out-Null
Write-Status 'preparing' 'validating source fixture'
if (-not (Test-Path -LiteralPath (Join-Path $source ".ae2-crafting-time-test-fixture.json") -PathType Leaf)) {
    throw "Missing tracked $fixtureTarget test fixture"
}
$sourceMarker = Get-Content -LiteralPath (Join-Path $source ".ae2-crafting-time-test-fixture.json") -Raw | ConvertFrom-Json
if ($sourceMarker.schema -ne 1 -or $sourceMarker.scenario -ne "craft-plan" -or
        $sourceMarker.sourceFixtureId -ne "ae2-crafting-time" -or $sourceMarker.disposableWorldId -ne "SOURCE_ONLY") {
    throw "Tracked source fixture marker is invalid or executable"
}
$sourceHash = Get-TreeHash $source
Write-Status 'preparing' 'validating fixture metadata'
$metadata = Join-Path $root "versions/$Target/run/saves/ae2-crafting-time/level.dat"
$metadataHash = (Get-FileHash -LiteralPath $metadata).Hash
$buildRoot = [IO.Path]::GetFullPath((Join-Path $root "build\ui-smoke"))
$resolvedBase = [IO.Path]::GetFullPath($base)
if (-not $resolvedBase.StartsWith($buildRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "UI-smoke output escapes build directory"
}
Write-Status 'preparing' 'creating isolated runtime directories'
New-Item -ItemType Directory -Path $base, $report, (Split-Path -Parent $worldCopy) -Force | Out-Null
try {
    $runtimeLock = [IO.File]::Open((Join-Path ([IO.Path]::GetTempPath()) "ae2-crafting-time-smoke-client.lock"), "OpenOrCreate", "ReadWrite", "None")
} catch {
    throw "Another Minecraft UI-smoke client is already running; wait for its exit before launching another target or scenario"
}
if (Test-Path -LiteralPath $evidence) { Remove-Item -LiteralPath $evidence -Recurse -Force }
if ($resumeState) {
    Copy-Item -LiteralPath $resumeState.evidenceDirectory -Destination $evidence -Recurse
} elseif ($Scenario -ne "suite") { New-Item -ItemType Directory -Path $evidence -Force | Out-Null }
Remove-Item -LiteralPath $stdout, $stderr -Force -ErrorAction SilentlyContinue
Write-Status "preparing"
if ($Scenario -eq "suite") {
    $suiteName = if ($Target -eq "26.1.2-neoforge") { "neoforge-26.1.2" } else { $loader }
    $scenarios = if ($selectedCases) { $selectedCases } else { @(& (Join-Path $PSScriptRoot "expand-ui-smoke-groups.ps1") -Target $Target -Scenarios suite) }
    $suite = & (Join-Path $PSScriptRoot "prepare-ui-smoke-suite.ps1") -Target $Target -RuntimeDirectory $runtime -OutputDirectory $evidence -Scenarios $scenarios -VanillaMetadata:($Target -eq '1.20.1-forge' -and $BundleDirectory -and !(Get-ChildItem -LiteralPath (Join-Path $BundleDirectory 'mods') -Filter 'BloodMagic*.jar'))
    $world = $suite.world
    $plan = Get-Content -LiteralPath (Join-Path $evidence "suite-plan.json") -Raw | ConvertFrom-Json
    $worldCopies = @($plan.cases | ForEach-Object { Join-Path $runtime "saves\$($_.world)" } | Select-Object -Unique)
} elseif ($resumeState) {
    if (Test-Path -LiteralPath $worldCopy) { Remove-Item -LiteralPath $worldCopy -Recurse -Force }
    Copy-Item -LiteralPath $resumeState.worldDirectory -Destination $worldCopy -Recurse
} else {
    # The tracked Forge world names Blood Magic dimensions. Reduced graphs need native metadata.
    $vanillaMetadata = $Target -eq '1.20.1-forge' -and $BundleDirectory -and
        -not (Get-ChildItem -LiteralPath (Join-Path $BundleDirectory 'mods') -Filter 'BloodMagic*.jar')
    & (Join-Path $PSScriptRoot 'copy-ui-smoke-fixture.ps1') -Source $source -Destination $worldCopy -Target $Target -Scenario $Scenario -VanillaMetadata:$vanillaMetadata
    $markerPath = Join-Path $worldCopy ".ae2-crafting-time-test-fixture.json"
    $marker = Get-Content -LiteralPath $markerPath -Raw | ConvertFrom-Json
    $marker.disposableWorldId = $world
    if ($dependencyIdentity) {
        $marker | Add-Member -NotePropertyName dependencyMode -NotePropertyValue $dependencyIdentity.mode -Force
        $marker | Add-Member -NotePropertyName dependencyCatalogueSha256 -NotePropertyValue $dependencyIdentity.catalogueSha256 -Force
    }
    [IO.File]::WriteAllText($markerPath, ($marker | ConvertTo-Json -Depth 10), [Text.UTF8Encoding]::new($false))
}
[IO.File]::WriteAllText((Join-Path $runtime "options.txt"), @"
version:3465
fullscreen:false
onboardAccessibility:false
overrideWidth:854
overrideHeight:480
guiScale:0
lang:en_us
maxFps:60
pauseOnLostFocus:false
soundCategory_master:0.0
"@, [Text.UTF8Encoding]::new($false))

if ($Scenario -eq 'standard-status-controls' -or $selectedCases -contains 'standard-status-controls') {
    $fontPack = Join-Path $runtime 'resourcepacks/ae2ct-status-wide'
    $fontPath = Join-Path $fontPack 'assets/minecraft/font'
    New-Item -ItemType Directory -Path $fontPath -Force | Out-Null
    $packFormat = switch ($Target) {
        '1.20.1-forge' { 15 }
        '1.20.1-fabric' { 15 }
        '1.21.1-neoforge' { 34 }
        '26.1.2-neoforge' { 8 }
    }
    [IO.File]::WriteAllText((Join-Path $fontPack 'pack.mcmeta'),
        (@{pack=@{description='AE2 Crafting Time status font probe';pack_format=$packFormat}} | ConvertTo-Json -Depth 3),
        [Text.UTF8Encoding]::new($false))
    # Vanilla's uniform font has the same advance as the default font for digits
    # and slashes. Use its built-in ASCII bitmap only for amount glyphs so the
    # actual quantity string exercises horizontal fitting while tooltip prose
    # keeps the normal font metrics and line spacing.
    $blank = [string]::new([char]0, 16)
    $asciiRows = for ($row = 0; $row -lt 16; $row++) {
        if ($row -eq 2) { [string]::new([char]0, 15) + '/' }
        elseif ($row -eq 3) { '0123456789' + [string]::new([char]0, 6) }
        else { $blank }
    }
    $fontDefinition = @{providers=@(
        @{type='bitmap';file='minecraft:font/ascii.png';ascent=7;height=9;chars=@($asciiRows)},
        @{type='reference';id='minecraft:uniform'}
    )}
    [IO.File]::WriteAllText((Join-Path $fontPath 'default.json'),
        ($fontDefinition | ConvertTo-Json -Depth 6), [Text.UTF8Encoding]::new($false))
}

if ($Scenario -ne 'suite') {
    [ordered]@{schema=1;cases=@(@{scenario=$Scenario;world=$world})} | ConvertTo-Json -Depth 5 |
        Set-Content -LiteralPath (Join-Path $evidence 'suite-plan.json') -Encoding UTF8
}
$resourceHashes = @()
$resourceDirectory = Join-Path $runtime 'resourcepacks'
if (Test-Path -LiteralPath $resourceDirectory) {
    $resourceHashes = @(Get-ChildItem -LiteralPath $resourceDirectory -Recurse -File | Sort-Object FullName | ForEach-Object {
        [ordered]@{file=$_.FullName.Substring($resourceDirectory.Length).TrimStart('\');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
    })
}
[ordered]@{files=@($resourceHashes);selection=@(Get-Content -LiteralPath (Join-Path $runtime 'options.txt') | Where-Object { $_ -match '^(resourcePacks|incompatibleResourcePacks):' })} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'resource-hashes.json') -Encoding UTF8

$arguments = @(
    "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$(Join-Path $PSScriptRoot 'run-client.ps1')`"",
    "-Target", $Target, "-RuntimeDirectory", "`"$runtime`"",
    "-DriverScenario", $Scenario, "-DriverOutputDirectory", "`"$evidence`"", "-DriverWorld", $world,
    "--no-daemon"
)
if ($Latest) { $arguments += "-Latest" }
if ($Interactive) { $arguments += "-Interactive" }
if ($ProjectId) { $arguments += @("-ProjectId") + $ProjectId }

$previousToken = $env:AE2CT_TEST_DRIVER_TOKEN
if ($Interactive) {
    if ($previousToken -and $previousToken -notmatch '^[a-f0-9]{64}$') {
        throw "AE2CT_TEST_DRIVER_TOKEN must be 256-bit lowercase hex"
    }
    if (-not $previousToken) {
        $bytes = [byte[]]::new(32)
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        $env:AE2CT_TEST_DRIVER_TOKEN = ([BitConverter]::ToString($bytes) -replace '-', '').ToLowerInvariant()
    }
} else {
    Remove-Item Env:\AE2CT_TEST_DRIVER_TOKEN -ErrorAction SilentlyContinue
}

try {
    try {
        $continuationPath = if ($Scenario -eq 'standard-status-controls') {
            Join-Path $evidence 'status-amounts-continuation.json'
        } elseif ($Scenario -eq 'badge-background') {
            Join-Path $evidence 'badge-background-continuation.json'
        } elseif ($Scenario -eq 'suite') {
            Join-Path $evidence 'cpu-list-total-ttc/cpu-list-continuation.json'
        } else { Join-Path $evidence 'cpu-list-continuation.json' }
        $phase = if ($resumeState) { 2 } else { 1 }
        $predecessorHash = if ($resumeState) { $resumeState.predecessorSha256 } else { $null }
        $capturedOnly = $false
        . (Join-Path $PSScriptRoot 'ui-smoke-progress.ps1')
        if ($PrepareOnly) {
            if (!$PreparedLaunch) { throw 'Prepare-only mode requires a native launch manifest' }
            $phase = if ($resumeState) { 2 } else { 1 }
            $launchParameters = @{ LaunchManifest=$PreparedLaunch; BundleDirectory=$BundleDirectory
                RuntimeDirectory=$runtime; Target=$Target; Profile=$profile; Scenario=$Scenario; World=$world
                Evidence=$evidence; ProjectId=$ProjectId; Interactive=$Interactive; DedicatedAddress=$DedicatedAddress
                ControlDirectory=$ControlDirectory; CampaignId=$campaignId; ResourceFixtureId=$resourceFixtureId; HeadSha=$headSha
                GraphIdentity=$(if ($dependencyIdentity) { $dependencyIdentity.catalogueSha256 } else { "" }) }
            if ($Role) { $launchParameters.Role=$Role; $launchParameters.OfflineName=$OfflineName; $launchParameters.OfflineUuid=$OfflineUuid }
            if ($RuntimeDirectory) { $launchParameters.AllowedRuntimeRoot=$report }
            if ($phase -eq 2) { $launchParameters.ContinuationPath=$continuationPath; $launchParameters.ResumeOnly=$true }
            if ($ResourceFixtureOnly) { $launchParameters.ResourceFixtureOnly = $true }
            if ($Prewarm) { $launchParameters.Prewarm=$true; $launchParameters.PrewarmDeadline=$PrewarmDeadline
                $launchParameters.PrewarmHead=$headSha; $launchParameters.PrewarmBundle=$PrewarmBundle }
            $launch = & (Join-Path $PSScriptRoot 'prepare-ui-smoke-launch.ps1') @launchParameters
            [ordered]@{schema=1;phase=$phase;world=$world;campaignId=$campaignId;executable=$launch.executable
                arguments=$launch.arguments;finalApproval=$launch.finalApproval;runtime=$runtime;evidence=$evidence} | ConvertTo-Json -Depth 5 |
                Set-Content -LiteralPath (Join-Path $report 'prepared-only.json') -Encoding UTF8
            $preservePreparedWorld = $true
            Write-Status 'diagnostic-prepared' 'native launch prepared without starting a client' 0
            return
        }
        do {
            $executable = 'powershell.exe'
            $phaseArguments = $arguments
            if ($PreparedLaunch) {
                $launchParameters = @{ LaunchManifest=$PreparedLaunch; BundleDirectory=$BundleDirectory
                    RuntimeDirectory=$runtime; Target=$Target; Profile=$profile; Scenario=$Scenario; World=$world
                    Evidence=$evidence; ProjectId=$ProjectId; Interactive=$Interactive; DedicatedAddress=$DedicatedAddress
                    ControlDirectory=$ControlDirectory; CampaignId=$campaignId; ResourceFixtureId=$resourceFixtureId; HeadSha=$headSha
                GraphIdentity=$(if ($dependencyIdentity) { $dependencyIdentity.catalogueSha256 } else { "" }) }
                if ($Role) { $launchParameters.Role=$Role; $launchParameters.OfflineName=$OfflineName; $launchParameters.OfflineUuid=$OfflineUuid }
                if ($RuntimeDirectory) { $launchParameters.AllowedRuntimeRoot=$report }
                if ($phase -eq 2) {
                    $launchParameters.ContinuationPath = $continuationPath
                    if ($resumeState) { $launchParameters.ResumeOnly = $true }
                }
                if ($ResourceFixtureOnly) { $launchParameters.ResourceFixtureOnly = $true }
                if ($Prewarm) { $launchParameters.Prewarm=$true; $launchParameters.PrewarmDeadline=$PrewarmDeadline
                    $launchParameters.PrewarmHead=$headSha; $launchParameters.PrewarmBundle=$PrewarmBundle }
                $launch = & (Join-Path $PSScriptRoot 'prepare-ui-smoke-launch.ps1') @launchParameters
                $executable = $launch.executable
                $phaseArguments = $launch.arguments
            }
            $workingDirectory = if ($PreparedLaunch) { $runtime } else { $root }
            $phaseStdout = if ($phase -eq 1) { $stdout } else { Join-Path $report "launcher.phase-$phase.stdout.log" }
            $phaseStderr = if ($phase -eq 1) { $stderr } else { Join-Path $report "launcher.phase-$phase.stderr.log" }
            $scheduledIdentity = $null
            if ($ScheduledJava) {
                if (!$PreparedLaunch) { throw 'Scheduled Java execution requires a prepared native client' }
                $scheduledTaskName = "AE2 Crafting Time Java $runId Phase $phase"
                $tokenParameters = @{}
                if ($Interactive) { $tokenParameters.InteractiveToken = $env:AE2CT_TEST_DRIVER_TOKEN }
                $scheduledIdentity = Start-UiSmokeScheduledJava -Executable $executable -Arguments ([string]$phaseArguments) `
                    -WorkingDirectory $workingDirectory -TaskName $scheduledTaskName -InteractiveUser $InteractiveUser `
                    @tokenParameters
                $process = $scheduledIdentity.process
            } else {
                $process = Start-Process -FilePath $executable -ArgumentList $phaseArguments -PassThru -WindowStyle Hidden `
                    -WorkingDirectory $workingDirectory `
                    -RedirectStandardOutput $phaseStdout -RedirectStandardError $phaseStderr
                $null = $process.Handle
            }
            $identity = [ordered]@{ phase=$phase; pid=$process.Id
                startedAt=$process.StartTime.ToUniversalTime().ToString('o'); stdout=$phaseStdout; stderr=$phaseStderr
                taskName=$(if($scheduledIdentity){$scheduledIdentity.taskName}else{$null})
                executable=$executable; argumentFile=$(Join-Path $runtime 'ui-smoke-java.args') }
            $processes += $identity
            Write-Status "running" "client phase $phase"
            $timeout = if ($Interactive) { [TimeSpan]::FromMinutes(30) }
                elseif ($Scenario -in @("suite", "cpu-list-total-ttc", "standard-status-controls", "delayed-resource-icons", "appmek-resource-icons")) { [TimeSpan]::FromMinutes(40) }
                else { [TimeSpan]::FromMinutes(8) }
            $deadline = [DateTime]::UtcNow.Add($timeout)
            $watchdogReason = $null
            $lastCallback = $process.StartTime.ToUniversalTime()
            $lastCheckpoint = $lastCallback
            $progressPid = 0
            $callbackSequence = 0
            $checkpoint = ''
            $activeScenarioObserved = $false
            $processExitObserved = $false
            $scheduledExitCode = $null
            $prewarmArmedAt = $null
            $scenarioStartedAt = $process.StartTime.ToUniversalTime()
            while ([DateTime]::UtcNow -lt $deadline) {
                if ($ScheduledJava) {
                    $scheduledState = Get-UiSmokeScheduledJavaProcessState -ProcessId $process.Id -TaskName $scheduledTaskName
                    if ($scheduledState.state -eq 'disappeared') {
                        $processDisappeared = $true
                        break
                    }
                    if ($scheduledState.state -eq 'exited') {
                        $scheduledExitCode = [int]$scheduledState.exitCode
                        break
                    }
                    Start-Sleep -Seconds 1
                } elseif ($process.WaitForExit(1000)) {
                    break
                }
                if ($Scenario -in @('cpu-list-total-ttc', 'standard-status-controls', 'recurrent-plan', 'stored-variant-plan', 'delayed-resource-icons', 'appmek-resource-icons') -or
                        $selectedCases -contains 'recurrent-plan' -or $selectedCases -contains 'stored-variant-plan') {
                    $progressPath = Join-Path $evidence 'driver-progress.json'
                    if (Test-Path -LiteralPath $progressPath -PathType Leaf) {
                        try {
                            $progress = Get-Content -LiteralPath $progressPath -Raw | ConvertFrom-Json
                            $progressPid = [int]$progress.pid
                            $callbackSequence = [long]$progress.callbackSequence
                            if ($progressPid -eq $process.Id) {
                                if ($progress.callbackAt) { $lastCallback = [DateTime]::Parse($progress.callbackAt).ToUniversalTime() }
                                if ($progress.checkpointAt) { $lastCheckpoint = [DateTime]::Parse($progress.checkpointAt).ToUniversalTime() }
                                if ($progress.checkpoint) { $checkpoint = [string]$progress.checkpoint }
                                if ($checkpoint -match '(^|\s)state=WORLD_READY(\s|$)' -and
                                        $checkpoint -match '(^|\s)phase=(?!PREPARE(?:\s|$))[A-Z_]+(\s|$)') {
                                    $activeScenarioObserved = $true
                                }
                            }
                        } catch { }
                    }
                    if ($Prewarm) {
                        $prewarmFailure = Get-ResourcePrewarmWatchdog ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) `
                            $PrewarmDeadline ([bool]$prewarmArmedAt) $callbackSequence ([DateTime]::UtcNow - $lastCallback).TotalSeconds
                        if ($prewarmFailure) { $watchdogReason=$prewarmFailure; break }
                    }
                    if ($Prewarm -and !$prewarmArmedAt) {
                        if (Update-ResourcePrewarm $ControlDirectory $campaignId $headSha $PrewarmBundle `
                                $PrewarmServerProcessId $PrewarmServerStartedAt $process.Id $process.StartTime.ToUniversalTime()) {
                            $prewarmArmedAt = [DateTime]::UtcNow
                            $scenarioStartedAt = $prewarmArmedAt
                            $deadline = $prewarmArmedAt.AddMinutes(40)
                            [ordered]@{schema=1;readinessResult='READY';armedAt=$prewarmArmedAt.ToString('o')
                                connectionCheckpoint=$checkpoint;fixtureMutationBeforeArm=$false
                                armSha256=(Get-FileHash -LiteralPath (Join-Path $ControlDirectory 'prewarm/arm.json')).Hash} |
                                ConvertTo-Json | Set-Content -LiteralPath (Join-Path $report 'prewarm-evidence.json') -Encoding UTF8
                        } else { continue }
                    }
                    if (!$Prewarm -and $FailOnInitialDisconnect -and $phase -eq 1 -and $progressPid -eq $process.Id -and
                            $checkpoint -match '^state=STARTING .* screen=(?:net\.minecraft\.client\.gui\.screens\.DisconnectedScreen|net\.minecraft\.class_419)$') {
                        $watchdogReason = 'initial-disconnect'
                        break
                    }
                    if (!$watchdogReason) {
                        $watchdogReason = Get-UiSmokeProgressDecision -Now ([DateTime]::UtcNow) -CallbackAt $lastCallback `
                            -CheckpointAt $lastCheckpoint -CallbackTimeoutSeconds $CallbackTimeoutSeconds `
                            -CheckpointTimeoutSeconds $CheckpointTimeoutSeconds -ProcessId $process.Id `
                            -ProgressProcessId $progressPid -CallbackSequence $callbackSequence `
                            -StartedAt $scenarioStartedAt -StartupTimeoutSeconds $StartupTimeoutSeconds `
                            -ActiveScenarioObserved:$activeScenarioObserved -Checkpoint $checkpoint
                    }
                    if ($watchdogReason) { break }
                }
            }
            if ($processDisappeared) {
                throw "Scheduled UI-smoke client process $($process.Id) disappeared before exit could be observed"
            }
            if ($null -eq $scheduledExitCode -and !$process.HasExited) {
                $null = $process.CloseMainWindow()
                if (-not $process.WaitForExit(10000)) {
                    & taskkill.exe /PID $process.Id /T /F | Out-Null
                    if (-not $process.WaitForExit(10000)) { throw 'Recorded smoke client did not exit after termination' }
                }
                if ($watchdogReason) {
                    [ordered]@{schema=1;reason=$watchdogReason;campaignId=$campaignId;world=$world;pid=$identity.pid
                        processStartedAt=$identity.startedAt;headSha=$headSha;bundleSha256=$(if($resumeState){$resumeState.bundleSha256}else{$null})
                        dependencyMode=$(if($dependencyIdentity){$dependencyIdentity.mode}else{$null})
                        dependencyCatalogueSha256=$(if($dependencyIdentity){$dependencyIdentity.catalogueSha256}else{$null})
                        callbackAt=$lastCallback.ToString('o');checkpointAt=$lastCheckpoint.ToString('o');launchCount=$processes.Count
                        progressPid=$progressPid;callbackSequence=$callbackSequence;checkpoint=$checkpoint;startupTimeoutSeconds=$StartupTimeoutSeconds
                        callbackTimeoutSeconds=$CallbackTimeoutSeconds;checkpointTimeoutSeconds=$CheckpointTimeoutSeconds
                        capturedAt=[DateTime]::UtcNow.ToString('o')} |
                        ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $evidence 'watchdog-evidence.json') -Encoding UTF8
                    throw "UI-smoke phase $phase watchdog: $watchdogReason"
                }
                throw "UI-smoke client phase $phase exceeded $($timeout.TotalMinutes) minutes"
            }
            $observedExitCode = if ($null -ne $scheduledExitCode) { $scheduledExitCode } else { $process.ExitCode }
            $processExitObserved = $true
            $identity['exitCode'] = $observedExitCode
            $identity['exitedAt'] = [DateTime]::UtcNow.ToString('o')
            if ($scheduledTaskName) {
                Remove-UiSmokeScheduledJava -TaskName $scheduledTaskName
                $scheduledTaskName = $null
            }
            Write-Status 'validating' "client phase $phase exited; validating evidence" $observedExitCode
            if ($observedExitCode -ne 0) {
                throw "UI-smoke $profile-profile phase $phase failed with launcher exit $observedExitCode; see $phaseStderr"
            }
            if ($phase -eq 1 -and (Test-Path -LiteralPath $continuationPath -PathType Leaf)) {
                if (!$PreparedLaunch) { throw 'Runner-owned relaunch requires a prepared native client' }
                if ($Scenario -in @('standard-status-controls', 'badge-background')) {
                    Assert-UiSmokeStatusContinuation -Path $continuationPath -World $world -CampaignId $campaignId `
                        -ConfigPath (Join-Path $runtime 'config/ae2craftingtime-client.toml')
                }
                $predecessorHash = (Get-FileHash -LiteralPath $continuationPath -Algorithm SHA256).Hash
                if ($CaptureResumeOnly) {
                    $resumeOutput = Join-Path $report 'resume'
                    & (Join-Path $PSScriptRoot 'prepare-ui-smoke-resume.ps1') -Mode Capture -ResumeDirectory $resumeOutput `
                        -WorldDirectory $worldCopy -EvidenceDirectory $evidence -BundleDirectory $BundleDirectory `
                        -Target $Target -Profile $profile -Scenario $Scenario -CampaignId $campaignId -HeadSha $headSha | Out-Null
                    $capturedOnly = $true
                    break
                }
                $phase = 2
                continue
            }
            break
        } while ($phase -le 2)
        if ($capturedOnly) {
            Write-Status 'diagnostic-captured' 'immutable phase-1 resume bundle captured' 0
            return
        }
        Assert-UiSmokeJavaPhaseIdentities -Processes @($processes) -ExpectedPhases $plannedPhases `
            -FinalApproval:($Scenario -in @('cpu-list-total-ttc', 'standard-status-controls', 'badge-background') -and !$resumeState)
        if ($phase -eq 2) {
            if (!$resumeState -and ($processes.Count -ne 2 -or $processes[0].pid -eq $processes[1].pid -or
                    $processes[0].startedAt -eq $processes[1].startedAt)) {
                throw 'Relaunch did not produce a distinct second client process identity'
            }
            $artifactHashes = if ($BundleDirectory) { @(Get-ChildItem -LiteralPath (Join-Path $BundleDirectory 'mods') -File -Filter '*.jar' |
                Sort-Object Name | ForEach-Object { [ordered]@{name=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash} }) } else { @() }
            $controlStatePath = if ($ControlDirectory) { Join-Path $ControlDirectory 'state.properties' } else { $null }
            if ($DedicatedAddress -and (!$controlStatePath -or !(Test-Path -LiteralPath $controlStatePath -PathType Leaf))) {
                throw 'Relaunch evidence requires the connected control state'
            }
            Write-UiSmokeRelaunchEvidence -Path (Join-Path $evidence 'relaunch-evidence.json') `
                -CampaignId $campaignId -Target $Target -Profile $profile -Scenario $Scenario -World $world `
                -ConnectionEpoch $(if($DedicatedAddress){$campaignId}else{$null}) `
                -PredecessorCheckpointSha256 $predecessorHash -Processes @($processes) -Artifacts $artifactHashes `
                -DependencyMode $(if($dependencyIdentity){$dependencyIdentity.mode}else{$null}) `
                -DependencyCatalogueSha256 $(if($dependencyIdentity){$dependencyIdentity.catalogueSha256}else{$null}) `
                -ControlStatePath $controlStatePath -ResumeOnly:([bool]$resumeState) -FinalApproval:(-not [bool]$resumeState)
            if ($Scenario -eq 'standard-status-controls') {
                Assert-UiSmokeStatusRelaunchCaptures -Evidence $evidence
                Assert-UiSmokeStatusAddonKeys -Evidence $evidence -Target $Target
            }
            if ($Scenario -eq 'badge-background') {
                Assert-UiSmokeBadgeRelaunchCaptures -Evidence $evidence
            }
        }

    $caseScenarios = @($Scenario)
    if ($Scenario -eq "suite") {
        $summary = Get-Content -LiteralPath (Join-Path $evidence "result.json") -Raw | ConvertFrom-Json
        if ($summary.schema -ne 1 -or -not $summary.complete -or $summary.result -ne "PASS" -or $summary.processId -le 0 -or
                @($summary.cases).Count -ne $scenarios.Count) { throw "Incomplete or failed UI-smoke suite" }
        for ($i = 0; $i -lt $scenarios.Count; $i++) {
            $case = $summary.cases[$i]
            if ($case.scenario -ne $scenarios[$i] -or $case.world -ne $plan.cases[$i].world -or
                    $case.result -ne "PASS" -or -not $case.startedAt -or -not $case.finishedAt) {
                throw "Invalid suite case outcome: $($scenarios[$i])"
            }
        }
        $caseScenarios = $scenarios
    }
    foreach ($caseScenario in $caseScenarios) {
        $caseEvidence = if ($Scenario -eq "suite") { Join-Path $evidence $caseScenario } else { $evidence }
        $resultPath = Join-Path $caseEvidence "result.json"
        if (-not (Test-Path -LiteralPath $resultPath -PathType Leaf)) { throw "Missing atomic result.json" }
        $result = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
        if ($result.result -eq "FAIL" -and $result.failure) {
            throw "UI-smoke driver failed: step=$($result.failure.step) code=$($result.failure.code) expected=$($result.failure.expected) observed=$($result.failure.observed)"
        }
        $modVersion = ((Get-Content -LiteralPath (Join-Path $root "gradle.properties")) |
            Where-Object { $_ -match '^modVersion=' } | Select-Object -First 1) -replace '^modVersion=', ''
        $driverName = "ae2-crafting-time-$modVersion-$loader-$game-test-driver.jar"
        $standardContracts = (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ui-smoke-groups.json') -Raw | ConvertFrom-Json).cases
        $requiredChecks = if ($caseScenario -in @('delayed-resource-icons','appmek-resource-icons')) {
            @('server-identity','real-dispatch','delayed-plates','native-locate','lifecycle',
                'capture-integrity','cleanup',$(if ($ResourceFixtureOnly) { 'fixture-only' } else { 'typed-keys' }))
        } elseif ($standardContracts.$caseScenario) {
            if ($DedicatedAddress -and $standardContracts.$caseScenario.connectedChecks) {
                @($standardContracts.$caseScenario.checks) + @($standardContracts.$caseScenario.connectedChecks)
            } else {
            if ($result.checks.'advanced-cpu' -is [bool] -and $standardContracts.$caseScenario.advancedChecks) {
                @($standardContracts.$caseScenario.advancedChecks)
            } else { @($standardContracts.$caseScenario.checks) }
            }
        } elseif ($caseScenario -eq "no-space-status") {
            @("screen", "external-machine", "warning", "tooltip", "layout", "recovered")
        } elseif ($caseScenario -eq "no-power-status") {
            @("screen", "real-job", "external-unpowered", "active-network", "mixed-row", "tooltip", "layout", "power-restored", "cancelled", "inactive-cpu")
        } elseif ($caseScenario -eq "no-provider-status") {
            @("screen", "real-job", "mixed-row", "pattern-removed", "tooltip", "layout",
                "pattern-restored", "second-provider", "provider-removed", "provider-restored", "cancelled")
        } elseif ($caseScenario -eq "crafting-tree-read-recovery") {
            @("screen", "host-content", "overlay-absent", "layout", "tooltip", "details-ignored", "reset-ignored")
        } elseif ($caseScenario -eq "merequester-read-recovery") {
            @("screen", "host-content", "overlay-absent", "layout")
        } elseif ($caseScenario -eq "crafting-tree-screen") {
            @("screen", "node-ttc", "tooltip", "layout", "details", "reset")
        } elseif ($caseScenario -eq "ae2networkanalyser-screen") {
            @("screen", "layout")
        } elseif ($caseScenario -eq "merequester-screen") {
            @("screen", "ttc-row", "total-ttc", "layout")
        } elseif ($caseScenario -eq "aeinfinitybooster-terminal") {
            @("screen", "plan-ttc")
        } elseif ($caseScenario -like "*-terminal") {
            @("screen", "ttc-tooltip", "plan-ttc")
        } elseif ($caseScenario -ne "craft-plan") {
            @("cpu-selected", "job-accepted", "dispatch-amount", "returned-amount", "job-finished", "profile-sample", "ttc-after-sample")
        } else {
            @("screen", "ttc-row", "total-ttc", "sort-cycle", "tooltip", "layout")
        }
        if ($result.schema -ne 1 -or -not $result.complete -or $result.result -ne "PASS" -or
                $result.driver -ne $driverName -or $result.target -ne $Target -or
                $result.profile -ne $profile -or $result.scenario -ne $caseScenario -or $result.language -ne "en_us") {
            throw "Invalid UI-smoke result identity or completion state"
        }
        if ($PreparedLaunch) {
            $catalogue = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ui-smoke-groups.json') -Raw | ConvertFrom-Json
            $expected = Get-Content -LiteralPath (Join-Path $BundleDirectory 'expected-adapters.json') -Raw | ConvertFrom-Json
            $adapterPolicies = @($catalogue.adapterCases.psobject.Properties) +
                @($catalogue.adapterBehaviorCases.psobject.Properties) + @($catalogue.readRecoveryCases.psobject.Properties)
            foreach ($adapter in $adapterPolicies) {
                $dependency = $adapter.Name
                if ($caseScenario -cin $adapter.Value -and $expected.$dependency -and
                        ($result.adapters.$dependency.variant -cne $expected.$dependency -or $result.adapters.$dependency.reason -cne 'selected')) {
                    throw "Newest adapter not exercised: $dependency requires $($expected.$dependency)"
                }
            }
        }
        $actualChecks = @($result.checks.psobject.Properties.Name)
        if (Compare-Object $requiredChecks $actualChecks -CaseSensitive) { throw "Invalid UI-smoke check set: $caseScenario" }
        foreach ($check in $requiredChecks) { if (-not $result.checks.$check) { throw "Failed UI-smoke check: $check" } }
        $requiredScreenshots = if ($caseScenario -in @('delayed-resource-icons','appmek-resource-icons')) {
            Get-ResourceFixtureScreenshots (Get-ResourceFixtureCases $caseScenario $Target) `
                ([bool]$DedicatedAddress) (!$ResourceFixtureOnly)
        } elseif ($standardContracts.$caseScenario) {
            if ($DedicatedAddress -and $standardContracts.$caseScenario.connectedScreenshots) {
                @($standardContracts.$caseScenario.screenshots) + @($standardContracts.$caseScenario.connectedScreenshots)
            } else {
            if ($result.checks.'advanced-cpu' -is [bool] -and $standardContracts.$caseScenario.advancedScreenshots) {
                @($standardContracts.$caseScenario.advancedScreenshots)
            } else { @($standardContracts.$caseScenario.screenshots) }
            }
        } elseif ($caseScenario -eq "no-space-status") {
            @("no-space-before.png", "no-space-en-us.png", "no-space-recovered.png")
        } elseif ($caseScenario -eq "no-power-status") {
            @("no-power-external-unpowered.png", "no-power-en-us.png", "no-power-restored.png", "no-power-inactive.png")
        } elseif ($caseScenario -eq "no-provider-status") {
            @("no-provider-before.png", "no-provider-en-us.png", "no-provider-pattern-restored.png", "no-provider-redundant.png", "no-provider-block-removed.png",
                "no-provider-block-restored.png", "no-provider-cancelled.png")
        } elseif ($caseScenario -eq "crafting-tree-screen") {
            @("crafting-tree-screen.png", "crafting-tree-tooltip.png", "crafting-tree-details.png", "crafting-tree-reset.png")
        } elseif ($caseScenario -eq "crafting-tree-read-recovery") {
            @("read-recovery-screen.png", "read-recovery-tooltip.png", "read-recovery-details.png", "read-recovery-reset.png")
        } elseif ($caseScenario -eq "merequester-read-recovery") {
            @("read-recovery-screen.png")
        } elseif ($caseScenario -eq "ae2networkanalyser-screen") {
            @("ae2networkanalyser-screen.png")
        } elseif ($caseScenario -eq "merequester-screen") {
            @("merequester-screen.png")
        } elseif ($caseScenario -like "*-terminal") {
            $prefix = $caseScenario -replace '-terminal$', ''
            @("$prefix-terminal.png", "$prefix-plan.png")
        } elseif ($caseScenario -ne "craft-plan") {
            @("$($caseScenario -replace '-cpu$', '')-profiled-plan.png")
        } else {
            @("craft-plan.png", "craft-plan-sort-1.png", "craft-plan-sort-2.png", "craft-plan-sort-3.png", "craft-plan-tooltip.png")
        }
        foreach ($screenshot in $requiredScreenshots) {
            if ($screenshot -notin $result.screenshots -or -not (Test-Path -LiteralPath (Join-Path $caseEvidence $screenshot))) {
                throw "Missing required screenshot $screenshot"
            }
            $sidecar = Join-Path $caseEvidence $screenshot.Replace('.png','.json')
            if (!(Test-Path -LiteralPath $sidecar -PathType Leaf)) { throw "Missing semantic snapshot $screenshot" }
            $snapshot = Get-Content -LiteralPath $sidecar -Raw | ConvertFrom-Json
            if (!$snapshot.screen -or !$snapshot.gui -or !(Test-UiSnapshotBounds $snapshot)) { throw "Invalid semantic snapshot $screenshot" }
        }
        if ($caseScenario -in @('delayed-resource-icons','appmek-resource-icons')) {
            $fixturePath = Join-Path $caseEvidence 'resource-fixture-evidence.json'
            if (!(Test-Path -LiteralPath $fixturePath -PathType Leaf)) { throw 'Missing resource fixture evidence' }
            $fixture = Get-Content -LiteralPath $fixturePath -Raw | ConvertFrom-Json
            $fixture | Add-Member -NotePropertyName sidecars -NotePropertyValue @($fixture.screenshots | ForEach-Object {
                Get-Content -LiteralPath (Join-Path $caseEvidence $_.name.Replace('.png','.json')) -Raw | ConvertFrom-Json
            }) -Force
            Assert-ResourceFixtureContract $fixture $caseScenario $Target ([bool]$DedicatedAddress) `
                $campaignId $resourceFixtureId | Out-Null
            if (!$ResourceFixtureOnly) {
                $iconPath = Join-Path $caseEvidence 'resource-icon-evidence.json'
                if (!(Test-Path -LiteralPath $iconPath -PathType Leaf)) { throw 'Missing resource icon production evidence' }
                $icon = Get-Content -LiteralPath $iconPath -Raw | ConvertFrom-Json
                Assert-ResourceIconEvidence $icon $fixture $headSha $dependencyIdentity.catalogueSha256
            }
            $expectedCases = Get-ResourceFixtureCases $caseScenario $Target
            $expectedCaptures = Get-ResourceFixtureScreenshots $expectedCases ([bool]$DedicatedAddress) (!$ResourceFixtureOnly)
            $connectedEvidence = [bool]$DedicatedAddress
            if ($fixture.schema -ne 1 -or $fixture.fixtureResult -cne 'PASS' -or
                    $fixture.productionIconAcceptance -cne 'NOT_RUN' -or !$fixture.clientObservations.Count -or
                    $fixture.connected -ne $connectedEvidence -or
                    (Compare-Object $expectedCaptures @($fixture.screenshots.name) -SyncWindow 0 -CaseSensitive) -or
                    (Compare-Object $expectedCaptures @($result.screenshots) -SyncWindow 0 -CaseSensitive) -or
                    !$fixture.clientEvidence.digest -or $fixture.clientEvidence.captures -ne $expectedCaptures.Count) {
                throw 'Resource fixture semantic evidence is incomplete'
            }
            $integratedProperties = @($fixture.integratedServerEvidence.psobject.Properties)
            if ($connectedEvidence) {
                if ($integratedProperties.Count) { throw 'Connected resource evidence manufactured an integrated server result' }
            } elseif ($fixture.integratedServerEvidence.teardownComplete -ne $true -or
                    $fixture.integratedServerEvidence.fixtureResult -cne 'PASS' -or
                    $fixture.integratedServerEvidence.clientEvidence.digest -cne $fixture.clientEvidence.digest -or
                    $fixture.integratedServerEvidence.clientEvidence.captures -ne $fixture.clientEvidence.captures -or
                    $fixture.integratedServerEvidence.clientEvidence.revision -ne $fixture.clientEvidence.revision) {
                throw 'Integrated resource evidence lacks authoritative server cleanup'
            }
            foreach ($fixtureCase in $expectedCases) {
                $caseReceipts = @($fixture.receipts | Where-Object { $_.case -ceq $fixtureCase })
                $requiredActions = @('CREATE') + $(if (!$ResourceFixtureOnly -and $fixtureCase -ceq 'WATER') { @('UNLOAD_RELOAD') }) +
                    $(if ($connectedEvidence) { @('REJOIN_PREPARE','RECONNECT') } else { @() }) +
                    @('RELEASE','RESET','CREATE','CANCEL','RESET')
                if ($fixtureCase.EndsWith('OVERLAP')) {
                    $requiredActions = @('CREATE') + $(if ($connectedEvidence) { @('REJOIN_PREPARE','RECONNECT') } else { @() }) +
                        @('RELEASE','RELEASE','RESET','CREATE') +
                        $(if (!$ResourceFixtureOnly -and $fixtureCase -ceq $expectedCases[-1]) { @('REMOVE_PROVIDER') }) +
                        @('CANCEL','CANCEL','RESET')
                }
                if ($fixtureCase -ceq $expectedCases[-1]) { $requiredActions += 'COMPLETE' }
                if ((Compare-Object $requiredActions @($caseReceipts.action) -SyncWindow 0 -CaseSensitive) -or
                        @($caseReceipts | Where-Object { $_.sequence -le 0 -or $_.revision -le 0 -or
                            $_.ackRevision -ne $_.revision -or $_.stateRevision -ne
                                $(if ($_.action -ceq 'RESET') { $_.revision + 1 } else { $_.revision }) }).Count) {
                    throw "Resource fixture lifecycle evidence is invalid for $fixtureCase"
                }
            }
            if (Compare-Object (1..@($fixture.receipts).Count) @($fixture.receipts.sequence) -SyncWindow 0) {
                throw 'Resource fixture receipt sequence is not contiguous'
            }
            if (Compare-Object $expectedCaptures @($fixture.clientObservations.checkpoint) -SyncWindow 0 -CaseSensitive) {
                throw 'Resource fixture observations do not match the fixed capture contract'
            }
            foreach ($observation in @($fixture.clientObservations | Where-Object { $_.checkpoint -notlike '*-cleanup.png' })) {
                if (!$observation.serverJobs.Count -or $observation.frame -lt 0 -or !$observation.screen) {
                    throw "Resource fixture observation lacks frame-bound server facts: $($observation.checkpoint)"
                }
                foreach ($job in @($observation.serverJobs)) {
                    if (!$job.resource -or !$job.keyFingerprint -or !$job.keyEncoding -or !$job.cpu -or !$job.provider -or
                            $job.rawAmount -le 0 -or $job.dispatchCount -ne 1) {
                        throw "Resource fixture job evidence is incomplete: $($observation.checkpoint)"
                    }
                }
            }
            foreach ($capture in @($fixture.screenshots)) {
                $capturePath = Join-Path $caseEvidence $capture.name
                if ((Get-FileHash -LiteralPath $capturePath -Algorithm SHA256).Hash -ine $capture.sha256) {
                    throw "Resource fixture screenshot hash mismatch: $($capture.name)"
                }
            }
            if (!$connectedEvidence) {
                $serverReceipts = @($fixture.integratedServerEvidence.receipts)
                Assert-ResourceFixtureServerTiming $serverReceipts | Out-Null
                if ($serverReceipts.Count -ne @($fixture.receipts).Count) {
                    throw 'Integrated server and client receipt counts differ'
                }
                for ($receiptIndex = 0; $receiptIndex -lt $serverReceipts.Count; $receiptIndex++) {
                    $serverReceipt = $serverReceipts[$receiptIndex]
                    $clientReceipt = $fixture.receipts[$receiptIndex]
                    if ($serverReceipt.sequence -ne $clientReceipt.sequence -or
                            $serverReceipt.revision -ne $clientReceipt.revision -or
                            $serverReceipt.stateRevision -ne $clientReceipt.stateRevision -or
                            $serverReceipt.action -cne $clientReceipt.action -or
                            $serverReceipt.case -cne $clientReceipt.case -or
                            $serverReceipt.slot -ne $clientReceipt.slot -or
                            $serverReceipt.phase -cne $clientReceipt.phase -or
                            ($serverReceipt.jobs | ConvertTo-Json -Depth 12 -Compress) -cne
                                ($clientReceipt.jobs | ConvertTo-Json -Depth 12 -Compress)) {
                        throw "Integrated server and client receipt $receiptIndex differ"
                    }
                }
            }
        }
    }
    $manifest = Join-Path $runtime "$modsDirectory\.ae2-crafting-time-run-mods.json"
    if (-not (Test-Path -LiteralPath $manifest)) { throw "Missing managed dependency manifest" }
    $managed = Get-Content -LiteralPath $manifest -Raw | ConvertFrom-Json
    if ($driverName -notin $managed) { throw "Managed dependency manifest omits $driverName" }
    Copy-Item -LiteralPath $manifest -Destination (Join-Path $evidence "resolved-mods.json")
    $latestLog = Join-Path $runtime "logs\latest.log"
    if (-not (Test-Path -LiteralPath $latestLog)) { throw "Missing launched client log" }
    Copy-Item -LiteralPath $latestLog -Destination (Join-Path $evidence "latest.log")
    $fatal = Select-String -LiteralPath $latestLog -Pattern @(
        'Exception caught from mod bus', 'Mixin apply failed ae2craftingtime.mixins.json',
        'Mixin apply failed ae2craftingtime_test_driver.mixins.json', 'MixinTransformerError',
        'Failed to load resource', 'The game crashed whilst', 'There is no mod with modId',
        "Reference map 'ae2craftingtime.refmap.json'",
        "Reference map 'ae2craftingtime_test_driver.refmap.json'"
    ) -SimpleMatch
        if ($fatal) { throw "Fatal loader, mixin, resource, or crash signature in latest.log" }
    } finally {
        if ($Target -like '*-neoforge') {
            $fmlConfig = Join-Path $runtime 'config/fml.toml'
            $snapshot = Join-Path $evidence 'fml-postlaunch.toml'
            $absent = Join-Path $evidence 'fml-postlaunch.absent'
            Remove-Item -LiteralPath $snapshot, $absent -Force -ErrorAction SilentlyContinue
            if (Test-Path -LiteralPath $fmlConfig -PathType Leaf) {
                Copy-Item -LiteralPath $fmlConfig -Destination $snapshot
            } else {
                [IO.File]::WriteAllText($absent, '', [Text.UTF8Encoding]::new($false))
            }
        }
        $manifest = Join-Path $runtime "$modsDirectory\.ae2-crafting-time-run-mods.json"
        if (Test-Path -LiteralPath $manifest) { Copy-Item -LiteralPath $manifest -Destination (Join-Path $evidence 'resolved-mods.json') -Force }
        $latestLog = Join-Path $runtime "logs\latest.log"
        if (Test-Path -LiteralPath $latestLog) { Copy-Item -LiteralPath $latestLog -Destination (Join-Path $evidence "latest.log") -Force }
        if ($previousToken) { $env:AE2CT_TEST_DRIVER_TOKEN = $previousToken }
        else { Remove-Item Env:\AE2CT_TEST_DRIVER_TOKEN -ErrorAction SilentlyContinue }
        foreach ($copy in $worldCopies) {
            if (!$preservePreparedWorld -and (-not $process -or $processDisappeared -or $processExitObserved -or $process.HasExited) -and
                    (Test-Path -LiteralPath $copy)) {
                Remove-Item -LiteralPath $copy -Recurse -Force
            }
        }
        $afterHash = Get-TreeHash $source
        $afterMetadata = (Get-FileHash -LiteralPath $metadata).Hash
        [ordered]@{ before = $sourceHash; after = $afterHash; metadataBefore = $metadataHash; metadataAfter = $afterMetadata
            disposableWorlds = @($worldCopies | ForEach-Object {
                [ordered]@{ world = Split-Path -Leaf $_; removed = !(Test-Path -LiteralPath $_) }
            })
            unchanged = ($afterHash -eq $sourceHash -and $afterMetadata -eq $metadataHash) } |
            ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $evidence 'fixture-hashes.json') -Encoding UTF8
        if ($afterHash -ne $sourceHash -or $afterMetadata -ne $metadataHash) { throw "Tracked source fixture changed during UI smoke" }
    }
    Write-Status "passed" "UI smoke passed" $observedExitCode
    Write-Host "UI smoke passed: $evidence"
} catch {
    if ($scheduledTaskName) { Remove-UiSmokeScheduledJava -TaskName $scheduledTaskName }
    $exitCode = if ($processExitObserved) { [Nullable[int]]$observedExitCode }
        elseif ($process -and !$processDisappeared -and $process.HasExited) { [Nullable[int]]$process.ExitCode }
        else { $null }
    Write-Status "failed" $_.Exception.Message $exitCode
    throw
} finally {
    $runtimeLock.Dispose()
}
