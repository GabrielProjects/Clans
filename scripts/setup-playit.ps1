# Host senza port forwarding con playit.gg
# Solo TU installi playit; gli amici usano l'indirizzo che ti viene dato.
#
# Uso: powershell -ExecutionPolicy Bypass -File scripts\setup-playit.ps1

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$ToolsDir = Join-Path $ProjectRoot ".tools\playit"
$PlayitExe = Join-Path $ToolsDir "playit.exe"

New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null

Write-Host "=== Minecraft online SENZA port forwarding (playit.gg) ===" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path $PlayitExe)) {
    Write-Host "Download playit.gg..." -ForegroundColor Yellow
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/playit-cloud/playit-agent/releases/latest"
    $asset = $release.assets | Where-Object { $_.name -eq "playit-windows-x86_64-signed.exe" } | Select-Object -First 1
    if (-not $asset) {
        Write-Host "Download manuale: https://playit.gg/download/windows" -ForegroundColor Red
        exit 1
    }
    Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $PlayitExe
    Write-Host "Scaricato: $PlayitExe" -ForegroundColor Green
} else {
    Write-Host "playit gia presente: $PlayitExe" -ForegroundColor Green
}

Write-Host ""
Write-Host "=== PASSI (solo tu, una volta) ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Crea account gratuito su https://playit.gg (se non ce l'hai)"
Write-Host ""
Write-Host "2. Avvia MariaDB e il server Minecraft:"
Write-Host "   scripts\start-mariadb.ps1"
Write-Host "   scripts\start-test-server.ps1"
Write-Host ""
Write-Host "3. In un TERZO terminale, avvia playit:"
Write-Host "   & `"$PlayitExe`"" -ForegroundColor Yellow
Write-Host ""
Write-Host "4. Al primo avvio:"
Write-Host "   - Accedi con il tuo account playit.gg"
Write-Host "   - Scegli: Add tunnel / Aggiungi tunnel"
Write-Host "   - Tipo: Minecraft Java"
Write-Host "   - Indirizzo locale: 127.0.0.1"
Write-Host "   - Porta locale: 25565"
Write-Host ""
Write-Host "5. playit ti mostra un indirizzo pubblico, es.:"
Write-Host "   abc123.gl.joinmc.link" -ForegroundColor Green
Write-Host "   oppure qualcosa.playit.gg"
Write-Host ""
Write-Host "6. Manda QUELL'indirizzo ai tuoi amici."
Write-Host "   Loro in Minecraft 1.21.1: Multiplayer -> Aggiungi server -> incollano l'indirizzo"
Write-Host ""
Write-Host "=== I tuoi amici NON devono ===" -ForegroundColor Cyan
Write-Host "  - fare port forwarding"
Write-Host "  - installare playit"
Write-Host "  - aprire porte sul router"
Write-Host "  - usare il tuo IP di casa"
Write-Host ""
Write-Host "Per avviare playit manualmente:" -ForegroundColor Yellow
Write-Host "  & `"$PlayitExe`""
