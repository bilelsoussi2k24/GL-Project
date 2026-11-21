@echo off
rem Compiles the API into backend\out.
cd /d "%~dp0"
if not exist out mkdir out
dir /s /b src\*.java > out\sources.txt
javac -encoding UTF-8 -d out -cp "lib/*" @out\sources.txt
if errorlevel 1 exit /b 1
del out\sources.txt
echo Built to %cd%\out
