# Usage: .\deploy\validate-prod.ps1
#        .\deploy\validate-prod.ps1 -ApiHost backend.guildsystem.com.br -AppHost blacklist.guildsystem.com.br
param(
    [string] $ApiHost = 'backend.guildsystem.com.br',
    [string] $AppHost = 'blacklist.guildsystem.com.br'
)

$ErrorActionPreference = 'Stop'
$ApiBase = "https://$ApiHost"
$AppBase = "https://$AppHost"

Write-Host "==> Health: $ApiBase/actuator/health"
$health = Invoke-RestMethod -Uri "$ApiBase/actuator/health"
$health | ConvertTo-Json -Compress
if ($health.status -ne 'UP') { throw 'FAIL: health not UP' }

Write-Host "==> SockJS info: $ApiBase/ws/info"
$info = Invoke-RestMethod -Uri "$ApiBase/ws/info"
Write-Host ($info | ConvertTo-Json -Compress).Substring(0, [Math]::Min(200, ($info | ConvertTo-Json -Compress).Length))

Write-Host "==> Frontend: $AppBase"
try {
    $app = Invoke-WebRequest -Uri $AppBase -UseBasicParsing
    Write-Host "HTTP $($app.StatusCode)"
    if ($app.StatusCode -ne 200) { throw "FAIL: app not 200" }
} catch {
    throw "FAIL: could not reach app — $_"
}

Write-Host "==> CORS header check (Origin: $AppBase)"
$corsResp = Invoke-WebRequest -Uri "$ApiBase/api/seeds/races" -Headers @{ Origin = $AppBase } -UseBasicParsing
$cors = $corsResp.Headers['Access-Control-Allow-Origin']
if ($cors) { Write-Host "Access-Control-Allow-Origin: $cors" } else { Write-Host 'WARN: no Access-Control-Allow-Origin' }

Write-Host 'OK — basic production checks passed.'
