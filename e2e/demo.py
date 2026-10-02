#!/usr/bin/env python3
"""
Records a short demo video of Stillpoint on the emulator. It uses the data that e2e.py left,
so run e2e.py first. Output: e2e/artifacts/demo.mp4

Usage: python3 e2e/demo.py
"""
import subprocess
import time

import e2e
from e2e import CHROME, CONTACTS, PKG, ROOT, home, open_stillpoint, rebind, sh, tap, type_text, wait_block, wait_for, wait_top

OUT = ROOT / "e2e/artifacts/demo.mp4"


def pause(seconds: float = 2.0):
    time.sleep(seconds)


def scroll(down: bool = True, times: int = 1):
    for _ in range(times):
        sh("input swipe 540 1700 540 900 600" if down else "input swipe 540 900 540 1700 600")
        pause(0.8)


def main():
    sh("cmd uimode night yes")
    sh("settings put system show_touches 1")
    sh(f"am force-stop {PKG}")
    rebind()
    home()
    recorder = subprocess.Popen(["adb", "shell", "screenrecord", "--bit-rate", "8000000", "--time-limit", "180", "/sdcard/demo.mp4"])
    pause(1)
    try:
        open_stillpoint("TODAY")
        pause(3)
        scroll()
        pause(2)
        tap("Blocks", exact=True)
        pause(2)
        scroll(times=3)
        pause(1)
        scroll(down=False, times=3)
        tap("Focus", exact=True)
        pause(1.5)
        scroll(times=2)
        scroll(down=False, times=2)
        tap("What are you working on?")
        type_text("Chapter 3")
        tap("Start focus")
        wait_for("Round 1 of 4")
        pause(3)
        rebind()

        e2e.open_app(CONTACTS)
        wait_block("Contacts is blocked during focus")
        pause(3)
        sh("input keyevent KEYCODE_BACK")  # goes home, and the home lock returns to focus
        wait_top(".ui.MainActivity")
        pause(2)

        sh(f"am force-stop {CHROME}")
        sh(f"am start -a android.intent.action.VIEW -d https://example.com -p {CHROME} >/dev/null")
        wait_block("example.com is blocked", timeout=30)
        pause(3)
        sh("input keyevent KEYCODE_BACK")
        wait_top(".ui.MainActivity")
        pause(1)

        sh(f"am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:{PKG} >/dev/null")
        wait_block("Stillpoint settings are locked")
        pause(3)
        sh("input keyevent KEYCODE_BACK")
        wait_top(".ui.MainActivity")
        pause(1)

        tap("Blocks", exact=True)
        pause(2.5)
        tap("Focus", exact=True)
        pause(1)
        tap("End session")
        pause(1.5)
        if e2e.find("You focused for"):
            tap("Save", exact=True)
        tap("Today", exact=True)
        pause(3)
    finally:
        sh("pkill -INT screenrecord")
        recorder.wait(timeout=20)
        pause(2)
        subprocess.run(["adb", "pull", "/sdcard/demo.mp4", str(OUT)], check=True)
        sh("settings put system show_touches 0")
    print(OUT)


if __name__ == "__main__":
    main()
