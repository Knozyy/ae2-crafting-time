param(
    [Parameter(Mandatory)][string]$LaunchManifest,
    [Parameter(Mandatory)][string]$BundleDirectory,
    [Parameter(Mandatory)][string]$RuntimeDirectory,
    [Parameter(Mandatory)][string]$Target,
    [Parameter(Mandatory)][string]$Profile,
    [Parameter(Mandatory)][string]$Scenario,
    [Parameter(Mandatory)][string]$World,
    [Parameter(Mandatory)][string]$Evidence,
    [string]$AllowedRuntimeRoot,
    [string[]]$ProjectId,
    [string]$DedicatedAddress,
    [string]$ControlDirectory,
    [string]$ContinuationPath,
    [string]$CampaignId,
    [string]$ResourceFixtureId,
    [string]$HeadSha,
    [string]$GraphIdentity,
    [ValidateSet('alpha')][string]$Role,
    [string]$OfflineName,
    [ValidatePattern('^[a-f0-9]{32}$')][string]$OfflineUuid,
    [switch]$ResumeOnly,
    [switch]$ResourceFixtureOnly,
    [switch]$Prewarm,
    [long]$PrewarmDeadline,
    [string]$PrewarmHead,
    [string]$PrewarmBundle,
    [switch]$Interactive,
    [switch]$ObservationMode,
    [switch]$ClientInstalled,
    [switch]$ExpectUnsupportedPeer,
    [string]$ObservationFile,
    [string]$ProductionSha256,
    [string]$DriverSha256,
    [string]$ConnectionEpoch
)
$ErrorActionPreference = 'Stop'
$resourceScenario = $Scenario -in @('delayed-resource-icons','appmek-resource-icons')
if ($Prewarm -and (!$resourceScenario -or !$DedicatedAddress -or $PrewarmDeadline -le 0 -or
        $PrewarmHead -cnotmatch '^[a-f0-9]{40}$' -or $PrewarmBundle -cnotmatch '^[a-fA-F0-9]{64}$')) {
    throw 'Prewarm requires a connected resource launch and complete immutable identity'
}
if ($ResourceFixtureOnly -and !$resourceScenario -and $Scenario -ne 'suite') {
    throw 'ResourceFixtureOnly requires a resource fixture scenario'
}
$launch = Get-Content -LiteralPath $LaunchManifest -Raw | ConvertFrom-Json
$bundle = Get-Content -LiteralPath (Join-Path $BundleDirectory 'profile.json') -Raw | ConvertFrom-Json
if ($bundle.schema -ne 1 -or $bundle.target -ne $Target -or $bundle.profile -ne $Profile -or
        $bundle.dependencyMode -notin @('base','catalogue') -or
        $launch.target -ne $Target -or $launch.java -ne $bundle.java) { throw 'Prepared launch target/profile/Java mismatch' }
