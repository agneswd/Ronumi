#!/usr/bin/env python3
"""
End-to-end test for Stillpoint on a running emulator or phone.

It installs the debug APK, grants the permissions with adb, and drives the real UI.
Each check saves a screenshot. The run writes e2e/artifacts/<run>/report.md.

Usage: ./gradlew assembleDebug && python3 e2e/e2e.py [--only name,name]
"""
import argparse
import datetime
import pathlib
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "dev.agneswd.stillpoint"
ROOT = pathlib.Path(__file__).resolve().parent.parent
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
RUN = ROOT / "e2e/artifacts" / datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
CLOCK = "com.google.android.deskclock"
CONTACTS = "com.google.android.contacts"
CHROME = "com.android.chrome"
YOUTUBE = "com.google.android.youtube"
# A public YouTube Short. Any Short works. The guard looks at the player, not the video.
SHORT_URL = "https://www.youtube.com/shorts/aqz-KE-bpKQ"

results: list[tuple[str, str, str]] = []
shots = 0


def adb(*args: str, check: bool = True) -> str:
    out = subprocess.run(["adb", *args], capture_output=True, text=True)
    if check and out.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {out.stderr.strip()}")
    return out.stdout


def sh(cmd: str) -> str:
    return adb("shell", cmd, check=False)


def screen() -> ET.Element:
    for _ in range(3):
        sh("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1")
        raw = adb("exec-out", "cat", "/sdcard/ui.xml", check=False)
        if raw.strip().startswith("<?xml"):
            return ET.fromstring(raw)
        time.sleep(0.5)
    return ET.fromstring("<hierarchy/>")


def find(text: str, exact: bool = False):
    """The center of the last node whose text or description matches. Lists come after input fields."""
    match = None
    for node in screen().iter("node"):
        for attr in ("text", "content-desc"):
            value = node.get(attr, "")
            if (value == text) if exact else (text.lower() in value.lower()):
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
                match = (x1 + x2) // 2, (y1 + y2) // 2
    return match


def wait_for(text: str, timeout: float = 15, exact: bool = False):
    end = time.time() + timeout
    while time.time() < end:
        point = find(text, exact)
        if point:
            return point
        time.sleep(0.7)
    raise AssertionError(f'"{text}" did not show within {timeout:.0f}s')


def gone(text: str, timeout: float = 10):
    end = time.time() + timeout
    while time.time() < end:
        if not find(text):
            return
        time.sleep(0.7)
    raise AssertionError(f'"{text}" is still on screen')


def tap(text: str, timeout: float = 15, exact: bool = False):
    x, y = wait_for(text, timeout, exact)
    sh(f"input tap {x} {y}")
    time.sleep(0.8)


def scroll_to(text: str, tries: int = 8):
    for _ in range(tries):
        if find(text):
            return
        sh("input swipe 540 1700 540 700 300")
        time.sleep(0.6)
    raise AssertionError(f'"{text}" not found after scrolling')


def type_text(text: str):
    sh(f"input text '{text}'")
    time.sleep(0.5)
    # Close the keyboard so later taps do not land on keys. Back closes only the keyboard.
    sh("input keyevent KEYCODE_BACK")
    time.sleep(0.5)


