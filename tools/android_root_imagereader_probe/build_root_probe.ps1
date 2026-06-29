param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string]$OutDir = (Join-Path $ProjectRoot 'outputs\android_probe\root_ir_probe'),
    [switch]$SkipNative
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

$javac = Join-Path $javaHome 'bin\javac.exe'
$jar = Join-Path $javaHome 'bin\jar.exe'
$d8 = Join-Path $buildTools 'd8.bat'
$cmake = Join-Path $androidHome 'cmake\4.1.2\bin\cmake.exe'
$ninja = Join-Path $androidHome 'cmake\4.1.2\bin\ninja.exe'
$ndkRoot = Join-Path $androidHome 'ndk\26.2.11394342'
$ndkToolchain = Join-Path $ndkRoot 'build\cmake\android.toolchain.cmake'

foreach ($path in @($javac, $jar, $d8, $androidJar, $cmake, $ninja, $ndkToolchain)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing required build path: $path"
    }
}

$buildDir = Join-Path $PSScriptRoot 'build'
$classesDir = Join-Path $buildDir 'classes'
$classesJar = Join-Path $buildDir 'classes.jar'
$dexDir = Join-Path $buildDir 'dex'
$nativeBuildDir = Join-Path $buildDir 'native-arm64-v8a'
$outJar = Join-Path $OutDir 'maanikke-root-ir-probe.jar'
$outBridge = Join-Path $OutDir 'libmaanikke_maacore_bridge.so'

Remove-Item -LiteralPath $buildDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classesDir, $dexDir, $OutDir | Out-Null

$javaFiles = @(Get-ChildItem -Path (Join-Path $PSScriptRoot 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
Invoke-Native $javac (@('-encoding', 'UTF-8', '-source', '8', '-target', '8', '-bootclasspath', $androidJar, '-d', $classesDir) + $javaFiles)

Push-Location $classesDir
try {
    Invoke-Native $jar @('cf', $classesJar, '.')
} finally {
    Pop-Location
}

Invoke-Native $d8 @('--min-api', '26', '--output', $dexDir, $classesJar)

Push-Location $dexDir
try {
    Invoke-Native $jar @('cf', $outJar, 'classes.dex')
} finally {
    Pop-Location
}

if ($SkipNative) {
    if (-not (Test-Path -LiteralPath $outBridge)) {
        throw "SkipNative requested but MaaCore bridge is missing: $outBridge"
    }
} else {
    Invoke-Native $cmake @(
        '-S', (Join-Path $PSScriptRoot 'native'),
        '-B', $nativeBuildDir,
        '-G', 'Ninja',
        "-DCMAKE_MAKE_PROGRAM=$ninja",
        "-DCMAKE_TOOLCHAIN_FILE=$ndkToolchain",
        '-DANDROID_ABI=arm64-v8a',
        '-DANDROID_PLATFORM=android-26',
        '-DCMAKE_BUILD_TYPE=RelWithDebInfo'
    )
    Invoke-Native $cmake @('--build', $nativeBuildDir, '--target', 'maanikke_maacore_bridge')
    Copy-Item -LiteralPath (Join-Path $nativeBuildDir 'libmaanikke_maacore_bridge.so') -Destination $outBridge -Force
}

Write-Host "DEX jar: $outJar"
Write-Host "MaaCore bridge: $outBridge$(if ($SkipNative) { ' (reused)' } else { '' })"
