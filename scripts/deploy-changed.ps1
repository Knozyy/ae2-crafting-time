param(
    [switch]$Deploy,
    [switch]$DryRun,
    [switch]$SkipModrinth,
    [string]$ReleaseType,
    [string]$ModrinthProjectId,
    [string]$CurseProjectId,
    [string]$Changelog,
    [string]$ChangelogPath,
    [string]$JavaHome = "C:\Users\cccTu\.gradle\jdks\eclipse_adoptium-17-amd64-windows\jdk-17.0.19+10",
    [string]$MatrixPath = (Join-Path $PSScriptRoot "release-matrix.json"),
    [string]$StatePath = (Join-Path (Split-Path -Parent $PSScriptRoot) ".release-state.json"),
    [string]$VersionPath = (Join-Path (Split-Path -Parent $PSScriptRoot) "gradle.properties")
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$gradlew = Join-Path $root "gradlew.bat"

if (Test-Path $JavaHome) {
    $env:JAVA_HOME = $JavaHome
    $env:PATH = "$JavaHome\bin;$env:PATH"
}

function Read-Json($path, $fallback) {
    if (Test-Path $path) {
        return Get-Content $path -Raw | ConvertFrom-Json
    }
    return $fallback
}

function Write-Json($path, $value) {
    [IO.File]::WriteAllText($path, ($value | ConvertTo-Json -Depth 8), (New-Object Text.UTF8Encoding($false)))
}

function Assert-ReleaseEntry($entry) {
    $required = @("id", "module", "loader", "loaderName", "minecraftVersion", "projectDir", "modName", "initialVersion", "releaseType", "modrinthDependencies")
    foreach ($name in $required) {
        if (-not $entry.$name) {
            throw "Release entry is missing '$name'"
        }
    }
    if ($entry.initialVersion -notmatch '^\d+\.\d+\.\d+$') {
        throw "$($entry.id) initialVersion must be x.y.z"
    }
    if ($entry.releaseType -notin @("alpha", "beta", "release")) {
        throw "$($entry.id) releaseType must be alpha, beta, or release"
    }
    $dependencyIds = [Collections.Generic.HashSet[string]]::new()
    foreach ($dependency in @($entry.modrinthDependencies)) {
        if (-not $dependency.project_id -or $dependency.dependency_type -notin @("required", "optional", "incompatible", "embedded")) {
            throw "$($entry.id) has invalid Modrinth dependency metadata"
        }
        if (-not $dependencyIds.Add([string]$dependency.project_id)) {
            throw "$($entry.id) has duplicate Modrinth dependency $($dependency.project_id)"
        }
    }
}

function Resolve-ReleaseEntry($entry) {
    $resolved = [ordered]@{}
    foreach ($property in $entry.PSObject.Properties) {
        $resolved[$property.Name] = $property.Value
    }

    $resolved.releaseType =
        if ($ReleaseType) { $ReleaseType }
        elseif ($env:RELEASE_TYPE) { $env:RELEASE_TYPE }
        else { $resolved.releaseType }

    $resolved.modrinthProjectId =
        if ($ModrinthProjectId) { $ModrinthProjectId }
        elseif ($env:MODRINTH_PROJECT_ID) { $env:MODRINTH_PROJECT_ID }
        else { $resolved.modrinthProjectId }

    $resolved.curseProjectId =
        if ($CurseProjectId) { $CurseProjectId }
        elseif ($env:CURSEFORGE_PROJECT_ID) { $env:CURSEFORGE_PROJECT_ID }
        else { $resolved.curseProjectId }

    return [pscustomobject]$resolved
}

function Get-CurseForgeRelations($entry) {
    if (-not @($entry.modrinthDependencies | Where-Object project_id -eq "Ck4E7v7R")) {
        return @()
    }
    return @(
        [ordered]@{ slug = "applied-energistics-2"; type = "requiredDependency" },
        [ordered]@{ slug = "guideme"; type = "optionalDependency" }
    )
}

function Next-PatchVersion([string]$version) {
    if ($version -notmatch '^(\d+)\.(\d+)\.(\d+)$') {
        throw "Version '$version' is not x.y.z"
    }
    return "$($Matches[1]).$($Matches[2]).$([int]$Matches[3] + 1)"
}

function Get-ArtifactFileName($entry, [string]$version) {
    return "$($entry.modName)-$version-$($entry.loader)-$($entry.minecraftVersion).jar"
}

function Get-DevelopmentVersion([string]$path) {
    if (-not (Test-Path $path)) {
        throw "Version file does not exist: $path"
    }
    foreach ($line in Get-Content -LiteralPath $path) {
        if ($line -match '^modVersion=(\d+\.\d+\.\d+)$') {
            return $Matches[1]
        }
    }
    throw "Version file must contain modVersion=x.y.z: $path"
}

function Set-DevelopmentVersion([string]$path, [string]$version) {
    $updated = foreach ($line in Get-Content -LiteralPath $path) {
        if ($line -match '^modVersion=') { "modVersion=$version" } else { $line }
    }
    [IO.File]::WriteAllText($path, (($updated -join [Environment]::NewLine) + [Environment]::NewLine), (New-Object Text.UTF8Encoding($false)))
}

function Get-InputFingerprint($entry) {
    $paths = @(
        "build.gradle",
        "settings.gradle",
        "shared/src/main",
        "shared/src/mcCommon",
        $(if ($entry.minecraftVersion -eq '26.1.2') { 'shared/src/mc2612' } else { 'shared/src/mc1201' }),
        "shared/build.gradle",
        "$($entry.projectDir)/build.gradle",
        "$($entry.projectDir)/src/main"
    )
    if ($entry.loader -eq 'neoforge') {
        $paths += 'shared/src/neoforge'
    }
    elseif ($entry.loader -eq 'forge') {
        $paths += 'shared/src/neoforge/java/com/ctux/ae2craftingtime/mc1201/mixin'
    }

    $hashes = foreach ($path in $paths) {
        $fullPath = Join-Path $root $path
        if (-not (Test-Path $fullPath)) {
            throw "Missing release input path: $path"
        }

        Get-ChildItem -Path $fullPath -Recurse -File |
            Where-Object { $_.FullName -notmatch '\\(build|run|bin|logs)\\' } |
            Sort-Object FullName |
            ForEach-Object {
                $relative = $_.FullName.Substring($root.Length + 1).Replace('\', '/')
                $fileHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash
                "$relative $fileHash"
            }
    }

    $bytes = [Text.Encoding]::UTF8.GetBytes(($hashes -join "`n"))
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($sha.ComputeHash($bytes)) -replace '-', '').ToLowerInvariant()
    }
    finally {
        $sha.Dispose()
    }
}

