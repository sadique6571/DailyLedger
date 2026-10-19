#!/bin/bash
set -uo pipefail
result=0
./gradlew --no-daemon connectedDebugAndroidTest || result=$?
mkdir -p ui-previews
for p in $(adb shell ls '/data/local/tmp/zip-original-*.png' | tr -d '\r'); do adb pull "$p" ui-previews/; done
exit "$result"
