# Setup server Paper locale per testare il plugin Clans
# Esegui: powershell -ExecutionPolicy Bypass -File scripts\setup-test-server.ps1

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$ServerDir = Join-Path (Split-Path $ProjectRoot -Parent) "MinecraftServer"
$PluginsDir = Join-Path $ServerDir "plugins"
$JavaHome = Join-Path $ProjectRoot ".tools\jdk-21.0.11+10"

Write-Host "=== Setup server di test Clans ===" -ForegroundColor Cyan
Write-Host "Cartella server: $ServerDir"

New-Item -ItemType Directory -Force -Path $PluginsDir | Out-Null

# Paper 1.21.1
$PaperUrl = "https://api.papermc.io/v2/projects/paper/versions/1.21.1/builds/133/downloads/paper-1.21.1-133.jar"
$PaperJar = Join-Path $ServerDir "paper.jar"
if (-not (Test-Path $PaperJar)) {
    Write-Host "Download Paper 1.21.1..." -ForegroundColor Yellow
    Invoke-WebRequest -Uri $PaperUrl -OutFile $PaperJar
}

# WorldEdit + WorldGuard (Modrinth, compatibili 1.21.1)
$downloads = @{
    "worldedit-bukkit-7.3.9.jar"     = "https://cdn.modrinth.com/data/1u6JkXh5/versions/Bu1zaaoc/worldedit-bukkit-7.3.9.jar"
    "worldguard-bukkit-7.0.12-dist.jar" = "https://cdn.modrinth.com/data/DKY9btbd/versions/J66QOTLZ/worldguard-bukkit-7.0.12-dist.jar"
}

foreach ($entry in $downloads.GetEnumerator()) {
    $dest = Join-Path $PluginsDir $entry.Key
    if (-not (Test-Path $dest)) {
        Write-Host "Download $($entry.Key)..." -ForegroundColor Yellow
        Invoke-WebRequest -Uri $entry.Value -OutFile $dest
    }
}

# Plugin Clans
$ClansJar = Join-Path $ProjectRoot "target\Clans-1.0.0.jar"
if (-not (Test-Path $ClansJar)) {
    Write-Host "JAR Clans non trovato. Esegui prima: mvn clean package" -ForegroundColor Red
    exit 1
}
Copy-Item $ClansJar (Join-Path $PluginsDir "Clans-1.0.0.jar") -Force

# EULA
$EulaPath = Join-Path $ServerDir "eula.txt"
if (-not (Test-Path $EulaPath)) {
    Set-Content $EulaPath "eula=true"
    Write-Host "EULA accettata automaticamente (eula=true)" -ForegroundColor Green
}

# server.properties base
$PropsPath = Join-Path $ServerDir "server.properties"
if (-not (Test-Path $PropsPath)) {
    @"
motd=Clans Test Server
gamemode=creative
difficulty=peaceful
online-mode=false
max-players=10
spawn-protection=0
"@ | Set-Content $PropsPath -Encoding UTF8
    Write-Host "Creato server.properties (creative, peaceful, online-mode=false per test locale)" -ForegroundColor Green
}

# Config Clans con credenziali di test
$ClansConfigDir = Join-Path $PluginsDir "Clans"
New-Item -ItemType Directory -Force -Path $ClansConfigDir | Out-Null
$ConfigPath = Join-Path $ClansConfigDir "config.yml"
if (-not (Test-Path $ConfigPath)) {
    Copy-Item (Join-Path $ProjectRoot "src\main\resources\config.yml") $ConfigPath
}
(Get-Content $ConfigPath -Raw) -replace 'password: ""', 'password: "clans_test"' | Set-Content $ConfigPath -Encoding UTF8

Write-Host ""
Write-Host "=== Setup completato ===" -ForegroundColor Green
Write-Host ""
Write-Host "PROSSIMI PASSI:" -ForegroundColor Cyan
Write-Host "1. Installa MariaDB (se non ce l'hai):"
Write-Host "   winget install MariaDB.Server"
Write-Host ""
Write-Host "2. Crea il database (dopo aver installato MariaDB):"
Write-Host "   mysql -u root -p < `"$ProjectRoot\scripts\setup-database.sql`""
Write-Host "   (oppure esegui lo script SQL da HeidiSQL / phpMyAdmin)"
Write-Host ""
Write-Host "3. Avvia il server:"
Write-Host "   powershell -ExecutionPolicy Bypass -File `"$ProjectRoot\scripts\start-test-server.ps1`""
Write-Host ""
Write-Host "4. Apri Minecraft 1.21.1 e connettiti a: localhost" -ForegroundColor Yellow
