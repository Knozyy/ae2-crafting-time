param(
    [ValidateSet("1.20.1-forge", "1.20.1-fabric", "1.21.1-neoforge", "26.1.2-neoforge")][string]$Target = "1.20.1-forge",
    [switch]$Latest,
    [switch]$Interactive,
    [switch]$ResourceFixtureOnly,
    [switch]$Prewarm,
    [switch]$AcceptMinecraftEula,
    [string]$ServerDirectory,
    [switch]$Scheduled,
    [switch]$Stop,
    [ValidatePattern("^(suite|standard-ae2|provider-dispatch-statuses|recurrent-plan|stored-variant-plan|delayed-resource-icons|appmek-resource-icons|chance-output-status|standard-plan-controls|badge-background|standard-status-controls|waiting-status|running-status|delayed-status|craft-lifecycle|cpu-list-total-ttc|crafting-suspension|craft-plan|no-space-status|no-provider-status|no-power-status|no-channel-status|no-target-status|input-blocked-status|locked-status|crafting-tree-screen|merequester-screen|crafting-tree-read-recovery|merequester-read-recovery|ae2networkanalyser-screen|aeinfinitybooster-terminal|ae2importexportcard-terminal|ae2(?:wcwt|wtlib)-terminal|[a-z0-9]+(?:-[a-z0-9]+)*-cpu)$")][string]$Scenario = "craft-plan",
    [string]$CasesBase64,
    [string[]]$ProjectId,
    [string]$LocalRoot,
    [string]$InteractiveUser = "Codex",
    [string]$BundleDirectory,
    [string]$ReportDirectory,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{40}$')][string]$HeadSha,
    [string]$ResumeBundleDirectory,
    [switch]$CaptureResumeOnly,
    [int]$CallbackTimeoutSeconds = 20,
    [int]$CheckpointTimeoutSeconds = 60,
    [int]$StartupTimeoutSeconds = 300,
    [string]$PreparedLaunchRoot = 'C:\Users\Public\Documents\AE2CraftingTimeSmoke\prepared'
)

$ErrorActionPreference = "Stop"
if ($Prewarm -and (!$ServerDirectory -or ($Scenario -notin @('delayed-resource-icons','appmek-resource-icons')) -or $Latest -or $Interactive)) {
    throw 'Prewarm requires a compatible connected resource fixture'
}

function Get-WorkspaceId([string]$path) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return (([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes(
            [IO.Path]::GetFullPath($path).ToLowerInvariant()))) -replace '-', '').Substring(0, 12)).ToLowerInvariant()
    } finally { $sha.Dispose() }
}

function Get-ReportDirectory([string]$sourceRoot, [bool]$latest, [string]$scenario) {
    $profile = if ($latest) { "latest" } else { "compatible" }
    return Join-Path $sourceRoot "build\ui-smoke\$Target\$profile\$scenario"
}

function Stop-Smoke([string]$report, [string]$statusPath = (Join-Path $report 'status.json')) {
    if (-not (Test-Path -LiteralPath $statusPath -PathType Leaf)) { throw "No UI-smoke status exists at $statusPath" }
    $status = Get-Content -LiteralPath $statusPath -Raw | ConvertFrom-Json
    if (-not $status.pid -or $status.phase -notin @("preparing", "running")) { throw "UI smoke is not running" }
    $running = Get-CimInstance Win32_Process -Filter "ProcessId = $($status.pid)"
    if (-not $running) { throw "Recorded UI-smoke PID $($status.pid) is no longer running" }
    $matchesCommand = if ($status.commandScript) { $running.CommandLine.Contains($status.commandScript) }
        elseif ($status.argumentFile) { $running.CommandLine.Contains($status.argumentFile) }
        else { $running.CommandLine -like "*$($status.stagedRoot)*run-client.ps1*" }
    if (-not $matchesCommand -or -not $status.processStartedAt -or
            [Math]::Abs(($running.CreationDate.ToUniversalTime() - ([DateTime]$status.processStartedAt).ToUniversalTime()).TotalSeconds) -gt 1) {
        throw "PID $($status.pid) does not match the recorded UI-smoke command"
    }
    if ($status.commandScript) {
        # Scheduled Java is not a descendant of this wrapper. Stop its separately
        # identity-checked process before stopping the wrapper and dedicated server.
        foreach ($attempt in @(Get-ChildItem -LiteralPath $status.report -Directory -Filter 'client-attempt-*' -ErrorAction SilentlyContinue)) {
            $clientStatus = Join-Path $attempt.FullName 'status.json'
            if (!(Test-Path -LiteralPath $clientStatus)) { continue }
            $client = Get-Content -LiteralPath $clientStatus -Raw | ConvertFrom-Json
            if ($client.pid -and $client.phase -in @('preparing','running')) { Stop-Smoke $attempt.FullName }
        }
    }
    & taskkill.exe /PID $status.pid /T /F | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not stop UI-smoke process tree $($status.pid)" }
    if ($status.commandScript) {
        $status.phase = 'stopped'
        $status | ConvertTo-Json | Set-Content -LiteralPath $statusPath -Encoding UTF8
    }
    Write-Host "Stopped UI-smoke process tree $($status.pid)"
}

