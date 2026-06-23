param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string]$OutDir = (Join-Path $ProjectRoot 'outputs\android_probe\apk')
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [Parameter(Mandatory = $true)]
        [string[]]$ArgumentList
    )

    & $FilePath @ArgumentList
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($ArgumentList -join ' ')"
    }
}

$androidHome = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$buildTools = Join-Path $androidHome 'build-tools\36.0.0'
$androidJar = Join-Path $androidHome 'platforms\android-36\android.jar'
$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot' }

$aapt2 = Join-Path $buildTools 'aapt2.exe'
$d8 = Join-Path $buildTools 'd8.bat'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$apksigner = Join-Path $buildTools 'apksigner.bat'
$javac = Join-Path $javaHome 'bin\javac.exe'
$jar = Join-Path $javaHome 'bin\jar.exe'
$keytool = Join-Path $javaHome 'bin\keytool.exe'

foreach ($path in @($aapt2, $d8, $zipalign, $apksigner, $javac, $jar, $keytool, $androidJar)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing required build path: $path"
    }
}

$srcRoot = $PSScriptRoot
$buildDir = Join-Path $srcRoot 'build'
$classesDir = Join-Path $buildDir 'classes'
$dexDir = Join-Path $buildDir 'dex'
$compiledResDir = Join-Path $buildDir 'compiled_res'
$unsignedApk = Join-Path $buildDir 'probe-unsigned.apk'
$alignedApk = Join-Path $buildDir 'probe-aligned.apk'
$signedApk = Join-Path $OutDir 'MaaNikkeVirtualDisplayProbe.apk'
$keystore = Join-Path $OutDir 'maanikke-probe-debug.jks'

Remove-Item -LiteralPath $buildDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classesDir, $dexDir, $compiledResDir, $OutDir | Out-Null

Invoke-Native $aapt2 @('compile', '--dir', (Join-Path $srcRoot 'res'), '-o', (Join-Path $buildDir 'res.zip'))

Invoke-Native $aapt2 @(
    'link',
    '-o', $unsignedApk,
    '-I', $androidJar,
    '--manifest', (Join-Path $srcRoot 'AndroidManifest.xml'),
    '-R', (Join-Path $buildDir 'res.zip'),
    '--java', (Join-Path $buildDir 'generated'),
    '--auto-add-overlay',
    '--min-sdk-version', '23',
    '--target-sdk-version', '36'
)

$javaFiles = @(Get-ChildItem -Path (Join-Path $srcRoot 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$generatedFiles = @(Get-ChildItem -Path (Join-Path $buildDir 'generated') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
Invoke-Native $javac (@('-encoding', 'UTF-8', '-source', '8', '-target', '8', '-bootclasspath', $androidJar, '-d', $classesDir) + $javaFiles + $generatedFiles)

$classesJar = Join-Path $buildDir 'classes.jar'
Push-Location $classesDir
try {
    Invoke-Native $jar @('cf', $classesJar, '.')
} finally {
    Pop-Location
}

Invoke-Native $d8 @('--min-api', '23', '--output', $dexDir, $classesJar)

Push-Location $dexDir
try {
    Invoke-Native $jar @('uf', $unsignedApk, 'classes.dex')
} finally {
    Pop-Location
}

Invoke-Native $zipalign @('-f', '-p', '4', $unsignedApk, $alignedApk)

if (-not (Test-Path -LiteralPath $keystore)) {
    Invoke-Native $keytool @(
        '-genkeypair',
        '-keystore', $keystore,
        '-storepass', 'android',
        '-keypass', 'android',
        '-alias', 'maanikke-probe',
        '-keyalg', 'RSA',
        '-keysize', '2048',
        '-validity', '10000',
        '-dname', 'CN=MaaNikke Probe, OU=Codex, O=Codex, L=Local, ST=Local, C=US'
    )
}

Invoke-Native $apksigner @(
    'sign',
    '--ks', $keystore,
    '--ks-pass', 'pass:android',
    '--key-pass', 'pass:android',
    '--out', $signedApk,
    $alignedApk
)

Invoke-Native $apksigner @('verify', '--verbose', $signedApk)

Write-Host "APK: $signedApk"
