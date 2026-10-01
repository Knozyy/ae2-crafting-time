param(
    [switch]$Changed,
    [string]$BaseRef = 'origin/master',
    [string]$Target,
    [string]$Scenario = 'suite',
    [string[]]$ProjectId,
    [switch]$Latest,
    [switch]$Interactive,
    [switch]$BaseOnly,
    [string]$Repository = (Split-Path -Parent $PSScriptRoot),
    [string]$MatrixDirectory = $PSScriptRoot,
    [string]$ExpectedFingerprint
)
$ErrorActionPreference = 'Stop'
if ($Changed -and ($Target -or $PSBoundParameters.ContainsKey('Scenario') -or $ProjectId -or $Latest -or $Interactive -or $BaseOnly)) {
    throw 'Changed mode cannot override Target, Scenario, ProjectId, Latest, Interactive or BaseOnly'
}
if ($Interactive -and (!$Target -or $Scenario -in @('suite','standard-ae2'))) { throw 'Interactive smoke requires one target and one leaf' }
if ($ProjectId -and $BaseOnly) { throw 'ProjectId and BaseOnly select different dependency graphs' }
if ($ProjectId -and $Scenario -in @('suite','standard-ae2')) { throw 'Groups require the full graph' }

# Read native stdout directly: PowerShell line enumeration loses newline filenames.
function Read-Git([string[]]$Arguments) {
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = 'git'
    $start.WorkingDirectory = $Repository
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.StandardOutputEncoding = [Text.Encoding]::UTF8
    $start.Arguments = ($Arguments | ForEach-Object { '"' + ($_ -replace '(\\*)"', '$1$1\"' -replace '(\\+)$', '$1$1') + '"' }) -join ' '
    $process = [Diagnostics.Process]::Start($start)
    try {
        $stderr = $process.StandardError.ReadToEndAsync()
        $stdout = $process.StandardOutput.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw "Git failed ($($Arguments[0])): $($stderr.Result)" }
        return $stdout
    } finally { $process.Dispose() }
}
function Hash-Text([string]$Value) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Value)))).Replace('-','') }
    finally { $sha.Dispose() }
}
function Read-Language([string]$Json) {
    # Language resources are flat string maps. Reject duplicate keys before ConvertFrom-Json.
    $string = '"(?:[^"\\\x00-\x1f]|\\(?:["\\/bfnrt]|u[0-9a-fA-F]{4}))*"'
    $pair = "(?<key>$string)\s*:\s*(?<value>$string)"
    if ($Json -cnotmatch "^\s*\{\s*(?:$pair(?:\s*,\s*$pair)*)?\s*\}\s*$") { throw 'Malformed language JSON; expected a flat string map' }
    $map = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach ($match in [regex]::Matches($Json, $pair)) {
        $key = $match.Groups['key'].Value | ConvertFrom-Json
        if ($map.ContainsKey($key)) { throw "Duplicate language key: $key" }
        $map.Add($key, ($match.Groups['value'].Value | ConvertFrom-Json))
    }
    return ,$map
}
$release = Get-Content -LiteralPath (Join-Path $MatrixDirectory 'release-matrix.json') -Raw | ConvertFrom-Json
$ids = @($release.id)
if ($Target -and $Target -cnotin $ids) { throw "Unknown target: $Target" }
$rules = Get-Content -LiteralPath (Join-Path $MatrixDirectory 'ui-smoke-impact.json') -Raw | ConvertFrom-Json
if ($rules.schema -ne 1) { throw 'Invalid smoke impact schema' }
$ruleIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($rule in @($rules.ownership) + @($rules.noRuntime) + @($rules.behavior)) {
    if (!$rule.id -or !$ruleIds.Add($rule.id) -or !$rule.pattern) { throw 'Missing or duplicate impact rule' }
    $null = [regex]::new($rule.pattern, [Text.RegularExpressions.RegexOptions]::CultureInvariant, [TimeSpan]::FromSeconds(1))
    foreach ($id in $rule.targets) { if ($id -cnotin $ids) { throw "Invalid ownership target: $id" } }
    foreach ($case in $rule.cases) {
        foreach ($id in $(if ($rule.targets) { $rule.targets } else { $ids })) {
            $null = & "$PSScriptRoot/expand-ui-smoke-groups.ps1" -Target $id -Scenarios $case -MatrixDirectory $MatrixDirectory
        }
    }
}
$head = (Read-Git @('rev-parse','--verify','--end-of-options','HEAD^{commit}')).Trim()
$base = $null
$merge = $null
$changes = [Collections.Generic.List[object]]::new()
function Add-Diff([string]$From, [string]$To, [string]$Layer, [string[]]$Arguments) {
    $records = (Read-Git $Arguments).Split([char]0)
    for ($i = 0; $i -lt $records.Length - 1; $i++) {
        $status = $records[$i]
        if ($status -cnotmatch '^[ACDMRTUXB][0-9]*$') { throw "Invalid diff status: $status" }
        if ($status.StartsWith('U')) { throw 'Unmerged files prevent smoke planning' }
        $path = $records[++$i]
        $changes.Add([pscustomobject]@{ path=$path; status=$status; layer=$Layer; from=$From; to=$To })
        if ($status -cmatch '^[RC]') {
            $path = $records[++$i]
            $changes.Add([pscustomobject]@{ path=$path; status=$status; layer=$Layer; from=$From; to=$To })
        }
    }
}
if (Read-Git @('ls-files','--unmerged','-z')) { throw 'Unmerged files prevent smoke planning' }
if ($Changed) {
    $base = (Read-Git @('rev-parse','--verify','--end-of-options',"$BaseRef^{commit}")).Trim()
    $merge = (Read-Git @('merge-base','--all',$base,$head)).Trim()
    if ($merge -cnotmatch '^[0-9a-f]{40,64}$') { throw 'Missing or ambiguous merge base' }
    Add-Diff $merge $head 'committed' @('diff','--name-status','-z','-M',$merge,$head,'--')
}
Add-Diff $head ':' 'staged' @('diff','--cached','--name-status','-z','-M',$head,'--')
Add-Diff ':' 'worktree' 'unstaged' @('diff','--name-status','-z','-M','--')
foreach ($path in (Read-Git @('ls-files','--others','--exclude-standard','-z')).Split([char]0)) {
    if ($path) { $changes.Add([pscustomobject]@{ path=$path; status='A'; layer='untracked'; from=$null; to='worktree' }) }
}
$hashes = [ordered]@{}
foreach ($name in @('release-matrix.json','run-client-versions.json','ui-smoke-impact.json','ui-smoke-groups.json','ui-smoke-coverage.json',
        'ui-smoke-forge-suite.json','ui-smoke-fabric-suite.json','ui-smoke-neoforge-suite.json','ui-smoke-neoforge-26.1.2-suite.json')) {
    $hashes[$name] = (Get-FileHash -LiteralPath (Join-Path $MatrixDirectory $name) -Algorithm SHA256).Hash
}
$content = @()
foreach ($path in @($changes.path | Sort-Object -Unique -CaseSensitive)) {
    $file = Join-Path $Repository $path
    $content += @($path, $(if ([IO.File]::Exists($file)) { (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash } else { 'DELETED' }))
}
# Include the index identity even when staged content differs from the final worktree.
$fingerprint = Hash-Text (@($head, $base, $merge, (Read-Git @('diff','--cached','--binary','--')), ($changes | ConvertTo-Json -Depth 6 -Compress),
    ($hashes | ConvertTo-Json -Compress), ($content | ConvertTo-Json -Compress)) | ConvertTo-Json -Compress)
if ($ExpectedFingerprint -and $ExpectedFingerprint -cne $fingerprint) { throw 'STALE_PLAN: HEAD, worktree or selection rules changed; replan' }
$selection = @{}
$reasons = [Collections.Generic.List[object]]::new()
foreach ($change in $changes) {
    if (!$Changed) { continue }
    $path = $change.path
    $owners = @($rules.ownership | Where-Object { $path -cmatch $_.pattern })
    $targets = if ($owners.Count) { @($owners.targets | Sort-Object -Unique) } else { $ids }
    $ignored = @($rules.noRuntime | Where-Object { $path -cmatch $_.pattern })
    $behavior = @($rules.behavior | Where-Object { $path -cmatch $_.pattern })
    if ($ignored.Count -and $behavior.Count) { throw "Contradictory runtime classification: $path" }
    $cases = @()
    $targetedBehavior = $false
    $reason = ''
    $fallback = $false
    if ($ignored.Count) { $reason = $ignored.reason -join '; ' }
    elseif ($path -cmatch '/resources/assets/ae2craftingtime/lang/en_us\.json$' -and $change.status -ceq 'M') {
        $oldRef = if ($change.from -eq ':') { ":$path" } else { "$($change.from):$path" }
        $old = Read-Language (Read-Git @('show',$oldRef,'--'))
        $newJson = if ($change.to -eq 'worktree') { Get-Content -LiteralPath (Join-Path $Repository $path) -Raw -Encoding UTF8 }
            else { $newRef = if ($change.to -eq ':') { ":$path" } else { "$($change.to):$path" }; Read-Git @('show',$newRef,'--') }
        $new = Read-Language $newJson
        $keys = @(@($old.Keys) + @($new.Keys) | Sort-Object -Unique -CaseSensitive | Where-Object { !$old.ContainsKey($_) -or !$new.ContainsKey($_) -or $old[$_] -cne $new[$_] })
        if (!$keys.Count) { $reason = 'Language formatting only; static validation still required' }
        elseif (!@($keys | Where-Object { $_ -notin @('text.ae2craftingtime.plan.stored_variant','text.ae2craftingtime.plan.stored_variant.explanation','text.ae2craftingtime.plan.stored_variant.suggestion') }).Count) {
            $cases = @('stored-variant-plan','standard-plan-controls','recurrent-plan'); $reason = 'English stored-variant labels changed'
        } elseif (!@($keys | Where-Object { $_ -notin @('text.ae2craftingtime.plan.recurrent','text.ae2craftingtime.plan.recurrent_hint') }).Count) {
            $cases = @('recurrent-plan','standard-plan-controls'); $reason = 'English recurrent-plan labels changed'
        } elseif (!@($keys | Where-Object { $_ -notin @('text.ae2craftingtime.status.amounts','text.ae2craftingtime.status.amounts_legend','config.ae2craftingtime.compactStatusAmounts') }).Count) {
            $cases = @('standard-status-controls'); $reason = 'English compact-status amount labels changed'
        } elseif (@($keys | Where-Object { $_ -cne 'text.ae2craftingtime.ttc_delayed' }).Count) { $cases = @('suite'); $reason = 'English keys affect general UI' }
        else { $cases = @('delayed-status'); $reason = 'English delayed label changed' }
    } elseif ($behavior.Count) { $cases = @($behavior.cases | Select-Object -Unique); $reason = $behavior.reason -join '; '; $targetedBehavior = $true }
    else {
        if ($path -cmatch '/resources/assets/ae2craftingtime/lang/en_us\.json$' -and [IO.File]::Exists((Join-Path $Repository $path))) {
            $null = Read-Language (Get-Content -LiteralPath (Join-Path $Repository $path) -Raw -Encoding UTF8)
        }
        $cases = @('suite'); $fallback = $true; $reason = 'No narrow behavior rule; full consuming suites required'
    }
    $reasons.Add([pscustomobject]@{ path=$path; layer=$change.layer; status=$change.status; targets=@($targets); cases=@($cases); reason=$reason; fallback=$fallback })
    foreach ($id in $targets) {
        $targetCases = if ($targetedBehavior) { @($behavior | Where-Object { !$_.targets -or $id -cin $_.targets } | ForEach-Object { $_.cases } | Select-Object -Unique) } else { $cases }
        if ($targetCases.Count) { if (!$selection.ContainsKey($id)) { $selection[$id] = @() }; $selection[$id] += $targetCases }
    }
}
if (!$Changed) {
    foreach ($id in $ids) {
        if ($Target -and $id -cne $Target) { continue }
        if ($Scenario -ceq 'appmek-resource-icons' -and $id -notin @('1.20.1-forge','1.21.1-neoforge')) { continue }
        if ($Scenario -ceq 'chance-output-status' -and $id -cne '1.20.1-forge') { continue }
        if ($Scenario -ceq 'crafting-suspension' -and $id -cne '1.20.1-forge') { continue }
        $selection[$id] = @($Scenario)
    }
}
$entries = @()
foreach ($id in $ids) {
    if (!$selection.ContainsKey($id)) { continue }
    $requested = @($selection[$id] | Select-Object -Unique)
    $full = 'suite' -cin $requested
    if ($full) { $requested = @('suite') }
    $cases = @(& "$PSScriptRoot/expand-ui-smoke-groups.ps1" -Target $id -Scenarios $requested -MatrixDirectory $MatrixDirectory)
    if (!$cases.Count) { continue }
    $allCases = @(& "$PSScriptRoot/expand-ui-smoke-groups.ps1" -Target $id -Scenarios suite -MatrixDirectory $MatrixDirectory)
    $graphs = @()
    $primary = @($cases)
    $catalogue = Get-Content -LiteralPath (Join-Path $MatrixDirectory 'ui-smoke-groups.json') -Raw | ConvertFrom-Json
    $clients = Get-Content -LiteralPath (Join-Path $MatrixDirectory 'run-client-versions.json') -Raw | ConvertFrom-Json
    $coverage = (Get-Content -LiteralPath (Join-Path $MatrixDirectory 'ui-smoke-coverage.json') -Raw | ConvertFrom-Json).$id
    $client = $clients | Where-Object id -CEQ $id
    if (!$Latest -and !$ProjectId -and !$BaseOnly) {
        foreach ($project in $client.projects) {
            $declaration = $coverage.($project.project_id)
            if ($declaration.disposition -cne 'FOCUSED_BEHAVIOR') { continue }
            $adapterCases = @($catalogue.adapterCases.($project.mod_id))
            if (!$adapterCases.Count) { throw "Missing adapter fixture mapping: $($project.mod_id)" }
            $behaviorCases = @($catalogue.adapterBehaviorCases.($project.mod_id))
            $focusedCases = @(@($adapterCases) + @($behaviorCases) | Where-Object { $_ })
            $focused = if ($full) { @($focusedCases) } else { @($primary | Where-Object { $_ -cin $focusedCases }) }
            if (!$focused.Count) { continue }
            $primary = @($primary | Where-Object { $_ -cnotin $adapterCases })
            $focusedProfile = if ($declaration.profile -ceq 'compatible') { 'compatible' } else { 'latest' }
            $graphs += [pscustomobject]@{ id=$project.project_id; profile=$focusedProfile; cases=$focused; projectId=@($project.project_id); baseOnly=$false
                reason=$declaration.reason; adapterPolicy="$focusedProfile packaged catalogue variant; verify runtime selection" }
        }
    }
    $directCases = @($coverage.psobject.Properties | Where-Object {
        $_.Value.disposition -cin @('DIRECT_UI', 'DIRECT_BEHAVIOR')
    } | ForEach-Object { $_.Value.scenario } | Where-Object { $_ } | Select-Object -Unique)
    if (!$ProjectId -and 'cpu-list-total-ttc' -cin $primary -and
            @($primary | Where-Object { $_ -cin $directCases }).Count) {
        $primary = @($primary | Where-Object { $_ -cne 'cpu-list-total-ttc' })
        $graphs = @([pscustomobject]@{ id='cpu-list-total-ttc'; profile=$(if ($Latest) { 'latest' } else { 'compatible' })
            cases=@('cpu-list-total-ttc'); projectId=@(); baseOnly=$true; reason='CPU-list relaunch requires a sealed base graph'
            adapterPolicy='base AE2 graph for direct cases' }) + $graphs
    }
    if ($primary.Count) {
        $primaryProjects = @($ProjectId)
        $needsAppMekGraph = @($primary | Where-Object { $_ -cin @('appmek-resource-icons', 'chance-output-status') }).Count -gt 0
        if ('chance-output-status' -cin $primary -and $Latest) {
            throw 'Chance output fixture requires the pinned compatible Mekanism recipe graph'
        }
        if ($needsAppMekGraph -and $ProjectId -and 'IiATswDj' -cnotin $ProjectId) {
            throw 'Chance/AppMek fixture requires Applied Mekanistics project IiATswDj'
        }
        if ($needsAppMekGraph -and !$ProjectId) {
            $primaryProjects = @($client.projects | Where-Object mod_id -CEQ 'appmek' | ForEach-Object project_id)
            if ($primaryProjects.Count -ne 1) { throw "AppMek resource fixture has no unique project graph for $id" }
        }
        if ($BaseOnly -and $needsAppMekGraph) { throw 'Chance/AppMek fixture cannot use the base-only graph' }
        $statusCompatible = 'standard-status-controls' -cin $primary
        $primaryBaseOnly = $BaseOnly -or (!$needsAppMekGraph -and !$statusCompatible -and !$ProjectId -and
            !@($primary | Where-Object { $_ -cin $directCases }).Count)
        $graphs = @([pscustomobject]@{ id='primary'; profile=$(if ($Latest) { 'latest' } else { 'compatible' }); cases=$primary
            projectId=$primaryProjects; baseOnly=$primaryBaseOnly; reason='Requested dependency graph'
            adapterPolicy=$(if ($primaryBaseOnly) { 'base AE2 graph for direct cases' } else { 'packaged catalogue graph for direct cases' }) }) + $graphs
    }
    foreach ($graph in @($graphs)) {
        if ($graph.cases.Count -gt 1 -and 'standard-status-controls' -cin $graph.cases) {
            $graph.cases = @($graph.cases | Where-Object { $_ -cne 'standard-status-controls' })
            $graphs += [pscustomobject]@{ id="$($graph.id)-status-amounts"; profile=$graph.profile
                cases=@('standard-status-controls'); projectId=$graph.projectId; baseOnly=$graph.baseOnly
                reason='Status option persistence requires two client processes'; adapterPolicy=$graph.adapterPolicy }
        }
    }
    $entries += [pscustomobject]@{ target=$id; graphs=$graphs; mode=$(if ($full) { 'full' } else { 'focused' }); cases=$cases
        notSelectedCases=@($allCases | Where-Object { $_ -cnotin $cases });
        groups=@($(if ($full -or 'standard-ae2' -cin $requested) { 'standard-ae2' })); profile=$(if ($Latest) { 'latest' } else { 'compatible' }) }
}
[pscustomobject]@{ schema=1; mode=$(if ($Changed) { 'changed' } else { 'manual' }); result=$(if ($entries.Count) { 'REQUIRED' } else { 'NOT_REQUIRED' })
    baseRef=$BaseRef; baseSha=$base; mergeBaseSha=$merge; headSha=$head; fingerprint=$fingerprint; ruleHashes=$hashes
    changes=$reasons.ToArray(); targets=$entries; notSelected=@($ids | Where-Object { !$selection.ContainsKey($_) }) }
