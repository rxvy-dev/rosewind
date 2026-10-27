@echo off
set APK=%1
if "%APK%"=="" set APK=Rosewind.apk
adb install -r "%APK%" && adb shell am start -n dev.rosewind.app/.MainActivity
