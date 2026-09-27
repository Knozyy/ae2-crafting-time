$ErrorActionPreference = 'Stop'
# Process and launch doubles exercise the real cell runner without starting Minecraft.
$root = Join-Path $env:TEMP ('ae2ct-observation-session-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root | Out-Null
try {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'run-connection-observation-cell.ps1'),
        (Join-Path $PSScriptRoot 'verify-connection-observation.ps1') -Destination $root
    @'
param($LaunchManifest,$BundleDirectory,$RuntimeDirectory,$AllowedRuntimeRoot,$Target,$Profile,$Scenario,$World,
    $Evidence,$DedicatedAddress,[switch]$ObservationMode,[switch]$ClientInstalled,[switch]$ExpectUnsupportedPeer,
    $ObservationFile,$ProductionSha256,$DriverSha256,$ConnectionEpoch)
New-Item -ItemType Directory -Path $RuntimeDirectory -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $BundleDirectory 'mods') -Destination $RuntimeDirectory -Recurse
[pscustomobject]@{executable='fixture-java.exe';arguments='fixture'}
'@ | Set-Content -LiteralPath (Join-Path $root 'prepare-ui-smoke-launch.ps1')
    $bundle = Join-Path $root 'bundle'
    New-Item -ItemType Directory -Path (Join-Path $bundle 'mods') -Force | Out-Null
    $names = @('ae2.jar','ae2-crafting-time-production.jar','ae2-crafting-time-test-driver.jar')
    foreach ($name in $names) { Set-Content -LiteralPath (Join-Path $bundle "mods/$name") -Value $name }
    ConvertTo-Json -InputObject $names | Set-Content -LiteralPath (Join-Path $bundle 'mods/.ae2-crafting-time-run-mods.json')
    '{"profile":"fixture"}' | Set-Content -LiteralPath (Join-Path $bundle 'profile.json')
    $productionHash = (Get-FileHash (Join-Path $bundle 'mods/ae2-crafting-time-production.jar')).Hash
    $driverHash = (Get-FileHash (Join-Path $bundle 'mods/ae2-crafting-time-test-driver.jar')).Hash
    $global:launches = 0
    $global:kills = 0
    $global:process = $null
    function Write-Checkpoints {
        $evidence = Join-Path $global:report 'client/evidence'
        New-Item -ItemType Directory -Path $evidence -Force | Out-Null
        foreach ($name in @('plan.png','status.png')) { Set-Content -LiteralPath (Join-Path $evidence $name) -Value 'fixture' }
        $checkpoint = [ordered]@{target='1.20.1-forge';mode=$global:mode;login=$true;craft=$true;
            observedAt=[DateTime]::UtcNow.ToString('o');clientProcessId=$global:process.Id;
            connectionOrdinal=$global:observedOrdinal;planScreenshot='plan.png';statusScreenshot='status.png'}
        if ($global:serverOrdinal) { $checkpoint.serverConnectionOrdinal = $global:serverOrdinal }
        $checkpoint | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $global:report 'manual-checkpoints.json')
        if ($global:mode -in @('both','client-only')) {
            $attempted = [ordered]@{}
            foreach ($type in @('StatsRequestC2S','StatsChatC2S','ProviderLocateC2S','CpuTtcRequestC2S','WarningPreferenceC2S','ServerOptionsUpdateC2S')) {
                $attempted["c2s:$type`:server"] = 1
            }
            $sent = if ($global:mode -eq 'both') { @{'c2s:StatsRequestC2S:server'=1} } else { @{} }
            for ($attempt = $global:ordinal; $attempt -le $global:observedOrdinal; $attempt++) {
                $prior = $attempt -lt $global:observedOrdinal
                $attemptedForReceipt = if ($prior) { @{} } else { $attempted }
                $sentForReceipt = if ($prior) {
                    if ($global:poisonPrior -and $attempt -eq $global:ordinal) { @{'c2s:StatsRequestC2S:server'=1} } else { @{} }
                } else { $sent }
                [ordered]@{target='1.20.1-forge';role='client';connectionEpoch="fixture:$attempt";
                    productionSha256=$productionHash;driverSha256=$driverHash;observerReadyAt=[DateTime]::UtcNow.ToString('o');
                    flushedAt=[DateTime]::UtcNow.ToString('o');attempted=$attemptedForReceipt;sent=$sentForReceipt} |
                    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $global:session "client-observation-$attempt.json")
            }
        }
        if ($global:mode -in @('both','server-only')) {
            $attempted = [ordered]@{}
            foreach ($type in @('StatsSnapshotS2C','CpuTtcSnapshotS2C','ProviderHighlightS2C','PlanRecurrenceS2C','PlanStoredVariantsS2C','ServerOptionsSnapshotS2C')) {
                $attempted["s2c:$type`:player"] = 1
            }
            $sent = if ($global:mode -eq 'both') { @{'s2c:StatsSnapshotS2C:player'=1} } else { @{} }
            $flushedAt = if ($global:staleServerReceipt) {
                ([datetime]$checkpoint.observedAt).AddMilliseconds(-1).ToString('o')
            } else { [DateTime]::UtcNow.ToString('o') }
            [ordered]@{target='1.20.1-forge';role='server';connectionEpoch='fixture:1';
                productionSha256=$productionHash;driverSha256=$driverHash;observerReadyAt=([datetime]$flushedAt).AddSeconds(-1).ToString('o');
                flushedAt=$flushedAt;attempted=$attempted;sent=$sent} |
                ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $global:report 'server-observation-1.json')
        }
    }
    function Start-Process {
        param($FilePath,$ArgumentList,$WorkingDirectory,[switch]$PassThru,$WindowStyle,$RedirectStandardOutput,$RedirectStandardError)
        $global:launches++
        $global:process = [pscustomobject]@{Id=537;StartTime=[DateTime]::UtcNow;Path=$FilePath;HasExited=$false}
        $global:process | Add-Member ScriptMethod Kill { $this.HasExited=$true; $global:kills++ }
        $global:process | Add-Member ScriptMethod WaitForExit { }
        $global:process | Add-Member ScriptMethod Dispose { }
        Write-Checkpoints
        $global:process
    }
    function Get-Process {
        param($Id,$ErrorAction)
        if (!$global:process -or $global:process.HasExited) { throw 'Fixture process ended' }
        Write-Checkpoints
        $global:process
    }
    function Run-Cell([string]$Mode,[int]$Ordinal,[int]$Observed=0,[switch]$Keep,[switch]$PoisonPrior,[int]$ServerOrdinal=0,[switch]$StaleServerReceipt) {
        $global:mode=$Mode; $global:ordinal=$Ordinal
        $global:observedOrdinal=if ($Observed) { $Observed } else { $Ordinal }
        $global:poisonPrior=[bool]$PoisonPrior
        $global:serverOrdinal=$ServerOrdinal
        $global:staleServerReceipt=[bool]$StaleServerReceipt
        $global:report=Join-Path $root ([guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $global:report | Out-Null
        & (Join-Path $root 'run-connection-observation-cell.ps1') -Target '1.20.1-forge' -InstallationMode $Mode `
            -ReportDirectory $global:report -BundleDirectory $bundle -PreparedLaunch 'fixture' -Address '127.0.0.1:25565' `
            -ConnectionEpoch fixture -ProductionSha256 $productionHash -DriverSha256 $driverHash `
            -ServerStartedAtUtc ([DateTime]::UtcNow.AddMinutes(-1)) -ManualTimeoutSeconds 1 `
            -ClientSessionDirectory $global:session -ConnectionOrdinal $Ordinal -KeepClientAlive:$Keep
    }
    $global:session=Join-Path $root 'installed-session'
    Run-Cell both 1 -Keep
    Run-Cell client-only 2 -Keep
    Run-Cell both 3
    if ($global:launches -ne 1 -or $global:kills -ne 1) { throw 'Installed transition did not reuse and finally stop one process' }
    $global:session=Join-Path $root 'native-session'
    Run-Cell server-only 1 -Keep
    Run-Cell neither 2
    if ($global:launches -ne 2 -or $global:kills -ne 2) { throw 'Native transition did not reuse and stop one process' }
    $global:session=Join-Path $root 'invalid-session'
    $failed=$false
    try { Run-Cell neither 2 } catch { $failed=$true }
    if (!$failed -or $global:launches -ne 2) { throw 'Invalid first ordinal started a client' }
    $global:session=Join-Path $root 'retry-session'
    Run-Cell client-only 1 4
    if ((Get-Content (Join-Path $global:session 'session.json') -Raw | ConvertFrom-Json).lastOrdinal -ne 4) {
        throw 'Warm retry did not persist the observed connection ordinal'
    }
    $global:session=Join-Path $root 'unsafe-retry-session'
    $failed=$false
    try { Run-Cell client-only 1 3 -PoisonPrior } catch { $failed=$true }
    if (!$failed) { throw 'Unsupported prior send was accepted' }
    $global:session=Join-Path $root 'server-retry-session'
    Run-Cell server-only 1 3 -ServerOrdinal 1
    $global:session=Join-Path $root 'missing-server-ordinal-session'
    $failed=$false
    try { Run-Cell server-only 1 3 } catch { $failed=$true }
    if (!$failed) { throw 'Server retry without an observed server ordinal was accepted' }
    $global:session=Join-Path $root 'stale-server-session'
    $failed=$false
    try { Run-Cell server-only 1 -StaleServerReceipt } catch {
        if ($_.Exception.Message -notlike 'Server observation predates the native craft checkpoint*') { throw }
        $failed=$true
    }
    if (!$failed) { throw 'Server receipt predating the craft checkpoint was accepted' }
    Write-Host 'Connection observation session contract passed'
} finally {
    $resolved = [IO.Path]::GetFullPath($root)
    if (!$resolved.StartsWith([IO.Path]::GetFullPath($env:TEMP).TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Fixture cleanup escaped TEMP'
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
