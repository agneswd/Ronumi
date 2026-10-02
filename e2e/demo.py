#!/usr/bin/env python3
"""Record the installed app after a full E2E run. Save e2e/artifacts/demo.mp4."""
import subprocess
import time

import e2e

OUT = e2e.ROOT / "e2e/artifacts/demo.mp4"
REMOTE = "/sdcard/stillpoint-demo.mp4"


def main():
    OUT.parent.mkdir(parents=True, exist_ok=True)
    previous_touches = e2e.sh("settings get system show_touches").strip()
    e2e.sh("settings put system show_touches 1")
    recorder = subprocess.Popen(["adb", "-s", e2e.SERIAL, "shell", "screenrecord", "--bit-rate", "4000000", "--time-limit", "120", REMOTE])
    time.sleep(1)
    try:
        for tab in ("HOME", "PLANNER", "BLOCKS", "PROGRESS"):
            e2e.open_stillpoint(tab)
            time.sleep(2)
        e2e.open_stillpoint("HOME")
        e2e.tap("Start focus")
        e2e.wait_for("Focus setup")
        time.sleep(2)
        e2e.tap("Start", exact=True)
        e2e.wait_for("Pause", exact=True)
        time.sleep(3)
        e2e.rebind()
        e2e.open_app(e2e.CONTACTS)
        e2e.wait_block("Contacts is blocked during focus")
        time.sleep(3)
        e2e.open_stillpoint("FOCUS")
        e2e.tap("Pause", exact=True)
        time.sleep(2)
        e2e.tap("Resume", exact=True)
        time.sleep(2)
        e2e.tap("GIVE UP", exact=True)
        e2e.tap("End session", exact=True)
        if e2e.find("Nice effort!"):
            e2e.tap("Continue", exact=True)
        e2e.open_stillpoint("HOME")
        time.sleep(3)
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
