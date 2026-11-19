@echo off
setlocal
cd /d "%~dp0"
set "OTONO_NO_PAUSE="
set "OTONO_REBUILD="
for %%A in (%*) do (
    if /i "%%~A"=="--no-pause" set "OTONO_NO_PAUSE=1"
    if /i "%%~A"=="--rebuild" set "OTONO_REBUILD=1"
)
if not exist "game" mkdir "game"
set "OTONO_GAME_DIR=%~dp0game"
if not exist "%OTONO_GAME_DIR%\MapleStory.exe" if exist "%OTONO_GAME_DIR%\MapleStory\MapleStory.exe" set "OTONO_GAME_DIR=%OTONO_GAME_DIR%\MapleStory"
if exist "%~dp0server\target\server.jar" if not defined OTONO_REBUILD goto alreadybuilt
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
where java >nul 2>&1
if errorlevel 1 goto missingjava
where javac >nul 2>&1
if errorlevel 1 goto missingjava
echo Building the Kinoko-based Otono v95 server. Stop it before rebuilding.
echo The first build downloads Maven and Java dependencies. Internet is required.
echo Setup skips tests because some upstream tests require game assets.
java -version
cd /d "%~dp0server"
call mvnw.cmd -B -ntp -DskipTests clean package
if errorlevel 1 goto failed
echo.
echo Server build complete: server\target\server.jar
:installclient
if not exist "%~dp0client\bin\Kinoko.exe" goto missingclient
if not exist "%~dp0client\bin\Kinoko.dll" goto missingclient
copy /b /y "%~dp0client\bin\Kinoko.exe" "%OTONO_GAME_DIR%\Kinoko.exe" >nul
if errorlevel 1 goto failed
copy /b /y "%~dp0client\bin\Kinoko.dll" "%OTONO_GAME_DIR%\Kinoko.dll" >nul
if errorlevel 1 goto failed
echo Official Kinoko v95.1.7 launcher files installed in "%OTONO_GAME_DIR%".
echo Supply the complete v95 game installation there before starting the server.
echo Start-Server.cmd will check the required assets before starting.
echo SQLite is created automatically on the first successful server startup.
if not defined OTONO_NO_PAUSE pause
exit /b 0
:missingclient
echo Bundled launcher files are missing from client\bin. Restore them from this repository.
goto failed
:missingjava
echo Install a Java 21 JDK and reopen this window. Set JAVA_HOME or add its bin folder to PATH.
goto failed
:failed
echo Setup did not complete. Read the error above.
if not defined OTONO_NO_PAUSE pause
exit /b 1



:alreadybuilt
echo The Otono server is already built. It will not be rebuilt or reset.
echo Checking and restoring the bundled Kinoko client files.
goto installclient
