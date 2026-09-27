param(
    [string]$StatePath = (Join-Path $env:TEMP "ae2-crafting-time-release-test-state.json")
)

$ErrorActionPreference = "Stop"

# Exercise fingerprint selection without building or modifying repository sources.
$deployAst = [Management.Automation.Language.Parser]::ParseFile("$PSScriptRoot/deploy-changed.ps1", [ref]$null, [ref]$null)
$fingerprintFunction = $deployAst.Find({ param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Get-InputFingerprint'
}, $true)
. ([scriptblock]::Create($fingerprintFunction.Extent.Text))
$root = Join-Path $env:TEMP ("release-fingerprint-" + [guid]::NewGuid())
$matrix = Get-Content "$PSScriptRoot/release-matrix.json" -Raw | ConvertFrom-Json
$inputs = @('build.gradle', 'settings.gradle', 'shared/build.gradle',
    'shared/src/main/input', 'shared/src/mcCommon/input', 'shared/src/mc1201/input',
    'shared/src/mc2612/input', 'shared/src/neoforge/input',
    'shared/src/neoforge/java/com/ctux/ae2craftingtime/mc1201/mixin/input',
    'shared/src/mc1201Test/input', 'shared/src/testDriverAddons/input')
$inputs += @($matrix | ForEach-Object { "$($_.projectDir)/build.gradle"; "$($_.projectDir)/src/main/input" })
try {
    foreach ($inputPath in $inputs) {
        $file = Join-Path $root $inputPath
        New-Item -ItemType Directory -Path (Split-Path $file) -Force | Out-Null
        Set-Content $file 'before'
    }
    foreach ($entry in $matrix) {
        $baseline = Get-InputFingerprint $entry
        foreach ($source in @('mcCommon', 'mc1201', 'mc2612', 'neoforge',
                'neoforge/java/com/ctux/ae2craftingtime/mc1201/mixin', 'mc1201Test', 'testDriverAddons')) {
            $file = Join-Path $root "shared/src/$source/input"
            Set-Content $file 'after'
            $changed = (Get-InputFingerprint $entry) -ne $baseline
            Set-Content $file 'before'
            $expected = $source -eq 'mcCommon' -or
                ($source -eq 'mc1201' -and $entry.minecraftVersion -ne '26.1.2') -or
                ($source -eq 'mc2612' -and $entry.minecraftVersion -eq '26.1.2') -or
                ($source -eq 'neoforge' -and $entry.loader -eq 'neoforge') -or
                ($source -like 'neoforge/*' -and $entry.loader -ne 'fabric')
            if ($changed -ne $expected) { throw "Wrong fingerprint selection: $($entry.id), $source" }
        }
    }
} finally {
    # The unique absolute fixture directory was created above under TEMP.
    Remove-Item -LiteralPath $root -Recurse -Force
}

Remove-Item -LiteralPath $StatePath -Force -ErrorAction SilentlyContinue
$versionPath = "$StatePath.version"
Set-Content -LiteralPath $versionPath -Value "modVersion=1.0.4" -Encoding UTF8

