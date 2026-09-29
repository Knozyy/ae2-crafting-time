$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'resource-fixture-contract.ps1')

function New-Evidence([bool]$connected, [bool]$expiredLater = $false, [bool]$production = $false) {
    $scenario = 'delayed-resource-icons'; $target = '1.20.1-fabric'
    $epoch = '11111111111111111111111111111111'; $fixture = '22222222222222222222222222222222'
    $cases = Get-ResourceFixtureContractCases $scenario $target
    $names = Get-ResourceFixtureContractCaptures $cases $connected $production
    $screenshots = @($names | ForEach-Object { [pscustomobject]@{name=$_;sha256=('a' * 64)} })
    $sidecars = @($names | ForEach-Object {
        $late = $_ -like '*-completed.png' -or $_ -like '*-cancel-held.png' -or $_ -like '*-cancelled.png'
        [pscustomobject]@{screen='world';capture=[pscustomobject]@{frame=10;sha256=('a' * 64);
            capturedAt=$(if($expiredLater-and$late){'1970-01-01T00:00:03Z'}else{'1970-01-01T00:00:01Z'})}}
    })
    $receipts = @()
    $observations = @()
    foreach ($case in $cases) {
        $outputs = @(Get-ResourceFixtureOutputs $case)
        function New-Jobs([string]$state) {
            @($outputs | ForEach-Object -Begin {$index=0} -Process {
                $winner = $state -eq 'winner' -and $index -eq $outputs.Count - 1
                $released = $state -eq 'completed' -or ($state -eq 'winner' -and !$winner)
                $cancelled = $state -eq 'cancelled'
                [pscustomobject]@{resource=$_;provider='1,2,3';rawAmount=100;heldAmount=$(if($released-or$cancelled){0}else{100});
                    releasedAmount=$(if($released){100}else{0});cancelled=$cancelled;busy=$(!$released-and!$cancelled);
                    delayed=$(!$released-and!$cancelled);dispatchCount=1;keyFingerprint=('b'*64);keyEncoding='YQ==';cpu="cpu-$index"}
                $index++
            })
        }
        $held=New-Jobs 'held';$winner=New-Jobs 'winner';$completed=New-Jobs 'completed';$cancelHeld=New-Jobs 'held';$cancelled=New-Jobs 'cancelled'
        $receipts += [pscustomobject]@{case=$case;action='CREATE';acceptedTick=10;serverTick=12;pollCount=3;providers=@('1,2,3');jobs=$held}
        if ($production -and $case -eq 'WATER') {
            $receipts += [pscustomobject]@{case=$case;action='UNLOAD_RELOAD';unloadedObserved=$true;providers=@('1,2,3');jobs=$held}
        }
        if($connected){$receipts += [pscustomobject]@{case=$case;action='RECONNECT';providers=@('1,2,3');jobs=$held}}
        if($outputs.Count -eq 2){$receipts += [pscustomobject]@{case=$case;action='RELEASE';providers=@('1,2,3');jobs=$winner}}
        $receipts += [pscustomobject]@{case=$case;action='RELEASE';providers=@('1,2,3');jobs=$completed}
        $receipts += [pscustomobject]@{case=$case;action='CREATE';acceptedTick=20;serverTick=22;pollCount=3;providers=@('1,2,3');jobs=$cancelHeld}
        if ($production -and $case.EndsWith('OVERLAP')) {
            $receipts += [pscustomobject]@{case=$case;action='REMOVE_PROVIDER';providers=@();jobs=$cancelHeld}
        }
        $receipts += [pscustomobject]@{case=$case;action='CANCEL';providers=@($(if($production -and $case.EndsWith('OVERLAP')){@()}else{@('1,2,3')}));jobs=$cancelled}
        foreach($name in @($names|Where-Object{$_ -like ($case.ToLowerInvariant().Replace('_','-')+'-*')})){
            $checkpoint=[IO.Path]::GetFileNameWithoutExtension($name);if($checkpoint.EndsWith('-cleanup')){continue}
            $jobs=if($checkpoint.EndsWith('-winner-promoted')){$winner}elseif($checkpoint.EndsWith('-recovery-pair')-or$checkpoint.EndsWith('-completed')){$completed}elseif($checkpoint.EndsWith('-cancelled')){$cancelled}elseif($checkpoint.EndsWith('-cancel-held')){$cancelHeld}else{$held}
            [object[]]$active=@($(if($checkpoint.EndsWith('-winner-promoted')){$outputs[-1]}elseif($checkpoint.EndsWith('-held')-or$checkpoint.EndsWith('-rejoined')-or$checkpoint.EndsWith('-resource-reloaded')-or$checkpoint.EndsWith('-chunk-reloaded')){$outputs}))
            $observations += [pscustomobject]@{case=$case;checkpoint=$name;frame=10;observedAtMillis=$(if($expiredLater-and
                    ($checkpoint.EndsWith('-completed')-or$checkpoint.EndsWith('-cancel-held')-or$checkpoint.EndsWith('-cancelled'))){3000}else{1000});serverJobs=$jobs;
                plates=@($active|ForEach-Object{[pscustomobject]@{outputId=$_;positions=@([pscustomobject]@{x=1;y=2;z=3})}});
                renderPlates=@($(if($active.Count){[pscustomobject]@{outputId=$active[0];position=[pscustomobject]@{x=1;y=2;z=3}}}));
                rainbows=@($(if(($checkpoint.EndsWith('-held')-and!$checkpoint.EndsWith('-cancel-held'))-or$checkpoint.EndsWith('-resource-reloaded')-or$checkpoint.EndsWith('-chunk-reloaded')-or
                        (!$connected-and!($production-and$case.EndsWith('OVERLAP')-and$checkpoint.EndsWith('-cancelled'))-and
                        (!$expiredLater-or!($checkpoint.EndsWith('-completed')-or$checkpoint.EndsWith('-cancel-held')-or$checkpoint.EndsWith('-cancelled'))))){
                    [pscustomobject]@{outputId=$outputs[0];expiresAtMillis=2000}}))}
        }
    }
    $observations += [pscustomobject]@{case=$cases[-1];checkpoint=$names[-1];frame=10;observedAtMillis=3000;serverJobs=@();plates=@();renderPlates=@();rainbows=@()}
    [pscustomobject]@{connected=$connected;checks=$(if($production){[pscustomobject]@{'typed-keys'=$true}}else{[pscustomobject]@{'fixture-only'=$true}});serverState=[pscustomobject]@{epoch=$epoch;fixture=$fixture}
        screenshots=$screenshots;sidecars=$sidecars;clientObservations=@($observations);receipts=$receipts
        integratedServerEvidence=[pscustomobject]@{receipts=$(if($production){
            @($receipts | ConvertTo-Json -Depth 20 | ConvertFrom-Json)
        }else{@()})}
        screenshotManifestDigest=(Get-ResourceFixtureManifestDigest $screenshots)
        clientEvidence=[pscustomobject]@{digest=(Get-ResourceFixtureManifestDigest $screenshots)}
        epoch=$epoch;fixture=$fixture}
}

