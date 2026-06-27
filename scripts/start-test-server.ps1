# Avvia il server Paper in locale (solo tu, stesso PC)
# Esegui: powershell -ExecutionPolicy Bypass -File scripts\start-test-server.ps1

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$ServerDir = Join-Path (Split-Path $ProjectRoot -Parent) "MinecraftServer"
$PaperJar = Join-Path $ServerDir "paper.jar"
$JavaHome = Join-Path $ProjectRoot ".tools\jdk-21.0.11+10"

if (-not (Test-Path $PaperJar)) {
    Write-Host "Server non configurato. Esegui prima: scripts\setup-test-server.ps1" -ForegroundColor Red
    exit 1
}

$JavaExe = if (Test-Path "$JavaHome\bin\java.exe") { "$JavaHome\bin\java.exe" } else { "java" }

Write-Host "=== Server locale ===" -ForegroundColor Cyan
Write-Host "Cartella: $ServerDir"
Write-Host "Connettiti con Minecraft 1.21.1 -> localhost" -ForegroundColor Yellow
Write-Host "CTRL+C per fermare" -ForegroundColor Gray
Write-Host ""

Set-Location $ServerDir
& $JavaExe -Xmx2G -jar paper.jar --nogui
