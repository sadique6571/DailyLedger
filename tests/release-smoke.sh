#!/usr/bin/env bash
# Runs only on the disposable CI emulator after instrumented regressions.
set -euo pipefail
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am force-stop com.sadique.dailyledger
adb shell pm clear com.sadique.dailyledger >/dev/null
adb logcat -c
adb shell am start -W -n com.sadique.dailyledger/.MainActivity
for attempt in $(seq 1 10); do
  adb shell uiautomator dump /data/local/tmp/release-ui.xml >/dev/null 2>&1 || true
  if adb shell cat /data/local/tmp/release-ui.xml | grep -q 'Sign in with Google'; then break; fi
  sleep 1
done
adb shell pidof com.sadique.dailyledger
adb shell cat /data/local/tmp/release-ui.xml | grep -q 'Sign in with Google'
if adb logcat -d -s AndroidRuntime:E | grep -q 'FATAL EXCEPTION'; then
  adb logcat -d -s AndroidRuntime:E
  exit 1
fi
mkdir -p ui-previews
adb shell screencap -p /data/local/tmp/dailyledger-minified-release.png
adb pull /data/local/tmp/dailyledger-minified-release.png ui-previews/
echo 'Minified release startup passed.'
