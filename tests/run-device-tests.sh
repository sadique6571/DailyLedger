#!/usr/bin/env bash
# emulator-runner executes each script line separately; keep the full shell workflow in one file.
set -uo pipefail
ledger_test_status=0
./gradlew --no-daemon connectedDebugAndroidTest || ledger_test_status=$?
mkdir -p ui-previews
while IFS= read -r ledger_preview; do
  [[ -n "$ledger_preview" ]] && adb pull "$ledger_preview" ui-previews/
done < <(adb shell ls '/data/local/tmp/dailyledger-*.png' '/data/local/tmp/dailyledger-*.pdf' | tr -d '\r')
exit "$ledger_test_status"
