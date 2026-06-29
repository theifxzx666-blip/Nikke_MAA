param(
    [string]$CasesPath,
    [string]$EvidenceRoot,
    [string]$OutputDir,
    [switch]$IncludeDisabled
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ApkRoot = Resolve-Path (Join-Path $ScriptDir "..")
$RepoRoot = Resolve-Path (Join-Path $ApkRoot "..\..")

if ([string]::IsNullOrWhiteSpace($EvidenceRoot)) {
    $EvidenceRoot = Join-Path $ApkRoot "assets\MaaSync\OcrEvidence"
}
if ([string]::IsNullOrWhiteSpace($CasesPath)) {
    $CasesPath = Join-Path $EvidenceRoot "ocr_regression_cases.json"
}
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputDir = Join-Path $RepoRoot "outputs\android_probe\ocr_regression_$stamp"
}

$EvidenceRoot = (Resolve-Path $EvidenceRoot).Path
$CasesPath = (Resolve-Path $CasesPath).Path
$OutputDir = New-Item -ItemType Directory -Force -Path $OutputDir | Select-Object -ExpandProperty FullName
$RoiDir = New-Item -ItemType Directory -Force -Path (Join-Path $OutputDir "roi") | Select-Object -ExpandProperty FullName

function Read-JsonFile {
    param([string]$Path)
    $raw = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    return $raw | ConvertFrom-Json
}

function Test-ImageDimensions {
    param(
        [string]$Path,
        [int]$ExpectedWidth,
        [int]$ExpectedHeight
    )
    $image = $null
    try {
        $image = [System.Drawing.Image]::FromFile($Path)
        return @{
            width = $image.Width
            height = $image.Height
            ok = ($image.Width -eq $ExpectedWidth -and $image.Height -eq $ExpectedHeight)
        }
    } finally {
        if ($null -ne $image) {
            $image.Dispose()
        }
    }
}

function Save-RoiCrop {
    param(
        [string]$ImagePath,
        [int[]]$Roi,
        [string]$OutPath
    )
    $source = $null
    $crop = $null
    $graphics = $null
    try {
        $source = [System.Drawing.Bitmap]::new($ImagePath)
        $rect = [System.Drawing.Rectangle]::new($Roi[0], $Roi[1], $Roi[2], $Roi[3])
        $crop = [System.Drawing.Bitmap]::new($rect.Width, $rect.Height)
        $graphics = [System.Drawing.Graphics]::FromImage($crop)
        $graphics.DrawImage($source, [System.Drawing.Rectangle]::new(0, 0, $rect.Width, $rect.Height), $rect, [System.Drawing.GraphicsUnit]::Pixel)
        $crop.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        if ($null -ne $graphics) {
            $graphics.Dispose()
        }
        if ($null -ne $crop) {
            $crop.Dispose()
        }
        if ($null -ne $source) {
            $source.Dispose()
        }
    }
}

function ConvertTo-IntArray {
    param($Value)
    $items = @()
    foreach ($item in @($Value)) {
        $items += [int]$item
    }
    return [int[]]$items
}

$cases = Read-JsonFile -Path $CasesPath
$expectedResolution = ConvertTo-IntArray $cases.resolution
if ($expectedResolution.Count -ne 2) {
    throw "Cases file must include resolution [width,height]."
}

$mirrorPath = Join-Path $RepoRoot "outputs\android_probe\ocr_regression_cases.json"
$mirrorStatus = "missing"
if (Test-Path -LiteralPath $mirrorPath) {
    try {
        $mirror = Read-JsonFile -Path $mirrorPath
        $caseIds = @($cases.cases | ForEach-Object { $_.id }) -join "|"
        $mirrorIds = @($mirror.cases | ForEach-Object { $_.id }) -join "|"
        $mirrorStatus = if ($caseIds -eq $mirrorIds) { "case_ids_match" } else { "case_ids_differ" }
    } catch {
        $mirrorStatus = "parse_error: $($_.Exception.Message)"
    }
}

