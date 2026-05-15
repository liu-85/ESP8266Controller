@echo off
setlocal

set VERSION=%1
if "%VERSION%"=="" (
    for /f "tokens=2 delims==" %%a in ('wmic os get localdatetime /value') do set dt=%%a
    set VERSION=%dt:~0,8%
)

echo Building ESP8266 Controller Version: %VERSION%

REM Update versionName in app/build.gradle
powershell -Command "(gc app/build.gradle) -replace 'versionName \".*\"', 'versionName \"%VERSION%\"' | Out-File -encoding ASCII app/build.gradle"

call gradlew.bat assembleDebug

if %ERRORLEVEL% EQU 0 (
    echo Build Successful!
    echo APK Location: app\build\outputs\apk\debug\
) else (
    echo Build Failed!
)

pause
