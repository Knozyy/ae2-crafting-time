$ErrorActionPreference = 'Stop'
$root = Join-Path $env:TEMP ("ae2ct-connection-observation-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root | Out-Null
try {
    $receipt = Join-Path $root 'receipt.json'
    $now = [DateTime]::UtcNow
    $types = @('StatsRequestC2S','StatsChatC2S','ProviderLocateC2S',
        'CpuTtcRequestC2S','WarningPreferenceC2S','ServerOptionsUpdateC2S')
    $attempted = [ordered]@{}
    foreach ($type in $types) { $attempted["c2s:$type`:server"] = 1 }
    $data = [ordered]@{target='1.20.1-forge';role='client';connectionEpoch='fixture:1';
        productionSha256=('a' * 64);driverSha256=('b' * 64);
        observerReadyAt=$now.ToString('o');flushedAt=$now.AddSeconds(1).ToString('o');
        attempted=$attempted;sent=[ordered]@{}}
    function Write-Receipt { $data | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $receipt -Encoding UTF8 }
    function Verify([switch]$Unsupported) {
        & (Join-Path $PSScriptRoot 'verify-connection-observation.ps1') -Receipt $receipt `
            -Direction c2s -Role client -Target '1.20.1-forge' -ConnectionEpoch 'fixture:1' `
            -ProductionSha256 ('a' * 64) -DriverSha256 ('b' * 64) `
            -NotBeforeUtc $now.AddSeconds(-1) -Unsupported:$Unsupported | Out-Null
    }
    function Must-Fail([scriptblock]$Action) {
        $failed = $false
        try { & $Action } catch { $failed = $true }
        if (!$failed) { throw 'Invalid connection observation was accepted' }
    }
    Write-Receipt
    Verify -Unsupported
    $data.sent['c2s:StatsRequestC2S:server'] = 1
    Write-Receipt
    Must-Fail { Verify -Unsupported }
    Verify
    $data.sent.Clear()
    Write-Receipt
    Must-Fail { Verify }
    $data.attempted.Remove('c2s:StatsChatC2S:server')
    Write-Receipt
    Must-Fail { Verify -Unsupported }
    $data.attempted['c2s:StatsChatC2S:server'] = 1
    $data.connectionEpoch = 'old:1'
    Write-Receipt
    Must-Fail { Verify -Unsupported }
    Write-Host 'Connection observation receipt contract passed'
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force
}
