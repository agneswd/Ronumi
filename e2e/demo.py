#!/usr/bin/env python3
"""Record a tour of a fresh debug install. Save e2e/artifacts/demo.mp4.

It clears the app data on the device, like the E2E run does.
"""
import subprocess
import time

import e2e

OUT = e2e.ROOT / "e2e/artifacts/demo.mp4"
REMOTE = "/sdcard/stillpoint-demo.mp4"


def pause(seconds: float = 2.5):
    time.sleep(seconds)


def scroll():
    e2e.sh("input swipe 540 1700 540 900 600")
    pause(1.5)


def back():
    e2e.sh("input keyevent BACK")
    pause(1.5)


def tour():
    # First launch: Pebble asks a few questions and builds a plan.
    e2e.open_stillpoint("HOME")
    pause()
    e2e.tap("Get started")
    pause(2)
    e2e.tap("Continue")
    pause(2)
    e2e.tap("Continue")
    pause(1.5)
    e2e.tap("Study", exact=True)
    pause(1)
    e2e.tap("Continue")
    pause(1.5)
    e2e.tap("1 hour")
    pause(1)
    e2e.tap("Continue")
    pause(1.5)
    e2e.tap("Shorts and Reels")
    e2e.tap("Social media")
    pause(1)
    e2e.tap("Continue")
    pause(1.5)
    e2e.tap("Evening")
    pause(1)
    e2e.tap("Continue")
    pause(2.5)
    e2e.tap("Sounds great")
    for _ in range(4):
        pause(2.5)
        e2e.tap("Continue")
    pause(2)
    e2e.tap("Continue")
    pause(1.5)
    e2e.tap("Continue")
    pause(1.5)
    e2e.tap("Maybe later")
    e2e.wait_for("Daily quests")
    pause(3)
    scroll()
    pause()

    # The tabs and the Blocks pages.
    e2e.open_stillpoint("PLANNER")
    pause()
    e2e.open_stillpoint("BLOCKS")
    pause()
    e2e.tap("Short videos", exact=True)
    pause(1.5)
    e2e.tap("YouTube Shorts")
    pause(1.5)
    back()
    e2e.tap("Websites", exact=True)
    pause()
    back()
    e2e.tap("Strict mode", exact=True)
    pause()
    back()
    e2e.open_stillpoint("PROGRESS")
    pause()
    scroll()
    scroll()

    # A focus session that blocks Contacts.
    e2e.open_stillpoint("HOME")
    e2e.tap("Start focus")
    e2e.wait_for("Focus setup")
    pause(1.5)
    e2e.tap("Dawn", exact=True)
    pause(1.5)
    e2e.scroll_to("Blocked apps")
    e2e.tap("Blocked apps")
    e2e.tap("Search")
    e2e.type_text("Contacts")
    e2e.tap("Contacts", exact=True)
    e2e.tap("Done (")
    pause(1)
    e2e.tap("Start", exact=True)
    e2e.wait_for("Pause", exact=True)
    pause(4)
    e2e.rebind()
    e2e.open_app(e2e.CONTACTS)
    e2e.wait_block("Contacts is blocked during focus")
    pause(4)
    e2e.open_stillpoint("FOCUS")
    pause(1.5)
    e2e.tap("Pause", exact=True)
    pause(2)
    e2e.tap("Resume", exact=True)
    pause(2)
    e2e.tap("GIVE UP", exact=True)
    pause(2)
    e2e.tap("End session", exact=True)
    pause(1)
    if e2e.find("Nice effort!"):
        e2e.tap("Continue", exact=True)
    # Ending a session brings the app to the front once more. Wait for that first.
    pause(4)

    # The home screen widgets, hosted by the debug preview activity.
    e2e.sh(f"appwidget grantbind --package {e2e.PKG}")
    # A new task, so the widgets show in front of the app.
    e2e.sh(f"am start -f 0x10008000 -n {e2e.PKG}/.WidgetPreviewActivity")
    pause(4)


def main():
    OUT.parent.mkdir(parents=True, exist_ok=True)
    e2e.fresh_install()
    previous_touches = e2e.sh("settings get system show_touches").strip()
    e2e.sh("settings put system show_touches 1")
    recorder = subprocess.Popen(["adb", "-s", e2e.SERIAL, "shell", "screenrecord", "--bit-rate", "4000000", "--time-limit", "180", REMOTE])
    time.sleep(1)
    try:
        tour()
    finally:
        for line in e2e.sh("ps -A -o PID,ARGS").splitlines():
            if line.strip().endswith(REMOTE):
                e2e.sh(f"kill -INT {int(line.split()[0])}")
        recorder.wait(timeout=20)
        e2e.adb("pull", REMOTE, str(OUT))
        e2e.sh("settings put system show_touches " + previous_touches)
        e2e.sh("rm -f " + REMOTE)
    print(OUT)


if __name__ == "__main__":
    main()
