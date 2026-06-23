param(
    [string]$Serial = "2fb37497",
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true

$androidHome = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot' }
$env:ANDROID_HOME = $androidHome
$env:JAVA_HOME = $javaHome
$env:Path = "$androidHome\platform-tools;$androidHome\cmdline-tools\latest\bin;C:\Program Files\Git\cmd;$javaHome\bin;$env:Path"

$outDir = Join-Path $ProjectRoot 'outputs\android_probe\root_ir_probe'
$jarPath = Join-Path $outDir 'maanikke-root-ir-probe.jar'

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'build_root_probe.ps1') -ProjectRoot $ProjectRoot

adb -s $Serial shell am force-stop com.codex.maanikke.probe | Out-Host
adb -s $Serial shell su -c 'am force-stop com.tencent.nikke' | Out-Host
adb -s $Serial push $jarPath /data/local/tmp/maanikke-root-ir-probe.jar | Out-Host

$remoteCommand = "rm -f /data/local/tmp/maanikke_root_ir_probe.log /data/local/tmp/maanikke_root_ir_last.png /data/local/tmp/maanikke_root_ir_before_touch.png /data/local/tmp/maanikke_root_ir_after_touch.png /data/local/tmp/maanikke_root_ir_result.txt; CLASSPATH=/data/local/tmp/maanikke-root-ir-probe.jar app_process /system/bin com.codex.maanikke.rootprobe.RootImageReaderProbe"
adb -s $Serial shell su -c $remoteCommand | Out-Host

adb -s $Serial pull /data/local/tmp/maanikke_root_ir_result.txt (Join-Path $outDir 'maanikke_root_ir_result_latest.txt') | Out-Host
adb -s $Serial pull /data/local/tmp/maanikke_root_ir_probe.log (Join-Path $outDir 'maanikke_root_ir_probe_latest.log') | Out-Host
adb -s $Serial pull /data/local/tmp/maanikke_root_ir_last.png (Join-Path $outDir 'maanikke_root_ir_last_latest.png') | Out-Host
adb -s $Serial pull /data/local/tmp/maanikke_root_ir_before_touch.png (Join-Path $outDir 'maanikke_root_ir_before_touch_latest.png') | Out-Host
adb -s $Serial pull /data/local/tmp/maanikke_root_ir_after_touch.png (Join-Path $outDir 'maanikke_root_ir_after_touch_latest.png') | Out-Host

Get-Content -Path (Join-Path $outDir 'maanikke_root_ir_result_latest.txt')
