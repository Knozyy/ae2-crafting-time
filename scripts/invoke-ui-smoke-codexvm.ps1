param(
    [ValidateSet("1.20.1-forge", "1.20.1-fabric", "1.21.1-neoforge", "26.1.2-neoforge")][string]$Target = "1.20.1-forge",
    [ValidateSet("OpenSSH", "Vmrun")][string]$Transport = "OpenSSH",
    [switch]$Latest,
    [switch]$Interactive,
    [switch]$ResourceFixtureOnly,
    [switch]$Prewarm,
    [switch]$AcceptMinecraftEula,
    [string]$ServerDirectory,
    [switch]$Stop,
    [ValidatePattern("^(suite|standard-ae2|provider-dispatch-statuses|recurrent-plan|stored-variant-plan|delayed-resource-icons|appmek-resource-icons|chance-output-status|standard-plan-controls|badge-background|standard-status-controls|waiting-status|running-status|delayed-status|craft-lifecycle|cpu-list-total-ttc|craft-plan|no-space-status|no-provider-status|no-power-status|no-channel-status|no-target-status|input-blocked-status|locked-status|crafting-tree-screen|merequester-screen|crafting-tree-read-recovery|merequester-read-recovery|ae2networkanalyser-screen|aeinfinitybooster-terminal|ae2importexportcard-terminal|ae2(?:wcwt|wtlib)-terminal|[a-z0-9]+(?:-[a-z0-9]+)*-cpu)$")][string]$Scenario = "craft-plan",
    [string]$CasesBase64,
    [string[]]$ProjectId,
    [switch]$BaseOnly,
    [string]$SshUser = "Codex",
    [string]$SshKeyPath = (Join-Path $env:USERPROFILE ".ssh\codexvm_smoke_ed25519"),
    [string]$VmrunUser = "CodexSmoke",
    [string]$CredentialPath = (Join-Path $env:APPDATA "Codex\codexvm-smoke.credential.xml"),
    [string]$GuestSourceRoot,
    [string]$BundleDirectory,
    [string]$ReportDirectory,
    [string]$ResumeBundleDirectory,
    [switch]$CaptureResumeOnly,
    [int]$CallbackTimeoutSeconds = 20,
    [int]$CheckpointTimeoutSeconds = 60,
    [int]$StartupTimeoutSeconds = 300,
    [string]$PreparedLaunchRoot = 'C:\Users\Public\Documents\AE2CraftingTimeSmoke\prepared'
)