function Assert-Rejected([scriptblock]$Action, [string]$Name) {
    try { & $Action; throw "Validator accepted invalid fixture: $Name" }
    catch { if ($_.Exception.Message -like 'Validator accepted*') { throw } }
}

foreach ($scenario in @('delayed-resource-icons','appmek-resource-icons')) {
    foreach ($connectedMode in @($false,$true)) {
        foreach ($fixtureOnly in @($false,$true)) {
            $names = @('server-identity','real-dispatch','delayed-plates','native-locate','lifecycle',
                'capture-integrity','cleanup',$(if ($fixtureOnly) { 'fixture-only' } else { 'typed-keys' }))
            if ($scenario -eq 'delayed-resource-icons') {
                $names += @('roof','foreign-owner','chat-beam')
                $names += $(if ($connectedMode) { @('reconnect-clears-beam') } else { @('beam-recovery','beam-expiry') })
            }
            $checks = [pscustomobject]@{}
            foreach ($name in $names) { $checks | Add-Member -NotePropertyName $name -NotePropertyValue $true }
            Assert-ResourceFixtureChecks $checks $scenario $connectedMode $fixtureOnly
            $checks.$($names[-1]) = $false
            Assert-Rejected { Assert-ResourceFixtureChecks $checks $scenario $connectedMode $fixtureOnly } 'false fixture check'
            foreach ($invalid in @('true',1,$null)) {
                $checks.$($names[-1]) = $invalid
                Assert-Rejected { Assert-ResourceFixtureChecks $checks $scenario $connectedMode $fixtureOnly } 'non-boolean fixture check'
            }
            $checks.$($names[-1]) = $true
            $checks.PSObject.Properties.Remove($names[-1])
            Assert-Rejected { Assert-ResourceFixtureChecks $checks $scenario $connectedMode $fixtureOnly } 'missing fixture check'
            $checks | Add-Member -NotePropertyName $names[-1] -NotePropertyValue $true
            $checks | Add-Member -NotePropertyName unexpected -NotePropertyValue $true
            Assert-Rejected { Assert-ResourceFixtureChecks $checks $scenario $connectedMode $fixtureOnly } 'unexpected fixture check'
            $checks.PSObject.Properties.Remove('unexpected')
            $checks.PSObject.Properties.Remove($names[-1])
            $checks | Add-Member -NotePropertyName $names[-1].ToUpperInvariant() -NotePropertyValue $true
            Assert-Rejected { Assert-ResourceFixtureChecks $checks $scenario $connectedMode $fixtureOnly } 'case changed fixture check'
        }
    }
}

