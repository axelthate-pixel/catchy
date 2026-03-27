@echo off
REM ============================================================================
REM AngelApp - Automatisiertes Test-Skript (Batch-Wrapper)
REM ============================================================================
REM Dieses Skript ruft die PowerShell Version auf

REM PowerShell-Ausführungsrichtlinie prüfen
powershell -Command "& {if ((Get-ExecutionPolicy) -eq 'Restricted') { Write-Host 'Warnung: ExecutionPolicy ist Restricted. Setze auf RemoteSigned...' -ForegroundColor Yellow; Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser -Force }}"

REM PowerShell-Skript ausführen
powershell -NoProfile -ExecutionPolicy RemoteSigned -File "%~dp0run-tests.ps1" %*

exit /b %errorlevel%
