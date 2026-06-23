param(
    [string]$Serial = "2fb37497",
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [int]$TimeoutSeconds = 220,
    [string]$ScrcpyPath = "",
    [switch]$ShowVirtualDisplay,
    [Alias('Task')]
    [string[]]$TaskIds = @()
)

$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) {
    throw "adb not found: $adb"
}

if ($ScrcpyPath.Length -eq 0) {
    $scrcpyCandidate = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE 'Downloads') -Recurse -Filter scrcpy.exe -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -like '*scrcpy-win64-v3.3.4*' } |
        Select-Object -First 1
    if ($scrcpyCandidate) {
        $ScrcpyPath = $scrcpyCandidate.FullName
    }
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outDir = Join-Path $ProjectRoot "outputs\android_probe\full_adapter_regression_$stamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$tasks = @()
if ($TaskIds.Count -gt 0) {
    foreach ($item in $TaskIds) {
        foreach ($raw in @($item)) {
        foreach ($part in (($raw -as [string]) -split ',')) {
            $name = $part.Trim()
            if ($name.Length -gt 0) {
                $tasks += $name
            }
        }
        }
    }
} else {
    $tasks = @(
        'start_game',
        'login_rewards',
        'visit_paid_shop',
        'visit_free_shop',
        'visit_outpost_defense',
        'visit_dispatch_board',
        'visit_inquiry_and_gift',
        'visit_gear_up',
        'visit_friend_points',
        'visit_team_recruit',
        'visit_sim_room',
        'visit_arena',
        'visit_interception',
        'visit_climb_tower',
        'visit_daily_rewards',
        'visit_mail',
        'visit_loop_room_and_sync',
        'visit_pass_rewards',
        'visit_team_battle',
        'visit_union_raid',
        'visit_rehabilitation',
        'stop_game'
    )
}

function Invoke-AdbShell {
    param([Parameter(Mandatory = $true)][string]$Command)
    & $adb -s $Serial shell $Command
}

function Read-RemoteText {
    param([Parameter(Mandatory = $true)][string]$RemotePath)
    $lines = Invoke-AdbShell "su -c 'cat $RemotePath 2>/dev/null || true'"
    return ($lines -join "`n")
}

function Pull-IfExists {
    param(
        [Parameter(Mandatory = $true)][string]$RemotePath,
        [Parameter(Mandatory = $true)][string]$LocalPath
    )
    $exists = Invoke-AdbShell "su -c 'if [ -f $RemotePath ]; then echo yes; fi'"
    if (($exists -join "`n").Trim() -eq 'yes') {
        & $adb -s $Serial shell "su -c 'chmod 644 $RemotePath'"
        & $adb -s $Serial pull $RemotePath $LocalPath | Out-Null
    }
}

function Show-VirtualDisplay {
    param(
        [Parameter(Mandatory = $true)][string]$DisplayId,
        [Parameter(Mandatory = $true)]$TaskName
    )
    if (-not $ShowVirtualDisplay -or $ScrcpyPath.Length -eq 0 -or -not (Test-Path -LiteralPath $ScrcpyPath)) {
        return
    }
    $safeTaskName = (($TaskName -join '_') -replace '[^A-Za-z0-9_-]', '_')
    Get-Process | Where-Object {
        $_.ProcessName -like 'scrcpy*' -and $_.MainWindowTitle -like 'MaaNikkeTaskDisplay*'
    } | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Process -FilePath $ScrcpyPath -WorkingDirectory (Split-Path -Parent $ScrcpyPath) -ArgumentList @(
        "-s", $Serial,
        "--window-title", "MaaNikkeTaskDisplay${DisplayId}_${safeTaskName}",
        "--display-id", $DisplayId,
        "--stay-awake",
        "--max-fps", "30",
        "--video-bit-rate", "8M"
    )
}