$releaseDryRun = & powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\deploy-changed.ps1" `
    -StatePath $StatePath `
    -VersionPath $versionPath `
    -Deploy `
    -DryRun `
    -ModrinthProjectId test-project `
    -CurseProjectId 1591476
if ($LASTEXITCODE -ne 0 -or ($releaseDryRun -join "`n") -notmatch 'dry-run GitHub Release: 1\.0\.4') {
    throw "Release dry run did not create the expected GitHub Release metadata"
}
if (($releaseDryRun -join "`n") -notmatch '### FIXED\s+- The total TTC now sits in the crafting status header, so it no longer overlaps the action buttons\.') {
    throw "Release dry run did not create a categorized human-readable changelog"
}
if (($releaseDryRun -join "`n") -notmatch 'dry-run next development version: 1\.0\.5') {
    throw "Release dry run did not advance the development version"
}
if (($releaseDryRun -join "`n") -notmatch 'dry-run GitHub tag: release-1\.0\.4') {
    throw "Release dry run did not use a deterministic version tag"
}
$skipModrinthDryRun = & powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\deploy-changed.ps1" `
    -StatePath $StatePath -VersionPath $versionPath -Deploy -DryRun -SkipModrinth `
    -CurseProjectId 1591476
if ($LASTEXITCODE -ne 0 -or ($skipModrinthDryRun -join "`n") -notmatch 'dry-run Modrinth: skipped' -or
    ($skipModrinthDryRun -join "`n") -match 'dry-run Modrinth version:' -or
    ($skipModrinthDryRun -join "`n") -notmatch 'dry-run CurseForge versions:' -or
    ($skipModrinthDryRun -join "`n") -notmatch 'Modrinth was skipped\.') {
    throw "CurseForge-only dry run did not skip Modrinth"
}
$releaseOutput = $releaseDryRun -join "`n"
$releaseMatrix = Get-Content -LiteralPath (Join-Path $PSScriptRoot "release-matrix.json") -Raw | ConvertFrom-Json
foreach ($entry in $releaseMatrix) {
    $ids = @($entry.modrinthDependencies.project_id)
    if (@($ids | Group-Object | Where-Object Count -gt 1).Count) { throw "Duplicate matrix dependency in $($entry.id)" }
    $expected = "dry-run Modrinth dependencies: " + ((@($entry.modrinthDependencies) |
        ForEach-Object { "$($_.project_id):$($_.dependency_type)" }) -join ", ")
    $section = [regex]::Match($releaseOutput,
        "(?s)dry-run deploy $([regex]::Escape($entry.id)):.*?(?=dry-run deploy |dry-run GitHub Release:)").Value
    if (-not $section -or $section -notmatch "(?m)^$([regex]::Escape($expected))$") {
        throw "Release dry run did not preserve matrix dependencies for $($entry.id)"
    }
    $hasGuide = @($entry.modrinthDependencies | Where-Object project_id -eq "Ck4E7v7R").Count -eq 1
    $hasCurseRelations = $section -match '(?m)^dry-run CurseForge relations: applied-energistics-2:requiredDependency, guideme:optionalDependency$'
    if ($hasCurseRelations -ne $hasGuide) {
        throw "Wrong CurseForge GuideME relations for $($entry.id)"
    }
}

$duplicateMatrixPath = "$StatePath.duplicate-matrix.json"
$duplicateMatrix = Get-Content -LiteralPath (Join-Path $PSScriptRoot "release-matrix.json") -Raw | ConvertFrom-Json
$duplicateMatrix[0].modrinthDependencies += $duplicateMatrix[0].modrinthDependencies[0]
$duplicateMatrix | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $duplicateMatrixPath -Encoding UTF8
try {
    $preference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $duplicateOutput = & powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\deploy-changed.ps1" `
            -MatrixPath $duplicateMatrixPath -StatePath "$StatePath.duplicate" -VersionPath $versionPath 2>&1
    } finally { $ErrorActionPreference = $preference }
    if ($LASTEXITCODE -eq 0 -or ($duplicateOutput -join "`n") -notlike "*has duplicate Modrinth dependency*") {
        throw "Release script did not reject duplicate dependencies"
    }
} finally {
    Remove-Item -LiteralPath $duplicateMatrixPath, "$StatePath.duplicate" -Force -ErrorAction SilentlyContinue
}

$sharedChangelog = "### CHANGED`n`n- Shared release note ([#111](https://github.com/cTux/ae2-crafting-time/issues/111))"
$groupedDryRun = & powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\deploy-changed.ps1" `
    -StatePath $StatePath `
    -VersionPath $versionPath `
    -Deploy `
    -DryRun `
    -ModrinthProjectId test-project `
    -CurseProjectId 1591476 `
    -Changelog $sharedChangelog
