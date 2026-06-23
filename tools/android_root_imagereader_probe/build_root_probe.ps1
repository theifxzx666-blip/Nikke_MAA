param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string]$OutDir = (Join-Path $ProjectRoot 'outputs\android_probe\root_ir_probe')
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

foreach ($path in @($javac, $jar, $d8, $androidJar)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing required build path: $path"
    }
}

$buildDir = Join-Path $PSScriptRoot 'build'
$classesDir = Join-Path $buildDir 'classes'
$classesJar = Join-Path $buildDir 'classes.jar'
$dexDir = Join-Path $buildDir 'dex'
$outJar = Join-Path $OutDir 'maanikke-root-ir-probe.jar'

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

Write-Host "DEX jar: $outJar"
