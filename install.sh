#!/bin/sh
# Install on a USB- or wireless-debugging phone: sh install.sh [path/to/Wayglass.apk]
APK=${1:-Wayglass.apk}
adb install -r "$APK" && adb shell am start -n dev.wayglass.app/.MainActivity