$groupedOutput = $groupedDryRun -join "`n"
$groupedGitHubOutput = [regex]::Match($groupedOutput, '(?s)dry-run GitHub Release:.*').Value
if ($LASTEXITCODE -ne 0 -or $groupedGitHubOutput -notmatch '## All versions' -or
    ([regex]::Matches($groupedGitHubOutput, [regex]::Escape('- Shared release note ([#111]')).Count -ne 1)) {
    throw "GitHub Release did not group a shared changelog once for all versions"
}

$scopedChangelogPath = "$StatePath.changelog.json"
[ordered]@{
    all = "### IMPROVED`n`n- Shared release note ([#111](https://github.com/cTux/ae2-crafting-time/issues/111))"
    '1.21.1-neoforge' = "### FIXED`n`n- NeoForge 1.21.1-only note ([#112](https://github.com/cTux/ae2-crafting-time/issues/112))"
} | ConvertTo-Json | Set-Content -LiteralPath $scopedChangelogPath -Encoding UTF8
$scopedDryRun = & powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\deploy-changed.ps1" `
    -StatePath $StatePath `
    -VersionPath $versionPath `
    -Deploy `
    -DryRun `
    -ModrinthProjectId test-project `
    -CurseProjectId 1591476 `
    -ChangelogPath $scopedChangelogPath
$scopedOutput = $scopedDryRun -join "`n"
$fabricChangelog = [regex]::Match($scopedOutput,
    '(?s)dry-run changelog 1\.20\.1-fabric:\s*(?<notes>.*?)(?=dry-run deploy 1\.21\.1-neoforge:)').Groups['notes'].Value
$neoForgeChangelog = [regex]::Match($scopedOutput,
    '(?s)dry-run changelog 1\.21\.1-neoforge:\s*(?<notes>.*?)(?=dry-run deploy 26\.1\.2-neoforge:)').Groups['notes'].Value
if ($LASTEXITCODE -ne 0 -or $fabricChangelog -notmatch 'Shared release note' -or
    $fabricChangelog -match 'NeoForge 1\.21\.1-only note' -or
    $neoForgeChangelog -notmatch 'Shared release note' -or
    $neoForgeChangelog -notmatch 'NeoForge 1\.21\.1-only note' -or
    $scopedOutput -notmatch '## NeoForge 1\.21\.1' -or
    ([regex]::Matches($scopedOutput, [regex]::Escape('- NeoForge 1.21.1-only note ([#112]')).Count -ne 2)) {
    throw "Scoped changelog leaked a version-specific note or built the wrong GitHub sections"
}

$invalidChangelog = "### FIXED`n`n- Link wraps the whole sentence."
$preference = $ErrorActionPreference
try {
    $ErrorActionPreference = "Continue"
    $invalidOutput = & powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\deploy-changed.ps1" `
        -StatePath $StatePath -VersionPath $versionPath -Deploy -DryRun `
        -ModrinthProjectId test-project -CurseProjectId 1591476 -Changelog $invalidChangelog 2>&1
} finally { $ErrorActionPreference = $preference }
if ($LASTEXITCODE -eq 0 -or ($invalidOutput -join "`n") -notmatch 'must end with a linked GitHub reference') {
    throw "Release script did not reject a manual changelog without end-of-line GitHub references"
}

$first = & powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\deploy-changed.ps1" `
    -StatePath $StatePath `
    -VersionPath $versionPath
if ($LASTEXITCODE -ne 0 -or ($first -join "`n") -notmatch 'build 1\.20\.1-forge: 1\.0\.4') {
    throw "First release run did not build 1.20.1-forge"
}
if (($first -join "`n") -notmatch 'build 1\.21\.1-neoforge: 1\.0\.4') {
    throw "First release run did not build 1.21.1-neoforge"
}
if (($first -join "`n") -notmatch 'build 1\.20\.1-fabric: 1\.0\.4') {
    throw "First release run did not build 1.20.1-fabric"
}
if (($first -join "`n") -notmatch 'build 26\.1\.2-neoforge: 1\.0\.4') {
    throw "First release run did not build 26.1.2-neoforge"
}
$stateBytes = [IO.File]::ReadAllBytes($StatePath)
if ($stateBytes.Length -ge 3 -and $stateBytes[0] -eq 0xEF -and $stateBytes[1] -eq 0xBB -and $stateBytes[2] -eq 0xBF) {
    throw "Release JSON must be UTF-8 without a BOM"
}

