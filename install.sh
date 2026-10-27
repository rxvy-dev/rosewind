#!/bin/sh
# Install on a USB- or wireless-debugging phone: sh install.sh [path/to/OpenMaps.apk]
APK=${1:-OpenMaps.apk}
adb install -r "$APK" && adb shell am start -n org.openmaps.app/.MainActivity
