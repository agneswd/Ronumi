#!/usr/bin/env python3
"""Run device checks and print failure diagnostics from the disposable CI device."""
import pathlib
import re
import sys
from functools import wraps

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[2] / "e2e"))
import e2e

notification_help = e2e.sh("cmd notification help")
if not re.search(r"(?m)^\s*post(?:\s|$)", notification_help):
    @wraps(e2e.notification_workflow)
    def notification_unavailable():
        raise e2e.Skip("The device has no shell command for posting test notifications")

    e2e.CHECKS = [notification_unavailable if check is e2e.notification_workflow else check for check in e2e.CHECKS]
    print("Notification posting is unavailable. Device commands:\n" + notification_help, flush=True)

try:
    e2e.main()
except SystemExit as result:
    if result.code:
        print("Crash buffer:", flush=True)
        print(e2e.adb("logcat", "-d", "-b", "crash", check=False), flush=True)
        print("Foreground window:", flush=True)
        print(e2e.front(), flush=True)
        try:
            texts = [node.get("text") or node.get("content-desc") for node in e2e.screen().iter("node")
                     if node.get("visible") != "false"]
            print("Visible text:", [text for text in texts if text][:80], flush=True)
        except Exception as error:
            print("Cannot read the failure screen:", error, flush=True)
    raise
