@echo off
rem Double-click launcher for Love Space in LAN mode.
rem Requires: JDK 17+, a running MySQL, and the JAR built by scripts\build.ps1.
setlocal
cd /d "%~dp0"

set "PS_CMD=powershell"
where pwsh >nul 2>nul && set "PS_CMD=pwsh"

echo Starting Love Space in LAN mode...
echo Keep this window open while using the app. Press Ctrl+C or close it to stop.
echo.

%PS_CMD% -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start.ps1" -Lan

echo.
echo ==========================================================
echo  Love Space has stopped.
echo  If it failed to start, check that:
echo   1. MySQL is running (port 3306).
echo   2. backend\target\love-space-backend-1.0.0.jar exists.
echo      If not, run: scripts\build.ps1
echo   3. Windows Firewall allows TCP 8080 when prompted.
echo ==========================================================
pause
