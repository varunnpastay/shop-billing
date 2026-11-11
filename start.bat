@echo off
rem Starts the Shop Billing application and opens it in the browser.
rem Keep this window open while the shop is working; close it to stop the app.
title Shop Billing
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
    echo Java is not installed. Install Java 17 or newer from https://adoptium.net and run this again.
    pause
    exit /b 1
)

set JAR=shop-billing-1.0.0.jar
if exist "target\%JAR%" set JAR=target\%JAR%
if not exist "%JAR%" (
    echo Could not find %JAR%. Build it first with: mvn -DskipTests package
    pause
    exit /b 1
)

rem Port: PORT=... in db.properties, otherwise 8080
set APP_PORT=8080
if exist db.properties (
    for /f "usebackq tokens=1,* delims==" %%a in ("db.properties") do (
        if /i "%%a"=="PORT" set APP_PORT=%%b
    )
)

echo Starting Shop Billing on http://localhost:%APP_PORT% ...
echo Keep this window open. Close it to stop the app.
echo.

rem open the browser once the server has had time to start
start "" /min cmd /c "timeout /t 15 /nobreak >nul & start http://localhost:%APP_PORT%"

java -jar "%JAR%"
echo.
echo Shop Billing has stopped.
pause
