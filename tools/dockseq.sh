#!/usr/bin/env bash
# Frame-by-frame check of the bottom bar: slows animations 10x, opens then closes it (optionally picking a tab),
# and writes a contact sheet of the bar to $1. Usage: tools/dockseq.sh out.png [pickX]
set -euo pipefail
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
OUT=${1:?out}; PICK=${2:-}
T=$(mktemp -d)
$ADB shell settings put global animator_duration_scale 10
$ADB shell settings put global transition_animation_scale 10
trap '$ADB shell settings put global animator_duration_scale 1; $ADB shell settings put global transition_animation_scale 1' EXIT
$ADB shell input tap 450 2285
for i in 1 2 3 4 5 6; do $ADB exec-out screencap -p > $T/o$i.png; done
sleep 3
if [ -n "$PICK" ]; then $ADB shell input tap "$PICK" 2285; else $ADB shell input tap 450 2285; fi
for i in 7 8 9 10 11 12; do $ADB exec-out screencap -p > $T/o$i.png; done
python3 - "$T" "$OUT" <<'PY'
import sys
from PIL import Image
T,O=sys.argv[1:]
ims=[Image.open(f"{T}/o{i}.png").crop((0,2150,1080,2400)).resize((540,125)) for i in range(1,13)]
W=Image.new("RGB",(1080,125*6))
for i,im in enumerate(ims): W.paste(im,((i//6)*540,125*(i%6)))
W.save(O)
PY
