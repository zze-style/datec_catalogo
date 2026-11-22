$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
& "$PSScriptRoot/build.ps1"
if (Test-Path -LiteralPath 'dist/portable/DATEC') { throw 'Ya existe dist/portable/DATEC. Mueva esa versión antes de generar otra.' }
& jpackage --type app-image --name DATEC --input dist/app --main-jar datec.jar --main-class pe.datec.App --dest dist/portable --app-version 1.0.0 --vendor 'Proyecto GDI DATEC' --icon assets/logo/datalogo.ico --add-modules java.desktop,java.sql,java.naming,java.security.jgss,java.management,jdk.crypto.ec
if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear el paquete portable.' }
Write-Output 'Ejecutable portable: dist/portable/DATEC/DATEC.exe'
