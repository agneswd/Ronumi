#!/usr/bin/env python3
"""Run device checks and print failure diagnostics from the disposable CI device."""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[2] / "e2e"))
import e2e


try:
    e2e.main()
except SystemExit as result:
    if result.code:
        print("Crash buffer:", flush=True)
        print(e2e.adb("logcat", "-d", "-b", "crash", check=False), flush=True)
        print("Foreground window:", flush=True)
        print(e2e.front(), flush=True)
        print("Accessibility state:", e2e.sh("dumpsys accessibility"), flush=True)
        print("Device time:", e2e.sh("date"), flush=True)
        alarm_lines = e2e.sh("dumpsys alarm").splitlines()
        selected = set()
        for index, line in enumerate(alarm_lines):
            if e2e.PKG in line:
                selected.update(range(max(0, index - 4), min(len(alarm_lines), index + 9)))
        print("Stillpoint alarms:", flush=True)
        print("\n".join(alarm_lines[index] for index in sorted(selected)) or "No matching alarms", flush=True)
        print("Recent device warnings:", flush=True)
        print(e2e.sh("logcat -d -t 200 -v brief '*:W'"), flush=True)
        try:
            texts = [node.get("text") or node.get("content-desc") for node in e2e.screen().iter("node")
                     if node.get("visible") != "false"]
            print("Visible text:", [text for text in texts if text][:80], flush=True)
        except Exception as error:
            print("Cannot read the failure screen:", error, flush=True)
    raise
