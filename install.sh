#!/bin/sh
# Install on a USB- or wireless-debugging phone: sh install.sh [path/to/Rosewind.apk]
APK=${1:-Rosewind.apk}
adb install -r "$APK" && adb shell am start -n dev.rosewind.app/.MainActivity