$connected = New-Evidence $true
Assert-ResourceFixtureContract $connected 'delayed-resource-icons' '1.20.1-fabric' $true $connected.epoch $connected.fixture | Out-Null
$reorderedOverlap = @($connected.clientObservations | Where-Object checkpoint -eq 'fluid-overlap-held.png')[0]
$reorderedOverlap.plates = @($reorderedOverlap.plates | Sort-Object outputId -Descending)
Assert-ResourceFixtureContract $connected 'delayed-resource-icons' '1.20.1-fabric' $true $connected.epoch $connected.fixture | Out-Null
Assert-ResourceFixtureServerTiming @($connected.receipts) | Out-Null
Assert-Rejected { Assert-ResourceFixtureContract $connected 'delayed-resource-icons' '1.20.1-fabric' $true ('3' * 32) $connected.fixture } 'stale epoch'
$connected.screenshotManifestDigest = '0' * 64
Assert-Rejected { Assert-ResourceFixtureContract $connected 'delayed-resource-icons' '1.20.1-fabric' $true $connected.epoch $connected.fixture } 'screenshot digest'
$connected = New-Evidence $true
$connected.clientEvidence.digest = '0' * 64
Assert-Rejected { Assert-ResourceFixtureContract $connected 'delayed-resource-icons' '1.20.1-fabric' $true $connected.epoch $connected.fixture } 'client acknowledgement digest'
$connected = New-Evidence $true
$connected.clientObservations[0].plates = @()
Assert-Rejected { Assert-ResourceFixtureContract $connected 'delayed-resource-icons' '1.20.1-fabric' $true $connected.epoch $connected.fixture } 'held plate convergence'
$connected = New-Evidence $true
(@($connected.clientObservations | Where-Object checkpoint -like '*-rejoined.png')[0]).plates = @()
Assert-Rejected { Assert-ResourceFixtureContract $connected 'delayed-resource-icons' '1.20.1-fabric' $true $connected.epoch $connected.fixture } 'empty rejoined plates'
$connected = New-Evidence $true
$connected.clientObservations[0].serverJobs[0].resource = 'minecraft:dirt'
Assert-Rejected { Assert-ResourceFixtureContract $connected 'delayed-resource-icons' '1.20.1-fabric' $true $connected.epoch $connected.fixture } 'output identity corruption'
$connected = New-Evidence $true
$connected.clientObservations[0].serverJobs[0].provider = '9,9,9'
Assert-Rejected { Assert-ResourceFixtureContract $connected 'delayed-resource-icons' '1.20.1-fabric' $true $connected.epoch $connected.fixture } 'provider identity corruption'
$connected = New-Evidence $true
$connected.clientObservations[0].serverJobs[0].busy = $false
Assert-Rejected { Assert-ResourceFixtureContract $connected 'delayed-resource-icons' '1.20.1-fabric' $true $connected.epoch $connected.fixture } 'held job state corruption'
$connected = New-Evidence $true
$connected.receipts[0].jobs = @(($connected.receipts[0].jobs | ConvertTo-Json -Depth 8) | ConvertFrom-Json)
$connected.receipts[0].jobs[0].heldAmount = 99
Assert-Rejected { Assert-ResourceFixtureContract $connected 'delayed-resource-icons' '1.20.1-fabric' $true $connected.epoch $connected.fixture } 'observation receipt disagreement'
$connected = New-Evidence $true
$connected.receipts[0].jobs = @(($connected.receipts[0].jobs | ConvertTo-Json -Depth 8) | ConvertFrom-Json)
$connected.receipts[0].jobs[0].heldAmount = 100.0
Assert-ResourceFixtureContract $connected 'delayed-resource-icons' '1.20.1-fabric' $true $connected.epoch $connected.fixture | Out-Null
$connected = New-Evidence $true
$connected.sidecars[0].capture.frame = 11
Assert-Rejected { Assert-ResourceFixtureContract $connected 'delayed-resource-icons' '1.20.1-fabric' $true $connected.epoch $connected.fixture } 'sidecar frame corruption'
$connected = New-Evidence $true
$connected.receipts[0].jobs[0].delayed = $false
Assert-Rejected { Assert-ResourceFixtureServerTiming @($connected.receipts) } 'fresh delayed observation'
$integrated = New-Evidence $false
$winner = @($integrated.clientObservations | Where-Object checkpoint -like '*overlap-winner-promoted.png')[0]
Assert-ResourceFixtureContract $integrated 'delayed-resource-icons' '1.20.1-fabric' $false $integrated.epoch $integrated.fixture | Out-Null
$expiredIntegrated = New-Evidence $false $true
Assert-ResourceFixtureContract $expiredIntegrated 'delayed-resource-icons' '1.20.1-fabric' $false $expiredIntegrated.epoch $expiredIntegrated.fixture | Out-Null
$lateSidecarIntegrated = New-Evidence $false
$lateSidecarIntegrated.sidecars | ForEach-Object { $_.capture.capturedAt = '1970-01-01T00:00:03Z' }
Assert-ResourceFixtureContract $lateSidecarIntegrated 'delayed-resource-icons' '1.20.1-fabric' $false $lateSidecarIntegrated.epoch $lateSidecarIntegrated.fixture | Out-Null
$winner.plates = @()
Assert-Rejected { Assert-ResourceFixtureContract $integrated 'delayed-resource-icons' '1.20.1-fabric' $false $integrated.epoch $integrated.fixture } 'empty winner plates'
$integrated = New-Evidence $false
$winner = @($integrated.clientObservations | Where-Object checkpoint -like '*overlap-winner-promoted.png')[0]
$winner.rainbows = @()
Assert-Rejected { Assert-ResourceFixtureContract $integrated 'delayed-resource-icons' '1.20.1-fabric' $false $integrated.epoch $integrated.fixture } 'integrated rainbow lifetime'
$integrated = New-Evidence $false
$integrated.clientObservations[0].rainbows[0].outputId = 'minecraft:dirt'
Assert-Rejected { Assert-ResourceFixtureContract $integrated 'delayed-resource-icons' '1.20.1-fabric' $false $integrated.epoch $integrated.fixture } 'wrong located rainbow identity'
Write-Host 'Resource fixture executable evidence contract tests passed'

