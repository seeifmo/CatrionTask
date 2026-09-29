@echo off
rem Windows entry point: runs run.sh with Git Bash, so it works from PowerShell and cmd.
rem   .\run.cmd            start backend + frontend
rem   .\run.cmd test       run every automated check
rem   .\run.cmd smoke      live API checks (app must be running)
setlocal

set "GITBASH="
for %%G in ("%ProgramFiles%\Git\bin\bash.exe" "%ProgramFiles(x86)%\Git\bin\bash.exe" "%LocalAppData%\Programs\Git\bin\bash.exe") do (
  if not defined GITBASH if exist "%%~G" set "GITBASH=%%~G"
)
rem Fall back to the Git on PATH (...\Git\cmd\git.exe -> ...\Git\bin\bash.exe).
rem Deliberately not plain "bash": on Windows that is often WSL, which has no access to this JDK/Node.
if not defined GITBASH for /f "delims=" %%G in ('where git 2^>nul') do (
  if not defined GITBASH if exist "%%~dpG..\bin\bash.exe" set "GITBASH=%%~dpG..\bin\bash.exe"
)
if not defined GITBASH (
  echo error: Git Bash not found. Install Git for Windows ^(https://git-scm.com/download/win^) or see README "Run manually".
  exit /b 1
)

"%GITBASH%" "%~dp0run.sh" %*
exit /b %ERRORLEVEL%
