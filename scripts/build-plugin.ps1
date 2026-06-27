# Build del plugin Clans e copia nel server di test
$ErrorActionPreference = "Stop"

$Base = Split-Path $PSScriptRoot -Parent
$JavaHome = Join-Path $Base ".tools\jdk-21.0.11+10"
$Lib = Join-Path $Base ".tools\lib"
$ServerPlugins = "C:\Users\gdast\Documents\Lavoro\MinecraftServer\plugins"

$Java = if (Test-Path "$JavaHome\bin\javac.exe") { "$JavaHome\bin\java.exe" } else { "java" }
$Javac = if (Test-Path "$JavaHome\bin\javac.exe") { "$JavaHome\bin\javac.exe" } else { "javac" }
$Jar = if (Test-Path "$JavaHome\bin\jar.exe") { "$JavaHome\bin\jar.exe" } else { "jar" }

$ClassesDir = Join-Path $Base "target\classes"
$BuildDir = Join-Path $Base "target\jar-staging"
$OutputJar = Join-Path $Base "target\Clans-1.0.0.jar"

Write-Host "Compilazione..." -ForegroundColor Cyan
if (Test-Path $ClassesDir) { Remove-Item $ClassesDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $ClassesDir | Out-Null

$Cp = (Get-ChildItem $Lib -Filter *.jar | ForEach-Object { $_.FullName }) -join ';'
$Sources = Get-ChildItem "$Base\src\main\java" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& $Javac -encoding UTF-8 -cp $Cp -d $ClassesDir $Sources

Copy-Item "$Base\src\main\resources\*" $ClassesDir -Recurse -Force

Write-Host "Packaging JAR..." -ForegroundColor Cyan
if (Test-Path $BuildDir) { Remove-Item $BuildDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null

# Solo classi del plugin (no librerie gia estratte per errore)
Copy-Item "$ClassesDir\dev" $BuildDir -Recurse -Force
Copy-Item "$ClassesDir\plugin.yml" $BuildDir -Force
Copy-Item "$ClassesDir\config.yml" $BuildDir -Force
Copy-Item "$ClassesDir\messages.yml" $BuildDir -Force
if (Test-Path "$ClassesDir\META-INF") {
    Copy-Item "$ClassesDir\META-INF" $BuildDir -Recurse -Force
}

# Dipendenze bundled (una sola volta)
Push-Location $BuildDir
& $Jar xf (Join-Path $Lib "HikariCP-5.1.0.jar")
& $Jar xf (Join-Path $Lib "mariadb-java-client-3.4.1.jar")
& $Jar cf $OutputJar *
Pop-Location

Remove-Item $BuildDir -Recurse -Force

if (Test-Path $ServerPlugins) {
    Copy-Item $OutputJar (Join-Path $ServerPlugins "Clans-1.0.0.jar") -Force
    Write-Host "Copiato in $ServerPlugins" -ForegroundColor Green
}

Write-Host "Build completata: $OutputJar" -ForegroundColor Green
