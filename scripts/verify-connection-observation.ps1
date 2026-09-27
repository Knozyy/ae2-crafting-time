param(
    [Parameter(Mandatory)][string]$Receipt,
    [Parameter(Mandatory)][ValidateSet('c2s','s2c')][string]$Direction,
    [Parameter(Mandatory)][ValidateSet('client','server')][string]$Role,
    [Parameter(Mandatory)][string]$Target,
    [Parameter(Mandatory)][string]$ConnectionEpoch,
    [Parameter(Mandatory)][ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$ProductionSha256,
    [Parameter(Mandatory)][ValidatePattern('^[A-Fa-f0-9]{64}$')][string]$DriverSha256,
    [Parameter(Mandatory)][datetime]$NotBeforeUtc,
    [switch]$Unsupported,
    [switch]$Prior
)
$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $Receipt -PathType Leaf)) { throw "Missing connection observation: $Receipt" }
$file = Get-Item -LiteralPath $Receipt
if ($file.Length -gt 1MB -or $file.Length -eq 0) { throw 'Connection observation is empty or oversized' }
$data = Get-Content -LiteralPath $Receipt -Raw | ConvertFrom-Json
if ($data.target -cne $Target -or $data.role -cne $Role -or
    $data.connectionEpoch -cne $ConnectionEpoch -or
    $data.productionSha256 -cne $ProductionSha256 -or $data.driverSha256 -cne $DriverSha256 -or
    !$data.observerReadyAt -or !$data.flushedAt -or
    ([datetime]$data.observerReadyAt).ToUniversalTime() -lt $NotBeforeUtc.ToUniversalTime() -or
    ([datetime]$data.flushedAt).ToUniversalTime() -lt ([datetime]$data.observerReadyAt).ToUniversalTime()) {
    throw 'Connection observation identity or freshness mismatch'
}
$attempted = @($data.attempted.PSObject.Properties)
$sent = @($data.sent.PSObject.Properties)
if ($null -eq $data.attempted -or $null -eq $data.sent) { throw 'Connection observation counters are missing' }
if ($Prior) {
    if ($Unsupported -and @($sent | Where-Object { $_.Value -gt 0 }).Count) {
        throw 'Unsupported prior connection sent a loader payload'
    }
    return $data
}
if ($Unsupported) {
    $types = if ($Direction -eq 'c2s') {
        @('StatsRequestC2S','StatsChatC2S','ProviderLocateC2S','CpuTtcRequestC2S','WarningPreferenceC2S','ServerOptionsUpdateC2S')
    } else {
        @('StatsSnapshotS2C','CpuTtcSnapshotS2C','ProviderHighlightS2C','PlanRecurrenceS2C','PlanStoredVariantsS2C','ServerOptionsSnapshotS2C')
    }
    foreach ($type in $types) {
        if (!@($attempted | Where-Object { $_.Name -like "$Direction`:$type`:*" -and $_.Value -gt 0 }).Count) {
            throw "Missing attempted $Direction $type probe"
        }
    }
    if (@($sent | Where-Object { $_.Value -gt 0 }).Count) { throw 'Unsupported peer received a loader send' }
} elseif (!@($sent | Where-Object { $_.Name -like "$Direction`:*" -and $_.Value -gt 0 }).Count) {
    throw 'Supported control did not reach a loader send'
}
$data