function Get-StateEntry($state, [string]$id) {
    if ($state.PSObject.Properties.Name -contains $id) {
        return $state.$id
    }
    return $null
}

function Set-StateEntry($state, [string]$id, $value) {
    if ($state.PSObject.Properties.Name -contains $id) {
        $state.$id = $value
    }
    else {
        $state | Add-Member -NotePropertyName $id -NotePropertyValue $value
    }
}

function Invoke-Curl($arguments) {
    $output = & curl.exe @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "curl failed with exit code $LASTEXITCODE`: $($output -join "`n")"
    }
    return $output
}

function Invoke-Git($arguments) {
    $output = & git @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git failed with exit code $LASTEXITCODE"
    }
    return $output
}

function Format-Changelog($subjects) {
    $groups = [ordered]@{
        ADDED = @()
        FIXED = @()
        IMPROVED = @()
        DELETED = @()
        CHANGED = @()
    }

    foreach ($subject in $subjects) {
        $type = ""
        $text = $subject.Trim()
        if ($text -match '^(?<type>[a-z]+)(?:\([^)]+\))?!?:\s*(?<text>.+)$') {
            $type = $Matches.type
            $text = $Matches.text.Trim()
        }
        $category = if ($text -match '^(?i:delete|drop|remove)\b') {
            "DELETED"
        }
        else {
            switch ($type) {
                "feat" { "ADDED" }
                "fix" { "FIXED" }
                "perf" { "IMPROVED" }
                default { "CHANGED" }
            }
        }
        if ($text) {
            $text = $text.Substring(0, 1).ToUpperInvariant() + $text.Substring(1)
            if ($text -notmatch '[.!?]$') { $text += "." }
            $groups[$category] += "- $text"
        }
    }

    return (($groups.Keys | Where-Object { $groups[$_].Count -gt 0 } | ForEach-Object {
        "### $_`n`n$($groups[$_] -join "`n")"
    }) -join "`n`n")
}

function Assert-Changelog([string]$text, [switch]$RequireLinks) {
    if ($text -notmatch '(?m)^### (ADDED|FIXED|IMPROVED|DELETED|CHANGED)$') {
        throw "Changelog must use human-readable ### ADDED, FIXED, IMPROVED, DELETED, or CHANGED categories"
    }
    if ($RequireLinks) {
        foreach ($line in @($text -split "`r?`n" | Where-Object { $_ -match '^- ' })) {
            if ($line -notmatch '\(\[#\d+\]\(https://github\.com/cTux/ae2-crafting-time/(issues|pull)/\d+\)\)$') {
                throw "Every manual changelog item must end with a linked GitHub reference such as ([#111](https://github.com/cTux/ae2-crafting-time/issues/111))"
            }
        }
    }
    return $text
}

