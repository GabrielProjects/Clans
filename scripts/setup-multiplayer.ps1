# Configura il server per far entrare amici (LAN + Internet)
# Esegui: powershell -ExecutionPolicy Bypass -File scripts\setup-multiplayer.ps1

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$ServerDir = Join-Path (Split-Path $ProjectRoot -Parent) "MinecraftServer"
$PropsFile = Join-Path $ServerDir "server.properties"

if (-not (Test-Path $PropsFile)) {
    Write-Host "Server non trovato. Esegui prima setup-test-server.ps1" -ForegroundColor Red
    exit 1
}

Write-Host "=== Configurazione multiplayer ===" -ForegroundColor Cyan

# Aggiorna server.properties
$props = Get-Content $PropsFile -Raw
$updates = @{
    'max-players' = '20'
    'motd' = 'Clans Test Server - Plugin Clan'
    'spawn-protection' = '0'
    'white-list' = 'false'
    'server-port' = '25565'
    'server-ip' = ''
}

foreach ($key in $updates.Keys) {
    if ($props -match "(?m)^$key=.*") {
        $props = $props -replace "(?m)^$key=.*", "$key=$($updates[$key])"
    } else {
        $props += "`n$key=$($updates[$key])"
    }
}
# Rimuovi righe motd duplicate (BOM/duplicati)
$lines = $props -split "`n" | Where-Object { $_ -notmatch '^\s*$' }
$seen = @{}
$clean = foreach ($line in $lines) {
    if ($line -match '^([^=]+)=') {
        $k = $matches[1]
        if (-not $seen.ContainsKey($k)) { $seen[$k] = $true; $line }
    } else { $line }
}
$clean -join "`n" | Set-Content $PropsFile -Encoding UTF8

Write-Host "server.properties aggiornato (max 20 giocatori)" -ForegroundColor Green

# Firewall Windows (porta 25565)
$ruleName = "Minecraft Server 25565"
$existing = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
if (-not $existing) {
  try {
    New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Protocol TCP -LocalPort 25565 -Action Allow -ErrorAction Stop | Out-Null
    Write-Host "Regola firewall creata (porta 25565 TCP)" -ForegroundColor Green
  } catch {
    Write-Host "Firewall: esegui PowerShell COME AMMINISTRATORE e lancia:" -ForegroundColor Yellow
    Write-Host "  New-NetFirewallRule -DisplayName '$ruleName' -Direction Inbound -Protocol TCP -LocalPort 25565 -Action Allow"
  }
} else {
  Write-Host "Regola firewall gia presente" -ForegroundColor Green
}

# Indirizzi IP
$localIp = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
    $_.IPAddress -notlike '127.*' -and $_.PrefixOrigin -ne 'WellKnown'
} | Select-Object -First 1).IPAddress

$publicIp = try {
    (Invoke-RestMethod -Uri "https://api.ipify.org" -TimeoutSec 5)
} catch { "non disponibile" }

Write-Host ""
Write-Host "=== COME SI CONNETTONO I TUOI AMICI ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Versione Minecraft richiesta: 1.21.1 (Java Edition)" -ForegroundColor Yellow
Write-Host ""
Write-Host "AMICI SULLA STESSA WIFI / LAN:" -ForegroundColor White
Write-Host "  Indirizzo: $localIp" -ForegroundColor Green
Write-Host "  (porta 25565, di solito non serve scriverla)"
Write-Host ""
Write-Host "AMICI DA CASA LORO (Internet):" -ForegroundColor White
Write-Host "  1. Sul router fai PORT FORWARDING:" -ForegroundColor Yellow
Write-Host "     Porta esterna 25565 TCP -> $localIp :25565"
Write-Host "  2. Dai ai amici il tuo IP pubblico:" -ForegroundColor Yellow
Write-Host "     $publicIp" -ForegroundColor Green
Write-Host ""
Write-Host "SICUREZZA (consigliato per test con amici):" -ForegroundColor Cyan
Write-Host "  Nel server, dopo l'avvio, in console:"
Write-Host "    whitelist on"
Write-Host "    whitelist add NomeAmico1"
Write-Host "    whitelist add NomeAmico2"
Write-Host ""
Write-Host "  Con online-mode=false chiunque puo entrare con qualsiasi nome."
Write-Host "  Se tutti hanno Minecraft originale, imposta online-mode=true in server.properties"
Write-Host ""
Write-Host "ALTERNATIVA SENZA PORT FORWARDING:" -ForegroundColor Cyan
Write-Host "  Usa https://playit.gg (tunnel gratuito per Minecraft)"
Write-Host ""
Write-Host "Avvia server:" -ForegroundColor Cyan
Write-Host "  powershell -ExecutionPolicy Bypass -File `"$ProjectRoot\scripts\start-test-server.ps1`""
