#!/usr/bin/env python3
"""Compare an emulator screenshot with a design reference.

Usage: tools/compare.py <screen-name e.g. 01_Today> <shot.png> [out.png]
Writes a side-by-side image (design | device | diff) and prints the mean abs difference.
The top 48dp status band is ignored (design draws a fake status bar).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

root = Path(__file__).resolve().parent.parent
name, shot = sys.argv[1], sys.argv[2]
out = sys.argv[3] if len(sys.argv) > 3 else f"/tmp/compare_{name}.png"
ref = Image.open(root / "design/ref3x" / f"{name}.png").convert("RGB")
dev = Image.open(shot).convert("RGB").resize(ref.size)
a, b = np.asarray(ref).astype(int), np.asarray(dev).astype(int)
diff = np.abs(a - b).sum(axis=2)
band = 48 * 3
score = diff[band:].mean() / 3
heat = Image.fromarray(np.clip(diff * 2, 0, 255).astype("uint8")).convert("RGB")
w, h = ref.size
sheet = Image.new("RGB", (w * 3 + 40, h), "white")
for i, im in enumerate((ref, dev, heat)):
    sheet.paste(im, (i * (w + 20), 0))
sheet.resize((sheet.width // 3, sheet.height // 3)).save(out)
print(f"mean abs diff (0-255): {score:.2f}  -> {out}")
