@echo off
setlocal enabledelayedexpansion
echo ========================================
echo LanCall - Update and Install Script
echo ========================================

REM Change to the project directory
cd /d "c:\Users\Osama\Desktop\laval4\LanCall_Arabic_Java17_SDK34\LanCall_Arabic_Java17_SDK34"

REM Build the project
echo 🛠️  Building the project...
call gradlew.bat assembleDebug

if %errorlevel% neq 0 (
    echo ❌ Build failed!
    pause
    exit /b %errorlevel%
)

echo ✅ Build successful!

REM Install the APK on all connected devices
echo 📱 Installing on all connected devices...
for /f %%d in ('"E:\platform-tools\adb.exe" devices ^| findstr device$') do (
    echo 🔗 Installing on device %%d ...
    "E:\platform-tools\adb.exe" -s %%d install -r -t -g "app\build\outputs\apk\debug\app-debug.apk"
    if !errorlevel! equ 0 (
        echo ✅ Successfully installed on device %%d
    ) else (
        echo ❌ Failed to install on device %%d
    )
)

echo ========================================
echo Update and installation completed!
echo ========================================
pause