function Get-EntryChangelogs($entry, $previous) {
    if ($scopedChangelogs) {
        $common = Get-StateEntry $scopedChangelogs "all"
        $specific = Get-StateEntry $scopedChangelogs $entry.id
        if (-not $common -and -not $specific) {
            throw "Scoped changelog has no 'all' or '$($entry.id)' entry"
        }
        $common = if ($common) { Assert-Changelog $common -RequireLinks } else { $null }
        $specific = if ($specific) { Assert-Changelog $specific -RequireLinks } else { $null }
        return [pscustomobject]@{
            common = $common
            specific = $specific
            platform = (@($common, $specific) | Where-Object { $_ }) -join "`n`n"
        }
    }

    $notes = if ($Changelog) {
        Assert-Changelog $Changelog -RequireLinks
    }
    elseif (-not ($previous -and $previous.commit)) {
        Assert-Changelog $entry.changelog
    }
    else {
        $paths = @(
            "build.gradle",
            "settings.gradle",
            "shared/src/main",
            "shared/src/mc1201",
            "$($entry.projectDir)/build.gradle",
            "$($entry.projectDir)/src/main"
        )
        $subjects = @(Invoke-Git (@("log", "$($previous.commit)..HEAD", "--format=%s", "--") + $paths))
        if ($subjects.Count -eq 0) { Assert-Changelog $entry.changelog } else { Format-Changelog $subjects }
    }

    return [pscustomobject]@{ common = $null; specific = $null; platform = $notes }
}

function Publish-Modrinth($entry, [string]$version, [string]$jarPath, [string]$notes) {
    if (-not $entry.modrinthProjectId) { return }
    if (-not $env:MODRINTH_TOKEN) { throw "MODRINTH_TOKEN is required for Modrinth upload" }

    $dataPath = New-TemporaryFile
    try {
        $data = @{
            name = [IO.Path]::GetFileNameWithoutExtension((Get-ArtifactFileName $entry $version))
            version_number = "$version-$($entry.loader)-$($entry.minecraftVersion)"
            changelog = $notes
            dependencies = @($entry.modrinthDependencies)
            game_versions = @($entry.minecraftVersion)
            version_type = $entry.releaseType
            loaders = @($entry.loader)
            featured = $false
            status = "listed"
            requested_status = "listed"
            project_id = $entry.modrinthProjectId
            file_parts = @("file")
            primary_file = "file"
        }
        Write-Json $dataPath $data

        Invoke-Curl @(
            "-sS", "--fail-with-body",
            "-H", "Authorization: $env:MODRINTH_TOKEN",
            "-H", "User-Agent: ctux/ae2-crafting-time-release-script",
            "-F", "data=<$dataPath;type=application/json",
            "-F", "file=@$jarPath;type=application/java-archive",
            "https://api.modrinth.com/v2/version"
        ) | Out-Null
    }
    finally {
        Remove-Item -LiteralPath $dataPath -Force -ErrorAction SilentlyContinue
    }
}

function Publish-CurseForge($entry, [string]$version, [string]$jarPath, [string]$notes) {
    if (-not $entry.curseProjectId) { return }
    if (-not $env:CURSEFORGE_TOKEN) { throw "CURSEFORGE_TOKEN is required for CurseForge upload" }

    $metadataPath = New-TemporaryFile
    try {
        $metadata = @{
            changelog = $notes
            changelogType = "text"
            displayName = [IO.Path]::GetFileNameWithoutExtension((Get-ArtifactFileName $entry $version))
            gameVersionNames = @($entry.minecraftVersion, $entry.loaderName, "Client", "Server")
            releaseType = $entry.releaseType
            isMarkedForManualRelease = $false
        }
        $relations = @(Get-CurseForgeRelations $entry)
        if ($relations.Count) {
            $metadata["relations"] = @{ projects = $relations }
        }
        Write-Json $metadataPath $metadata

        Invoke-Curl @(
            "-sS", "--fail-with-body",
            "-H", "X-Api-Token: $env:CURSEFORGE_TOKEN",
            "-F", "metadata=<$metadataPath;type=application/json",
            "-F", "file=@$jarPath;type=application/java-archive",
            "https://minecraft.curseforge.com/api/projects/$($entry.curseProjectId)/upload-file"
        ) | Out-Null
    }
    finally {
        Remove-Item -LiteralPath $metadataPath -Force -ErrorAction SilentlyContinue
    }
}

