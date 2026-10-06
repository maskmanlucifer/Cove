#!/usr/bin/env python3
"""Drives a fixed UI tour on the emulator and prints gfxinfo frame stats (p90/p99, janky %).
Usage: tools/jank.py old|new   (old = centred pill dock, new = anchored pill dock; only the dock taps differ)
Needs ANDROID_SERIAL exported, a seeded app on screen (tools/run.sh --seed --now 10:35)."""
import re, subprocess, sys, time, xml.etree.ElementTree as ET

ADB = ["adb"]
PKG = "app.cove.companion"
D = 2.625  # px per dp at 420 dpi

def sh(*a):
    return subprocess.run(ADB + ["shell", *a], capture_output=True, text=True).stdout

def tap(x, y):
    sh("input", "tap", str(int(x)), str(int(y)))

def tap_text(text, wait=0.9):
    sh("uiautomator", "dump", "/sdcard/u.xml")
    xml = subprocess.run(ADB + ["exec-out", "cat", "/sdcard/u.xml"], capture_output=True, text=True).stdout
    for n in ET.fromstring(xml).iter("node"):
        if n.get("text") == text or n.get("content-desc") == text:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
            tap((x1 + x2) / 2, (y1 + y2) / 2)
            time.sleep(wait)
            return True
    return False

def swipe(y1, y2, n=1):
    for _ in range(n):
        sh("input", "swipe", "540", str(y1), "540", str(y2), "250")
        time.sleep(0.25)

OLD = [131, 294, 457, 622, 785]
NEW = [161, 298, 434, 571, 707]
Y = 2285

def dock_to(mode, cur, target):
    xs = OLD if mode == "old" else NEW
    tap(450 if mode == "old" else xs[cur], Y)
    time.sleep(0.5)
    tap(xs[target], Y)
    time.sleep(0.8)

def stats():
    out = sh("dumpsys", "gfxinfo", PKG)
    g = lambda p: re.search(p, out).group(1)
    return dict(frames=int(g(r"Total frames rendered: (\d+)")), janky=g(r"Janky frames: (\d+ \([\d.]+%\))"),
                p50=g(r"50th percentile: (\d+)ms"), p90=g(r"90th percentile: (\d+)ms"), p95=g(r"95th percentile: (\d+)ms"), p99=g(r"99th percentile: (\d+)ms"))

def main():
    mode = sys.argv[1]
    sh("settings", "put", "global", "animator_duration_scale", "1")
    sh("settings", "put", "global", "transition_animation_scale", "1")
    sh("dumpsys", "gfxinfo", PKG, "reset")
    cur = 0
    for lap in range(2):
        for t in (1, 2, 3, 4, 0):
            dock_to(mode, cur, t); cur = t
            swipe(1700, 900); swipe(900, 1700)
    # sheets
    dock_to(mode, cur, 1); cur = 1
    for _ in range(2):
        tap(1010 * 1080 / 1080, 2400 - 84 * D - 22 * D)  # add button
        time.sleep(1.0); sh("input", "keyevent", "4"); time.sleep(0.4); sh("input", "keyevent", "4"); time.sleep(0.8)
    # full-screen routes from Me
    dock_to(mode, cur, 4); cur = 4
    for name in ("Alarms", "Habits", "Training"):
        if tap_text(name):
            time.sleep(0.8); swipe(1700, 800); sh("input", "keyevent", "4"); time.sleep(1.0)
    print(stats())

main()
