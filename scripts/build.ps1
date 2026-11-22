$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
New-Item -ItemType Directory -Force -Path 'build/classes','dist/app/lib','dist/app/config' | Out-Null
$sources = Get-ChildItem -LiteralPath 'src/main/java' -Recurse -Filter '*.java' | ForEach-Object FullName
& javac -encoding UTF-8 --release 17 -d build/classes @sources
if ($LASTEXITCODE -ne 0) { throw 'La compilación falló.' }
$manifest = "Manifest-Version: 1.0`nMain-Class: pe.datec.App`nClass-Path: lib/postgresql-42.7.14.jar`n`n"
[IO.File]::WriteAllText((Join-Path $projectRoot 'build/MANIFEST.MF'), $manifest, [Text.UTF8Encoding]::new($false))
& jar --create --file dist/app/datec.jar --manifest build/MANIFEST.MF -C build/classes .
if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear el JAR.' }
Copy-Item -LiteralPath 'lib/postgresql-42.7.14.jar' -Destination 'dist/app/lib' -Force
Copy-Item -LiteralPath 'lib/LICENSE-postgresql.txt' -Destination 'dist/app/lib' -Force
Copy-Item -LiteralPath 'config/database.properties' -Destination 'dist/app/config' -Force
Copy-Item -LiteralPath 'sql' -Destination 'dist/app' -Recurse -Force
if (Test-Path -LiteralPath 'README.md') { Copy-Item -LiteralPath 'README.md' -Destination 'dist/app' -Force }
Write-Output 'Aplicación compilada: dist/app/datec.jar'
