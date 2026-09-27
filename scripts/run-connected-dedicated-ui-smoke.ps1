param(
    [Parameter(Mandatory)][ValidateSet('1.20.1-forge','1.20.1-fabric','1.21.1-neoforge','26.1.2-neoforge')][string]$Target,
    [Parameter(Mandatory)][string]$ServerDirectory,
    [Parameter(Mandatory)][string]$PreparedLaunch,
    [Parameter(Mandatory)][string]$BundleDirectory,
    [Parameter(Mandatory)][string]$ReportDirectory,
    [ValidatePattern('^[a-f0-9]{40}$')][string]$HeadSha,
    [string]$Address = '127.0.0.1:25565',
    [string]$JavaHome,
    [ValidateRange(1, 1800)][int]$ServerStartupTimeoutSeconds = 300,
    [switch]$ScheduledJava,
    [string]$InteractiveUser = 'Codex',
    [ValidateSet('cpu-list-total-ttc','recurrent-plan','stored-variant-plan','delayed-resource-icons','appmek-resource-icons')][string]$Scenario = 'cpu-list-total-ttc',
    [switch]$ResourceFixtureOnly,
    [switch]$Prewarm,
    [switch]$AcceptMinecraftEula,
    [switch]$PlanOnly,
    [switch]$ObservationMode,
    [ValidateSet('both','server-only','client-only','neither')][string]$InstallationMode = 'both',
    [string]$NativeWorldDirectory,
    [ValidateRange(60,3600)][int]$ManualTimeoutSeconds = 1200,
    [string]$ClientSessionDirectory,
    [ValidateRange(1,100)][int]$ConnectionOrdinal = 1,
    [switch]$KeepClientAlive
)
$ErrorActionPreference = 'Stop'
if ($ObservationMode -and (!$NativeWorldDirectory -or $Prewarm -or $ResourceFixtureOnly)) {
    throw 'Observation requires a stopped native world and no scenario-specific fixture mode'
}
$serverInstalled = $InstallationMode -in @('both','server-only')
$clientInstalled = $InstallationMode -in @('both','client-only')
$observationClientRetained = $false
function Stop-RetainedObservationClient {
    if (!$observationClientRetained) { return }
    $sessionRoot = if ($ClientSessionDirectory) { $ClientSessionDirectory } else { Join-Path $report 'client-session' }
    $session = Get-Content -LiteralPath (Join-Path $sessionRoot 'session.json') -Raw | ConvertFrom-Json
    $owned = Get-Process -Id $session.processId -ErrorAction SilentlyContinue
    if ($owned -and $owned.StartTime.ToUniversalTime().Ticks -eq ([datetime]$session.processStartedAt).ToUniversalTime().Ticks -and
        $owned.Path -ieq $session.executable) {
        $owned.Kill(); $owned.WaitForExit(); $owned.Dispose()
    }
}
. (Join-Path $PSScriptRoot 'resource-fixture-contract.ps1')
. (Join-Path $PSScriptRoot 'ui-smoke-dependency-identity.ps1')
. (Join-Path $PSScriptRoot 'dedicated-source-contract.ps1')
if (!$PlanOnly -and !$AcceptMinecraftEula) { throw 'Connected execution requires explicit -AcceptMinecraftEula consent' }
$resourceScenario = $Scenario -in @('delayed-resource-icons','appmek-resource-icons')
if ($Prewarm -and (!$resourceScenario -or $HeadSha -cnotmatch '^[a-f0-9]{40}$')) {
    throw 'Prewarm requires resource fixture mode and the complete tested head'
}
function Get-ResourceFixtureScreenshots([string[]]$FixtureCases, [bool]$Production = $false) {
    $values = foreach ($fixtureCase in $FixtureCases) {
        $prefix = $fixtureCase.ToLowerInvariant().Replace('_','-')
        $checkpoints = @('held') + $(if ($Production -and $fixtureCase -ceq 'WATER') { @('resource-reloaded','chunk-reloaded') }) +
            @('rejoined') + $(if ($fixtureCase.EndsWith('OVERLAP')) { @('winner-promoted') } else { @() }) +
            @('completed','cancel-held') +
            $(if ($Production -and $fixtureCase.EndsWith('OVERLAP')) { @('provider-removed') }) + @('cancelled')
        foreach ($checkpoint in $checkpoints) { "$prefix-$checkpoint.png" }
    }
    $values += $FixtureCases[-1].ToLowerInvariant().Replace('_','-') + '-cleanup.png'
    return @($values)
}
if ($ResourceFixtureOnly -and !$resourceScenario) {
    throw 'ResourceFixtureOnly requires a resource fixture scenario'
}
if ($Scenario -eq 'appmek-resource-icons' -and $Target -notin @('1.20.1-forge','1.21.1-neoforge')) {
    throw 'AppMek resource fixtures are supported only on Forge 1.20.1 and NeoForge 1.21.1'
}
$sourceServer = [IO.Path]::GetFullPath($ServerDirectory)
$bundle = [IO.Path]::GetFullPath($BundleDirectory)
$dependencyIdentity = if ($resourceScenario -and !$ResourceFixtureOnly -and !$PlanOnly) {
    Get-UiSmokeDependencyIdentity $bundle
}
$prepared = [IO.Path]::GetFullPath($PreparedLaunch)
$report = [IO.Path]::GetFullPath($ReportDirectory)
if ($Address -notmatch '^(?<host>[^:]+):(?<port>\d+)$' -or [int]$Matches.port -lt 1 -or [int]$Matches.port -gt 65535) {
    throw 'Dedicated address must be host:port with a valid TCP port'
}
$serverHost = $Matches.host; $serverPort = [int]$Matches.port
if ($ObservationMode -and $serverPort -ge 65535) { throw 'Observation requires a free adjacent RCON port' }
if ($serverHost -notin @('127.0.0.1', 'localhost')) { throw 'Disposable dedicated smoke requires a loopback address' }
if (Test-Path -LiteralPath $report) { throw 'Connected smoke requires a new report directory to preserve prior evidence' }
if ($report.StartsWith($sourceServer.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The report must be outside the prepared source server'
}
if (!(Test-Path -LiteralPath $sourceServer -PathType Container)) { throw "Missing prepared dedicated server $sourceServer" }
if ((Get-Item -LiteralPath $sourceServer).Attributes -band [IO.FileAttributes]::ReparsePoint -or
        @(Get-ChildItem -LiteralPath $sourceServer -Recurse -Force -Attributes ReparsePoint).Count) {
    throw 'Prepared dedicated server must not contain filesystem links'
}
if (!(Test-Path -LiteralPath $prepared -PathType Leaf)) { throw "Missing prepared client launch $prepared" }
$markerPath = Join-Path $sourceServer '.ae2-crafting-time-dedicated-fixture.json'
if (!(Test-Path -LiteralPath $markerPath -PathType Leaf)) { throw 'Prepared server is not marked as an AE2 Crafting Time source fixture' }
$marker = Get-Content -LiteralPath $markerPath -Raw | ConvertFrom-Json
$provisionedSource = $null -ne $marker.provisioning
if ($provisionedSource) { Assert-DedicatedSeal $sourceServer | Out-Null }
if ($marker.schema -ne 2 -or $marker.sourceFixtureId -ne 'ae2-crafting-time' -or
        $marker.role -ne 'source' -or $marker.target -ne $Target) {
    throw 'Prepared server marker does not match the requested source fixture and target'
}
$profilePath = Join-Path $bundle 'profile.json'
if (!(Test-Path -LiteralPath $profilePath -PathType Leaf)) { throw 'Connected bundle has no profile.json' }
$allArtifacts = @(Get-ChildItem -LiteralPath (Join-Path $bundle 'mods') -File -Filter 'ae2-crafting-time-*.jar')
$drivers = @($allArtifacts | Where-Object BaseName -Match '-test-driver$')
$production = @($allArtifacts | Where-Object BaseName -NotMatch '-test-driver$')
if ($drivers.Count -ne 1 -or $production.Count -ne 1) {
    throw 'Connected dedicated smoke requires exactly one production and one test-driver artifact'
}
$artifacts = @($production[0], $drivers[0]) | ForEach-Object {
    [ordered]@{ path=$_.FullName; name=$_.Name; sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
}
if (@($artifacts | Where-Object { !$_.sha256 -or $_.sha256.Length -ne 64 }).Count) {
    throw 'Connected dedicated artifact validation did not produce SHA-256 identities'
}
$profile = Get-Content -LiteralPath $profilePath -Raw | ConvertFrom-Json
$expectedJava = if ($Target -like '1.20.1-*') { 17 } elseif ($Target -eq '1.21.1-neoforge') { 21 } else { 25 }
$targetParts = $Target.Split('-')
if ($profile.target -ne $Target -or $profile.java -ne $expectedJava -or !$profile.loader) {
    throw 'Connected bundle target, Java, and loader must match the requested target'
}
if ($production[0].Name -notlike "*-$($targetParts[1])-$($targetParts[0]).jar" -or
        $drivers[0].BaseName -ne ($production[0].BaseName + '-test-driver')) {
    throw 'Production and driver filenames must identify the same version and target'
}
$preparedProfile = Get-Content -LiteralPath $prepared -Raw | ConvertFrom-Json
if ($Prewarm) {
    $prewarmGraph = Get-DedicatedGraph $Target $bundle $prepared
    if ($prewarmGraph.headSha -cne $HeadSha) { throw 'Prewarm bundle does not identify the tested head' }
    $adapters = Read-DedicatedJson (Join-Path $bundle 'expected-adapters.json') 64KB
    if ($Scenario -eq 'delayed-resource-icons' -and @($adapters.PSObject.Properties).Count -ne 0) {
        throw 'Native prewarm requires the empty base adapter contract'
    }
    if ($Scenario -eq 'appmek-resource-icons' -and (@($adapters.PSObject.Properties).Count -ne 0 -or $prewarmGraph.graph -cne 'appmek')) {
        throw 'Chemical prewarm requires the focused AppMek shared-hooks graph and its empty adapter contract'
    }
    if ($Scenario -eq 'delayed-resource-icons' -and $prewarmGraph.graph -cne 'native') {
        throw 'Native prewarm requires the native dependency graph'
    }
}
if ($preparedProfile.target -ne $Target -or $preparedProfile.java -ne $expectedJava) {
    throw 'Prepared client target and Java must match the connected server'
}
$loaderVersion = $profile.loader -replace ('^' + [regex]::Escape($targetParts[0]) + '-'), ''
$loader = if ($Target -eq '1.20.1-forge') { "net/minecraftforge/forge/1.20.1-$loaderVersion" }
    elseif ($Target -like '*-neoforge') { "net/neoforged/neoforge/$($profile.loader)" }
    else { $null }
$launcherRelative = if ($Target -eq '1.20.1-fabric') { 'fabric-server-launch.jar' }
    else { "libraries/$loader/win_args.txt" }
$launcher = Join-Path $sourceServer $launcherRelative
if (!(Test-Path -LiteralPath $launcher -PathType Leaf)) { throw "Prepared server launcher is missing: $launcher" }
$sourceArtifacts = @(Get-ChildItem -LiteralPath (Join-Path $sourceServer 'mods') -File -Filter 'ae2-crafting-time-*.jar' -ErrorAction SilentlyContinue)
if ($sourceArtifacts.Count) { throw 'Prepared source fixture must not contain AE2 Crafting Time artifacts' }
$sourceDependencies = @(Get-ChildItem -LiteralPath (Join-Path $sourceServer 'mods') -File -Filter '*.jar' | Sort-Object Name | ForEach-Object {
    [ordered]@{name=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
})
$bundleDependencies = @(Get-ChildItem -LiteralPath (Join-Path $bundle 'mods') -File -Filter '*.jar' | Where-Object {
    $_.Name -notlike 'ae2-crafting-time-*.jar'
} | Sort-Object Name | ForEach-Object {
    [ordered]@{name=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
})
$sourceDependencyKeys = @($sourceDependencies | ForEach-Object { "$($_.name)|$($_.sha256)" })
$bundleDependencyKeys = @($bundleDependencies | ForEach-Object { "$($_.name)|$($_.sha256)" })
$markerDependencyKeys = @($marker.dependencies | Sort-Object name | ForEach-Object { "$($_.name)|$($_.sha256)" })
if ($marker.javaMajor -ne $expectedJava -or $marker.loader -ne $profile.loader -or
        $marker.launcher.path -ne $launcherRelative.Replace('\\', '/') -or
        $marker.launcher.sha256 -ne (Get-FileHash -LiteralPath $launcher -Algorithm SHA256).Hash -or
        @(Compare-Object $sourceDependencyKeys $bundleDependencyKeys -SyncWindow 0).Count -or
        @(Compare-Object $sourceDependencyKeys $markerDependencyKeys -SyncWindow 0).Count) {
    throw 'Prepared source loader, Java, launcher, or dependency identity does not match its marker and connected bundle'
}
if (!$JavaHome) { $JavaHome = & (Join-Path $PSScriptRoot 'get-java-home.ps1') -Major $profile.java }
$java = Join-Path ([IO.Path]::GetFullPath($JavaHome)) 'bin/java.exe'
if (!(Test-Path -LiteralPath $java -PathType Leaf)) { throw "Java executable is missing: $java" }
$campaignId = [guid]::NewGuid().ToString('N')
$connectionEpoch = [guid]::NewGuid().ToString('N')
$connectionFixture = [guid]::NewGuid().ToString('N')

# All inputs are validated before the disposable copy is created or mutated.
New-Item -ItemType Directory -Path $report -Force | Out-Null
$runtimeRoot = Join-Path $report 'runtime'
$server = Join-Path $runtimeRoot "server-$Target"
$resolvedRuntime = [IO.Path]::GetFullPath($runtimeRoot)
$resolvedServer = [IO.Path]::GetFullPath($server)
if (!$resolvedServer.StartsWith($resolvedRuntime.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Disposable server path escapes the report runtime directory'
}
New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
Copy-Item -LiteralPath $sourceServer -Destination $resolvedServer -Recurse
if ($provisionedSource) {
    Assert-DedicatedSeal $resolvedServer | Out-Null
    Assert-DedicatedSeal $sourceServer | Out-Null
}
# A read-only VMware source share projects that attribute onto copied files.
# The copy is report-owned and must be writable; the validated source stays untouched.
foreach ($entry in @(Get-Item -LiteralPath $resolvedServer) + @(Get-ChildItem -LiteralPath $resolvedServer -Recurse -Force)) {
    if ($entry.Attributes -band [IO.FileAttributes]::ReadOnly) {
        $entry.Attributes = $entry.Attributes -bxor [IO.FileAttributes]::ReadOnly
    }
}
$copyMarker = [ordered]@{ schema=2; sourceFixtureId='ae2-crafting-time'; role='disposable'; target=$Target
    source=[IO.Path]::GetFullPath($sourceServer); createdAt=[DateTime]::UtcNow.ToString('o') }
$copyMarker | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $resolvedServer '.ae2-crafting-time-dedicated-fixture.json') -Encoding UTF8
$validatedCopy = Get-Content -LiteralPath (Join-Path $resolvedServer '.ae2-crafting-time-dedicated-fixture.json') -Raw | ConvertFrom-Json
if ($validatedCopy.role -ne 'disposable' -or $validatedCopy.source -ne $sourceServer -or $resolvedServer -eq $sourceServer) {
    throw 'Connected runner refused an unmarked or in-place dedicated server'
}
$copiedWorld = [IO.Path]::GetFullPath((Join-Path $resolvedServer 'ae2ct-cpu-list-connected'))
if (!$copiedWorld.StartsWith($resolvedServer.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Disposable world path escapes its server directory'
}
if (Test-Path -LiteralPath $copiedWorld) { Remove-Item -LiteralPath $copiedWorld -Recurse -Force }
if ($ObservationMode) {
    $nativeWorld = [IO.Path]::GetFullPath($NativeWorldDirectory)
    $nativeMarkerPath = Join-Path $nativeWorld '.ae2-crafting-time-native-world.json'
    if (!(Test-Path -LiteralPath $nativeWorld -PathType Container) -or
        !(Test-Path -LiteralPath (Join-Path $nativeWorld 'level.dat') -PathType Leaf) -or
        !(Test-Path -LiteralPath $nativeMarkerPath -PathType Leaf) -or
        $nativeWorld.StartsWith($resolvedServer.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Observation requires a marked stopped native world outside the disposable server'
    }
    $nativeMarker = Get-Content -LiteralPath $nativeMarkerPath -Raw | ConvertFrom-Json
    if ($nativeMarker.target -cne $Target -or $nativeMarker.stopped -ne $true -or
        $nativeMarker.driverRegistryClean -ne $true -or
        $nativeMarker.levelDatSha256 -ine (Get-FileHash -LiteralPath (Join-Path $nativeWorld 'level.dat') -Algorithm SHA256).Hash) {
        throw 'Native world identity or reviewed registry-clean attestation failed'
    }
    Copy-Item -LiteralPath $nativeWorld -Destination $copiedWorld -Recurse
}

$control = Join-Path $report 'control'
$serverResult = Join-Path $report 'server-result.json'
$serverOut = Join-Path $report 'server.stdout.log'
$serverErr = Join-Path $report 'server.stderr.log'
New-Item -ItemType Directory -Path $control -Force | Out-Null
$mods = Join-Path $resolvedServer 'mods'
New-Item -ItemType Directory -Path $mods -Force | Out-Null
Get-ChildItem -LiteralPath $mods -File -Filter 'ae2-crafting-time-*.jar' | Remove-Item -Force
if (!$ObservationMode -or $serverInstalled) {
    foreach ($artifact in $artifacts) { Copy-Item -LiteralPath $artifact.path -Destination (Join-Path $mods $artifact.name) }
}
[ordered]@{ target=$Target; sourceServer=$sourceServer; disposableServer=$resolvedServer
    artifacts=@($artifacts | ForEach-Object { [ordered]@{name=$_.name;sha256=$_.sha256} })
    dependencies=@(Get-ChildItem -LiteralPath $mods -File -Filter '*.jar' | Sort-Object Name | ForEach-Object {
        [ordered]@{name=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
    }) } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $report 'dedicated-artifacts.json') -Encoding UTF8
if ($AcceptMinecraftEula) {
    Set-Content -LiteralPath (Join-Path $resolvedServer 'eula.txt') -Value 'eula=true' -Encoding Ascii
}
@('level-name=ae2ct-cpu-list-connected','online-mode=false','enforce-secure-profile=false', 'server-ip=127.0.0.1', "server-port=$serverPort",
    'pause-when-empty-seconds=-1','view-distance=6','simulation-distance=6') +
    $(if ($ObservationMode) {
        $rconPassword = [guid]::NewGuid().ToString('N')
        @('enable-rcon=true',"rcon.port=$($serverPort + 1)","rcon.password=$rconPassword")
    } else { @() }) |
    Set-Content -LiteralPath (Join-Path $resolvedServer 'server.properties') -Encoding Ascii

$serverArgs = @("-Dae2ct.testDriver.serverScenario=$Scenario-connected",
    "-Dae2ct.testDriver.serverTarget=$Target", "-Dae2ct.testDriver.serverResult=$serverResult",
    "-Dae2ct.testDriver.serverControl=$control", "-Dae2ct.testDriver.serverCampaign=$connectionEpoch",
    "-Dae2craftingtime.test.resourceFixture=$connectionFixture", '-Xmx4G')
if ($ObservationMode) {
    $serverArgs = @('-Xmx4G')
    if ($serverInstalled) {
        $serverArgs = @('-Dae2craftingtime.test.observeConnection=true',
            "-Dae2craftingtime.test.expectUnsupportedPeer=$($InstallationMode -eq 'server-only')",
            "-Dae2craftingtime.test.target=$Target",'-Dae2craftingtime.test.role=server',
            "-Dae2craftingtime.test.connectionEpoch=$connectionEpoch",
            "-Dae2craftingtime.test.observationFile=$(Join-Path $report 'server-observation-{epoch}.json')",
            "-Dae2craftingtime.test.productionSha256=$($artifacts[0].sha256)",
            "-Dae2craftingtime.test.driverSha256=$($artifacts[1].sha256)",'-Xmx4G')
    }
}
if ($ResourceFixtureOnly) { $serverArgs = @('-Dae2craftingtime.test.resourceFixtureOnly=true') + $serverArgs }
if ($Prewarm) {
    $prewarmDeadline = [DateTimeOffset]::UtcNow.AddSeconds(600).ToUnixTimeMilliseconds()
    foreach ($property in @('prewarm=true',"prewarmDeadline=$prewarmDeadline","prewarmHead=$HeadSha",
            "prewarmBundle=$($prewarmGraph.bundleSha256)","prewarmEpoch=$connectionEpoch")) {
        $serverArgs = @("-Dae2craftingtime.test.$property") + $serverArgs
    }
}
if ($Target -eq '1.20.1-fabric') { $serverArgs += @('-jar','fabric-server-launch.jar','nogui') }
$argsFile = Join-Path $report 'dedicated-java.args'
$quotedServerArgs = @($serverArgs | ForEach-Object { '"' + $_.Replace('\', '\\').Replace('"', '\"') + '"' })
[IO.File]::WriteAllLines($argsFile, $quotedServerArgs, [Text.UTF8Encoding]::new($false))
$launchArguments = @("@$argsFile")
if ($Target -ne '1.20.1-fabric') { $launchArguments += @("@libraries/$loader/win_args.txt", 'nogui') }
$launchCommandLine = ($launchArguments | ForEach-Object {
    '"' + ($_ -replace '(\\*)"', '$1$1\"' -replace '(\\+)$', '$1$1') + '"'
}) -join ' '
$planPath = Join-Path $report 'connected-runner-plan.json'
$sourceIdentity = [ordered]@{ markerSha256=(Get-FileHash -LiteralPath $markerPath -Algorithm SHA256).Hash
    launcherSha256=(Get-FileHash -LiteralPath $launcher -Algorithm SHA256).Hash
    loader=$profile.loader; javaMajor=$expectedJava; javaPath=$java; javaVersion="required-$expectedJava"
    dependencies=$sourceDependencies;provisioned=$provisionedSource
    provisioning=$(if($provisionedSource){$marker.provisioning}else{$null}) }
$runnerPlan = [ordered]@{ target=$Target; headSha=$HeadSha; campaignId=$campaignId; connectionEpoch=$connectionEpoch; resourceFixture=$connectionFixture
    sourceServer=$sourceServer; disposableServer=$resolvedServer; java=$java
    sourceIdentity=$sourceIdentity; relaunch=[ordered]@{required=($Scenario -eq 'cpu-list-total-ttc');minimumProcesses=$(if ($Scenario -eq 'cpu-list-total-ttc') { 2 } else { 1 })}
    scheduledJava=$ScheduledJava.IsPresent; interactiveUser=$InteractiveUser
    argumentFile=$argsFile; arguments=$serverArgs; launchArguments=$launchArguments
    launchCommandLine=$launchCommandLine; preparedLaunch=$prepared; address=$Address }
$runnerPlan |
    ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $planPath -Encoding UTF8
if ($PlanOnly) { Write-Host "Connected runner plan validated: $planPath"; return }
$javaVersionOut = Join-Path $report 'java-version.stdout.log'
$javaVersionErr = Join-Path $report 'java-version.stderr.log'
$javaVersionProcess = Start-Process -FilePath $java -ArgumentList '-version' -Wait -PassThru -WindowStyle Hidden `
    -RedirectStandardOutput $javaVersionOut -RedirectStandardError $javaVersionErr
$javaIdentity = @(
    Get-Content -LiteralPath $javaVersionOut
    Get-Content -LiteralPath $javaVersionErr
) -join "`n"
$javaMajorPattern = '(?:version |openjdk )"?{0}(?:\.|\")' -f $expectedJava
if ($javaVersionProcess.ExitCode -ne 0 -or $javaIdentity -notmatch $javaMajorPattern) {
    throw "Dedicated Java runtime does not match required major $expectedJava"
}
$sourceIdentity.javaVersion = $javaIdentity
$runnerPlan | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $planPath -Encoding UTF8

$portReservation = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $serverPort)
try { $portReservation.Start() } catch { throw "Dedicated smoke port $serverPort is already in use" }
finally { $portReservation.Stop() }
if ($ObservationMode) {
    $rconReservation = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $serverPort + 1)
    try { $rconReservation.Start() } catch { throw "Observation RCON port $($serverPort + 1) is already in use" }
    finally { $rconReservation.Stop() }
}

$serverProcess = Start-Process -FilePath $java -ArgumentList $launchCommandLine -WorkingDirectory $resolvedServer `
    -PassThru -WindowStyle Hidden -RedirectStandardOutput $serverOut -RedirectStandardError $serverErr
$serverStartedAt = $serverProcess.StartTime.ToUniversalTime()
try {
    $deadline = if ($Prewarm) { [DateTimeOffset]::FromUnixTimeMilliseconds($prewarmDeadline).UtcDateTime }
        else { [DateTime]::UtcNow.AddSeconds($ServerStartupTimeoutSeconds) }
    $ready = $false
    while ([DateTime]::UtcNow -lt $deadline -and !$ready) {
        if ($serverProcess.HasExited) { throw "Dedicated server exited $($serverProcess.ExitCode) before accepting a client" }
        $probe = [Net.Sockets.TcpClient]::new()
        try {
            $connect = $probe.ConnectAsync($serverHost, $serverPort)
            $ready = $connect.Wait(250) -and $probe.Connected
        } catch { $ready = $false } finally { $probe.Dispose() }
    }
    if ([DateTime]::UtcNow -ge $deadline) { throw 'Dedicated server did not become ready' }
    $ready = $false
    $serverLog = Join-Path $resolvedServer 'logs/latest.log'
    while ([DateTime]::UtcNow -lt $deadline -and !$ready) {
        if ($serverProcess.HasExited) { throw "Dedicated server exited $($serverProcess.ExitCode) during startup" }
        if (Test-Path -LiteralPath $serverLog -PathType Leaf) {
            $ready = [bool](Select-String -LiteralPath $serverLog -SimpleMatch ']: Done (' -Quiet)
        }
        if (!$ready) { Start-Sleep -Milliseconds 250 }
    }
    if (!$ready) { throw 'Dedicated server did not finish startup' }
    if ($ObservationMode) {
        & (Join-Path $PSScriptRoot 'run-connection-observation-cell.ps1') -Target $Target `
            -InstallationMode $InstallationMode -ReportDirectory $report -BundleDirectory $bundle `
            -PreparedLaunch $prepared -Address $Address -ConnectionEpoch $connectionEpoch `
            -ProductionSha256 $artifacts[0].sha256 -DriverSha256 $artifacts[1].sha256 `
            -ServerStartedAtUtc $serverStartedAt -ManualTimeoutSeconds $ManualTimeoutSeconds `
            -ClientSessionDirectory $ClientSessionDirectory -ConnectionOrdinal $ConnectionOrdinal -KeepClientAlive:$KeepClientAlive
        $observationClientRetained = [bool]$KeepClientAlive
        if ($serverProcess.HasExited) { throw "Dedicated server exited $($serverProcess.ExitCode) during observation" }
        return
    }
    $clientParameters = @{ Target=$Target; Scenario=$Scenario; ReportDirectory=(Join-Path $report 'client')
        BundleDirectory=$bundle; PreparedLaunch=$prepared; DedicatedAddress=$Address
        ControlDirectory=$control; CampaignId=$connectionEpoch; ResourceFixtureId=$connectionFixture; FailOnInitialDisconnect=$true
        StartupTimeoutSeconds=$ServerStartupTimeoutSeconds }
    if ($ResourceFixtureOnly) { $clientParameters.ResourceFixtureOnly = $true }
    if ($Prewarm) {
        $clientParameters.Prewarm=$true; $clientParameters.PrewarmDeadline=$prewarmDeadline
        $clientParameters.PrewarmBundle=$prewarmGraph.bundleSha256
        $clientParameters.PrewarmServerProcessId=$serverProcess.Id
        $clientParameters.PrewarmServerStartedAt=$serverProcess.StartTime.ToUniversalTime()
        $clientParameters.FailOnInitialDisconnect=$false
        $clientParameters.StartupTimeoutSeconds=300
    }
    if ($resourceScenario) {
        $clientParameters.OfflineName = 'Ae2ctAlpha'
        $clientParameters.OfflineUuid = '446b6d0ccadd3e57baf699d70f01a628'
    }
    if ($Scenario -in @('recurrent-plan','stored-variant-plan')) {
        $clientParameters.Role = 'alpha'; $clientParameters.OfflineName = 'Ae2ctAlpha'
        $clientParameters.OfflineUuid = '446b6d0ccadd3e57baf699d70f01a628'
    }
    if ($HeadSha) { $clientParameters.HeadSha = $HeadSha }
    if ($ScheduledJava) { $clientParameters.ScheduledJava = $true; $clientParameters.InteractiveUser = $InteractiveUser }
    $attempts = @()
    $attemptLedger = Join-Path $report 'client-attempts.json'
    $clientPassed = $false
    $maxAttempts = 3
    if ($Prewarm) { $maxAttempts = 1 }
    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
        $attemptReport = Join-Path $report "client-attempt-$attempt"
        $clientParameters.ReportDirectory = $attemptReport
        $clientParameters.RuntimeDirectory = Join-Path $attemptReport 'runtime'
        $clientError = $null
        try {
            & (Join-Path $PSScriptRoot 'run-ui-smoke.ps1') @clientParameters
            if ($LASTEXITCODE) { throw "Connected client smoke exited $LASTEXITCODE" }
            $clientPassed = $true
        } catch {
            $clientError = $_
        }
        $statusPath = Join-Path $attemptReport 'status.json'
        $progressPath = Join-Path $attemptReport 'evidence/driver-progress.json'
        $fixturePath = Join-Path $attemptReport 'evidence/fixture-hashes.json'
        $status = if (Test-Path -LiteralPath $statusPath -PathType Leaf) {
            Get-Content -LiteralPath $statusPath -Raw | ConvertFrom-Json
        } else { $null }
        $progress = if (Test-Path -LiteralPath $progressPath -PathType Leaf) {
            Get-Content -LiteralPath $progressPath -Raw | ConvertFrom-Json
        } else { $null }
        $fixture = if (Test-Path -LiteralPath $fixturePath -PathType Leaf) {
            Get-Content -LiteralPath $fixturePath -Raw | ConvertFrom-Json
        } else { $null }
        $processes = @($status.processes)
        $startupFailure = $null -ne $clientError -and $status.phase -eq 'failed' -and
            $processes.Count -eq 1 -and $processes[0].phase -eq 1 -and
            $progress.pid -eq $processes[0].pid -and
            $status.message -in @('UI-smoke phase 1 watchdog: initial-disconnect',
                'UI-smoke phase 1 watchdog: startup-timeout') -and
            !(Test-Path -LiteralPath (Join-Path $attemptReport 'evidence/result.json')) -and
            !(Test-Path -LiteralPath (Join-Path $attemptReport 'evidence/relaunch-evidence.json')) -and
            (!$resourceScenario -or !(Test-Path -LiteralPath (Join-Path $control 'resource/command.properties')))
        $processAlive = $processes.Count -and $null -ne (Get-Process -Id $processes[0].pid -ErrorAction SilentlyContinue)
        $taskAlive = $ScheduledJava -and $processes.Count -and $processes[0].taskName -and
            $null -ne (Get-ScheduledTask -TaskName $processes[0].taskName -ErrorAction SilentlyContinue)
        $worldsRemoved = $fixture -and @($fixture.disposableWorlds | Where-Object { !$_.removed }).Count -eq 0
        $retry = $startupFailure -and !$processAlive -and !$taskAlive -and $worldsRemoved -and $attempt -lt $maxAttempts
        $reason = if ($clientPassed) { 'PASS' }
            elseif ($startupFailure -and $attempt -eq $maxAttempts) { 'startup-failure-cap-exhausted' }
            elseif ($retry) { 'startup-failure-retry' }
            else { $clientError.Exception.Message }
        $attempts += [ordered]@{ attempt=$attempt; report=$(if($clientPassed){'client'}else{Split-Path -Leaf $attemptReport}); result=$(if($clientPassed){'PASS'}else{'FAIL'})
            reason=$reason; checkpoint=$(if($progress){$progress.checkpoint}else{$null}); processes=@($processes | ForEach-Object {
                [ordered]@{ phase=$_.phase; pid=$_.pid; startedAt=$_.startedAt; exitedAt=$_.exitedAt; exitCode=$_.exitCode }
            }) }
        ConvertTo-Json -InputObject @($attempts) -Depth 5 | Set-Content -LiteralPath $attemptLedger -Encoding UTF8
        if ($clientPassed) {
            Move-Item -LiteralPath $attemptReport -Destination (Join-Path $report 'client')
            break
        }
        if ($retry) { continue }
        if ($startupFailure -and $attempt -eq $maxAttempts) {
            throw "Connected client exhausted $maxAttempts pre-fixture startup attempts"
        }
        throw $clientError
    }
    if (!$clientPassed) { throw 'Connected client smoke did not pass' }
    if (!$serverProcess.WaitForExit(60000)) { throw 'Dedicated server did not finish after client evidence completed' }
    if (!(Test-Path -LiteralPath $serverResult -PathType Leaf)) { throw 'Connected server produced no result artifact' }
    $result = Get-Content -LiteralPath $serverResult -Raw | ConvertFrom-Json
    if ($result.result -ne 'PASS') { throw "Connected dedicated server failed: $($result.error)" }
    $stateFiles = if ($resourceScenario) {
        @(Join-Path $control 'resource/state.properties')
    } elseif ($Scenario -eq 'recurrent-plan') {
        @(Join-Path $control 'alpha/state.properties')
    } elseif ($Scenario -eq 'stored-variant-plan') {
        @(Join-Path $control 'variant/state.properties')
    } else { @(Join-Path $control 'state.properties') }
    foreach ($required in @((Join-Path $resolvedServer 'logs/latest.log')) + @($stateFiles)) {
        if (!(Test-Path -LiteralPath $required -PathType Leaf)) { throw "Connected evidence is missing: $required" }
    }
    if ($resourceScenario) {
        $fixtureEvidencePath = Join-Path $report "client/evidence/resource-fixture-evidence.json"
        if (!(Test-Path -LiteralPath $fixtureEvidencePath -PathType Leaf)) { throw 'Resource fixture evidence is missing' }
        $fixtureEvidence = Get-Content -LiteralPath $fixtureEvidencePath -Raw | ConvertFrom-Json
        $fixtureEvidence | Add-Member -NotePropertyName sidecars -NotePropertyValue @($fixtureEvidence.screenshots | ForEach-Object {
            Get-Content -LiteralPath (Join-Path $report "client/evidence/$($_.name.Replace('.png','.json'))") -Raw | ConvertFrom-Json
        }) -Force
        Assert-ResourceFixtureContract $fixtureEvidence $Scenario $Target $true $connectionEpoch $connectionFixture | Out-Null
        if (!$ResourceFixtureOnly) {
            $iconPath = Join-Path $report 'client/evidence/resource-icon-evidence.json'
            if (!(Test-Path -LiteralPath $iconPath -PathType Leaf)) { throw 'Connected resource icon evidence is missing' }
            $icon = Get-Content -LiteralPath $iconPath -Raw | ConvertFrom-Json
            Assert-ResourceIconEvidence $icon $fixtureEvidence $HeadSha $dependencyIdentity.catalogueSha256
        }
        if ($fixtureEvidence.schema -ne 1 -or $fixtureEvidence.fixtureResult -ne 'PASS' -or
                $fixtureEvidence.productionIconAcceptance -ne 'NOT_RUN' -or !$fixtureEvidence.receipts.Count -or
                !$fixtureEvidence.screenshots.Count -or !$fixtureEvidence.clientObservations.Count) {
            throw 'Resource fixture evidence contract failed validation'
        }
        $expectedChecks = @('server-identity','real-dispatch','delayed-plates','native-locate','lifecycle',
            'capture-integrity','cleanup',$(if ($ResourceFixtureOnly) { 'fixture-only' } else { 'typed-keys' }))
        if ((Compare-Object $expectedChecks @($fixtureEvidence.checks.psobject.Properties.Name) -CaseSensitive) -or
                @($fixtureEvidence.checks.psobject.Properties | Where-Object { $_.Value -ne $true }).Count) {
            throw 'Resource fixture client checks are missing, unexpected, or false'
        }
        $expectedCases = if ($Scenario -eq 'appmek-resource-icons') {
            @('OXYGEN','HYDROGEN','CHEMICAL_OVERLAP')
        } else {
            @('ITEM','WATER','LAVA') + $(if ($Target -eq '1.20.1-forge') { @('BUCKETLESS') } else { @() }) + @('FLUID_OVERLAP')
        }
        $expectedCaptures = Get-ResourceFixtureScreenshots $expectedCases (!$ResourceFixtureOnly)
        if ($fixtureEvidence.connected -ne $true -or $fixtureEvidence.serverState.epoch.Replace('-','') -cne $connectionEpoch -or
                $fixtureEvidence.serverState.fixture.Replace('-','') -cne $connectionFixture -or !$fixtureEvidence.clientEvidence.digest -or
                $fixtureEvidence.clientEvidence.captures -ne $expectedCaptures.Count -or
                (Compare-Object $expectedCaptures @($fixtureEvidence.screenshots.name) -SyncWindow 0 -CaseSensitive) -or
                (Compare-Object $expectedCaptures @($fixtureEvidence.clientObservations.checkpoint) -SyncWindow 0 -CaseSensitive)) {
            throw 'Connected resource fixture capture/evidence acknowledgement is invalid'
        }
        foreach ($fixtureCase in $expectedCases) {
            $caseReceipts = @($fixtureEvidence.receipts | Where-Object { $_.case -ceq $fixtureCase })
            $requiredActions = @('CREATE') + $(if (!$ResourceFixtureOnly -and $fixtureCase -ceq 'WATER') { @('UNLOAD_RELOAD') }) +
                @('REJOIN_PREPARE','RECONNECT','RELEASE','RESET','CREATE','CANCEL','RESET')
            if ($fixtureCase.EndsWith('OVERLAP')) {
                $requiredActions = @('CREATE','REJOIN_PREPARE','RECONNECT','RELEASE','RELEASE','RESET','CREATE') +
                    $(if (!$ResourceFixtureOnly -and $fixtureCase -ceq $expectedCases[-1]) { @('REMOVE_PROVIDER') }) +
                    @('CANCEL','CANCEL','RESET')
            }
            if ($fixtureCase -ceq $expectedCases[-1]) { $requiredActions += 'COMPLETE' }
            if ((Compare-Object $requiredActions @($caseReceipts.action) -SyncWindow 0 -CaseSensitive) -or
                    @($caseReceipts | Where-Object { $_.sequence -le 0 -or $_.revision -le 0 -or
                        $_.ackRevision -ne $_.revision -or $_.stateRevision -ne
                            $(if ($_.action -ceq 'RESET') { $_.revision + 1 } else { $_.revision }) }).Count) {
                throw "Resource fixture receipts failed independent validation for $fixtureCase"
            }
        }
        if (Compare-Object $expectedCases @($fixtureEvidence.receipts.case | Select-Object -Unique) -CaseSensitive) {
            throw 'Resource fixture evidence contains a missing or unexpected case'
        }
        foreach ($capture in @($fixtureEvidence.screenshots)) {
            $capturePath = Join-Path $report "client/evidence/$($capture.name)"
            $sidecarPath = [IO.Path]::ChangeExtension($capturePath, '.json')
            if (!(Test-Path -LiteralPath $capturePath -PathType Leaf) -or
                    !(Test-Path -LiteralPath $sidecarPath -PathType Leaf) -or
                    (Get-FileHash -LiteralPath $capturePath -Algorithm SHA256).Hash -ine $capture.sha256) {
                throw "Resource fixture capture hash mismatch: $($capture.name)"
            }
            $sidecar = Get-Content -LiteralPath $sidecarPath -Raw | ConvertFrom-Json
            if ($sidecar.capture.sha256 -ine $capture.sha256 -or $sidecar.screen -cne 'world') {
                throw "Resource fixture semantic capture mismatch: $($capture.name)"
            }
        }
        $serverEvidence = $result.resourceEvidence
        Assert-ResourceFixtureServerTiming @($serverEvidence.receipts) | Out-Null
        if (!$ResourceFixtureOnly -and $Scenario -eq 'delayed-resource-icons') {
            Assert-ResourceFixtureServerUnloaded @($serverEvidence.receipts)
        }
        if ($serverEvidence.fixtureResult -cne 'PASS' -or $serverEvidence.epoch -cne $fixtureEvidence.serverState.epoch -or
                $serverEvidence.fixture -cne $fixtureEvidence.serverState.fixture -or
                $serverEvidence.player -cne $fixtureEvidence.player -or $serverEvidence.teardownComplete -ne $true -or
                $serverEvidence.clientEvidence.digest -cne $fixtureEvidence.clientEvidence.digest -or
                $serverEvidence.clientEvidence.captures -ne $fixtureEvidence.clientEvidence.captures -or
                $serverEvidence.clientEvidence.revision -ne $fixtureEvidence.clientEvidence.revision -or
                $result.resourceCleanup.liveCleanup -cne 'PASS' -or $result.resourceCleanup.originalFailure) {
            throw 'Authoritative server resource identity or cleanup evidence is invalid'
        }
        foreach ($fixtureCase in $expectedCases) {
            $facts = $serverEvidence.caseFacts.$fixtureCase
            if (!$facts -or $facts.storageValidated -eq $false -or
                    ($fixtureCase -ceq 'BUCKETLESS' -and ($facts.hasBucket -ne $false -or
                        $facts.tintArgb -ne -16711681 -or $facts.id -cne 'ae2craftingtime_test_driver:resource_fixture_fluid'))) {
                throw "Authoritative server resource facts are invalid for $fixtureCase"
            }
        }
        if (@($serverEvidence.receipts).Count -ne @($fixtureEvidence.receipts).Count) {
            throw 'Client and authoritative server receipt counts differ'
        }
        for ($i = 0; $i -lt @($serverEvidence.receipts).Count; $i++) {
            $serverReceipt = $serverEvidence.receipts[$i]
            $clientReceipt = $fixtureEvidence.receipts[$i]
            if ($serverReceipt.sequence -ne $clientReceipt.sequence -or
                    $serverReceipt.revision -ne $clientReceipt.revision -or
                    $serverReceipt.stateRevision -ne $clientReceipt.stateRevision -or
                    $serverReceipt.action -cne $clientReceipt.action -or
                    $serverReceipt.case -cne $clientReceipt.case -or
                    $serverReceipt.slot -ne $clientReceipt.slot -or
                    $serverReceipt.phase -cne $clientReceipt.phase -or
                    ($serverReceipt.jobs | ConvertTo-Json -Depth 12 -Compress) -cne
                        ($clientReceipt.jobs | ConvertTo-Json -Depth 12 -Compress)) {
                throw "Client and authoritative server receipt $i differ"
            }
            if ($serverReceipt.action -notin @('RESET','COMPLETE') -and !$serverReceipt.jobs.Count) {
                throw "Authoritative server receipt $i omitted its job snapshot"
            }
            if ($serverReceipt.serverTick -lt 0 -or ($serverReceipt.action -notin @('RESET','COMPLETE') -and
                    @($serverReceipt.jobs | Where-Object { !$_.resource -or !$_.keyFingerprint -or !$_.keyEncoding -or
                        !$_.cpu -or !$_.provider -or $_.rawAmount -le 0 -or $_.dispatchCount -ne 1 }).Count)) {
                throw "Authoritative server receipt $i has incomplete job facts"
            }
        }
        [ordered]@{schema=1;fixtureResult='PASS';productionIconAcceptance='NOT_RUN';scope='fixture-only'
            headSha=$HeadSha;target=$Target;scenario=$Scenario;campaignId=$campaignId;epoch=$connectionEpoch
            sourceIdentity=$sourceIdentity;artifacts=$artifacts;dependencies=$sourceDependencies
            client=$fixtureEvidence;server=$result} | ConvertTo-Json -Depth 20 |
            Set-Content -LiteralPath (Join-Path $report 'resource-fixture-evidence.json') -Encoding UTF8
    }
    Copy-Item -LiteralPath (Join-Path $resolvedServer 'logs/latest.log') -Destination (Join-Path $report 'server.latest.log')
    if ($Scenario -eq 'cpu-list-total-ttc') {
        Copy-Item -LiteralPath (Join-Path $control 'state.properties') -Destination (Join-Path $report 'server-estimates.properties')
    }
} catch {
    Stop-RetainedObservationClient
    throw
} finally {
    if ($ObservationMode -and !$serverProcess.HasExited) {
        try {
            & (Join-Path $PSScriptRoot 'stop-observation-server.ps1') -Port ($serverPort + 1) -Password $rconPassword
            if (!$serverProcess.WaitForExit(60000)) { throw 'Dedicated server did not stop within 60 seconds' }
            Set-Content -LiteralPath (Join-Path $report 'server-stop.txt') -Value 'clean' -Encoding Ascii
        } catch {
            Stop-RetainedObservationClient
            Set-Content -LiteralPath (Join-Path $report 'server-stop.txt') -Value "failed: $_" -Encoding UTF8
            if (!$serverProcess.HasExited) { $serverProcess.Kill(); $serverProcess.WaitForExit() }
            throw
        }
    } elseif (!$serverProcess.HasExited) { $serverProcess.Kill(); $serverProcess.WaitForExit() }
    $serverProcess.Dispose()
    if ($Prewarm) {
        $prewarmDirectory = Join-Path $control 'prewarm'
        $receipts = @(Get-ChildItem -LiteralPath $prewarmDirectory -File -ErrorAction SilentlyContinue | ForEach-Object {
            [ordered]@{name=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName).Hash;size=$_.Length;publishedAt=$_.LastWriteTimeUtc.ToString('o')}
        })
        $clientPrewarmPath = Join-Path $report 'client-attempt-1/prewarm-evidence.json'
        $clientPrewarm = if (Test-Path -LiteralPath $clientPrewarmPath) { Read-DedicatedJson $clientPrewarmPath 64KB } else { $null }
        [ordered]@{schema=1;readinessResult=$(if(Test-Path -LiteralPath (Join-Path $prewarmDirectory 'armed.json')){'READY'}else{'FAILED'})
            deadlineMillis=$prewarmDeadline;headSha=$HeadSha;bundleSha256=$prewarmGraph.bundleSha256
            phaseTimings=[ordered]@{startedAt=[DateTimeOffset]::FromUnixTimeMilliseconds($prewarmDeadline-600000).ToString('o')
                serverProcessStartedAt=$serverStartedAt.ToString('o');finishedAt=[DateTime]::UtcNow.ToString('o')}
            clientReadiness=$clientPrewarm
            receiptHashes=$receipts;serverCleanup='EXITED';sourceMarkerSha256=(Get-FileHash -LiteralPath $markerPath).Hash
            fixtureResult=$(if($clientPassed){'PASS'}else{'NOT_QUALIFIED'})} |
            ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $report 'prewarm-evidence.json') -Encoding UTF8
    }
    if ($provisionedSource) { Assert-DedicatedSeal $sourceServer | Out-Null }
}
