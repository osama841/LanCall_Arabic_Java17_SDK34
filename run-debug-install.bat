@echo off
setlocal
echo 🚀 assembleDebug + install (single device)
call .\gradlew.bat assembleDebug || goto :fail

for /f "delims=" %%i in ('powershell -NoProfile -Command "(Get-ChildItem app\build\outputs\apk\debug -Filter *.apk -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName"') do set APK=%%i
if not defined APK (
  echo ❌ No APK found in app\build\outputs\apk\debug
  goto :fail
)
echo 📦 %APK%
"E:\platform-tools\adb.exe" install -r -t -g "%APK%" || goto :fail
echo ✅ Done
exit /b 0
:fail
echo ❌ Failed
exit /b 1
