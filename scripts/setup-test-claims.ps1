# Crea 2 clan di test con claim su chunk (0,0) e (0,1) nel database.
# Le regioni WorldGuard vengono create automaticamente dal plugin Clans all'avvio.
# Uso: powershell -ExecutionPolicy Bypass -File scripts\setup-test-claims.ps1

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path $PSScriptRoot -Parent
$SqlFile = Join-Path $PSScriptRoot "setup-test-claims.sql"
$Mysql = "C:\Program Files\MariaDB 12.3\bin\mysql.exe"

if (-not (Test-Path $Mysql)) {
    Write-Host "MariaDB mysql.exe non trovato." -ForegroundColor Red
    exit 1
}

Write-Host "=== Setup clan di test ===" -ForegroundColor Cyan
Write-Host "Inserimento dati nel database..." -ForegroundColor Gray

$QueryResult = & $Mysql -u clans -pclans_test clans -N -e "SOURCE $($SqlFile.Replace('\', '/'));" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host $QueryResult -ForegroundColor Red
    exit 1
}

$Rows = & $Mysql -u clans -pclans_test clans -N -e @"
SELECT c.id, c.name, c.tag, cc.chunk_x, cc.chunk_z
FROM clans c
INNER JOIN clan_claims cc ON cc.clan_id = c.id
WHERE c.name IN ('IronForge', 'ShadowPeak')
ORDER BY c.id;
"@

if (-not $Rows) {
    Write-Host "Nessun clan creato." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Clan creati:" -ForegroundColor Green
foreach ($Row in $Rows) {
    $Parts = $Row -split "`t"
    Write-Host "  [$($Parts[2])] $($Parts[1]) (id=$($Parts[0])) -> chunk ($($Parts[3]), $($Parts[4]))" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Prossimi passi:" -ForegroundColor Cyan
Write-Host "  1. Riavvia il server con /stop (NON usare /reload)"
Write-Host "  2. All'avvio Clans crea le regioni WorldGuard automaticamente"
Write-Host "  3. Vai alle coordinate chunk (0,0) e (0,1):"
Write-Host "     - (0,0) circa x=8 z=8   -> IronForge [IRON]"
Write-Host "     - (0,1) circa x=8 z=24  -> ShadowPeak [SHDW]"
Write-Host "  4. Testa /clan map e i permessi di build"
Write-Host ""
Write-Host "Come Gabriele6543210 (IronForge) dovresti poter costruire in (0,0) ma non in (0,1)." -ForegroundColor Yellow
