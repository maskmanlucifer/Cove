#!/usr/bin/env python3
"""Compare an emulator screenshot with a design reference.

Usage: tools/compare.py [--structure] <screen-name e.g. 01_Today> <shot.png> [out.png]
--structure compares layout only: both images are turned to grayscale, contrast-normalised (so the
warm "calm garden" palette does not count) and compared on their edge maps. Use it to catch layout
regressions; plain mode still measures raw pixel difference, which is dominated by colour now.
Writes a side-by-side image (design | device | diff) and prints the mean abs difference.
The top 48dp status band is ignored (design draws a fake status bar).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

root = Path(__file__).resolve().parent.parent
args = [a for a in sys.argv[1:] if a != "--structure"]
structure = len(args) != len(sys.argv) - 1
name, shot = args[0], args[1]
out = args[2] if len(args) > 2 else f"/tmp/compare_{name}.png"
ref = Image.open(root / "design/ref3x" / f"{name}.png").convert("RGB")
dev = Image.open(shot).convert("RGB").resize(ref.size)


def edges(im):
    """Normalised grayscale gradient magnitude, 0-255, blurred a little so 1 px shifts are forgiven."""
    g = np.asarray(im.convert("L").filter(ImageFilter.GaussianBlur(2))).astype(float)
    g = (g - g.mean()) / (g.std() + 1e-6)
    gy, gx = np.gradient(g)
    m = np.hypot(gx, gy)
    return np.clip(m / (np.percentile(m, 99) + 1e-6), 0, 1) * 255


if structure:
    a, b = edges(ref)[..., None].repeat(3, 2), edges(dev)[..., None].repeat(3, 2)
    ref, dev = Image.fromarray(a.astype("uint8")), Image.fromarray(b.astype("uint8"))
else:
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
print(f"{'structure' if structure else 'mean abs'} diff (0-255): {score:.2f}  -> {out}")
