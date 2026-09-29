function Get-ResourceFixtureContractCases([string]$Scenario, [string]$Target) {
    if ($Scenario -ceq 'appmek-resource-icons') { return @('OXYGEN','HYDROGEN','CHEMICAL_OVERLAP') }
    return @('ITEM','WATER','LAVA') + $(if ($Target -ceq '1.20.1-forge') { @('BUCKETLESS') }) + @('FLUID_OVERLAP')
}

function Assert-ResourceFixtureChecks([object]$Checks, [string]$Scenario, [bool]$Connected, [bool]$FixtureOnly) {
    $expected = @('server-identity','real-dispatch','delayed-plates','native-locate','lifecycle',
        'capture-integrity','cleanup',$(if ($FixtureOnly) { 'fixture-only' } else { 'typed-keys' }))
    if ($Scenario -ceq 'delayed-resource-icons') {
        $expected += @('roof','foreign-owner','chat-beam')
        $expected += $(if ($Connected) { @('reconnect-clears-beam') } else { @('beam-recovery','beam-expiry') })
    }
    if ((Compare-Object $expected @($Checks.psobject.Properties.Name) -CaseSensitive) -or
            @($Checks.psobject.Properties | Where-Object { $_.Value -isnot [bool] -or !$_.Value }).Count) {
        throw 'Resource fixture client checks are missing, unexpected, or false'
    }
}

function Assert-ResourceIconEvidence([object]$Icon, [object]$Fixture, [string]$HeadSha, [string]$Graph) {
    if ($Icon.schema -ne 1 -or $Icon.semanticResult -cne 'PASS' -or
            $Icon.visualAcceptance -cne 'REVIEW_REQUIRED' -or
            $Icon.headSha -cne $HeadSha -or $Icon.graph -cne $Graph.ToLowerInvariant() -or
            $Icon.target -cne $Fixture.target -or $Icon.scenario -cne $Fixture.scenario -or
            $Icon.profile -cne $Fixture.profile -or
            !(Test-ResourceFixtureSequence -Expected @($Fixture.screenshots.name) -Actual @($Icon.screenshots.name)) -or
            !(Test-ResourceFixtureSequence -Expected @($Fixture.screenshots.sha256) -Actual @($Icon.screenshots.sha256)) -or
            (ConvertTo-Json -InputObject @($Fixture.clientObservations) -Depth 100 -Compress) -cne
                (ConvertTo-Json -InputObject @($Icon.observations) -Depth 100 -Compress)) {
        throw 'Resource icon production evidence identity, result, or capture binding is invalid'
    }
    foreach ($observation in @($Icon.observations)) {
        $active = @($observation.plates)
        if (!$active.Count) { continue }
        foreach ($plate in $active) {
            $matching = @($observation.serverJobs | Where-Object {
                $_.resource -ceq $plate.outputId -and $_.keyFingerprint -ceq $plate.keyFingerprint
            })
            if (!$plate.keyType -or $matching.Count -ne 1) {
                throw "Resource icon retained key disagrees with server job in $($observation.checkpoint)"
            }
        }
        foreach ($plate in @($observation.renderPlates)) {
            $matching = @($active | Where-Object {
                $_.outputId -ceq $plate.outputId -and $_.keyFingerprint -ceq $plate.keyFingerprint
            })
            if (!$plate.keyType -or $matching.Count -ne 1) {
                throw "Resource icon selected key disagrees with retained plate in $($observation.checkpoint)"
            }
        }
    }
}

function Assert-ResourceFixtureServerUnloaded([object[]]$Receipts) {
    $unloads = @($Receipts | Where-Object { $_.case -ceq 'WATER' -and $_.action -ceq 'UNLOAD_RELOAD' })
    if ($unloads.Count -ne 1 -or $unloads[0].unloadedObserved -ne $true) {
        throw 'Chunk reload lacks an observed unloaded chunk'
    }
}

function Get-ResourceFixtureContractCaptures([string[]]$Cases, [bool]$Connected, [bool]$Production = $false) {
    $result = @()
    foreach ($case in $Cases) {
        $prefix = $case.ToLowerInvariant().Replace('_','-')
        $states = @('held') + $(if ($Production -and $case -ceq 'WATER') {
            @('resource-reloaded','chunk-reloaded') }) +
            $(if ($Connected) { @('rejoined') }) +
            $(if ($case.EndsWith('OVERLAP')) { @('winner-promoted') }) +
            $(if ($case -ceq 'ITEM' -and !$Connected) { @('recovery-pair') }) + @('completed','cancel-held') +
            $(if ($Production -and $case.EndsWith('OVERLAP')) { @('provider-removed') }) + @('cancelled')
        $result += @($states | ForEach-Object { "$prefix-$_.png" })
    }
    return @($result + ($Cases[-1].ToLowerInvariant().Replace('_','-') + '-cleanup.png'))
}