$sourceRoot = Split-Path -Parent $PSScriptRoot
$workspaceId = Get-WorkspaceId $sourceRoot
$stage = if ($LocalRoot) { [IO.Path]::GetFullPath($LocalRoot) } else { Join-Path $env:PUBLIC "Documents\AE2CraftingTimeSmoke\$workspaceId" }
$destinationReport = if ($ReportDirectory) { [IO.Path]::GetFullPath($ReportDirectory) } else { Get-ReportDirectory $sourceRoot $Latest.IsPresent $Scenario }
$profile = if ($Latest) { 'latest' } else { 'compatible' }
$report = Join-Path $stage "reports\$Target\$profile\$Scenario"
$connectedStatusPath = $report + '-connected-status.json'

if ($Stop) {
    if ($ServerDirectory) {
        if (!(Test-Path -LiteralPath $connectedStatusPath)) { throw "No connected UI-smoke status exists at $connectedStatusPath" }
        $connected = Get-Content -LiteralPath $connectedStatusPath -Raw | ConvertFrom-Json
        if ($connected.headSha -cne $HeadSha -or $connected.serverDirectory -ine [IO.Path]::GetFullPath($ServerDirectory)) {
            throw 'Connected UI-smoke stop identity does not match the requested campaign'
        }
        Stop-Smoke $connected.report $connectedStatusPath
    } else { Stop-Smoke $report }
    exit 0
}
# The staging tree is shared by all targets. Refuse before any mirror, copy,
# directory creation or Java setup can touch another connected campaign.
$stageReports = Join-Path $stage 'reports'
$ledgers = if (Test-Path -LiteralPath $stageReports) {
    @(foreach ($targetReports in Get-ChildItem -LiteralPath $stageReports -Directory -ErrorAction Stop) {
        foreach ($profileReports in Get-ChildItem -LiteralPath $targetReports.FullName -Directory -ErrorAction Stop) {
            Get-ChildItem -LiteralPath $profileReports.FullName -File -Filter '*-connected-status.json' -ErrorAction Stop
        }
    })
} else { @() }
foreach ($ledger in $ledgers) {
    $previous = Get-Content -LiteralPath $ledger.FullName -Raw | ConvertFrom-Json
    if ($previous.phase -notin @('finished','stopped')) {
        throw 'A connected campaign status is still active; stop or resolve it before starting another'
    }
}
if ($ServerDirectory) { $report = Join-Path $stage ('runtime/c-' + [guid]::NewGuid().ToString('N')) }
if (-not $BundleDirectory) { throw 'Build the bundle on the host through invoke-ui-smoke-codexvm.ps1' }
$loader = (Get-Content -LiteralPath (Join-Path $BundleDirectory 'profile.json') -Raw | ConvertFrom-Json).loader
if ($loader -cnotmatch '^[A-Za-z0-9][A-Za-z0-9._+-]*$') { throw 'Invalid prepared loader version' }
$preparedLaunch = Join-Path $PreparedLaunchRoot "$Target/$loader/launch.json"
if (!(Test-Path -LiteralPath $preparedLaunch -PathType Leaf)) {
    $preparedLaunch = Join-Path $PreparedLaunchRoot "$Target/launch.json"
}

$major = if ($Target -like '1.20.1-*') { 17 } elseif ($Target -eq '1.21.1-neoforge') { 21 } else { 25 }
$smokeJava = & (Join-Path $PSScriptRoot 'get-java-home.ps1') -Major $major
New-Item -ItemType Directory -Path $stage -Force | Out-Null
if (!$ServerDirectory) { New-Item -ItemType Directory -Path $report -Force | Out-Null }
& robocopy.exe $sourceRoot $stage /MIR /XD .git .gradle build reports runtime connected-bundle-* /XF .git /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -gt 7) { throw "Failed to stage the checkout with robocopy exit $LASTEXITCODE" }

