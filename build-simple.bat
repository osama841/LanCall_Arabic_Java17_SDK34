@echo off
echo بناء مشروع لان كول...
echo جار التحقق من متطلبات البناء...

REM Removed ANDROID_HOME check since it's not required when using Gradle wrapper

echo بناء المشروع...
call gradlew.bat clean
call gradlew.bat assembleDebug

if %ERRORLEVEL% == 0 (
    echo ✅ تم البناء بنجاح!
    echo 📁 ملف APK في: app\build\outputs\apk\debug\
    dir app\build\outputs\apk\debug\*.apk
) else (
    echo ❌ فشل البناء
    echo يرجى التحقق من الأخطاء أعلاه
)

pause