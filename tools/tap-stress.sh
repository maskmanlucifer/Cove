#!/usr/bin/env bash
# Fires N (default 5) simultaneous taps at one point, to check that Save/Done/Create run once and Back never empties the stack.
# Usage: tools/tap-stress.sh X Y [N]   (pixels on the 1170x2532 emulator; ANDROID_SERIAL must be set)
set -euo pipefail
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
x=${1:?x}; y=${2:?y}; n=${3:-5}
cmd=""
for _ in $(seq "$n"); do cmd+="input tap $x $y & "; done
$ADB shell "${cmd}wait"
sleep 1.5
echo "top activity: $($ADB shell dumpsys activity activities | grep -m1 topResumedActivity)"
echo "screen has text nodes: $($ADB shell uiautomator dump /dev/stdout 2>/dev/null | grep -c 'text="[^"]')"