$env:JAVA_HOME = $smokeJava
$env:Path = "$(Join-Path $smokeJava 'bin');$env:Path"
$arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $stage 'scripts\run-ui-smoke.ps1'),
    '-ReportDirectory', $report, '-Scenario', $Scenario, '-Target', $Target, '-HeadSha', $HeadSha,
    '-BundleDirectory', $BundleDirectory, '-PreparedLaunch', $preparedLaunch,
    '-CallbackTimeoutSeconds', [string]$CallbackTimeoutSeconds,
    '-CheckpointTimeoutSeconds', [string]$CheckpointTimeoutSeconds,
    '-StartupTimeoutSeconds', [string]$StartupTimeoutSeconds)
if ($CasesBase64) { $arguments += @('-CasesBase64', $CasesBase64) }
if ($ProjectId) { $arguments += @('-ProjectId') + @($ProjectId) }
if ($Latest) { $arguments += '-Latest' }
if ($Interactive) { $arguments += '-Interactive' }
if ($ResourceFixtureOnly) { $arguments += '-ResourceFixtureOnly' }
if ($ResumeBundleDirectory) { $arguments += @('-ResumeBundleDirectory', $ResumeBundleDirectory) }
if ($CaptureResumeOnly) { $arguments += '-CaptureResumeOnly' }
if ($Scheduled) { $arguments += @('-ScheduledJava', '-InteractiveUser', $InteractiveUser) }
if ($ServerDirectory) {
    if (!$AcceptMinecraftEula) { throw 'Connected execution requires explicit Minecraft EULA acceptance' }
    if ($Latest -or $Interactive -or $ResumeBundleDirectory -or $CasesBase64 -or $CaptureResumeOnly) {
        throw 'Connected resource dispatch does not accept integrated or latest options'
    }
    $localBundle = Join-Path $stage ('connected-bundle-' + [guid]::NewGuid().ToString('N'))
    if ((Get-Item -LiteralPath $BundleDirectory).Attributes -band [IO.FileAttributes]::ReparsePoint -or
            @(Get-ChildItem -LiteralPath $BundleDirectory -Recurse -Force -Attributes ReparsePoint).Count) {
        throw 'Connected bundle copy refuses filesystem links'
    }
    Copy-Item -LiteralPath $BundleDirectory -Destination $localBundle -Recurse
    $arguments = @('-NoProfile','-ExecutionPolicy','Bypass','-File',(Join-Path $stage 'scripts/run-connected-dedicated-ui-smoke.ps1'),
        '-Target',$Target,'-Scenario',$Scenario,'-HeadSha',$HeadSha,'-ServerDirectory',$ServerDirectory,
        '-BundleDirectory',$localBundle,'-PreparedLaunch',$preparedLaunch,'-ReportDirectory',$report,'-AcceptMinecraftEula')
    if ($ResourceFixtureOnly) { $arguments += '-ResourceFixtureOnly' }
    if ($Prewarm) { $arguments += '-Prewarm' }
    if ($Scheduled) { $arguments += @('-ScheduledJava','-InteractiveUser',$InteractiveUser) }
}
$innerExitCode = 0
$connectedStatus = $null
try {
    if ($ServerDirectory) {
        New-Item -ItemType Directory -Path (Split-Path -Parent $connectedStatusPath) -Force | Out-Null
        $connectedStatus = [ordered]@{schema=1;phase='running';pid=$PID;commandScript=$PSCommandPath;report=$report
            headSha=$HeadSha;serverDirectory=[IO.Path]::GetFullPath($ServerDirectory)
            processStartedAt=(Get-Process -Id $PID).StartTime.ToUniversalTime().ToString('o')}
        $connectedStatus | ConvertTo-Json | Set-Content -LiteralPath $connectedStatusPath -Encoding UTF8
    }
    & powershell.exe @arguments
    $innerExitCode = $LASTEXITCODE
} finally {
    if ($connectedStatus) {
        $connectedStatus.phase = 'finished'
        $connectedStatus | ConvertTo-Json | Set-Content -LiteralPath $connectedStatusPath -Encoding UTF8
    }
    if (Test-Path -LiteralPath $report -PathType Container) {
        New-Item -ItemType Directory -Path $destinationReport -Force | Out-Null
        & robocopy.exe $report $destinationReport /MIR /NFL /NDL /NJH /NJS /NP | Out-Null
        if ($LASTEXITCODE -gt 7) { throw "Failed to retain CodexVM smoke report with robocopy exit $LASTEXITCODE" }
    }
}
exit $innerExitCode
