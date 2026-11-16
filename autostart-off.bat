@echo off
rem Stops Shop Billing from starting by itself (removes the shortcut added by autostart-on.bat).
set "LNK=%APPDATA%MicrosoftWindowsStart MenuProgramsStartupShop Billing.lnk"
if exist "%LNK%" (
    del "%LNK%"
    echo Done. Shop Billing will no longer start by itself.
) else (
    echo Shop Billing was not set to start by itself.
)
pause
