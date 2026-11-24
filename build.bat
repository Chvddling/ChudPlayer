@echo off
setlocal EnableExtensions EnableDelayedExpansion
title ChudPlayer - build
cd /d "%~dp0"
set "LOG=%~dp0build-log.txt"

echo ==================================================
echo    ChudPlayer - build helper
echo ==================================================
echo.

rem ---------- 1. Java (JDK 17 or newer) ----------
call :findjava
if errorlevel 1 goto :nojava

rem ---------- 2. libmpv ----------
if exist "native-resources\windows\libmpv-2.dll" (
    echo [OK] libmpv-2.dll found
) else (
    echo [!!] libmpv-2.dll is missing from native-resources\windows\
    echo      The app will still build, but videos won't play until you add it.
    echo      Instructions: native-resources\windows\PUT_LIBMPV_HERE.txt
    choice /c YN /m "     Open the download page now"
    if !errorlevel! equ 1 start "" "https://github.com/shinchiro/mpv-winbuild-cmake/releases"
)

rem ---------- 3. What to build ----------
rem You can also skip the menu:  build.bat run  /  build.bat createDistributable  ...
if not "%~1"=="" (
    set "TASK=%~1"
    goto :build
)

:menu
echo.
echo   1. Run the app            (build + start it)
echo   2. Portable app folder    (no install needed, includes Java)
echo   3. .exe installer
echo   4. .msi installer
echo   5. Clean                  (delete old build files)
echo   6. Exit
echo.
choice /c 123456 /n /m "Choose 1-6: "
set "C=%errorlevel%"
if "%C%"=="1" set "TASK=run"
if "%C%"=="2" set "TASK=createDistributable"
if "%C%"=="3" set "TASK=packageExe"
if "%C%"=="4" set "TASK=packageMsi"
if "%C%"=="5" set "TASK=clean"
if "%C%"=="6" exit /b 0

:build
echo.
echo Running: gradlew %TASK%
echo Everything is also saved to build-log.txt
echo (the first build downloads a lot and can take 5-10 minutes)
echo --------------------------------------------------

rem Run Gradle through a small PowerShell helper: shows output live AND saves it to build-log.txt
if not exist "%~dp0tools\gradle-log.ps1" (
    echo [!!] tools\gradle-log.ps1 is missing - re-download the project.
    goto :failed
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\gradle-log.ps1" -Task "%TASK%" -Log "%LOG%"
set "RC=%errorlevel%"

echo --------------------------------------------------
if not "%RC%"=="0" goto :failed

echo.
echo [OK] Done: %TASK%
if /i "%TASK%"=="createDistributable" if exist "build\compose\binaries\main\app" start "" "build\compose\binaries\main\app"
if /i "%TASK%"=="packageExe" if exist "build\compose\binaries\main\exe" start "" "build\compose\binaries\main\exe"
if /i "%TASK%"=="packageMsi" if exist "build\compose\binaries\main\msi" start "" "build\compose\binaries\main\msi"
echo.
pause
exit /b 0

:failed
echo.
echo [!!] The build FAILED.
echo      Tell Claude "check the build log" - it can read build-log.txt from this folder
echo      and fix the code directly. Or send build-log.txt in the chat.
echo.
pause
exit /b 1

rem ================= helpers =================

:findjava
set "JAVA_EXE="
set "JVER="
set "JMAJOR="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE for /f "delims=" %%j in ('where java 2^>nul') do if not defined JAVA_EXE set "JAVA_EXE=%%j"
if not defined JAVA_EXE exit /b 1
"%JAVA_EXE%" -version 2> "%TEMP%\npd_javaver.txt"
for /f "tokens=3" %%v in ('findstr /i /c:" version " "%TEMP%\npd_javaver.txt"') do if not defined JVER set "JVER=%%~v"
del "%TEMP%\npd_javaver.txt" >nul 2>&1
for /f "tokens=1 delims=." %%m in ("%JVER%") do set "JMAJOR=%%m"
if "%JMAJOR%"=="" exit /b 1
if "%JMAJOR%"=="1" (
    echo [!!] Found Java %JVER% at "%JAVA_EXE%" - too old.
    exit /b 1
)
if %JMAJOR% LSS 17 (
    echo [!!] Found Java %JVER% at "%JAVA_EXE%" - too old.
    exit /b 1
)
echo [OK] Java %JVER%  ^(%JAVA_EXE%^)
exit /b 0

:nojava
echo [!!] A Java JDK 17 or newer is needed (JDK 21 recommended).
where winget >nul 2>&1
if not errorlevel 1 (
    choice /c YN /m "     Install Eclipse Temurin JDK 21 now with winget"
    if !errorlevel! equ 1 (
        winget install --id EclipseAdoptium.Temurin.21.JDK -e --accept-package-agreements --accept-source-agreements
        echo.
        echo Close this window and run build.bat again so Windows picks up the new Java.
        pause
        exit /b 1
    )
)
echo Opening the JDK download page. Install it with "Set JAVA_HOME" ticked, then run build.bat again.
start "" "https://adoptium.net/temurin/releases/?version=21"
pause
exit /b 1
