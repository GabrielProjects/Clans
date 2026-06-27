# Avvia MariaDB in locale (senza servizio Windows)
# Esegui come amministratore NON e richiesto.
# Uso: powershell -ExecutionPolicy Bypass -File scripts\start-mariadb.ps1

$ErrorActionPreference = "Stop"
$MariaDbBin = "C:\Program Files\MariaDB 12.3\bin"
$DefaultsFile = "C:\Program Files\MariaDB 12.3\data\my.ini"

if (-not (Test-Path "$MariaDbBin\mysqld.exe")) {
    Write-Host "MariaDB non trovato. Installa con:" -ForegroundColor Red
    Write-Host "  winget install MariaDB.Server --source winget"
    exit 1
}

# Verifica se gia in esecuzione
try {
    & "$MariaDbBin\mysql.exe" -u root -e "SELECT 1" 2>$null | Out-Null
    Write-Host "MariaDB e gia in esecuzione." -ForegroundColor Green
    exit 0
} catch {}

Write-Host "Avvio MariaDB..." -ForegroundColor Cyan
Start-Process -FilePath "$MariaDbBin\mysqld.exe" -ArgumentList "--defaults-file=`"$DefaultsFile`"", "--standalone" -WindowStyle Hidden
Start-Sleep 3

try {
    & "$MariaDbBin\mysql.exe" -u root -e "SELECT VERSION();"
    Write-Host "MariaDB avviato con successo." -ForegroundColor Green
} catch {
    Write-Host "Impossibile connettersi a MariaDB. Controlla i log." -ForegroundColor Red
    exit 1
}