function Publish-GitHubRelease($releases, $jars, [string]$sourceCommit) {
    $title = $releases[0].version
    $tag = "release-$title"
    if ($scopedChangelogs) {
        $sections = @()
        if ($releases[0].commonChangelog) {
            $sections += "## All versions`n`n$($releases[0].commonChangelog)"
        }
        $sections += @($releases | Where-Object specificChangelog | Group-Object specificChangelog | ForEach-Object {
            $heading = ($_.Group | ForEach-Object { "$($_.entry.loaderName) $($_.entry.minecraftVersion)" }) -join ", "
            "## $heading`n`n$($_.Name)"
        })
        $notes = $sections -join "`n`n"
    }
    else {
        $notes = ($releases | Group-Object changelog | ForEach-Object {
            $heading = if ($_.Count -eq $releases.Count) {
                "All versions"
            }
            else {
                ($_.Group | ForEach-Object { "$($_.entry.loaderName) $($_.entry.minecraftVersion)" }) -join ", "
            }
            "## $heading`n`n$($_.Name)"
        }) -join "`n`n"
    }
    if ($SkipModrinth) {
        $notes += "`n`n## Availability`n`nThis release is available on GitHub and CurseForge. Modrinth was skipped."
    }

    if ($DryRun) {
        Write-Host "dry-run GitHub Release: $title"
        Write-Host "dry-run GitHub tag: $tag"
        Write-Host "dry-run GitHub assets: $(($jars | ForEach-Object { Split-Path -Leaf $_.jarPath }) -join ', ')"
        Write-Host $notes
        return
    }

    $notesPath = New-TemporaryFile
    try {
        Set-Content -LiteralPath $notesPath -Value $notes -Encoding UTF8
        $arguments = @("release", "create", $tag) + @($jars.jarPath) + @(
            "--target", $sourceCommit,
            "--title", $title,
            "--notes-file", $notesPath
        )
        if ($releases.entry.releaseType -contains "alpha" -or $releases.entry.releaseType -contains "beta") {
            $arguments += "--prerelease"
        }
        & gh @arguments
        if ($LASTEXITCODE -ne 0) { throw "GitHub Release creation failed" }
    }
    finally {
        Remove-Item -LiteralPath $notesPath -Force -ErrorAction SilentlyContinue
    }
}

$matrix = Read-Json $MatrixPath @()
$state = Read-Json $StatePath ([pscustomobject]@{})
$developmentVersion = Get-DevelopmentVersion $VersionPath
if ($Changelog -and $ChangelogPath) {
    throw "Use either -Changelog or -ChangelogPath, not both"
}
$scopedChangelogs = if ($ChangelogPath) {
    if (-not (Test-Path -LiteralPath $ChangelogPath)) { throw "Changelog file does not exist: $ChangelogPath" }
    Read-Json $ChangelogPath ([pscustomobject]@{})
}
else { $null }
if ($scopedChangelogs) {
    $validKeys = @("all") + @($matrix.id)
    foreach ($key in $scopedChangelogs.PSObject.Properties.Name) {
        if ($key -notin $validKeys) { throw "Unknown scoped changelog entry: $key" }
    }
}

