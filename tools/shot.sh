#!/usr/bin/env bash
# Capture the emulator screen. Usage: tools/shot.sh out.png
set -euo pipefail
"$HOME/Library/Android/sdk/platform-tools/adb" exec-out screencap -p > "${1:?output path}"
