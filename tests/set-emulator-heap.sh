#!/usr/bin/env bash
# Disposable CI emulator only: configure the guest ART heap before starting tests.
set -euo pipefail
adb root
adb wait-for-device
adb shell setprop dalvik.vm.heapgrowthlimit 256m
adb shell setprop dalvik.vm.heapsize 512m
for ledger_heap_property in dalvik.vm.heapgrowthlimit dalvik.vm.heapsize; do
  ledger_heap_value=$(adb shell getprop "$ledger_heap_property" | tr -d '\r')
  echo "$ledger_heap_property=$ledger_heap_value"
  case "$ledger_heap_value" in 256m|512m) ;; *) exit 1 ;; esac
done
# Zygote reads these properties at startup; editing an AVD file alone is insufficient.
adb shell stop
adb shell setprop sys.boot_completed 0
adb shell start
for ledger_boot_attempt in $(seq 1 120); do
  if [[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == "1" ]]; then
    adb shell input keyevent 82
    exit 0
  fi
  sleep 1
done
echo 'CI emulator framework restart timed out' >&2
exit 1