$partialStatePath = "$StatePath.partial"
Copy-Item -LiteralPath $StatePath -Destination $partialStatePath -Force
$partialState = Get-Content -LiteralPath $partialStatePath -Raw | ConvertFrom-Json
$partialState.'1.20.1-fabric'.fingerprint = "changed"
$partialState.'1.20.1-fabric'.commit = (& git rev-list --max-parents=0 HEAD).Trim()
$partialState | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $partialStatePath -Encoding UTF8
Set-Content -LiteralPath $versionPath -Value "modVersion=1.0.5" -Encoding UTF8
$partial = & powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\deploy-changed.ps1" `
    -StatePath $partialStatePath `
    -VersionPath $versionPath `
    -Deploy `
    -DryRun `
    -ModrinthProjectId test-project `
    -CurseProjectId 1591476
$partialOutput = $partial -join "`n"
if ($LASTEXITCODE -ne 0 -or $partialOutput -notmatch 'dry-run GitHub Release: 1\.0\.5') {
    throw "Partial release did not publish only the affected jar at the development version"
}
if ($partialOutput -notmatch 'dry-run GitHub assets: ae2-crafting-time-1\.0\.4-forge-1\.20\.1\.jar, ae2-crafting-time-1\.0\.5-fabric-1\.20\.1\.jar, ae2-crafting-time-1\.0\.4-neoforge-1\.21\.1\.jar, ae2-crafting-time-1\.0\.4-neoforge-26\.1\.2\.jar') {
    throw "Partial release did not attach every latest jar to GitHub"
}
if ($partialOutput -notmatch 'dry-run Modrinth version: 1\.0\.5-fabric-1\.20\.1') {
    throw "Partial release did not use a version-first Modrinth version number"
}
if ($partialOutput -notmatch 'dry-run CurseForge versions: 1\.20\.1, Fabric, Client, Server') {
    throw "Partial release did not include CurseForge environment versions"
}
if ($partialOutput -notmatch '### ADDED' -or $partialOutput -notmatch '### FIXED' -or $partialOutput -match '(?m)^- (feat|fix)(\([^)]+\))?!?:') {
    throw "Generated changelog did not categorize and humanize conventional commits"
}
Remove-Item -LiteralPath $partialStatePath -Force -ErrorAction SilentlyContinue

$second = & powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\deploy-changed.ps1" `
    -StatePath $StatePath `
    -VersionPath $versionPath
if ($LASTEXITCODE -ne 0 -or ($second -join "`n") -notmatch 'skip 1\.20\.1-forge: unchanged at 1\.0\.4') {
    throw "Second release run did not skip unchanged 1.20.1-forge"
}
if (($second -join "`n") -notmatch 'skip 1\.21\.1-neoforge: unchanged at 1\.0\.4') {
    throw "Second release run did not skip unchanged 1.21.1-neoforge"
}
if (($second -join "`n") -notmatch 'skip 1\.20\.1-fabric: unchanged at 1\.0\.4') {
    throw "Second release run did not skip unchanged 1.20.1-fabric"
}
if (($second -join "`n") -notmatch 'skip 26\.1\.2-neoforge: unchanged at 1\.0\.4') {
    throw "Second release run did not skip unchanged 26.1.2-neoforge"
}

Remove-Item -LiteralPath $StatePath -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $versionPath -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $scopedChangelogPath -Force -ErrorAction SilentlyContinue
Write-Host "release script check passed"
