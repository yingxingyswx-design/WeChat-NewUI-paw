@echo off
rem Wrapper: run backup.ps1 with ExecutionPolicy Bypass and forward all args.
rem Usage: backup.cmd -Message "fix: ..."
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0backup.ps1" %*
exit /b %ERRORLEVEL%