function Get-ResourceFixtureManifestDigest([object[]]$Screenshots) {
    $canonical = [Text.StringBuilder]::new()
    foreach ($capture in $Screenshots) {
        [void]$canonical.Append([string]$capture.name).Append([char]0).Append(
            ([string]$capture.sha256).ToLowerInvariant()).Append("`n")
    }
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $hash = $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($canonical.ToString()))
        return -join @($hash | ForEach-Object { $_.ToString('x2') })
    }
    finally { $sha.Dispose() }
}

function Test-ResourceFixtureSequence {
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Expected,
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Actual
    )
    if ($Expected.Count -ne $Actual.Count) { return $false }
    for ($index = 0; $index -lt $Expected.Count; $index++) {
        if ([string]$Expected[$index] -cne [string]$Actual[$index]) { return $false }
    }
    return $true
}

function Test-ResourceFixtureJobs {
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Expected,
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Actual
    )
    if ($Expected.Count -ne $Actual.Count) { return $false }
    $numeric = @('slot','rawAmount','heldAmount','releasedAmount','dispatchCount','tick')
    $logical = @('busy','delayed','cancelled')
    for ($index = 0; $index -lt $Expected.Count; $index++) {
        $left = $Expected[$index]; $right = $Actual[$index]
        $leftNames = @($left.psobject.Properties.Name | Sort-Object)
        $rightNames = @($right.psobject.Properties.Name | Sort-Object)
        if (!(Test-ResourceFixtureSequence -Expected $leftNames -Actual $rightNames)) { return $false }
        foreach ($name in $leftNames) {
            if ($name -in $numeric) {
                if ([decimal]$left.$name -ne [decimal]$right.$name) { return $false }
            } elseif ($name -in $logical) {
                if ([bool]$left.$name -ne [bool]$right.$name) { return $false }
            } elseif ([string]$left.$name -cne [string]$right.$name) { return $false }
        }
    }
    return $true
}

function Get-ResourceFixtureOutputs([string]$Case) {
    switch ($Case) {
        'ITEM' { @('minecraft:stone') }
        'WATER' { @('minecraft:water') }
        'LAVA' { @('minecraft:lava') }
        'BUCKETLESS' { @('ae2craftingtime_test_driver:resource_fixture_fluid') }
        'FLUID_OVERLAP' { @('minecraft:water','minecraft:lava') }
        'OXYGEN' { @('mekanism:oxygen') }
        'HYDROGEN' { @('mekanism:hydrogen') }
        'CHEMICAL_OVERLAP' { @('mekanism:oxygen','mekanism:hydrogen') }
        default { throw "Unknown resource fixture case $Case" }
    }
}

function Get-ResourceFixtureReceipt([object[]]$Receipts, [string]$Case, [string]$Checkpoint) {
    $action = if ($Checkpoint.EndsWith('-cancel-held')) { 'CREATE' }
        elseif ($Checkpoint.EndsWith('-held') -or $Checkpoint.EndsWith('-resource-reloaded')) { 'CREATE' }
        elseif ($Checkpoint.EndsWith('-rejoined')) { 'RECONNECT' }
        elseif ($Checkpoint.EndsWith('-provider-removed')) { 'REMOVE_PROVIDER' }
        elseif ($Checkpoint.EndsWith('-chunk-reloaded')) { 'UNLOAD_RELOAD' }
        elseif ($Checkpoint.EndsWith('-winner-promoted') -or $Checkpoint.EndsWith('-recovery-pair') -or
                $Checkpoint.EndsWith('-completed')) { 'RELEASE' }
        elseif ($Checkpoint.EndsWith('-cancelled')) { 'CANCEL' }
        else { throw "No authoritative receipt mapping for $Checkpoint" }
    $matches = @($Receipts | Where-Object { $_.case -ceq $Case -and $_.action -ceq $action })
    if ($Checkpoint.EndsWith('-cancel-held')) { return $matches[-1] }
    if ($Checkpoint.EndsWith('-recovery-pair') -or $Checkpoint.EndsWith('-completed') -or
            $Checkpoint.EndsWith('-cancelled')) { return $matches[-1] }
    return $matches[0]
}