Push-Location $root
try {
    if ($Deploy -and -not $DryRun -and (git status --porcelain)) {
        throw "Commit or stash all changes before creating a release"
    }

    $sourceCommit = (Invoke-Git @("rev-parse", "HEAD")) -join ""
    $plans = @()
    foreach ($entry in $matrix) {
        $entry = Resolve-ReleaseEntry $entry
        Assert-ReleaseEntry $entry
        $fingerprint = Get-InputFingerprint $entry
        $previous = Get-StateEntry $state $entry.id
        $currentVersion = if ($previous -and $previous.version) { $previous.version } else { $entry.initialVersion }

        $changed = -not ($previous -and $previous.fingerprint -eq $fingerprint)
        if (-not $changed) {
            Write-Host "skip $($entry.id): unchanged at $currentVersion"
        }
        if ($changed -and $Deploy -and -not $SkipModrinth -and -not $entry.modrinthProjectId) {
            throw "$($entry.id) has no Modrinth project id. Set it in the matrix or MODRINTH_PROJECT_ID."
        }
        if ($changed -and $Deploy -and -not $entry.curseProjectId) {
            throw "$($entry.id) has no CurseForge project id. Set it in the matrix or CURSEFORGE_PROJECT_ID."
        }

        if ($changed -and $previous -and [Version]$developmentVersion -le [Version]$currentVersion) {
            throw "Development version $developmentVersion must be newer than released $($entry.id) $currentVersion"
        }
        $version = if ($changed) { $developmentVersion } else { $currentVersion }
        $changelogs = if ($changed) { Get-EntryChangelogs $entry $previous } else { $null }
        $plans += [pscustomobject]@{
            entry = $entry
            version = $version
            fingerprint = $fingerprint
            jarPath = Join-Path $root "dist\$(Get-ArtifactFileName $entry $version)"
            changelog = if ($changed) { $changelogs.platform } else { $null }
            commonChangelog = if ($changed) { $changelogs.common } else { $null }
            specificChangelog = if ($changed) { $changelogs.specific } else { $null }
            changed = $changed
        }
    }

    $releases = @($plans | Where-Object changed)
    $builds = if ($Deploy -and $releases.Count -gt 0) { $plans } else { $releases }
    foreach ($build in $builds) {
        $label = if ($build.changed) { "build" } else { "build latest" }
        Write-Host "$label $($build.entry.id): $($build.version)"
        if (-not $DryRun) {
            & $gradlew ":$($build.entry.module):distMod" "-PmodVersion=$($build.version)" --no-daemon
            if ($LASTEXITCODE -ne 0) {
                throw "Gradle failed for $($build.entry.id)"
            }
            if (-not (Test-Path $build.jarPath)) {
                throw "Expected jar was not created: $($build.jarPath)"
            }
        }
    }

    if ($Deploy -and $releases.Count -gt 0) {
        foreach ($release in $releases) {
            if ($DryRun) {
                Write-Host "dry-run deploy $($release.entry.id): $($release.jarPath)"
                if ($SkipModrinth) {
                    Write-Host "dry-run Modrinth: skipped"
                }
                else {
                    Write-Host "dry-run Modrinth version: $($release.version)-$($release.entry.loader)-$($release.entry.minecraftVersion)"
                    Write-Host "dry-run Modrinth dependencies: $((@($release.entry.modrinthDependencies) | ForEach-Object { "$($_.project_id):$($_.dependency_type)" }) -join ', ')"
                }
                Write-Host "dry-run CurseForge versions: $($release.entry.minecraftVersion), $($release.entry.loaderName), Client, Server"
                $relations = @(Get-CurseForgeRelations $release.entry)
                if ($relations.Count) {
                    Write-Host "dry-run CurseForge relations: $((@($relations) | ForEach-Object { "$($_.slug):$($_.type)" }) -join ', ')"
                }
                Write-Host "dry-run changelog $($release.entry.id):"
                Write-Host $release.changelog
            }
            else {
                if (-not $SkipModrinth) {
                    Publish-Modrinth $release.entry $release.version $release.jarPath $release.changelog
                }
                Publish-CurseForge $release.entry $release.version $release.jarPath $release.changelog
            }
        }
        Publish-GitHubRelease $releases $plans $sourceCommit
    }

    if ($Deploy -and $releases.Count -gt 0) {
        $nextDevelopmentVersion = Next-PatchVersion $developmentVersion
        if ($DryRun) {
            Write-Host "dry-run next development version: $nextDevelopmentVersion"
        }
        else {
            Set-DevelopmentVersion $VersionPath $nextDevelopmentVersion
        }
    }

    if (-not $DryRun) {
        foreach ($release in $releases) {
            Set-StateEntry $state $release.entry.id ([pscustomobject]@{
                version = $release.version
                fingerprint = $release.fingerprint
                jar = $release.jarPath.Substring($root.Length + 1).Replace('\', '/')
                commit = $sourceCommit
                updatedAt = (Get-Date).ToUniversalTime().ToString("o")
            })
        }
        Write-Json $StatePath $state
    }

    if ($Deploy -and -not $DryRun -and $releases.Count -gt 0) {
        if (-not $StatePath.StartsWith("$root\", [StringComparison]::OrdinalIgnoreCase)) {
            throw "Release state must be inside the repository: $StatePath"
        }
        if (-not $VersionPath.StartsWith("$root\", [StringComparison]::OrdinalIgnoreCase)) {
            throw "Version file must be inside the repository: $VersionPath"
        }
        $stateRelativePath = $StatePath.Substring($root.Length + 1)
        $versionRelativePath = $VersionPath.Substring($root.Length + 1)
        Invoke-Git @("add", "--", $stateRelativePath, $versionRelativePath) | Out-Null
        $versions = ($releases | ForEach-Object { "$($_.entry.id) $($_.version)" }) -join ", "
        Invoke-Git @("commit", "-m", "chore(release): $versions") | Out-Null
        Invoke-Git @("push") | Out-Null
    }
}
finally {
    Pop-Location
}
