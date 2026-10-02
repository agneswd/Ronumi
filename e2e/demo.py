#!/usr/bin/env python3
"""Record the app and its audio with scrcpy. This resets app data.

Use a disposable device, or back up its app data first. The rewards chapter
uses sample history and completes a real one-minute timer.
"""
import argparse
import json
import os
import pathlib
import signal
import subprocess
import time

import e2e

OUT = e2e.ROOT / "e2e/artifacts/demo.mp4"
IMAGES = None
STARTED = 0.0
CHAPTERS = []


def chapter(title):
    CHAPTERS.append({"title": title, "seconds": round(time.monotonic() - STARTED, 2)})
    print(title, flush=True)


def screenshot(name):
    if IMAGES:
        IMAGES.mkdir(parents=True, exist_ok=True)
        (IMAGES / (name + ".png")).write_bytes(subprocess.check_output(
            ["adb", "-s", e2e.SERIAL, "exec-out", "screencap", "-p"]))


def pause(seconds: float = 2.5):
    time.sleep(seconds)


def scroll():
    e2e.scroll_up()
    pause(1.5)


def back():
    e2e.sh("input keyevent BACK")
    pause(1.5)


def tour():
    chapter("Setup with Pebble")
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
    screenshot("home")
    scroll()
    pause()

    chapter("Plans and distraction controls")
    e2e.open_stillpoint("PLANNER")
    pause()
    e2e.open_stillpoint("BLOCKS")
    pause()
    screenshot("blocks")
    e2e.tap("Short videos", exact=True)
    pause(1.5)
    e2e.tap("YouTube Shorts")
    pause(1.5)
    back()
    e2e.tap("Websites", exact=True)
    pause()
    back()
    e2e.scroll_to("Strict mode")
    e2e.tap("Strict mode", exact=True)
    pause()
    back()
    e2e.open_stillpoint("PROGRESS")
    pause()
    scroll()
    scroll()

    chapter("Start focus, block an app, pause and resume")
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
    screenshot("focus")
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

    chapter("Offline focus sounds")
    for sound in ["White", "Pink", "Brown", "Rain", "Waves"]:
        e2e.open_stillpoint("FOCUS")
        e2e.wait_for("Focus setup")
        if sound in ("White", "Pink"):
            e2e.tap("Deep space" if sound == "White" else "Rain", exact=True)
        e2e.scroll_to("Sound")
        e2e.scroll_to(sound)
        e2e.tap(sound, exact=True)
        e2e.tap("Start", exact=True)
        e2e.wait_for("Pause", exact=True)
        chapter(sound)
        pause(10 if sound == "Waves" else 6)
        e2e.tap("GIVE UP", exact=True)
        e2e.tap("End session", exact=True)
        pause(2)
        if e2e.find("Nice effort!"):
            e2e.tap("Continue", exact=True)
        e2e.wait_for("Start focus")
        pause(2)

    finish_tour()


def finish_tour():
    chapter("Rewards with sample history and a real one-minute timer")
    e2e.device_workflow("demo-rewards")
    e2e.open_stillpoint("FOCUS")
    e2e.wait_for("Focus setup")
    e2e.tap("Start", exact=True)
    e2e.wait_for("Pause", exact=True)
    pause(63)
    pause(6)
    screenshot("rewards")
    e2e.tap("Continue", exact=True)
    e2e.open_stillpoint("PROGRESS")
    pause(3)
    screenshot("progress")

    chapter("Pebble wardrobe and daily quests with sample history")
    e2e.device_workflow("demo-wardrobe")
    e2e.open_stillpoint("HOME")
    pause(3)
    screenshot("home")
    e2e.tap("Pebble wardrobe", exact=True)
    pause(2)
    e2e.tap("Garden overalls", exact=True)
    pause(2)
    if e2e.find("Wear", exact=True):
        e2e.tap("Wear", exact=True)
    e2e.tap("Pebble", exact=True)
    pause(2)
    screenshot("wardrobe")
    e2e.tap("Hats", exact=True)
    e2e.tap("Soft beanie", exact=True)
    pause(2)
    e2e.tap("Wear", exact=True)
    pause(2)
    e2e.sh("cmd uimode night yes")
    pause(3)
    screenshot("wardrobe-dark")
    back()
    e2e.open_stillpoint("PROGRESS")
    pause(3)
    screenshot("progress-dark")
    e2e.scroll_to("Badges")
    pause(3)
    e2e.tap("Earned", exact=True)
    pause(3)
    screenshot("badges")
    e2e.sh("cmd uimode night no")
    pause(2)

    chapter("Home screen widgets")
    e2e.sh(f"appwidget grantbind --package {e2e.PKG}")
    # A new task, so the widgets show in front of the app.
    e2e.sh(f"am start -f 0x10008000 -n {e2e.PKG}/.WidgetPreviewActivity")
    pause(4)


def main():
    global OUT, IMAGES, STARTED
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scrcpy", default="scrcpy")
    parser.add_argument("--output", type=pathlib.Path, default=OUT)
    parser.add_argument("--images", type=pathlib.Path, help="Save fresh promotional screenshots here")
    parser.add_argument("--resume-rewards", action="store_true", help="Keep device data and resume an interrupted rewards chapter")
    args = parser.parse_args()
    OUT, IMAGES = args.output.resolve(), args.images
    OUT.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([args.scrcpy, "--version"], check=True, stdout=subprocess.DEVNULL)
    e2e.RUN.mkdir(parents=True, exist_ok=True)
    if not args.resume_rewards:
        e2e.fresh_install()
    previous_touches = e2e.sh("settings get system show_touches").strip()
    e2e.sh("settings put system show_touches 1")
    env = os.environ.copy()
    env["ADB"] = subprocess.check_output(["which", "adb"], text=True).strip()
    recorder = subprocess.Popen([args.scrcpy, "--serial=" + e2e.SERIAL,
        "--no-video-playback", "--no-control", "--require-audio", "--audio-source=output",
        "--audio-codec=aac", "--record=" + str(OUT)], env=env)
    STARTED = time.monotonic()
    time.sleep(2)
    try:
        if recorder.poll() is not None:
            raise RuntimeError("Audio recorder stopped before the tour")
        if args.resume_rewards:
            finish_tour()
        else:
            tour()
        if recorder.poll() is not None:
            raise RuntimeError("Audio recorder stopped during the tour")
    finally:
        if recorder.poll() is None:
            recorder.send_signal(signal.SIGINT)
        recorder.wait(timeout=20)
        e2e.sh("settings put system show_touches " + previous_touches)
        OUT.with_suffix(".chapters.json").write_text(json.dumps(CHAPTERS, indent=2) + "\n")
    if recorder.returncode != 0:
        raise RuntimeError("The recorder did not finish cleanly")
    print(OUT)


if __name__ == "__main__":
    main()