$ErrorActionPreference = "Stop"
if ($Prewarm -and (!$ServerDirectory -or !$BundleDirectory -or ($Scenario -notin @('delayed-resource-icons','appmek-resource-icons')) -or $Latest -or $Interactive)) {
    throw 'Connected prewarm requires an exact bundle, server directory and resource fixture mode'
}
$vmx = "F:\VMs\Codex-Windows11\Codex-Windows11.vmx"
$vmrun = "C:\Program Files\VMware\VMware Workstation\vmrun.exe"
$root = Split-Path -Parent $PSScriptRoot
$headSha = (& git -C $root rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $headSha -cnotmatch '^[a-f0-9]{40}$') { throw 'Cannot bind CodexVM dispatch to Git HEAD' }
if (-not $Stop -and -not $BundleDirectory) {
    $campaign = @{ Target = $Target; Scenario = $Scenario; Latest = $Latest; PreparedLaunchRoot = $PreparedLaunchRoot
        ProjectId = $ProjectId; BaseOnly = $BaseOnly; Interactive = $Interactive; ResourceFixtureOnly = $ResourceFixtureOnly }
    if ($GuestSourceRoot) { $campaign.GuestSourceRoot = $GuestSourceRoot }
    & (Join-Path $PSScriptRoot 'run-ui-smoke-matrix.ps1') @campaign
    exit $LASTEXITCODE
}
if (-not $GuestSourceRoot) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $name = 'ae2ct-' + ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($root))) -replace '-', '').Substring(0, 12).ToLowerInvariant() }
    finally { $sha.Dispose() }
    $null = & $vmrun -T ws setSharedFolderState $vmx $name $root writable 2>&1
    if ($LASTEXITCODE -ne 0) {
        & $vmrun -T ws addSharedFolder $vmx $name $root
        if ($LASTEXITCODE -ne 0) { throw 'Could not share the current worktree with CodexVM' }
    }
    & $vmrun -T ws enableSharedFolders $vmx
    if ($LASTEXITCODE -ne 0) { throw 'Could not enable the CodexVM worktree share' }
    $GuestSourceRoot = "\\vmware-host\Shared Folders\$name"
}
$guestScript = Join-Path $GuestSourceRoot "scripts\run-ui-smoke-codexvm.ps1"
$smokeArguments = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $guestScript, "-Target", $Target, "-Scenario", $Scenario)
$smokeArguments += @('-HeadSha', $headSha)
if ($BundleDirectory) {
    $bundlePath = [IO.Path]::GetFullPath($BundleDirectory)
    if (-not $bundlePath.StartsWith($root.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Bundle must be inside the shared worktree'
    }
    if (-not $Stop) {
        if ($Prewarm) {
            # A connected bundle already carries its exact graph; do not reinterpret
            # omitted host ProjectId/BaseOnly switches or rewrite its sealed contract.
            $identity = Get-Content -LiteralPath (Join-Path $bundlePath 'bundle-identity.json') -Raw | ConvertFrom-Json
            $null = & (Join-Path $PSScriptRoot 'use-ui-smoke-bundle-cache.ps1') -Mode Reuse -CacheDirectory $bundlePath `
                -HeadSha $headSha -Fingerprint $identity.fingerprint -Target $Target -Profile compatible `
                -GraphId $identity.graphId -BaseOnly:([bool]$identity.baseOnly)
        } else {
            & (Join-Path $PSScriptRoot 'prepare-ui-smoke-adapters.ps1') -Target $Target -BundleDirectory $bundlePath -ProjectId $ProjectId -BaseOnly:$BaseOnly
        }
        if (-not (Test-Path -LiteralPath (Join-Path $bundlePath 'expected-adapters.json') -PathType Leaf)) {
            throw 'Focused bundle adapter expectations were not prepared'
        }
    }
    $guestBundle = Join-Path $GuestSourceRoot $bundlePath.Substring($root.Length).TrimStart('\')
    $smokeArguments += @('-BundleDirectory', $guestBundle, '-PreparedLaunchRoot', $PreparedLaunchRoot)
}
if ($ReportDirectory) {
    $reportPath = [IO.Path]::GetFullPath($ReportDirectory)
    if (-not $reportPath.StartsWith($root.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Report directory must be inside the shared worktree'
    }
    $guestReport = Join-Path $GuestSourceRoot $reportPath.Substring($root.Length).TrimStart('\')
    $smokeArguments += @('-ReportDirectory', $guestReport)
}
if ($ResumeBundleDirectory) {
    $resumePath = [IO.Path]::GetFullPath($ResumeBundleDirectory)
    if (-not $resumePath.StartsWith($root.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Resume bundle must be inside the shared worktree'
    }
    $guestResume = Join-Path $GuestSourceRoot $resumePath.Substring($root.Length).TrimStart('\')
    $smokeArguments += @('-ResumeBundleDirectory', $guestResume)
}
if ($CaptureResumeOnly) { $smokeArguments += '-CaptureResumeOnly' }
$smokeArguments += @('-StartupTimeoutSeconds', [string]$StartupTimeoutSeconds)
$smokeArguments += @('-CallbackTimeoutSeconds', $CallbackTimeoutSeconds, '-CheckpointTimeoutSeconds', $CheckpointTimeoutSeconds)
if ($CasesBase64) {
    if ($CasesBase64 -cnotmatch '^[A-Za-z0-9+/]+={0,2}$') { throw 'Invalid encoded case list' }
    $smokeArguments += @('-CasesBase64', $CasesBase64)
}
if ($Latest) { $smokeArguments += "-Latest" }
if ($Interactive) { $smokeArguments += "-Interactive" }
if ($ResourceFixtureOnly) { $smokeArguments += "-ResourceFixtureOnly" }
if ($Prewarm) { $smokeArguments += '-Prewarm' }
if ($AcceptMinecraftEula) { $smokeArguments += '-AcceptMinecraftEula' }
if ($ServerDirectory) { $smokeArguments += @('-ServerDirectory',$ServerDirectory) }
if ($ProjectId) { $smokeArguments += @("-ProjectId") + $ProjectId }
if ($Stop) { $smokeArguments += "-Stop" } else { $smokeArguments += @("-Scheduled", "-InteractiveUser", "Codex") }

if ($Transport -eq "OpenSSH") {
    $ip = (& $vmrun -T ws getGuestIPAddress $vmx -wait).Trim()
    if ($LASTEXITCODE -ne 0 -or $ip -notmatch '^\d{1,3}(?:\.\d{1,3}){3}$') { throw "Could not resolve the CodexVM guest IP" }
    $remote = '& powershell.exe ' + (($smokeArguments | ForEach-Object { "'" + ([string]$_).Replace("'", "''") + "'" }) -join ' ')
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($remote))
    & ssh.exe -i $SshKeyPath -o BatchMode=yes -o ConnectTimeout=10 "$SshUser@$ip" powershell.exe -NoProfile -EncodedCommand $encoded
    if ($LASTEXITCODE -ne 0) { throw "OpenSSH UI-smoke dispatch failed with exit $LASTEXITCODE" }
    exit 0
}

if (-not (Test-Path -LiteralPath $CredentialPath -PathType Leaf)) {
    throw "Missing encrypted vmrun credential: $CredentialPath"
}
$credential = Import-Clixml -LiteralPath $CredentialPath
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($credential.Password)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    & $vmrun -T ws -gu $VmrunUser -gp $password runProgramInGuest $vmx `
        "C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe" @smokeArguments
    if ($LASTEXITCODE -ne 0) { throw "vmrun UI-smoke dispatch failed with exit $LASTEXITCODE" }
} finally {
    $password = $null
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}
