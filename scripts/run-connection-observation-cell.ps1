param(
    [Parameter(Mandatory)][string]$Target,
    [Parameter(Mandatory)][ValidateSet('both','server-only','client-only','neither')][string]$InstallationMode,
    [Parameter(Mandatory)][string]$ReportDirectory,
    [Parameter(Mandatory)][string]$BundleDirectory,
    [Parameter(Mandatory)][string]$PreparedLaunch,
    [Parameter(Mandatory)][string]$Address,
    [Parameter(Mandatory)][string]$ConnectionEpoch,
    [Parameter(Mandatory)][string]$ProductionSha256,
    [Parameter(Mandatory)][string]$DriverSha256,
    [Parameter(Mandatory)][datetime]$ServerStartedAtUtc,
    [Parameter(Mandatory)][int]$ManualTimeoutSeconds,
    [string]$ClientSessionDirectory,
    [ValidateRange(1,100)][int]$ConnectionOrdinal = 1,
    [switch]$KeepClientAlive
)
$ErrorActionPreference = 'Stop'
$clientInstalled = $InstallationMode -in @('both','client-only')
$serverInstalled = $InstallationMode -in @('both','server-only')
if (!$ClientSessionDirectory) { $ClientSessionDirectory = Join-Path $ReportDirectory 'client-session' }
$ClientSessionDirectory = [IO.Path]::GetFullPath($ClientSessionDirectory)
$sessionPath = Join-Path $ClientSessionDirectory 'session.json'
$session = if (Test-Path -LiteralPath $sessionPath -PathType Leaf) {
    Get-Content -LiteralPath $sessionPath -Raw | ConvertFrom-Json
} else { $null }
$cellStartedAt = [DateTime]::UtcNow
$client = $null
$success = $false
try {
if ($session) {
    if ($session.target -cne $Target -or $session.clientInstalled -ne $clientInstalled -or
        $session.productionSha256 -cne $ProductionSha256 -or $session.driverSha256 -cne $DriverSha256 -or
        $ConnectionOrdinal -ne ([int]$session.lastOrdinal + 1)) { throw 'Reusable client session identity or ordinal mismatch' }
    $candidate = Get-Process -Id $session.processId -ErrorAction Stop
    if ($candidate.StartTime.ToUniversalTime().Ticks -ne ([datetime]$session.processStartedAt).ToUniversalTime().Ticks -or
        $candidate.Path -ine $session.executable) { throw 'Reusable client process identity changed' }
    $client = $candidate
    $startedAt = [datetime]$session.startedAt
    $clientEpoch = $session.connectionEpoch
    $runtime = $session.runtime
} else {
if ($ConnectionOrdinal -ne 1 -or (Test-Path -LiteralPath $ClientSessionDirectory)) {
    throw 'First client connection requires a new session directory and ordinal 1'
}
$clientBundle = Join-Path $ReportDirectory 'client-bundle'
Copy-Item -LiteralPath $BundleDirectory -Destination $clientBundle -Recurse
$bundleMods = Join-Path $clientBundle 'mods'
$manifestPath = Join-Path $bundleMods '.ae2-crafting-time-run-mods.json'
$manifest = [string[]](ConvertFrom-Json -InputObject (Get-Content -LiteralPath $manifestPath -Raw))
if (!$clientInstalled) {
    $manifest = @($manifest | Where-Object { $_ -notlike 'ae2-crafting-time-*.jar' })
    Get-ChildItem -LiteralPath $bundleMods -File -Filter 'ae2-crafting-time-*.jar' | Remove-Item -Force
    ConvertTo-Json -InputObject $manifest | Set-Content -LiteralPath $manifestPath -Encoding UTF8
}
$evidence = Join-Path $ReportDirectory 'client/evidence'
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
$runtime = Join-Path $ClientSessionDirectory 'runtime'
$profile = Get-Content -LiteralPath (Join-Path $clientBundle 'profile.json') -Raw | ConvertFrom-Json
$launch = & (Join-Path $PSScriptRoot 'prepare-ui-smoke-launch.ps1') -LaunchManifest $PreparedLaunch `
    -BundleDirectory $clientBundle -RuntimeDirectory $runtime -AllowedRuntimeRoot $ClientSessionDirectory `
    -Target $Target -Profile $profile.profile -Scenario 'craft-plan' -World 'ae2ct-cpu-list-connected' `
    -Evidence $evidence -DedicatedAddress $Address -ObservationMode -ClientInstalled:$clientInstalled `
    -ExpectUnsupportedPeer:($InstallationMode -eq 'client-only') `
    -ObservationFile (Join-Path $ClientSessionDirectory 'client-observation-{epoch}.json') `
    -ProductionSha256 $ProductionSha256 -DriverSha256 $DriverSha256 -ConnectionEpoch $ConnectionEpoch
$startedAt = [DateTime]::UtcNow
$clientEpoch = $ConnectionEpoch
$client = Start-Process -FilePath $launch.executable -ArgumentList $launch.arguments -WorkingDirectory $runtime `
    -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $ClientSessionDirectory 'client.stdout.log') `
    -RedirectStandardError (Join-Path $ClientSessionDirectory 'client.stderr.log')
$session = [ordered]@{target=$Target;clientInstalled=$clientInstalled;productionSha256=$ProductionSha256;
    driverSha256=$DriverSha256;processId=$client.Id;processStartedAt=$client.StartTime.ToUniversalTime().ToString('o');
    executable=$client.Path;runtime=$runtime;startedAt=$startedAt.ToString('o');connectionEpoch=$clientEpoch;lastOrdinal=0}
$session | ConvertTo-Json | Set-Content -LiteralPath $sessionPath -Encoding UTF8
}
$evidence = Join-Path $ReportDirectory 'client/evidence'
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
$staged = @(Get-ChildItem -LiteralPath (Join-Path $runtime 'mods') -File -Filter '*.jar' | Sort-Object Name |
    ForEach-Object { [ordered]@{name=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash} })
if (@($staged | Where-Object { $_.name -like 'ae2-crafting-time-*.jar' }).Count -ne $(if($clientInstalled){2}else{0})) {
    throw 'Client installation inventory does not match the selected cell'
}
$expectedNames = [string[]](ConvertFrom-Json -InputObject (Get-Content -LiteralPath (Join-Path $BundleDirectory 'mods/.ae2-crafting-time-run-mods.json') -Raw))
if (!$clientInstalled) { $expectedNames = @($expectedNames | Where-Object { $_ -notlike 'ae2-crafting-time-*.jar' }) }
$expected = @($expectedNames | Sort-Object | ForEach-Object {
    if ([IO.Path]::GetFileName($_) -cne $_) { throw 'Invalid expected client artifact name' }
    "$($_):$((Get-FileHash -LiteralPath (Join-Path $BundleDirectory "mods/$_") -Algorithm SHA256).Hash)"
})
$actual = @($staged | ForEach-Object { "$($_.name):$($_.sha256)" })
if (Compare-Object $expected $actual) { throw 'Client artifact inventory differs from the current sealed bundle' }
[ordered]@{target=$Target;mode=$InstallationMode;createdAt=[DateTime]::UtcNow.ToString('o');artifacts=$staged} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $ReportDirectory 'client-artifacts.json') -Encoding UTF8
    $manualPath = Join-Path $ReportDirectory 'manual-checkpoints.json'
    $deadline = [DateTime]::UtcNow.AddSeconds($ManualTimeoutSeconds)
    while (!(Test-Path -LiteralPath $manualPath -PathType Leaf) -and [DateTime]::UtcNow -lt $deadline) {
        if ($client.HasExited) { throw "Native client exited before manual checkpoints: $($client.ExitCode)" }
        Start-Sleep -Seconds 1
    }
    if (!(Test-Path -LiteralPath $manualPath -PathType Leaf)) { throw 'Timed out waiting for native UI checkpoints' }
    $manual = Get-Content -LiteralPath $manualPath -Raw | ConvertFrom-Json
    if ($manual.target -cne $Target -or $manual.mode -cne $InstallationMode -or
        $manual.login -ne $true -or $manual.craft -ne $true -or
        $manual.clientProcessId -ne $client.Id -or
        $manual.connectionOrdinal -lt $ConnectionOrdinal -or $manual.connectionOrdinal -gt 100 -or
        ([datetime]$manual.observedAt).ToUniversalTime() -lt $cellStartedAt.ToUniversalTime()) {
        throw 'Manual checkpoint identity, login, craft, or freshness failed'
    }
    $observedOrdinal = [int]$manual.connectionOrdinal
    if ([string]$manual.connectionOrdinal -cne [string]$observedOrdinal) {
        throw 'Manual connection ordinal is not an integer'
    }
    $serverOrdinal = 1
    if ($serverInstalled -and $null -ne $manual.serverConnectionOrdinal) {
        $serverOrdinal = [int]$manual.serverConnectionOrdinal
        if ($serverOrdinal -lt 1 -or $serverOrdinal -gt 100 -or
            [string]$manual.serverConnectionOrdinal -cne [string]$serverOrdinal) {
            throw 'Manual server connection ordinal is invalid'
        }
    } elseif ($serverInstalled -and $observedOrdinal -ne $ConnectionOrdinal) {
        throw 'Server connection ordinal is required after a client retry'
    }
    $screens = @($manual.planScreenshot, $manual.statusScreenshot)
    foreach ($name in $screens) {
        if ([IO.Path]::GetFileName($name) -cne $name -or $name -notlike '*.png' -or
            !(Test-Path -LiteralPath (Join-Path $evidence $name) -PathType Leaf)) {
            throw "Missing native UI screenshot $name"
        }
    }
    if ($clientInstalled) {
        for ($ordinal = $ConnectionOrdinal; $ordinal -lt $observedOrdinal; $ordinal++) {
            & (Join-Path $PSScriptRoot 'verify-connection-observation.ps1') `
                -Receipt (Join-Path $ClientSessionDirectory "client-observation-$ordinal.json") -Direction c2s -Role client `
                -Target $Target -ConnectionEpoch "$clientEpoch`:$ordinal" -ProductionSha256 $ProductionSha256 `
                -DriverSha256 $DriverSha256 -NotBeforeUtc $startedAt `
                -Unsupported:($InstallationMode -eq 'client-only') -Prior | Out-Null
        }
        & (Join-Path $PSScriptRoot 'verify-connection-observation.ps1') `
            -Receipt (Join-Path $ClientSessionDirectory "client-observation-$observedOrdinal.json") -Direction c2s -Role client `
            -Target $Target -ConnectionEpoch "$clientEpoch`:$observedOrdinal" -ProductionSha256 $ProductionSha256 `
            -DriverSha256 $DriverSha256 -NotBeforeUtc $startedAt `
            -Unsupported:($InstallationMode -eq 'client-only') | Out-Null
        Copy-Item -LiteralPath (Join-Path $ClientSessionDirectory "client-observation-$observedOrdinal.json") -Destination (Join-Path $ReportDirectory 'client-observation.json')
    } elseif (@(Get-ChildItem -LiteralPath $ClientSessionDirectory -File -Filter 'client-observation-*.json').Count) {
        throw 'Absent client unexpectedly produced a Crafting Time observation'
    }
    if ($serverInstalled) {
        for ($ordinal = 1; $ordinal -lt $serverOrdinal; $ordinal++) {
            & (Join-Path $PSScriptRoot 'verify-connection-observation.ps1') `
                -Receipt (Join-Path $ReportDirectory "server-observation-$ordinal.json") -Direction s2c -Role server `
                -Target $Target -ConnectionEpoch "$ConnectionEpoch`:$ordinal" -ProductionSha256 $ProductionSha256 `
                -DriverSha256 $DriverSha256 -NotBeforeUtc $ServerStartedAtUtc `
                -Unsupported:($InstallationMode -eq 'server-only') -Prior | Out-Null
        }
        & (Join-Path $PSScriptRoot 'verify-connection-observation.ps1') `
            -Receipt (Join-Path $ReportDirectory "server-observation-$serverOrdinal.json") -Direction s2c -Role server `
            -Target $Target -ConnectionEpoch "$ConnectionEpoch`:$serverOrdinal" -ProductionSha256 $ProductionSha256 `
            -DriverSha256 $DriverSha256 -NotBeforeUtc $ServerStartedAtUtc `
            -Unsupported:($InstallationMode -eq 'server-only') | Out-Null
        $serverReceipt = Get-Content -LiteralPath (Join-Path $ReportDirectory "server-observation-$serverOrdinal.json") -Raw | ConvertFrom-Json
        if (([datetime]$serverReceipt.flushedAt).ToUniversalTime() -lt ([datetime]$manual.observedAt).ToUniversalTime()) {
            throw 'Server observation predates the native craft checkpoint; disconnect to flush the final epoch'
        }
    } elseif (@(Get-ChildItem -LiteralPath $ReportDirectory -File -Filter 'server-observation-*.json').Count) {
        throw 'Absent server unexpectedly produced a Crafting Time observation'
    }
    $screenHashes = @($screens | ForEach-Object {
        [ordered]@{name=$_;sha256=(Get-FileHash -LiteralPath (Join-Path $evidence $_) -Algorithm SHA256).Hash}
    })
    [ordered]@{target=$Target;mode=$InstallationMode;connectionEpoch=$ConnectionEpoch;login=$true;
        clientProcessId=$client.Id;clientProcessStartedAt=$session.processStartedAt;connectionOrdinal=$observedOrdinal;
        serverConnectionOrdinal=$(if ($serverInstalled) { $serverOrdinal } else { $null });
        manualReceipt=$manualPath;screenHashes=$screenHashes;completedAt=[DateTime]::UtcNow.ToString('o')} |
        ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $ReportDirectory 'connection-cell-evidence.json') -Encoding UTF8
    $session.lastOrdinal = $observedOrdinal
    $session | ConvertTo-Json | Set-Content -LiteralPath $sessionPath -Encoding UTF8
    $success = $true
} finally {
    if ($client) {
        if ((!$KeepClientAlive -or !$success) -and !$client.HasExited) { $client.Kill(); $client.WaitForExit() }
        $client.Dispose()
    }
}
