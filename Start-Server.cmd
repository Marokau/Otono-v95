@echo off
@title Otono v95
setlocal
cd /d "%~dp0"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
where java >nul 2>&1
if errorlevel 1 goto missingjava
if not exist "server\target\server.jar" goto missingjar
set "OTONO_GAME_DIR=%~dp0game"
if not exist "%OTONO_GAME_DIR%\MapleStory.exe" if exist "%OTONO_GAME_DIR%\MapleStory\MapleStory.exe" set "OTONO_GAME_DIR=%OTONO_GAME_DIR%\MapleStory"
set "MISSING_ASSETS="
for %%F in (Character Item Skill Morph Map Mob Npc Reactor Quest String Etc) do if not exist "%OTONO_GAME_DIR%\%%F.wz" echo Missing "%OTONO_GAME_DIR%\%%F.wz"
for %%F in (Character Item Skill Morph Map Mob Npc Reactor Quest String Etc) do if not exist "%OTONO_GAME_DIR%\%%F.wz" set "MISSING_ASSETS=1"
if defined MISSING_ASSETS goto missingassets
set "WZ_DIRECTORY=%OTONO_GAME_DIR%"
set "DATA_DIRECTORY=%~dp0server\data"
set "DATABASE_PROVIDER=sqlite"
set "OTONO_CONFIG=%~dp0config.yaml"
set "SERVER_HOST=127.0.0.1"
set "CENTRAL_HOST=127.0.0.1"
if /i "%~1"=="--check" exit /b 0
cd /d "%~dp0server"
java -Xmx2g -jar target\server.jar
set "SERVER_EXIT=%ERRORLEVEL%"
if /i not "%~1"=="--no-pause" if /i not "%~1"=="--check" pause
exit /b %SERVER_EXIT%
:missingjava
echo Java was not found. Install a Java 21 JDK first.
goto failed
:missingjar
echo Run Initial-Server-Setup.cmd first to build server\target\server.jar.
goto failed
:missingassets
echo Install the complete compatible v95 game files in the game folder first.
:failed
if /i not "%~1"=="--no-pause" if /i not "%~1"=="--check" pause
exit /b 1

