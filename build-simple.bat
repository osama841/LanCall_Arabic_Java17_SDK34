@echo off
echo بناء مشروع لان كول...
echo جار التحقق من متطلبات البناء...

REM Check if Android SDK is available
if not exist "%ANDROID_HOME%" (
    echo تحذير: ANDROID_HOME غير محدد
    echo يجب تثبيت Android SDK أولاً
    pause
    exit /b 1
)

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