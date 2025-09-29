@echo off
echo ========================================
echo LanCall - Quick Update Script
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

REM Install the APK on the connected device
echo 📱 Installing on connected device...
"E:\platform-tools\adb.exe" install -r -t -g "app\build\outputs\apk\debug\app-debug.apk"

if %errorlevel% neq 0 (
    echo ❌ Installation failed!
    pause
    exit /b %errorlevel%
)

echo ✅ Installation successful!
echo ========================================
echo Quick update completed!
echo ========================================
pause