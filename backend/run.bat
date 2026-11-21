@echo off
rem Builds if needed, then starts the API on http://localhost:8080.
cd /d "%~dp0"
if not exist out\Main.class call build.bat
java -cp "out;lib/*" Main
