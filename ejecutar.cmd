@echo off
cd /d "%~dp0"
if not exist "dist\app\datec.jar" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\build.ps1"
  if errorlevel 1 (pause & exit /b 1)
)
java -jar "dist\app\datec.jar"
if errorlevel 1 pause
