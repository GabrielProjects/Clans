@echo off
title Clans - Server locale
echo.
echo Avvio MariaDB...
powershell -ExecutionPolicy Bypass -File "%~dp0start-mariadb.ps1"
echo.
echo Avvio server Minecraft (localhost)...
echo Connettiti in Minecraft 1.21.1 con: localhost
echo.
powershell -ExecutionPolicy Bypass -File "%~dp0start-test-server.ps1"
