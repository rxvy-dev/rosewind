@echo off
set APK=%1
if "%APK%"=="" set APK=Wayglass.apk
adb install -r "%APK%" && adb shell am start -n dev.wayglass.app/.MainActivity