def shot(name: str):
    global shots
    shots += 1
    path = RUN / f"{shots:02d}-{name}.png"
    path.write_bytes(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
    return path.name


def front() -> str:
    out = sh("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
    return out


def open_app(pkg: str):
    sh(f"monkey -p {pkg} -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1")
    time.sleep(2)


def open_stillpoint(tab: str):
    sh(f"am start -n {PKG}/.ui.MainActivity --es tab {tab} >/dev/null")
    time.sleep(1.5)


def home():
    sh("input keyevent KEYCODE_HOME")
    time.sleep(1)


def fresh_install():
    adb("install", "-r", "-t", str(APK))
    sh(f"pm clear {PKG}")
    sh(f"appops set {PKG} GET_USAGE_STATS allow")
    sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    # pm clear kills the bound service, and Android marks it as crashed. Turning it off and on rebinds it.
    sh("settings put secure enabled_accessibility_services ''")
    time.sleep(1)
    sh(f"settings put secure enabled_accessibility_services {PKG}/{PKG}.guard.GuardService")
    sh("settings put secure accessibility_enabled 1")
    sh(f"cmd notification allow_listener {PKG}/{PKG}.notify.HoldListener")
    # Skip the Chrome welcome pages so the address bar shows at once.
    sh(f"am set-debug-app --persistent {CHROME}")
    sh("echo 'chrome --disable-fre --no-default-browser-check --no-first-run' > /data/local/tmp/chrome-command-line")
    sh(f"am force-stop {CHROME}")
    sh("logcat -c")
    time.sleep(2)


# Checks. Each one starts from the state the one before it left.
#
# A UI dump connects a second accessibility client, and Android pauses other
# accessibility services while it runs. So the checks set things up through the UI
# first, call rebind(), and then watch enforcement with dumpsys and the block log only.

def rebind():
    sh("settings put secure enabled_accessibility_services ''")
    time.sleep(1)
    sh(f"settings put secure enabled_accessibility_services {PKG}/{PKG}.guard.GuardService")
    time.sleep(3)


def top_activity() -> str:
    return sh("dumpsys activity activities | grep topResumedActivity").strip()


def wait_top(fragment: str, timeout: float = 15):
    end = time.time() + timeout
    while time.time() < end:
        if fragment in top_activity():
            return
        time.sleep(0.7)
    raise AssertionError(f"{fragment} is not in front: {top_activity()}")


def wait_block(title: str, timeout: float = 15):
    end = time.time() + timeout
    while time.time() < end:
        if title in sh("logcat -d -s Stillpoint:I"):
            wait_top(".guard.BlockActivity")
            time.sleep(1)
            return
        time.sleep(0.7)
    raise AssertionError(f'No block "{title}" within {timeout:.0f}s. In front: {top_activity()}')


def stays_open(fragment: str, seconds: float):
    end = time.time() + seconds
    while time.time() < end:
        if fragment not in top_activity():
            raise AssertionError(f"{fragment} lost the front: {top_activity()}")
        time.sleep(1)


def today_screen():
    open_stillpoint("TODAY")
    wait_for("Screen time today")
    wait_for("Most used")
    if find("Finish setup"):
        raise AssertionError("The setup prompt shows although adb granted the permissions")
    return shot("today")


def set_up_blocks_in_ui():
    open_stillpoint("BLOCKS")
    tap("Add app limit")
    tap("Search")
    type_text("Clock")
    tap("Clock", exact=True)
    wait_for("Limit for Clock")
    for _ in range(6):  # 30m down to the 1m minimum
        tap("-", exact=True)
    wait_for("1m", exact=True)
    shot("limit-dialog")
    tap("Save")

    scroll_to("YouTube Shorts")
    tap("YouTube Shorts")
    scroll_to("Block a site")
    tap("Block a site")
    type_text("example.com")
    tap("Add", exact=True)
    scroll_to("Lock Stillpoint while blocks run")
    tap("Lock Stillpoint while blocks run")
    shot("blocks-tab")
    rebind()
    sh("input swipe 540 700 540 1700 200")
    sh("input swipe 540 700 540 1700 200")
    sh("input swipe 540 700 540 1700 200")
    return shot("blocks-tab-top")


def start_focus_in_ui():
    open_stillpoint("FOCUS")
    tap("Blocked apps")
    tap("Search")
    type_text("Contacts")
    tap("Contacts", exact=True)
    tap("Done (1)")
    scroll_to("Lock the home screen")
    tap("Lock the home screen")
    scroll_to("Sound")
    tap("Brown", exact=True)
    shot("focus-options")
    sh("input swipe 540 700 540 1700 200")
    sh("input swipe 540 700 540 1700 200")
    tap("What are you working on?")
    type_text("Thesis")
    tap("Start focus")
    wait_for("Round 1 of 4")
    name = shot("focus-running")
    rebind()
    return name


def website_block():
    sh(f"am force-stop {CHROME}")
    sh(f"am start -a android.intent.action.VIEW -d https://example.com/some/page -p {CHROME} >/dev/null")
    wait_block("example.com is blocked", timeout=30)
    name = shot("site-blocked-in-chrome")
    sh("input keyevent KEYCODE_BACK")
    return name


class Skip(Exception):
    """The device cannot run this check. The report says why."""


def shorts_block():
    sh(f"am start -W -a android.intent.action.VIEW -d {SHORT_URL} -p {YOUTUBE} >/dev/null")
    try:
        wait_block("YouTube Shorts is blocked", timeout=30)
    except AssertionError:
        if "NewVersionAvailable" in sh("dumpsys activity activities | grep -E 'youtube'"):
            home()
            raise Skip("YouTube on this device forces an update and opens no video")
        raise
    name = shot("shorts-blocked")
    sh("input keyevent KEYCODE_BACK")
    return name


def focus_blocks_app():
    open_app(CONTACTS)
    wait_block("Contacts is blocked during focus")
    return shot("focus-blocks-contacts")


def home_lock_returns_to_focus():
    # Leaving the block screen goes home, and the home lock brings the focus screen back.
    sh("input keyevent KEYCODE_BACK")
    wait_top(".ui.MainActivity")
    home()
    wait_top(".ui.MainActivity")
    return shot("home-lock-returns-to-focus")


def strict_mode_protects_settings():
    sh(f"am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:{PKG} >/dev/null")
    wait_block("Stillpoint settings are locked")
    name = shot("protection-blocks-app-info")
    sh("input keyevent KEYCODE_BACK")
    open_stillpoint("BLOCKS")
    wait_for("Blocks are locked")
    shot("blocks-tab-locked")
    return name


def ending_focus_frees_app():
    open_stillpoint("FOCUS")
    tap("End session")
    # Sessions of a minute or more ask what they were for.
    if find("You focused for"):
        shot("session-notes")
        tap("Save", exact=True)
    wait_for("Start focus")
    rebind()
    open_app(CONTACTS)
    wait_top("contacts")
    stays_open("contacts", 5)
    name = shot("contacts-open-after-focus")
    home()
    return name


def gentle_limit():
    open_app(CLOCK)
    # One minute of use, then the guard tick (every 20 s) must catch it.
    wait_block("is used up", timeout=110)
    shot("limit-reached")
    time.sleep(11)
    tap("Open for 5 more minutes")
    rebind()
    wait_top("deskclock")
    # Longer than one guard tick, so a broken pass would show the block again.
    stays_open("deskclock", 25)
    name = shot("limit-extra-time")
    home()
    return name


CHECKS = [
    today_screen,
    set_up_blocks_in_ui,
    website_block,
    shorts_block,
    start_focus_in_ui,
    focus_blocks_app,
    home_lock_returns_to_focus,
    strict_mode_protects_settings,
    ending_focus_frees_app,
    gentle_limit,
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", help="comma-separated check names")
    args = parser.parse_args()
    checks = [c for c in CHECKS if not args.only or c.__name__ in args.only.split(",")]

    RUN.mkdir(parents=True, exist_ok=True)
    fresh_install()
    for check in checks:
        started = time.time()
        try:
            detail = check()
            results.append((check.__name__, "PASS", f"{detail} ({time.time() - started:.0f}s)"))
        except Skip as reason:
            results.append((check.__name__, "SKIP", str(reason)))
        except Exception as error:  # noqa: BLE001 - one failed check must not stop the others
            results.append((check.__name__, "FAIL", f"{error} [{shot('fail-' + check.__name__)}]"))
        print(f"{results[-1][1]} {check.__name__}: {results[-1][2]}", flush=True)

    crashes = adb("logcat", "-d", "-b", "crash", check=False).strip()
    (RUN / "crash.log").write_text(crashes or "No crashes.\n")
    lines = [f"# Stillpoint E2E {RUN.name}", "", "| Check | Result | Detail |", "|---|---|---|"]
    lines += [f"| {n} | {r} | {d} |" for n, r, d in results]
    lines += ["", "Crash buffer: " + ("empty" if not crashes else "see crash.log")]
    (RUN / "report.md").write_text("\n".join(lines) + "\n")
    print(f"\nArtifacts: {RUN}")
    failed = [r for r in results if r[1] == "FAIL"] or ([("crash", "FAIL", "")] if crashes else [])
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