# The installed native loader must be the resolved profile's loader, including latest diagnostics.
$loaderVersion = $bundle.loader -replace ('^' + [regex]::Escape($Target.Split('-')[0]) + '-'), ''
$loaderArguments = @($launch.arguments | Where-Object { $_ -match "(^|[-])$([regex]::Escape($loaderVersion))($|[-])" })
if (-not $loaderArguments.Count) { throw "Prepared loader does not match resolved loader $($bundle.loader)" }
$runtime = [IO.Path]::GetFullPath($RuntimeDirectory)
$ownedRoot = if ($AllowedRuntimeRoot) {
    [IO.Path]::GetFullPath($AllowedRuntimeRoot)
} else {
    [IO.Path]::GetFullPath((Join-Path (Split-Path -Parent $PSScriptRoot) 'build/ui-smoke'))
}
if (-not $runtime.StartsWith($ownedRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Native smoke runtime must stay inside its allowed runtime root'
}
$mods = Join-Path $runtime 'mods'
New-Item -ItemType Directory -Path $mods -Force | Out-Null
if ($Target -like '*-neoforge') {
    $fmlConfig = Join-Path $runtime 'config/fml.toml'
    $fmlTemplate = Join-Path ([string]$launch.guest) 'config/fml.toml'
    if (-not (Test-Path -LiteralPath $fmlTemplate -PathType Leaf)) { throw 'Prepared NeoForge launch has no FML config template' }
    $snapshot = Join-Path $Evidence 'fml-prelaunch.toml'
    $absent = Join-Path $Evidence 'fml-prelaunch.absent'
    Remove-Item -LiteralPath $snapshot, $absent -Force -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $fmlConfig -PathType Leaf) {
        Copy-Item -LiteralPath $fmlConfig -Destination $snapshot
        Remove-Item -LiteralPath $fmlConfig -Force
    } else {
        [IO.File]::WriteAllText($absent, '', [Text.UTF8Encoding]::new($false))
    }
    New-Item -ItemType Directory -Path (Split-Path $fmlConfig) -Force | Out-Null
    Copy-Item -LiteralPath $fmlTemplate -Destination $fmlConfig -Force
}
$manifest = Get-Content -LiteralPath (Join-Path $BundleDirectory 'mods/.ae2-crafting-time-run-mods.json') -Raw | ConvertFrom-Json
foreach ($name in $manifest) {
    if ([IO.Path]::GetFileName($name) -ne $name -or $name -notlike '*.jar') { throw 'Invalid bundle filename' }
}
Get-ChildItem -LiteralPath $mods -File -Filter '*.jar' | Remove-Item -Force
foreach ($name in $manifest) {
    $source = Join-Path $BundleDirectory "mods/$name"
    $destination = Join-Path $mods $name
    Copy-Item -LiteralPath $source -Destination $destination
    if ((Get-FileHash -LiteralPath $source).Hash -ne (Get-FileHash -LiteralPath $destination).Hash) {
        throw "Staged artifact hash differs: $name"
    }
}
Copy-Item -LiteralPath (Join-Path $BundleDirectory 'mods/.ae2-crafting-time-run-mods.json') -Destination $mods -Force
Copy-Item -LiteralPath (Join-Path $BundleDirectory 'profile.json') -Destination $Evidence -Force
$arguments = [Collections.Generic.List[string]]::new()
for ($i = 0; $i -lt $launch.arguments.Count; $i++) {
    $argument = [string]$launch.arguments[$i]
    if ($argument -match '^-Dae2craftingtime.test\.' -or $argument -match '^-Xm[xs]') { continue }
    if ($argument -in @('--gameDir', '--quickPlaySingleplayer', '--quickPlayMultiplayer') -or
            (($Role -or $ResourceFixtureId) -and $argument -in @('--username', '--uuid'))) { $i++; continue }
    $arguments.Add($argument)
}
$arguments.Insert(0, '-Xmx8G')
if ($Interactive) { $arguments.Insert(0, '-Dae2craftingtime.test.interactive=true') }
if ($ResourceFixtureOnly) { $arguments.Insert(0, '-Dae2craftingtime.test.resourceFixtureOnly=true') }
if ($Prewarm) {
    foreach ($property in @('prewarm=true',"prewarmDeadline=$PrewarmDeadline","prewarmHead=$PrewarmHead",
            "prewarmBundle=$($PrewarmBundle.ToLowerInvariant())","prewarmEpoch=$CampaignId")) {
        $arguments.Insert(0,"-Dae2craftingtime.test.$property")
    }
}
if ('rxYaglEe' -in @($ProjectId)) { $arguments.Insert(0, '-Dae2craftingtime.test.advancedStatus=true') }
foreach ($property in $(if ($ObservationMode) { @() } else {
        @("scenario=$Scenario", "profile=$Profile", "world=$World", "output=$Evidence", 'vmTextureProbe=true') })) {
    $arguments.Insert(0, "-Dae2craftingtime.test.$property")
}
if ($ObservationMode -and $ClientInstalled) {
    if (!$ObservationFile -or !$ConnectionEpoch -or $ProductionSha256 -notmatch '^[A-Fa-f0-9]{64}$' -or
        $DriverSha256 -notmatch '^[A-Fa-f0-9]{64}$') { throw 'Incomplete client observation identity' }
    foreach ($property in @('observeConnection=true',"target=$Target",'role=client',
            "observationFile=$ObservationFile","connectionEpoch=$ConnectionEpoch",
            "productionSha256=$ProductionSha256","driverSha256=$DriverSha256")) {
        $arguments.Insert(0, "-Dae2craftingtime.test.$property")
    }
    if ($ExpectUnsupportedPeer) { $arguments.Insert(0, '-Dae2craftingtime.test.expectUnsupportedPeer=true') }
}
if ($CampaignId) {
    if ($CampaignId -cnotmatch '^[A-Za-z0-9._-]{1,128}$') { throw 'Invalid UI-smoke campaign identity' }
    $arguments.Insert(0, "-Dae2craftingtime.test.campaign=$CampaignId")
}
if ($Role) {
    $expectedName = 'Ae2ctAlpha'
    $expectedUuid = '446b6d0ccadd3e57baf699d70f01a628'
    if ($Scenario -notin @('recurrent-plan','stored-variant-plan') -or $DedicatedAddress -notmatch '^(127\.0\.0\.1|localhost):[0-9]{1,5}$' -or
            $OfflineName -cne $expectedName -or $OfflineUuid -cne $expectedUuid) {
        throw 'Recurrent connected role requires its bounded offline identity'
    }
    $arguments.Insert(0, "-Dae2craftingtime.test.role=$Role")
    $arguments.Add('--username'); $arguments.Add($OfflineName)
    $arguments.Add('--uuid'); $arguments.Add($OfflineUuid)
}
if ($ResourceFixtureId) {
    if ($ResourceFixtureId -cnotmatch '^[a-f0-9]{32}$') { throw 'Resource fixture launch identity is required' }
    $arguments.Insert(0, "-Dae2craftingtime.test.resourceFixture=$ResourceFixtureId")
    if ($HeadSha -cnotmatch '^[a-f0-9]{40}$' -or $GraphIdentity -cnotmatch '^[a-fA-F0-9]{64}$') {
        throw 'Resource icon evidence requires a commit and dependency graph identity'
    }
    $arguments.Insert(0, "-Dae2craftingtime.test.headSha=$HeadSha")
    $arguments.Insert(0, "-Dae2craftingtime.test.graph=$($GraphIdentity.ToLowerInvariant())")
}
if ($ResourceFixtureId) {
    $arguments.Add('--username'); $arguments.Add('Ae2ctAlpha')
    $arguments.Add('--uuid'); $arguments.Add('446b6d0ccadd3e57baf699d70f01a628')
}
if ($ContinuationPath) {
    $continuation = [IO.Path]::GetFullPath($ContinuationPath)
    if (!(Test-Path -LiteralPath $continuation -PathType Leaf) -or
            !($continuation.StartsWith(([IO.Path]::GetFullPath($Evidence)).TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase))) {
        throw 'Relaunch continuation must be an existing evidence file'
    }
    $arguments.Insert(0, "-Dae2craftingtime.test.continuation=$continuation")
}
if ($Scenario -eq 'standard-status-controls') {
    $arguments.Insert(0, '-Dae2craftingtime.test.statusRelaunch=true')
}
if ($Scenario -eq 'badge-background') {
    $arguments.Insert(0, '-Dae2craftingtime.test.badgeRelaunch=true')
}
if ($ResumeOnly) {
    if (!$ContinuationPath -or $Scenario -ne 'cpu-list-total-ttc') { throw 'Resume-only launch requires a CPU-list continuation' }
    $arguments.Insert(0, '-Dae2craftingtime.test.resumeOnly=true')
}
if ($DedicatedAddress -and !$ObservationMode) {
    if (-not $ControlDirectory) { throw 'Connected dedicated launch requires a control directory' }
    $arguments.Insert(0, '-Dae2craftingtime.test.connectedDedicated=true')
    $arguments.Insert(0, "-Dae2craftingtime.test.dedicatedAddress=$DedicatedAddress")
    $arguments.Insert(0, "-Dae2craftingtime.test.control=$([IO.Path]::GetFullPath($ControlDirectory))")
}
$arguments.Add('--gameDir'); $arguments.Add($runtime)
if ($ObservationMode) {
    if ($DedicatedAddress -notmatch '^(127\.0\.0\.1|localhost):[0-9]{1,5}$') { throw 'Observation requires a loopback dedicated address' }
    $arguments.Add('--quickPlayMultiplayer'); $arguments.Add($DedicatedAddress)
} elseif (-not $DedicatedAddress) { $arguments.Add('--quickPlaySingleplayer'); $arguments.Add($World) }
$argsFile = Join-Path $runtime 'ui-smoke-java.args'
$quoted = @($arguments | ForEach-Object { '"' + $_.Replace('\', '\\').Replace('"', '\"') + '"' })
[IO.File]::WriteAllLines($argsFile, $quoted, [Text.UTF8Encoding]::new($false))
$javaHome = & (Join-Path $PSScriptRoot 'get-java-home.ps1') -Major $bundle.java
[pscustomobject]@{ executable = (Join-Path $javaHome 'bin/java.exe'); arguments = ('@"' + $argsFile + '"'); finalApproval = !$ResumeOnly }
