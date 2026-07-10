$ErrorActionPreference = 'Stop'
$scriptDir = $PSScriptRoot
$dataFile = Join-Path $scriptDir 'force-seed-data.json'
$migrationFile = Join-Path $scriptDir '..\backend\src\main\resources\db\migration\V4__weapon_cast_seeds.sql'

& (Join-Path $scriptDir 'scrape-force-table.ps1') | Out-Null
& (Join-Path $scriptDir 'download-force-images.ps1') | Out-Null

$forces = Get-Content $dataFile -Raw | ConvertFrom-Json
$lines = @(
    'ALTER TABLE item_seed ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);',
    '',
    'ALTER TABLE item_seed DROP CONSTRAINT IF EXISTS item_seed_category_name_key;',
    'ALTER TABLE item_seed ADD CONSTRAINT item_seed_category_name_image_url_key UNIQUE (category, name, image_url);',
    '',
    'INSERT INTO item_seed (category, name, image_url, sort_order) VALUES'
)

$values = @()
foreach ($force in $forces) {
    $name = $force.Name -replace "'", "''"
    $image = $force.LocalPath -replace "'", "''"
    $values += "    ('WEAPON_CAST', '$name', '$image', $($force.SortOrder))"
}

$lines += ($values -join ",`n") + ';'
$lines | Set-Content $migrationFile -Encoding UTF8
Write-Host "Generated $migrationFile with $($forces.Count) casts"