function Assert-ResourceFixtureContract([object]$Evidence, [string]$Scenario, [string]$Target,
        [bool]$Connected, [string]$Epoch, [string]$Fixture) {
    $cases = Get-ResourceFixtureContractCases $Scenario $Target
    $production = @($Evidence.checks.psobject.Properties.Name) -contains 'typed-keys'
    $captures = Get-ResourceFixtureContractCaptures $cases $Connected $production
    if ($Evidence.connected -ne $Connected -or
            $Evidence.serverState.epoch.Replace('-','') -cne $Epoch.Replace('-','') -or
            $Evidence.serverState.fixture.Replace('-','') -cne $Fixture.Replace('-','') -or
            (Compare-Object $captures @($Evidence.screenshots.name) -SyncWindow 0 -CaseSensitive) -or
            (Compare-Object $captures @($Evidence.clientObservations.checkpoint) -SyncWindow 0 -CaseSensitive)) {
        throw 'Resource fixture identity or fixed capture contract is invalid'
    }
    $digest = Get-ResourceFixtureManifestDigest @($Evidence.screenshots)
    if ($digest -cne $Evidence.screenshotManifestDigest -or $digest -cne $Evidence.clientEvidence.digest) {
        throw 'Resource screenshot manifest digest or final client acknowledgement is invalid'
    }
    if (@($Evidence.sidecars).Count -ne $captures.Count) { throw 'Resource sidecar count does not match captures' }
    $locatedExpiry = @{}
    foreach ($observation in @($Evidence.clientObservations)) {
        $checkpoint = [IO.Path]::GetFileNameWithoutExtension([string]$observation.checkpoint)
        $case = [string]$observation.case
        $captureIndex = [Array]::IndexOf($captures, [string]$observation.checkpoint)
        $sidecar = @($Evidence.sidecars)[$captureIndex]
        $shot = @($Evidence.screenshots)[$captureIndex]
        $observationMillis = [long]$observation.observedAtMillis
        [object[]]$serverJobs = @($observation.serverJobs)
        [object[]]$plates = @($observation.plates)
        [object[]]$renderPlates = @($observation.renderPlates)
        [object[]]$rainbows = @($observation.rainbows)
        if ($captureIndex -lt 0 -or $sidecar.capture.frame -ne $observation.frame -or
                $sidecar.capture.sha256 -cne $shot.sha256 -or $sidecar.screen -cne 'world') {
            throw "Sidecar frame/hash binding is invalid for $checkpoint"
        }
        if ($checkpoint.EndsWith('-cleanup')) {
            if ($plates.Count -or $renderPlates.Count -or $rainbows.Count) {
                throw 'Cleanup capture retained client highlight state'
            }
            continue
        }
        if (!$serverJobs.Count -or $observation.frame -lt 0 -or $observationMillis -le 0) { throw 'Capture lacks frame-bound server jobs or observation time' }
        $outputs = @(Get-ResourceFixtureOutputs $case)
        if (!(Test-ResourceFixtureSequence -Expected $outputs -Actual @($serverJobs.resource))) {
            throw "Resource output identities are invalid for $checkpoint"
        }
        $receipt = Get-ResourceFixtureReceipt @($Evidence.receipts) $case $checkpoint
        if (!$receipt -or !(Test-ResourceFixtureJobs -Expected @($receipt.jobs) -Actual $serverJobs)) {
            throw "Observation does not agree with its authoritative receipt for $checkpoint"
        }
        if ($checkpoint.EndsWith('-chunk-reloaded') -and !$Connected) {
            Assert-ResourceFixtureServerUnloaded @($Evidence.integratedServerEvidence.receipts)
        }
        [object[]]$active = @($(if ($checkpoint.EndsWith('-winner-promoted')) { $outputs[-1] }
            elseif ($checkpoint.EndsWith('-held') -or $checkpoint.EndsWith('-resource-reloaded') -or
                    $checkpoint.EndsWith('-chunk-reloaded') -or
                    $checkpoint.EndsWith('-rejoined')) { $outputs }))
        [object[]]$expectedPlateOutputs = @($active | Sort-Object)
        [object[]]$plateOutputs = @($plates | ForEach-Object { $_.outputId } | Sort-Object)
        if (!(Test-ResourceFixtureSequence -Expected $expectedPlateOutputs -Actual $plateOutputs) -or
                $plates.Count -ne $active.Count -or
                $renderPlates.Count -ne $(if($active.Count){1}else{0}) -or
                ($active.Count -and $renderPlates[0].outputId -notin $active)) {
            throw "Logical or rendered plate identities/cardinality are invalid for $checkpoint"
        }
        if ($checkpoint.EndsWith('-provider-removed')) {
            if (@($receipt.providers).Count -or $plates.Count -or $renderPlates.Count) {
                throw "Removed provider retained a plate for $checkpoint"
            }
            continue
        }
        $providers = @($observation.serverJobs.provider | Select-Object -Unique)
        $removedAndCancelled = $production -and $case -ceq $cases[-1] -and $checkpoint.EndsWith('-cancelled')
        if ($providers.Count -ne 1 -or
                ($removedAndCancelled -and @($receipt.providers).Count -ne 0) -or
                (!$removedAndCancelled -and (@($receipt.providers).Count -ne 1 -or
                    $providers[0].Replace(' ','') -cne ([string]$receipt.providers[0]).Replace(' ','')))) {
            throw "Provider identity is invalid for $checkpoint"
        }
        $provider = $providers[0].Replace(' ','')
        foreach ($plate in @($observation.plates)) {
            $position = $plate.positions[0]
            if ("$($position.x),$($position.y),$($position.z)" -cne $provider) { throw "Logical plate provider is invalid for $checkpoint" }
        }
        foreach ($plate in @($observation.renderPlates)) {
            if ("$($plate.position.x),$($plate.position.y),$($plate.position.z)" -cne $provider) { throw "Rendered plate provider is invalid for $checkpoint" }
        }
        $settled = $checkpoint.EndsWith('-recovery-pair') -or $checkpoint.EndsWith('-completed') -or
            $checkpoint.EndsWith('-cancelled')
        $cancelled = $checkpoint.EndsWith('-cancelled')
        $winner = $checkpoint.EndsWith('-winner-promoted')
        foreach ($job in @($observation.serverJobs)) {
            $jobWinner = $winner -and $job.resource -ceq $outputs[-1]
            if ($settled -and ($job.busy -or $job.heldAmount -ne 0 -or $job.cancelled -ne $cancelled)) {
                throw "Settled job state is invalid for $checkpoint"
            }
            if (!$settled -and ($job.dispatchCount -ne 1 -or
                    ($winner -and !$jobWinner -and ($job.heldAmount -ne 0 -or $job.busy)) -or
                    ((!$winner -or $jobWinner) -and (!$job.delayed -or $job.heldAmount -le 0 -or !$job.busy)))) {
                throw "Held/winner authoritative job state is invalid for $checkpoint"
            }
        }
        $rainbowOutputs = @($observation.rainbows | ForEach-Object { $_.outputId })
        $initialHeld = $checkpoint.EndsWith('-held') -and !$checkpoint.EndsWith('-cancel-held')
        $immediateWinner = !$Connected -and $checkpoint.EndsWith('-winner-promoted')
        if ($initialHeld) {
            if (!(Test-ResourceFixtureSequence -Expected @($outputs[0]) -Actual $rainbowOutputs) -or
                    $observation.rainbows[0].expiresAtMillis -le $observationMillis) {
                throw "Initial located rainbow identity or lifetime is invalid for $checkpoint"
            }
            $locatedExpiry[$case] = [long]$observation.rainbows[0].expiresAtMillis
        } elseif ($immediateWinner) {
            if (!(Test-ResourceFixtureSequence -Expected @($outputs[0]) -Actual $rainbowOutputs) -or
                    $observation.rainbows[0].expiresAtMillis -ne $locatedExpiry[$case] -or
                    $observation.rainbows[0].expiresAtMillis -le $observationMillis) {
                throw "Winner promotion lost or changed the located rainbow for $checkpoint"
            }
        } elseif ($checkpoint.EndsWith('-chunk-reloaded') -or $checkpoint.EndsWith('-resource-reloaded')) {
            if ($rainbowOutputs.Count -and
                    (!(Test-ResourceFixtureSequence -Expected @($outputs[0]) -Actual $rainbowOutputs) -or
                    $observation.rainbows[0].expiresAtMillis -ne $locatedExpiry[$case] -or
                    $observation.rainbows[0].expiresAtMillis -le $observationMillis)) {
                throw "Chunk reload changed the located rainbow for $checkpoint"
            }
        } elseif ($removedAndCancelled) {
            if ($rainbowOutputs.Count) { throw "Removed provider restored a rainbow for $checkpoint" }
        } elseif (!$Connected) {
            if ($rainbowOutputs.Count) {
                if (!(Test-ResourceFixtureSequence -Expected @($outputs[0]) -Actual $rainbowOutputs) -or
                        $observation.rainbows[0].expiresAtMillis -ne $locatedExpiry[$case] -or
                        $observation.rainbows[0].expiresAtMillis -le $observationMillis) {
                    throw "Retained integrated rainbow identity or lifetime is invalid for $checkpoint"
                }
            } elseif ($observationMillis -lt $locatedExpiry[$case]) {
                throw "Integrated rainbow disappeared before its recorded expiry for $checkpoint"
            }
        } elseif ($rainbowOutputs.Count) { throw "Unexpected rainbow state for $checkpoint" }
    }
    return $true
}

function Assert-ResourceFixtureServerTiming([object[]]$Receipts) {
    foreach ($receipt in $Receipts) {
        if ($receipt.action -ceq 'CREATE' -and (@($receipt.jobs | Where-Object { !$_.delayed }).Count -or
                $receipt.pollCount -lt 1 -or $receipt.serverTick -lt $receipt.acceptedTick)) {
            throw 'CREATE acknowledgement lacks authoritative delayed timing evidence'
        }
    }
    return $true
}
