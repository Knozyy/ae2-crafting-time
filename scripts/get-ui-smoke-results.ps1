param(
    [Parameter(Mandatory)][string]$Target,
    [Parameter(Mandatory)][string]$Profile,
    [Parameter(Mandatory)][string[]]$Scenarios,
    [Parameter(Mandatory)][string]$Evidence,
    [string]$ExpectedAdapters
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'resource-fixture-contract.ps1')

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

$catalogue = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ui-smoke-groups.json') -Raw | ConvertFrom-Json
$contracts = $catalogue.cases
foreach ($scenario in $Scenarios) {
    $directory = if ($Scenarios.Count -eq 1) { $Evidence } else { Join-Path $Evidence $scenario }
    $file = Join-Path $directory 'result.json'
    $result = 'NOT_RUN'
    $reason = 'No result from this invocation'
    if (Test-Path -LiteralPath $file -PathType Leaf) {
        try {
            $data = Get-Content -LiteralPath $file -Raw | ConvertFrom-Json
            if ($data.schema -ne 1 -or $data.complete -isnot [bool] -or !$data.complete -or $data.target -cne $Target -or $data.profile -cne $Profile -or
                    $data.scenario -cne $scenario -or $data.language -cne 'en_us' -or $data.result -cne 'PASS') { throw 'Failed or mismatched result' }
            if ($data.screenshots -isnot [array] -or $data.screenshots.Count -eq 0) { throw 'Missing screenshot evidence' }
            foreach ($image in $data.screenshots) {
                if ($image -isnot [string] -or [string]::IsNullOrWhiteSpace($image) -or
                        [IO.Path]::GetFileName($image) -cne $image -or !$image.EndsWith('.png', [StringComparison]::Ordinal)) { throw 'Invalid screenshot entry' }
                $sidecar = Join-Path $directory ($image.Replace('.png','.json'))
                if (!(Test-Path -LiteralPath (Join-Path $directory $image) -PathType Leaf) -or !(Test-Path -LiteralPath $sidecar -PathType Leaf)) { throw "Missing evidence: $image" }
                $snapshot = Get-Content -LiteralPath $sidecar -Raw | ConvertFrom-Json
                if (!$snapshot.screen -or !$snapshot.gui -or !(Test-UiSnapshotBounds $snapshot)) { throw "Invalid snapshot: $image" }
            }
            if ($ExpectedAdapters) {
                $expected = Get-Content -LiteralPath $ExpectedAdapters -Raw | ConvertFrom-Json
                foreach ($adapter in @($catalogue.adapterCases.psobject.Properties) + @($catalogue.readRecoveryCases.psobject.Properties)) {
                    $dependency = $adapter.Name
                    if ($scenario -cin $adapter.Value -and $expected.$dependency -and
                            ($data.adapters.$dependency.variant -cne $expected.$dependency -or $data.adapters.$dependency.reason -cne 'selected')) {
                        throw "Newest adapter not exercised: $dependency requires $($expected.$dependency)"
                    }
                }
            }
            if ($contracts.$scenario) {
                if ($scenario -in @('delayed-resource-icons','appmek-resource-icons')) {
                    Assert-ResourceFixtureChecks $data.checks $scenario $false ($data.checks.'fixture-only' -is [bool])
                } else {
                    $contractChecks = if ($data.checks.'advanced-cpu' -is [bool] -and $contracts.$scenario.advancedChecks) {
                        @($contracts.$scenario.advancedChecks)
                    } else { @($contracts.$scenario.checks) }
                    if (Compare-Object $contractChecks @($data.checks.psobject.Properties.Name) -CaseSensitive) { throw 'Incomplete check set' }
                    foreach ($check in $contractChecks) { if ($data.checks.$check -isnot [bool] -or !$data.checks.$check) { throw "Failed check: $check" } }
                }
                $contractScreenshots = if ($data.checks.'advanced-cpu' -is [bool] -and $contracts.$scenario.advancedScreenshots) {
                    @($contracts.$scenario.advancedScreenshots)
                } else { @($contracts.$scenario.screenshots) }
                foreach ($image in $contractScreenshots) {
                    if ($image -cnotin $data.screenshots) { throw "Missing evidence: $image" }
                }
            }
            $result = 'PASS'; $reason = ''
        } catch { $result = 'FAIL'; $reason = $_.Exception.Message }
    }
    [pscustomobject]@{ scenario=$scenario; result=$result; reason=$reason; evidence=$directory }
}
