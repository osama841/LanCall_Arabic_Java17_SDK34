@echo off
echo ========================================
echo LanCall - Quick Installation Script
echo ========================================

REM Find the latest APK
for /f "delims=" %%i in ('powershell -NoProfile -Command "(Get-ChildItem app\build\outputs\apk\debug -Filter *.apk -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName"') do set APK=%%i

if not defined APK (
  echo ❌ No APK found. Building the project...
  call gradlew.bat assembleDebug
  
  REM Try to find APK again after build
  for /f "delims=" %%i in ('powershell -NoProfile -Command "(Get-ChildItem app\build\outputs\apk\debug -Filter *.apk -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName"') do set APK=%%i
  
  if not defined APK (
    echo ❌ No APK found even after building
    pause
    exit /b 1
  )
)

echo 📦 Found APK: %APK%

REM Install on all connected devices
for /f %%d in ('"E:\platform-tools\adb.exe" devices ^| findstr device$') do (
  echo 🔗 Installing on device %%d ...
  "E:\platform-tools\adb.exe" -s %%d install -r -t -g "%APK%"
  if %errorlevel% equ 0 (
    echo ✅ Successfully installed on device %%d
  ) else (
    echo ❌ Failed to install on device %%d
  )
  echo.
)

echo ========================================
echo Installation completed!
echo ========================================
pause