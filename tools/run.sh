#!/usr/bin/env bash
# Build, install and launch the debug app on the connected emulator.
# Usage: tools/run.sh [--seed] [--dark] [--evening] [--now HH:mm]
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
./gradlew :app:installDebug -q
EXTRAS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --seed) EXTRAS+=(--ez seed true) ;;
    --dark) EXTRAS+=(--ez dark true) ;;
    --evening) EXTRAS+=(--ez evening true) ;;
    --now) shift; EXTRAS+=(--es now "$1") ;;
  esac
  shift
done
$ADB shell am force-stop app.cove.companion
if [ "${CLEAR:-0}" = "1" ]; then $ADB shell pm clear app.cove.companion >/dev/null; fi
$ADB shell am start -n app.cove.companion/.MainActivity ${EXTRAS[@]+"${EXTRAS[@]}"} >/dev/null
sleep 2
