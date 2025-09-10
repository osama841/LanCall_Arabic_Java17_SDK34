@echo off
setlocal
for /f "delims=" %%i in ('powershell -NoProfile -Command "(Get-ChildItem app\build\outputs\apk -Recurse -Filter *.apk -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName"') do set APK=%%i
if not defined APK (
  echo ❌ No APK found
  exit /b 1
)
for /f %%d in ('"E:\platform-tools\adb.exe" devices ^| findstr device$') do (
  echo 🔗 Installing on %%d ...
  "E:\platform-tools\adb.exe" -s %%d install -r -t -g "%APK%"
)
echo ✅ Done
