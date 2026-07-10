# Official RF Online class icons from wiki.rfdatabase.net (level 40)
# Run: powershell -ExecutionPolicy Bypass -File scripts/download-class-images.ps1

$ErrorActionPreference = 'Stop'
$outDir = Join-Path $PSScriptRoot '..\frontend\public\classes'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$base = 'https://wiki.rfdatabase.net/wp-content/uploads/2025/02'

# Display name => wiki filename
$classes = [ordered]@{
    'berserker'       = 'class_berserker.jpg'
    'armsman'           = 'class_warden.jpg'
    'shield-miller'     = 'class_sentinel.jpg'
    'hidden-soldier'    = 'class_sniper.jpg'
    'sentinel'          = 'class_harrier.jpg'
    'infiltrator'       = 'class_saboteur.jpg'
    'wizard'            = 'class_archmage.jpg'
    'astralist'         = 'class_chronomancer.jpg'
    'holy-chandra'      = 'class_soulchandra.jpg'
    'mental-smith'      = 'class_machinist.jpg'
    'armor-rider'       = 'armorrider.jpg'
    'templar-knight'    = '5oQooBr.jpg'
    'guardian'          = 'class_paladin.jpg'
    'black-knight'      = 'class_templar.jpg'
    'adventurer'        = 'class_marksman.jpg'
    'stealer'           = 'stealer.gif'
    'assassin'          = 'class_redeemer.jpg'
    'warlock'           = 'class_archmagus.jpg'
    'dark-priest'       = 'class_reave.jpg'
    'grazier'           = 'class_faust.jpg'
    'artist'            = 'artist.gif'
    'punisher'          = 'class_punisher.jpg'
    'assaulter'         = 'class_dreadnought.jpg'
    'mercenary'         = 'class_warder.jpg'
    'striker'           = 'class_annihilator.jpg'
    'dementer'          = 'class_desolator.jpg'
    'phantom-shadow'    = 'class_infiltrator.jpg'
    'scientist'         = 'class_scientist.jpg'
    'battle-leader'     = 'class_bleader.jpg'
}

foreach ($entry in $classes.GetEnumerator()) {
    $slug = $entry.Key
    $wikiFile = $entry.Value
    $ext = [System.IO.Path]::GetExtension($wikiFile)
    $localFile = Join-Path $outDir "$slug$ext"
    $url = "$base/$wikiFile"
    Write-Host "Downloading $slug from $wikiFile..."
    Invoke-WebRequest -Uri $url -OutFile $localFile -UseBasicParsing
}

Write-Host "Done. $($classes.Count) class images saved to frontend/public/classes/"
