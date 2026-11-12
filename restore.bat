@echo off
rem Restores the shop database from a backup zip (made automatically every day, or with "Backup now" in Settings).
rem   1. Close the app first (close the black Shop Billing window).
rem   2. Drag a backup .zip from the "backups" folder onto this file, or double-click this file and pick one.
rem The current data is kept in a folder named data_before_restore_<date-time>, so nothing is lost.
title Restore Shop Billing backup
cd /d "%~dp0"

set "ZIP=%~1"
if "%ZIP%"=="" (
    echo Backups in the backups folder ^(newest last^):
    echo.
    dir /b /o:n backups\shop_billing_*.zip 2>nul
    echo.
    set /p "ZIP=Type or paste the backup file name to restore: "
)
if "%ZIP%"=="" exit /b 1
if not exist "%ZIP%" if exist "backups\%ZIP%" set "ZIP=backups\%ZIP%"
if not exist "%ZIP%" (
    echo File not found: %ZIP%
    pause
    exit /b 1
)

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set STAMP=%%i

echo.
echo This will replace the current shop data with: %ZIP%
echo The current data will be kept in data_before_restore_%STAMP%
choice /c YN /m "Continue"
if errorlevel 2 exit /b 1

if exist data (
    ren data "data_before_restore_%STAMP%" 2>nul
    if errorlevel 1 (
        echo The app is still running ^(the data is in use^). Close the black Shop Billing window first, then run this again.
        pause
        exit /b 1
    )
)
mkdir data
powershell -NoProfile -Command "Expand-Archive -LiteralPath '%ZIP%' -DestinationPath 'data' -Force"
if not exist "data\billingdb.mv.db" (
    echo Restore failed - the zip did not contain the database. Putting the old data back.
    rmdir /s /q data
    ren "data_before_restore_%STAMP%" data
    pause
    exit /b 1
)
echo.
echo Restore complete. Start the app again with start.bat.
pause
