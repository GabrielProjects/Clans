# Mostra gli indirizzi per connettersi al server
$localIp = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
    $_.IPAddress -notlike '127.*' -and $_.PrefixOrigin -ne 'WellKnown'
} | Select-Object -First 1).IPAddress

$publicIp = try { Invoke-RestMethod -Uri "https://api.ipify.org" -TimeoutSec 5 } catch { "?" }

Write-Host "LAN (stessa WiFi):  $localIp" -ForegroundColor Green
Write-Host "Internet (public):  $publicIp" -ForegroundColor Green
Write-Host "Porta:              25565"
Write-Host "Versione client:    1.21.1"
