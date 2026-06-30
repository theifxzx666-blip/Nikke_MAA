param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string]$OutDir = (Join-Path $ProjectRoot 'outputs\android_probe\apk'),
    [switch]$SkipRootNativeBuild,
    [switch]$SkipPreviewNativeBuild
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
$cmake = Join-Path $androidHome 'cmake\4.1.2\bin\cmake.exe'
$ninja = Join-Path $androidHome 'cmake\4.1.2\bin\ninja.exe'
$ndkRoot = Join-Path $androidHome 'ndk\26.2.11394342'
$ndkToolchain = Join-Path $ndkRoot 'build\cmake\android.toolchain.cmake'

foreach ($path in @($aapt2, $d8, $zipalign, $apksigner, $javac, $jar, $keytool, $androidJar, $cmake, $ninja, $ndkToolchain)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing required build path: $path"
    }
}

$srcRoot = $PSScriptRoot
$buildDir = Join-Path $srcRoot 'build'
$classesDir = Join-Path $buildDir 'classes'
$dexDir = Join-Path $buildDir 'dex'
$assetsDir = Join-Path $buildDir 'assets'
$libDir = Join-Path $buildDir 'lib'
$nativeBuildDir = Join-Path $buildDir 'native-arm64-v8a'
$sourceAssetsDir = Join-Path $srcRoot 'assets'
$unsignedApk = Join-Path $buildDir 'maanikke-debug-unsigned.apk'
$alignedApk = Join-Path $buildDir 'maanikke-debug-aligned.apk'
$signedApk = Join-Path $OutDir 'MaaNikkeAndroidDebug.apk'
$keystore = Join-Path $OutDir 'maanikke-debug.jks'
$rootProbeJar = Join-Path $ProjectRoot 'outputs\android_probe\root_ir_probe\maanikke-root-ir-probe.jar'
$maaCoreBridge = Join-Path $ProjectRoot 'outputs\android_probe\root_ir_probe\libmaanikke_maacore_bridge.so'
$maaCoreSdkBin = Join-Path $ProjectRoot 'outputs\android_probe\maacore_sdk\v5.11.1\extract\bin'
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
$rootBuildArgs = @(
    '-NoLogo',
    '-NoProfile',
    '-ExecutionPolicy',
    'Bypass',
    '-File',
    (Join-Path $ProjectRoot 'tools\android_root_imagereader_probe\build_root_probe.ps1'),
    '-ProjectRoot',
    $ProjectRoot
)
if ($SkipRootNativeBuild) {
    $rootBuildArgs += '-SkipNative'
}
powershell.exe @rootBuildArgs
if ($LASTEXITCODE -ne 0) {
    throw "Root backend build failed with exit code $LASTEXITCODE"
}

if (-not (Test-Path -LiteralPath $rootProbeJar)) {
    throw "Missing root probe jar: $rootProbeJar"
}
if (-not (Test-Path -LiteralPath $maaCoreBridge)) {
    throw "Missing MaaCore bridge: $maaCoreBridge"
}

Remove-Item -LiteralPath $buildDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classesDir, $dexDir, (Join-Path $assetsDir 'assets'), (Join-Path $libDir 'lib\arm64-v8a'), $OutDir | Out-Null
if (Test-Path -LiteralPath $sourceAssetsDir) {
    Copy-Item -Path (Join-Path $sourceAssetsDir '*') -Destination (Join-Path $assetsDir 'assets') -Recurse -Force
}
Copy-Item -LiteralPath $rootProbeJar -Destination (Join-Path $assetsDir 'assets\maanikke-root-ir-probe.jar') -Force
Copy-Item -LiteralPath $maaCoreBridge -Destination (Join-Path $assetsDir 'assets\libmaanikke_maacore_bridge.so') -Force

$maaLibAssetDir = Join-Path $assetsDir 'assets\MaaSync\MaaLib\arm64-v8a'
New-Item -ItemType Directory -Force -Path $maaLibAssetDir | Out-Null
$maaCoreRuntimeLibs = @(
    'libc++_shared.so',
    'libonnxruntime.so',
    'libopencv_world4.so',
    'libfastdeploy_ppocr.so',
    'libMaaUtils.so',
    'libMaaFramework.so',
    'libMaaAndroidNativeControlUnit.so',
    'libMaaCustomControlUnit.so'
)
foreach ($libName in $maaCoreRuntimeLibs) {
    $sourceLib = Join-Path $maaCoreSdkBin $libName
    if (-not (Test-Path -LiteralPath $sourceLib)) {
        throw "Missing MaaCore runtime library: $sourceLib"
    }
    Copy-Item -LiteralPath $sourceLib -Destination (Join-Path $maaLibAssetDir $libName) -Force
}
Copy-Item -LiteralPath $maaCoreBridge -Destination (Join-Path $maaLibAssetDir 'libmaanikke_maacore_bridge.so') -Force

if ($SkipPreviewNativeBuild) {
    $existingPreviewLib = Join-Path $ProjectRoot 'outputs\android_probe\root_ir_probe\libmaanikke_preview_renderer.so'
    if (-not (Test-Path -LiteralPath $existingPreviewLib)) {
        throw "SkipPreviewNativeBuild requested but preview renderer is missing: $existingPreviewLib"
    }
    Copy-Item -LiteralPath $existingPreviewLib `
        -Destination (Join-Path $libDir 'lib\arm64-v8a\libmaanikke_preview_renderer.so') -Force
    Copy-Item -LiteralPath $existingPreviewLib `
        -Destination (Join-Path $assetsDir 'assets\libmaanikke_preview_renderer.so') -Force
} else {
    Invoke-Native $cmake @(
        '-S', (Join-Path $srcRoot 'native'),
        '-B', $nativeBuildDir,
        '-G', 'Ninja',
        "-DCMAKE_MAKE_PROGRAM=$ninja",
        "-DCMAKE_TOOLCHAIN_FILE=$ndkToolchain",
        '-DANDROID_ABI=arm64-v8a',
        '-DANDROID_PLATFORM=android-23',
        '-DCMAKE_BUILD_TYPE=RelWithDebInfo'
    )
    Invoke-Native $cmake @('--build', $nativeBuildDir, '--target', 'maanikke_preview_renderer')
    Copy-Item -LiteralPath (Join-Path $nativeBuildDir 'libmaanikke_preview_renderer.so') `
        -Destination (Join-Path $libDir 'lib\arm64-v8a\libmaanikke_preview_renderer.so') -Force
    Copy-Item -LiteralPath (Join-Path $nativeBuildDir 'libmaanikke_preview_renderer.so') `
        -Destination (Join-Path $assetsDir 'assets\libmaanikke_preview_renderer.so') -Force
}

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

Push-Location $libDir
try {
    Invoke-Native $jar @('uf', $unsignedApk, 'lib\arm64-v8a\libmaanikke_preview_renderer.so')
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
