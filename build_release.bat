@echo off
REM StreamHub Automated Release Build Script (Batch)
REM =================================================

echo ========================================================
echo    StreamHub: Automated Production Release Build
echo ========================================================

if exist "C:\Program Files\Android\openjdk\jdk-21.0.8" (
    set "JAVA_HOME=C:\Program Files\Android\openjdk\jdk-21.0.8"
    echo [+] Set JAVA_HOME to C:\Program Files\Android\openjdk\jdk-21.0.8
)

cd /d "%~dp0Mobile"
if %ERRORLEVEL% neq 0 (
    echo [-] Failed to navigate to Mobile directory.
    exit /b 1
)

echo [*] Executing Gradle Clean, Test, and Release Build...
call gradlew.bat clean test :app:assembleRelease :app:bundleRelease --no-daemon
if %ERRORLEVEL% neq 0 (
    echo [-] Build failed!
    cd /d "%~dp0"
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================================
echo    Release Build Succeeded!
echo ========================================================
cd /d "%~dp0"
echo Check artifacts in Mobile\app\build\outputs\apk\release\