$results = New-Object System.Collections.Generic.List[object]
foreach ($case in @($cases.cases)) {
    $enabled = $true
    if ($null -ne $case.enabled) {
        $enabled = [bool]$case.enabled
    }
    if (-not $enabled -and -not $IncludeDisabled) {
        $results.Add([pscustomobject]@{
            id = $case.id
            task = $case.task
            enabled = $enabled
            status = "skipped_disabled"
            image = $case.image
            imageExists = $false
            imageWidth = 0
            imageHeight = 0
            roiInBounds = $false
            roiCrop = ""
            notes = @("disabled case")
        })
        continue
    }

    $notes = New-Object System.Collections.Generic.List[string]
    $imagePath = ""
    if ($case.image -and $case.image -ne "pending") {
        $imagePath = Join-Path $EvidenceRoot $case.image
    }
    $imageExists = ($imagePath.Length -gt 0 -and (Test-Path -LiteralPath $imagePath))
    $width = 0
    $height = 0
    $dimensionsOk = $false
    $roiInBounds = $false
    $cropPath = ""

    if (-not $imageExists) {
        $notes.Add("image missing or pending")
    } else {
        $dim = Test-ImageDimensions -Path $imagePath -ExpectedWidth $expectedResolution[0] -ExpectedHeight $expectedResolution[1]
        $width = [int]$dim.width
        $height = [int]$dim.height
        $dimensionsOk = [bool]$dim.ok
        if (-not $dimensionsOk) {
            $notes.Add("unexpected image size")
        }
    }

    $roi = ConvertTo-IntArray $case.roi
    if ($roi.Count -eq 4) {
        $roiInBounds = $imageExists `
            -and $roi[0] -ge 0 `
            -and $roi[1] -ge 0 `
            -and $roi[2] -gt 0 `
            -and $roi[3] -gt 0 `
            -and ($roi[0] + $roi[2]) -le $width `
            -and ($roi[1] + $roi[3]) -le $height
        if ($roiInBounds) {
            $safeId = ($case.id -replace "[^A-Za-z0-9_.-]", "_")
            $cropPath = Join-Path $RoiDir "$safeId.png"
            Save-RoiCrop -ImagePath $imagePath -Roi $roi -OutPath $cropPath
            $cropPath = Resolve-Path $cropPath | Select-Object -ExpandProperty Path
        } else {
            $notes.Add("roi out of bounds")
        }
    } else {
        $notes.Add("roi must contain 4 integers")
    }

    $status = if ($imageExists -and $dimensionsOk -and $roiInBounds) { "ok" } else { "needs_attention" }
    $results.Add([pscustomobject]@{
        id = $case.id
        task = $case.task
        enabled = $enabled
        status = $status
        image = $case.image
        imageExists = $imageExists
        imageWidth = $width
        imageHeight = $height
        roi = $roi
        roiInBounds = $roiInBounds
        roiCrop = $cropPath
        pipeline = $case.pipeline
        node = $case.node
        expectedText = @($case.expected_text)
        notes = @($notes)
    })
}

$resultItems = @($results.ToArray())
$okCount = @($resultItems | Where-Object { $_.status -eq "ok" }).Count
$skippedDisabledCount = @($resultItems | Where-Object { $_.status -eq "skipped_disabled" }).Count
$needsAttentionCount = @($resultItems | Where-Object { $_.status -eq "needs_attention" }).Count

$summary = [pscustomobject]@{
    generatedAt = (Get-Date).ToString("s")
    casesPath = $CasesPath
    evidenceRoot = $EvidenceRoot
    outputDir = $OutputDir
    expectedResolution = $expectedResolution
    includeDisabled = [bool]$IncludeDisabled
    mirrorCasesPath = $mirrorPath
    mirrorStatus = $mirrorStatus
    total = $resultItems.Count
    ok = $okCount
    skippedDisabled = $skippedDisabledCount
    needsAttention = $needsAttentionCount
    results = $resultItems
}

$reportJson = Join-Path $OutputDir "report.json"
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportJson -Encoding UTF8

$reportMd = Join-Path $OutputDir "report.md"
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# OCR Evidence Regression")
$lines.Add("")
$lines.Add("- Generated: $($summary.generatedAt)")
$lines.Add("- Cases: $CasesPath")
$lines.Add("- Evidence: $EvidenceRoot")
$lines.Add("- Expected resolution: $($expectedResolution[0])x$($expectedResolution[1])")
$lines.Add("- Mirror cases: $mirrorStatus")
$lines.Add("- Summary: ok=$($summary.ok), skippedDisabled=$($summary.skippedDisabled), needsAttention=$($summary.needsAttention), total=$($summary.total)")
$lines.Add("")
$lines.Add("| Case | Task | Status | Image | ROI | Crop | Note |")
$lines.Add("| --- | --- | --- | --- | --- | --- | --- |")
foreach ($result in $results) {
    $roiText = if ($result.roi) { ($result.roi -join ",") } else { "" }
    $cropText = if ($result.roiCrop) { $result.roiCrop } else { "" }
    $noteText = if ($result.notes) { ($result.notes -join "; ") } else { "" }
    $lines.Add("| $($result.id) | $($result.task) | $($result.status) | $($result.image) | $roiText | $cropText | $noteText |")
}
$lines | Set-Content -LiteralPath $reportMd -Encoding UTF8

Write-Host "OCR evidence regression complete."
Write-Host "Output: $OutputDir"
Write-Host "OK: $($summary.ok), skippedDisabled: $($summary.skippedDisabled), needsAttention: $($summary.needsAttention), total: $($summary.total)"
