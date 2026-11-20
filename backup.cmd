@echo off
rem 以绕过执行策略的方式启动 backup.ps1，参数原样透传。
rem 用法: backup.cmd -Message "fix: ..."
setlocal
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0backup.ps1" %*
exit /b %ERRORLEVEL%