function Get-ResultValue {
    param(
        [AllowEmptyString()][string]$Text,
        [Parameter(Mandatory = $true)][string]$Key
    )
    if ($Text.Length -eq 0) {
        return ''
    }
    foreach ($line in ($Text -split "`n")) {
        if ($line.StartsWith("$Key=")) {
            return $line.Substring($Key.Length + 1).Trim()
        }
    }
    return ''
}

function Get-AdapterStatus {
    param(
        [AllowEmptyString()][string]$Success,
        [AllowEmptyString()][string]$FinalState
    )
    if ($Success -eq 'true') {
        return 'pass'
    }
    if ($FinalState -eq 'login_required' -or $FinalState -eq 'network_retry_required') {
        return 'needs_user'
    }
    return 'fail'
}

$summary = New-Object System.Collections.Generic.List[object]

foreach ($taskNameRaw in $tasks) {
    $taskName = $taskNameRaw -as [string]
    Write-Host "== $taskName =="
    Invoke-AdbShell "su -c 'killall app_process 2>/dev/null || true; am force-stop com.codex.maanikke.debug; rm -f /data/local/tmp/maanikke_task_result.txt /data/local/tmp/maanikke_task_runner.log /data/local/tmp/maanikke_task_frame.png /data/local/tmp/maanikke_task_before_action.png /data/local/tmp/maanikke_task_after_action.png'" | Out-Null
    Start-Sleep -Seconds 1
    Invoke-AdbShell "am start -a com.codex.maanikke.debug.RUN_TASK --es task_id $taskName -n com.codex.maanikke.debug/.MainActivity" | Out-Host

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $resultText = ''
    $displayShown = $false
    do {
        Start-Sleep -Seconds 5
        $resultText = Read-RemoteText "/data/local/tmp/maanikke_task_result.txt"
        $phase = Get-ResultValue $resultText 'phase'
        $displayId = Get-ResultValue $resultText 'displayId'
        if (-not $displayShown -and $displayId.Length -gt 0 -and $displayId -ne '-1') {
            Show-VirtualDisplay -DisplayId $displayId -TaskName $taskName
            $displayShown = $true
        }
        if ($phase -eq 'finished') {
            break
        }
    } while ((Get-Date) -lt $deadline)

    $taskDir = Join-Path $outDir $taskName
    New-Item -ItemType Directory -Force -Path $taskDir | Out-Null
    $resultPath = Join-Path $taskDir 'result.txt'
    $logPath = Join-Path $taskDir 'runner.log'
    $resultText | Set-Content -Path $resultPath -Encoding UTF8
    Pull-IfExists "/data/local/tmp/maanikke_task_runner.log" $logPath
    Pull-IfExists "/data/local/tmp/maanikke_task_frame.png" (Join-Path $taskDir 'frame.png')
    Pull-IfExists "/data/local/tmp/maanikke_task_before_action.png" (Join-Path $taskDir 'before_action.png')
    Pull-IfExists "/data/local/tmp/maanikke_task_after_action.png" (Join-Path $taskDir 'after_action.png')

    $success = Get-ResultValue $resultText 'actionSuccess'
    $finalState = Get-ResultValue $resultText 'finalState'
    $summary.Add([pscustomobject]@{
        task = $taskName
        phase = Get-ResultValue $resultText 'phase'
        success = $success
        finalState = $finalState
        status = Get-AdapterStatus -Success $success -FinalState $finalState
        displayId = Get-ResultValue $resultText 'displayId'
        frames = Get-ResultValue $resultText 'frames'
        nonBlackFrames = Get-ResultValue $resultText 'nonBlackFrames'
        actionCount = Get-ResultValue $resultText 'actionCount'
        targetPackage = Get-ResultValue $resultText 'targetPackage'
        targetComponent = Get-ResultValue $resultText 'targetComponent'
    })
}

$summaryPath = Join-Path $outDir 'summary.csv'
$summary | Export-Csv -Path $summaryPath -NoTypeInformation -Encoding UTF8
$summary | Format-Table -AutoSize
Write-Host "summary: $summaryPath"
