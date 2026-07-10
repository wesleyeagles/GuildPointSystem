# RF Online force (cast) icons from wiki.rfdatabase.net/game-controls/forces
# Run: powershell -ExecutionPolicy Bypass -File scripts/download-force-images.ps1

$ErrorActionPreference = 'Stop'
$scriptDir = $PSScriptRoot
$dataFile = Join-Path $scriptDir 'force-seed-data.json'
$outDir = Join-Path $scriptDir '..\frontend\public\forces'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (-not (Test-Path $dataFile)) {
    & (Join-Path $scriptDir 'scrape-force-table.ps1')
}

$forces = Get-Content $dataFile -Raw | ConvertFrom-Json
$usedSlugs = @{}

foreach ($force in $forces) {
    $slug = $force.Slug
    if ($usedSlugs.ContainsKey($slug)) {
        $slug = "$slug-$($force.Class.ToLower())"
    }
    $usedSlugs[$slug] = $true

    $ext = [System.IO.Path]::GetExtension([uri]$force.ImageUrl).LocalPath
    if (-not $ext) { $ext = '.png' }
    $localFile = Join-Path $outDir "$slug$ext"
    $localPath = "/forces/$slug$ext"

    Write-Host "Downloading $($force.Name) -> $slug$ext"
    Invoke-WebRequest -Uri $force.ImageUrl -OutFile $localFile -UseBasicParsing

    $force | Add-Member -NotePropertyName LocalPath -NotePropertyValue $localPath -Force
}

$forces | ConvertTo-Json -Depth 3 | Set-Content $dataFile -Encoding UTF8
Write-Host "Done. $($forces.Count) force images saved to frontend/public/forces/"
