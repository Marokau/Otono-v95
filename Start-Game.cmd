@echo off
setlocal
cd /d "%~dp0"
set "OTONO_GAME_DIR=%~dp0game"
if not exist "%OTONO_GAME_DIR%\MapleStory.exe" if exist "%OTONO_GAME_DIR%\MapleStory\MapleStory.exe" set "OTONO_GAME_DIR=%OTONO_GAME_DIR%\MapleStory"
if not exist "%OTONO_GAME_DIR%\MapleStory.exe" goto missing
if not exist "%OTONO_GAME_DIR%\Kinoko.exe" goto missing
if not exist "%OTONO_GAME_DIR%\Kinoko.dll" goto missing
if /i "%~1"=="--check" exit /b 0
start "" /D "%OTONO_GAME_DIR%" "%OTONO_GAME_DIR%\Kinoko.exe"
if errorlevel 1 exit /b 1
exit /b 0
:missing
echo Install MapleStory v95 in game, then add the compatible Kinoko.exe and Kinoko.dll.
echo Initial-Server-Setup.cmd installs the included Kinoko launcher automatically.
if /i not "%~1"=="--no-pause" if /i not "%~1"=="--check" pause
exit /b 1

