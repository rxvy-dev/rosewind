@echo off
set APK=%1
if "%APK%"=="" set APK=OpenMaps.apk
adb install -r "%APK%" && adb shell am start -n org.openmaps.app/.MainActivity
