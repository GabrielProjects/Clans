@echo off
title Clans Server - playit.gg (no port forwarding)
echo.
echo  [1/3] Avvio MariaDB...
powershell -ExecutionPolicy Bypass -File "%~dp0start-mariadb.ps1"
echo.
echo  [2/3] Avvio server Minecraft (lascia questa finestra aperta)...
echo        Gli amici si connettono con l'indirizzo playit.gg
echo.
start "Minecraft Server" powershell -ExecutionPolicy Bypass -File "%~dp0start-test-server.ps1"
timeout /t 8 /nobreak >nul
echo.
echo  [3/3] Avvio playit.gg...
if exist "%~dp0..\.tools\playit\playit.exe" (
    start "playit.gg" "%~dp0..\.tools\playit\playit.exe"
) else (
    echo  playit non trovato. Esegui prima: scripts\setup-playit.ps1
    pause
    exit /b 1
)
echo.
echo  Fatto! In playit.gg crea un tunnel "Minecraft Java" -> 127.0.0.1:25565
echo  Manda l'indirizzo agli amici.
pause
