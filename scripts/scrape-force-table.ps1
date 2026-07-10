$ErrorActionPreference = 'Stop'
$html = (Invoke-WebRequest -Uri 'https://wiki.rfdatabase.net/game-controls/forces' -UseBasicParsing).Content

function Normalize-ForceName {
    param([string]$Raw)
    ($Raw -replace '<br\s*/?>\s*', ' / ' -replace '\s*/\s*/\s*', ' / ' -replace '\s+', ' ').Trim()
}

$results = @()
$sort = 0

foreach ($chunk in ($html -split '</tr>')) {
    $rowMatch = [regex]::Match($chunk, '<td>(Holy|Dark|Fire|Aqua|Terra|Wind)</td><td>(.*?)</td><td>([^<]+)</td><td>([^<]+)</td>')
    if (-not $rowMatch.Success) { continue }

    $forceClass = $rowMatch.Groups[1].Value
    $name = Normalize-ForceName $rowMatch.Groups[2].Value
    $rank = $rowMatch.Groups[3].Value.Trim()
    $type = $rowMatch.Groups[4].Value.Trim()

    $imgMatch = [regex]::Match($chunk, 'data-src="(https://wiki\.rfdatabase\.net/wp-content/uploads/2024/06/[^"]+)"')
    if (-not $imgMatch.Success) {
        $imgMatch = [regex]::Match($chunk, 'src="(https://wiki\.rfdatabase\.net/wp-content/uploads/2024/06/[^"]+)"')
    }
    if (-not $imgMatch.Success) {
        Write-Warning "No image for $name"
        continue
    }

    $img = $imgMatch.Groups[1].Value -replace '-32x32', ''
    if ($img -match '-\d+x\d+\.') { continue }

    $sort++
    $slug = ($name -replace '[^a-zA-Z0-9]+', '-' -replace '^-|-$', '').ToLower()
    $results += [PSCustomObject]@{
        Name = $name
        Class = $forceClass
        Rank = $rank
        Type = $type
        Slug = $slug
        ImageUrl = $img
        SortOrder = $sort
    }
}

Write-Host "Found $($results.Count) forces"
$results | ForEach-Object { Write-Output "$($_.SortOrder)|$($_.Name)|$($_.Class)" }
$results | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $PSScriptRoot 'force-seed-data.json') -Encoding UTF8