$productionFixture = [pscustomobject]@{ target='1.20.1-fabric'; scenario='delayed-resource-icons';
    screenshots=@([pscustomobject]@{name='water-held.png';sha256=('a'*64)});
    clientObservations=@([pscustomobject]@{checkpoint='water-held.png'}) }
$productionIcon = [pscustomobject]@{ schema=1; semanticResult='PASS'; visualAcceptance='REVIEW_REQUIRED';
    headSha=('1'*40); graph=('2'*64); target=$productionFixture.target; scenario=$productionFixture.scenario;
    screenshots=@([pscustomobject]@{name='water-held.png';sha256=('a'*64)});
    observations=@([pscustomobject]@{checkpoint='water-held.png';
        serverJobs=@([pscustomobject]@{resource='minecraft:water';keyFingerprint=('b'*64)});
        plates=@([pscustomobject]@{outputId='minecraft:water';keyFingerprint=('b'*64);keyType='ae2:fluid'});
        renderPlates=@([pscustomobject]@{outputId='minecraft:water';keyFingerprint=('b'*64);keyType='ae2:fluid'})}) }
$productionFixture.clientObservations = @($productionIcon.observations | ConvertTo-Json -Depth 20 | ConvertFrom-Json)
Assert-ResourceIconEvidence $productionIcon $productionFixture ('1'*40) ('2'*64)
$productionFixture.scenario = 'appmek-resource-icons'
$productionIcon.scenario = $productionFixture.scenario
Assert-ResourceIconEvidence $productionIcon $productionFixture ('1'*40) ('2'*64)
$productionFixture.scenario = 'delayed-resource-icons'
$productionIcon.scenario = $productionFixture.scenario
$productionIcon.observations[0].renderPlates[0].keyFingerprint = 'c'*64
Assert-Rejected { Assert-ResourceIconEvidence $productionIcon $productionFixture ('1'*40) ('2'*64) } 'selected typed key mismatch'
$productionIcon.observations[0].renderPlates[0].keyFingerprint = 'b'*64
$productionIcon.headSha = '3'*40
Assert-Rejected { Assert-ResourceIconEvidence $productionIcon $productionFixture ('1'*40) ('2'*64) } 'tested head mismatch'
$productionIcon.headSha = '1'*40
$productionIcon.observations[0].plates = @()
Assert-Rejected { Assert-ResourceIconEvidence $productionIcon $productionFixture ('1'*40) ('2'*64) } 'empty independent production observation'
foreach ($connectedMode in @($false,$true)) {
    $production = New-Evidence $connectedMode $false $true
    Assert-ResourceFixtureContract $production 'delayed-resource-icons' '1.20.1-fabric' $connectedMode $production.epoch $production.fixture | Out-Null
    $serverReceipts = if($connectedMode){$production.receipts}else{$production.integratedServerEvidence.receipts}
    Assert-ResourceFixtureServerUnloaded @($serverReceipts)
    ($serverReceipts | Where-Object action -eq 'UNLOAD_RELOAD').unloadedObserved = $false
    Assert-Rejected { Assert-ResourceFixtureServerUnloaded @($serverReceipts) } 'missing native unload receipt'
    if(!$connectedMode){
        Assert-Rejected { Assert-ResourceFixtureContract $production 'delayed-resource-icons' '1.20.1-fabric' $false $production.epoch $production.fixture } 'missing integrated server unload receipt'
    }
}
