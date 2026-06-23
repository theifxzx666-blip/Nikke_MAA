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

$env:ANDROID_HOME = $androidHome
$env:JAVA_HOME = $javaHome
$env:Path = "$androidHome\platform-tools;$androidHome\cmdline-tools\latest\bin;$javaHome\bin;$env:Path"

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
$assetsDir = Join-Path $buildDir 'assets'
$sourceAssetsDir = Join-Path $srcRoot 'assets'
$unsignedApk = Join-Path $buildDir 'maanikke-debug-unsigned.apk'
$alignedApk = Join-Path $buildDir 'maanikke-debug-aligned.apk'
$signedApk = Join-Path $OutDir 'MaaNikkeAndroidDebug.apk'
$keystore = Join-Path $OutDir 'maanikke-debug.jks'
$rootProbeJar = Join-Path $ProjectRoot 'outputs\android_probe\root_ir_probe\maanikke-root-ir-probe.jar'
$shizukuDir = Join-Path $srcRoot 'third_party\shizuku'
$shizukuAidlJar = Join-Path $shizukuDir 'aidl\classes.jar'
$shizukuApiJar = Join-Path $shizukuDir 'api\classes.jar'
$shizukuProviderJar = Join-Path $shizukuDir 'provider\classes.jar'
$shizukuSharedJar = Join-Path $shizukuDir 'shared\classes.jar'

foreach ($path in @($shizukuAidlJar, $shizukuApiJar, $shizukuProviderJar, $shizukuSharedJar)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing Shizuku dependency: $path"
    }
}

Remove-Item -LiteralPath $rootProbeJar -Force -ErrorAction SilentlyContinue
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File (Join-Path $ProjectRoot 'tools\android_root_imagereader_probe\build_root_probe.ps1') -ProjectRoot $ProjectRoot
if ($LASTEXITCODE -ne 0) {
    throw "Root backend build failed with exit code $LASTEXITCODE"
}

if (-not (Test-Path -LiteralPath $rootProbeJar)) {
    throw "Missing root probe jar: $rootProbeJar"
}

Remove-Item -LiteralPath $buildDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classesDir, $dexDir, (Join-Path $assetsDir 'assets'), $OutDir | Out-Null
if (Test-Path -LiteralPath $sourceAssetsDir) {
    Copy-Item -Path (Join-Path $sourceAssetsDir '*') -Destination (Join-Path $assetsDir 'assets') -Recurse -Force
}
Copy-Item -LiteralPath $rootProbeJar -Destination (Join-Path $assetsDir 'assets\maanikke-root-ir-probe.jar') -Force

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
    '--target-sdk-version', '28'
)

$javaFiles = @(Get-ChildItem -Path (Join-Path $srcRoot 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$generatedFiles = @(Get-ChildItem -Path (Join-Path $buildDir 'generated') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$shizukuJars = @($shizukuAidlJar, $shizukuApiJar, $shizukuProviderJar, $shizukuSharedJar)
$compileClasspath = $shizukuJars -join ';'
Invoke-Native $javac (@('-encoding', 'UTF-8', '-source', '8', '-target', '8', '-bootclasspath', $androidJar, '-classpath', $compileClasspath, '-d', $classesDir) + $javaFiles + $generatedFiles)

$classesJar = Join-Path $buildDir 'classes.jar'
Push-Location $classesDir
try {
    Invoke-Native $jar @('cf', $classesJar, '.')
} finally {
    Pop-Location
}

Invoke-Native $d8 (@('--min-api', '23', '--output', $dexDir, $classesJar) + $shizukuJars)

Push-Location $dexDir
try {
    Invoke-Native $jar @('uf', $unsignedApk, 'classes.dex')
} finally {
    Pop-Location
}

Push-Location $assetsDir
try {
    $assetEntries = @(Get-ChildItem -LiteralPath (Join-Path $assetsDir 'assets') -Force | ForEach-Object { Join-Path 'assets' $_.Name })
    Invoke-Native $jar (@('uf', $unsignedApk) + $assetEntries)
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
        '-alias', 'maanikke-debug',
        '-keyalg', 'RSA',
        '-keysize', '2048',
        '-validity', '10000',
        '-dname', 'CN=MaaNikke Debug, OU=Codex, O=Codex, L=Local, ST=Local, C=US'
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
