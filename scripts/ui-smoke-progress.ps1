function Get-UiSmokeProgressDecision {
    param(
        [Parameter(Mandatory)][DateTime]$Now,
        [Parameter(Mandatory)][DateTime]$CallbackAt,
        [Parameter(Mandatory)][DateTime]$CheckpointAt,
        [Parameter(Mandatory)][int]$CallbackTimeoutSeconds,
        [Parameter(Mandatory)][int]$CheckpointTimeoutSeconds,
        [int]$ProcessId = 0,
        [int]$ProgressProcessId = 0,
        [long]$CallbackSequence = -1,
        [DateTime]$StartedAt = [DateTime]::MinValue,
        [int]$StartupTimeoutSeconds = 300,
        [switch]$ActiveScenarioObserved,
        [switch]$Interactive,
        [string]$Checkpoint = ''
    )
    $plannedFabricRejoin = $Checkpoint -match 'cpu-list=REJOIN_REQUEST(?:\s|$)' -and
        $Checkpoint -match 'screen=net\.minecraft\.class_419$'
    if ($plannedFabricRejoin -and
            ($Now.ToUniversalTime() - $CheckpointAt.ToUniversalTime()).TotalSeconds -le 20) {
        return $null
    }
    if ($ProcessId -gt 0 -and $ProgressProcessId -eq $ProcessId -and !$plannedFabricRejoin -and
            $Checkpoint -match 'screen=(?:net\.minecraft\.client\.gui\.screens\.DisconnectedScreen|net\.minecraft\.class_419)$') {
        return 'terminal-disconnect'
    }
    if ($ProcessId -gt 0 -and $ProgressProcessId -eq $ProcessId -and $plannedFabricRejoin) {
        return 'terminal-disconnect'
    }
    if ($ProcessId -gt 0 -and $ProgressProcessId -ne $ProcessId) {
        if ($StartedAt -eq [DateTime]::MinValue -or
                ($Now.ToUniversalTime() - $StartedAt.ToUniversalTime()).TotalSeconds -le $StartupTimeoutSeconds) {
            return $null
        }
        return 'no-callback'
    }
    if ($ProcessId -gt 0 -and $ProgressProcessId -eq $ProcessId -and $CallbackSequence -le 0) {
        if ($StartupTimeoutSeconds -gt 0 -and
                ($Now.ToUniversalTime() - $CallbackAt.ToUniversalTime()).TotalSeconds -gt $StartupTimeoutSeconds) {
            return 'no-callback'
        }
        return $null
    }
    $activeScenario = $ProcessId -le 0 -or $ActiveScenarioObserved -or ($Checkpoint -match '(^|\s)state=WORLD_READY(\s|$)' -and
        $Checkpoint -match '(^|\s)phase=(?!PREPARE(?:\s|$))[A-Z_]+(\s|$)')
    if (!$activeScenario) {
        if ($StartupTimeoutSeconds -gt 0 -and $StartedAt -ne [DateTime]::MinValue -and
                ($Now.ToUniversalTime() - $StartedAt.ToUniversalTime()).TotalSeconds -gt $StartupTimeoutSeconds) {
            return 'startup-timeout'
        }
        return $null
    }
    if ($CallbackTimeoutSeconds -gt 0 -and ($Now.ToUniversalTime() - $CallbackAt.ToUniversalTime()).TotalSeconds -gt $CallbackTimeoutSeconds) {
        return 'no-callback'
    }
    if ($CheckpointTimeoutSeconds -gt 0 -and !($Interactive -and $Checkpoint -match '^state=QUIT_REQUESTED(?:\s|$)') -and
            ($Now.ToUniversalTime() - $CheckpointAt.ToUniversalTime()).TotalSeconds -gt $CheckpointTimeoutSeconds) {
        return 'no-checkpoint'
    }
    return $null
}
