@echo off
rem Makes Shop Billing start by itself every time this computer starts (and you sign in to Windows).
rem It adds a shortcut to start.bat in your Windows "Startup" folder. Undo with autostart-off.bat.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$s = (New-Object -ComObject WScript.Shell).CreateShortcut([Environment]::GetFolderPath('Startup') + '\Shop Billing.lnk');" ^
  "$s.TargetPath = '%~dp0start.bat'; $s.WorkingDirectory = '%~dp0'; $s.WindowStyle = 7; $s.Description = 'Start Shop Billing'; $s.Save()"
if errorlevel 1 (
    echo Could not add the startup shortcut.
) else (
    echo Done. Shop Billing will now start by itself when this computer starts.
)
pause
