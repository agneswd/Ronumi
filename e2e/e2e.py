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
import os
import shlex
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
import json
import sqlite3

SERIAL = os.environ.get("ANDROID_SERIAL", "emulator-5554")
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
    out = subprocess.run(["adb", "-s", SERIAL, *args], capture_output=True, text=True)
    if check and out.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {out.stderr.strip()}")
    return out.stdout


def sh(cmd: str) -> str:
    return adb("shell", cmd, check=False)


def screen() -> ET.Element:
    raw = sh(f"am instrument -w -r {PKG}.e2e/{PKG}.e2e.E2eDriver")
    for line in raw.splitlines():
        if line.startswith("INSTRUMENTATION_RESULT: hierarchy="):
            return ET.fromstring(line.split("=", 1)[1])
    raise AssertionError("The Android test driver returned no accessibility tree: " + raw[-1000:])


def find(text: str, exact: bool = False):
    """The center of the last node whose text or description matches. Lists come after input fields."""
    match = None
    for node in screen().iter("node"):
        if node.get("visible") == "false":
            continue
        for attr in ("text", "content-desc"):
            value = node.get(attr, "")
            if (value.casefold() == text.casefold()) if exact else (text.lower() in value.lower()):
                x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", node.get("bounds")))
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
    print(f"tap {text}", flush=True)
    x, y = wait_for(text, timeout, exact)
    sh(f"input tap {x} {y}")
    time.sleep(0.8)


def scroll_up():
    width, height = map(int, re.findall(r"(\d+)x(\d+)", sh("wm size"))[-1])
    sh(f"input swipe {width // 2} {int(height * .72)} {width // 2} {int(height * .34)} 500")


def scroll_to(text: str, tries: int = 8):
    for _ in range(tries):
        if find(text):
            return
        scroll_up()
        time.sleep(0.6)
    raise AssertionError(f'"{text}" not found after scrolling')


def type_text(text: str):
    sh("input text " + shlex.quote(text.replace(" ", "%s")))
    time.sleep(0.5)
    # Close the keyboard so later taps do not land on keys. Back closes only the keyboard.
    sh("input keyevent KEYCODE_BACK")
    time.sleep(0.5)


def shot(name: str):
    global shots
    shots += 1
    path = RUN / f"{shots:02d}-{name}.png"
    path.write_bytes(subprocess.run(["adb", "-s", SERIAL, "exec-out", "screencap", "-p"], capture_output=True).stdout)
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
    adb("install", "-r", "-t", str(ROOT / "e2e-driver/build/outputs/apk/debug/e2e-driver-debug.apk"))
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
    sh(f"pm grant {CHROME} android.permission.POST_NOTIFICATIONS")
    sh("logcat -c")
    time.sleep(2)


# Checks. Each one starts from the state the one before it left.
#
# The UI reader preserves other accessibility services. Process-death checks still
# rebind the guard because Android can mark a force-stopped service as crashed.
# Enforcement checks use dumpsys and the block log instead of changing app state.

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


def onboarding():
    open_stillpoint("HOME")
    tap("Get started")
    wait_for("Hi! I'm Pebble")
    tap("Continue")
    wait_for("First, a few quick questions")
    tap("Continue")
    tap("Work", exact=True)
    tap("Continue")
    tap("1 hour")
    tap("Continue")
    tap("Social media")
    tap("Continue")
    tap("I'll set it later")
    tap("Sounds great")
    for title in ("Scrolling Shorts?", "Buzz, buzz, buzz?", "Want to quit early?", "Build a streak"):
        wait_for(title, exact=True)
        tap("Continue", exact=True)
    wait_for("Which apps steal your time?")
    tap("Continue", exact=True)
    wait_for("Usage access", exact=True)
    # Schema setup and process stops can disable the fixture's accessibility service.
    # Rebind it, then resume the app so its permission snapshot reads the new state.
    rebind()
    home()
    open_stillpoint("HOME")
    print("Accessibility services:", sh("settings get secure enabled_accessibility_services").strip(), flush=True)
    wait_for("All set! I can protect your focus now.")
    tap("Continue", exact=True)
    tap("Maybe later")
    wait_for("Daily quests")
    return shot("home-after-setup")


def today_screen():
    open_stillpoint("HOME")
    wait_for("Daily quests")
    if find("Finish setup"):
        raise AssertionError("Setup prompt shows although permissions are granted")
    return shot("home")


def device_workflow(scenario: str):
    sh(f"run-as {PKG} rm -f files/device-check.txt")
    sh(f"am broadcast -f 0x20 -n {PKG}/.StorageCheckReceiver --es scenario {scenario}")
    end = time.time() + (120 if scenario == "storage" else 30)
    while time.time() < end:
        result = sh(f"run-as {PKG} cat files/device-check.txt")
        if result.startswith(("PASS", "FAIL")):
            (RUN / f"{scenario}-check.txt").write_text(result)
            if not result.startswith("PASS"):
                raise AssertionError(result)
            return result.splitlines()[1:]
        time.sleep(0.3)
    raise AssertionError(f"{scenario} did not return a result")


def schema_upgrade():
    """Install the first database schema, then open it with the real upgraded app."""
    schema = json.loads((ROOT / "app/schemas/dev.agneswd.stillpoint.data.StillpointDatabase/1.json").read_text())["database"]
    fixture = RUN / "schema-1.db"
    now = int(time.time() * 1000)
    values = {
        "Settings": {"focusGoalMinutes": 90, "focusMinutes": 25, "focusRounds": 4, "breakMinutes": 5, "focusMode": "LISTED", "focusSound": "OFF"},
        "AppLimit": {"packageName": CLOCK, "minutesPerDay": 17, "mode": "GENTLE", "enabled": 1},
        "Schedule": {"id": 1, "name": "Preserve this plan", "startMinute": 1380, "endMinute": 360, "days": 127, "mode": "LISTED", "enabled": 1},
        "BlockedSite": {"domain": "example.com"},
        "FocusSession": {"id": 1, "startedAt": now - 1800000, "endedAt": now, "focusedMillis": 1800000, "completed": 1, "tag": "Preserve this history"},
        "HeldNotification": {"id": 1, "packageName": CLOCK, "title": "Preserve this message", "postedAt": now},
        "ActiveFocus": {"startedAt": now, "phase": "FOCUS", "phaseStartedAt": now, "phaseEndsAt": now + 1500000, "round": 1, "rounds": 4, "focusMinutes": 25, "breakMinutes": 5, "mode": "LISTED", "sound": "OFF", "tag": "Preserve this timer"},
    }
    with sqlite3.connect(fixture) as db:
        for entity in schema["entities"]:
            name = entity["tableName"]
            db.execute(entity["createSql"].replace("${TABLE_NAME}", name))
            columns = [f["columnName"] for f in entity["fields"]]
            row = [values[name].get(f["columnName"], "" if f["affinity"] == "TEXT" else 0) for f in entity["fields"]]
            db.execute(f"INSERT INTO {name} ({','.join(columns)}) VALUES ({','.join('?' for _ in row)})", row)
        db.execute("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        db.execute("INSERT INTO room_master_table VALUES (42, ?)", (schema["identityHash"],))
        db.execute("PRAGMA user_version=1")
    sh(f"pm clear {PKG}")
    adb("push", str(fixture), "/data/local/tmp/stillpoint-schema-1.db")
    sh(f"run-as {PKG} mkdir -p databases")
    sh(f"run-as {PKG} sh -c 'cat /data/local/tmp/stillpoint-schema-1.db > databases/stillpoint.db'")
    try:
        device_workflow("migration")
        return "migration-check.txt"
    finally:
        fresh_install()


def storage_workflow():
    home()
    details = device_workflow("storage")
    open_stillpoint("PROGRESS")
    wait_for("Progress", exact=True)
    shot("progress-after-storage-check")
    return f"storage-check.txt, {len(details)} device checks"


def focus_survives_restart():
    sh(f"am force-stop {PKG}")
    time.sleep(2)
    open_stillpoint("FOCUS")
    wait_for("Pause", exact=True)
    rebind()
    open_app(CONTACTS)
    wait_block("Contacts is blocked during focus")
    return shot("focus-after-process-restart")


def notification_workflow():
    home()
    device_workflow("notifications-start")
    sh(f"pm grant {PKG}.e2e android.permission.POST_NOTIFICATIONS")
    sh(f"am instrument -w -e notificationTitle 'First test' {PKG}.e2e/{PKG}.e2e.E2eDriver")
    time.sleep(2)
    sh(f"am instrument -w -e notificationTitle 'Updated test' {PKG}.e2e/{PKG}.e2e.E2eDriver")
    time.sleep(2)
    sh(f"am force-stop {PKG}")
    open_stillpoint("HOME")
    wait_for("Daily quests")
    device_workflow("notifications-check")
    return "notifications-check-check.txt"


def planned_focus_workflow():
    home()
    sh(f"appops set {PKG} SCHEDULE_EXACT_ALARM allow")
    device_workflow("plan-start")
    pid = sh(f"pidof {PKG}").strip()
    if pid:
        sh(f"run-as {PKG} kill -9 {pid}")
    end = time.time() + 80
    while time.time() < end:
        if "FocusService" in sh(f"dumpsys activity services {PKG}"):
            break
        time.sleep(1)
    else:
        raise AssertionError("Planned focus did not start through the Android alarm")
    open_stillpoint("FOCUS")
    wait_for("Device alarm check", exact=True)
    shot("focus-started-by-alarm")
    rebind()
    sh("input keyevent KEYCODE_SLEEP")
    end = time.time() + 80
    while time.time() < end:
        if "FocusService" not in sh(f"dumpsys activity services {PKG}"):
            break
        time.sleep(1)
    else:
        raise AssertionError("Focus service did not end while the screen was off")
    try:
        device_workflow("plan-check")
    finally:
        sh("input keyevent KEYCODE_WAKEUP")
        sh("wm dismiss-keyguard")
    return "plan-check-check.txt"


def set_up_blocks_in_ui():
    open_stillpoint("BLOCKS")
    tap("Add app limit")
    tap("Search")
    type_text("Clock")
    tap("Clock", exact=True)
    wait_for("Daily limit for Clock")
    for _ in range(6):
        tap("Decrease daily limit", exact=True)
    wait_for("1m", exact=True)
    shot("limit-dialog")
    tap("Save")
    name = shot("blocks")
    # The detail pages open from the "More blocks" group.
    scroll_to("Short videos")
    tap("Short videos", exact=True)
    tap("YouTube Shorts")
    sh("input keyevent BACK")
    tap("Websites", exact=True)
    tap("Block a site")
    type_text("example.com")
    tap("Add", exact=True)
    wait_for("example.com", exact=True)
    sh("input keyevent BACK")
    scroll_to("Strict mode")
    tap("Strict mode", exact=True)
    tap("Lock Stillpoint")
    sh("input keyevent BACK")
    rebind()
    return name


def start_focus_in_ui():
    open_stillpoint("HOME")
    tap("Start focus")
    wait_for("Focus setup")
    scroll_to("Blocked apps")
    tap("Blocked apps")
    tap("Search")
    type_text("Contacts")
    tap("Contacts", exact=True)
    tap("Done (")
    scroll_to("Lock the home screen")
    tap("Lock the home screen")
    tap("Start", exact=True)
    wait_for("Pause", exact=True)
    name = shot("focus-running")
    rebind()
    return name


def website_block():
    sh(f"am force-stop {CHROME}")
    sh(f"am start -a android.intent.action.VIEW -d https://example.com/some/page -p {CHROME} >/dev/null")
    if find("No thanks", exact=True):
        tap("No thanks", exact=True)
    rebind()
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


def pause_resume():
    open_stillpoint("FOCUS")
    tap("Pause", exact=True)
    wait_for("PAUSED", exact=True)
    rebind()
    open_app(CONTACTS)
    stays_open("contacts", 5)
    shot("paused-allows-contacts")
    open_stillpoint("FOCUS")
    tap("Resume", exact=True)
    rebind()
    open_app(CONTACTS)
    wait_block("Contacts is blocked during focus")
    return shot("resumed-blocks-contacts")


def ending_focus_frees_app():
    open_stillpoint("FOCUS")
    tap("GIVE UP", exact=True)
    tap("End session", exact=True)
    if find("Nice effort!"):
        tap("Continue", exact=True)
    open_stillpoint("HOME")
    wait_for("Start focus")
    rebind()
    open_app(CONTACTS)
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


def progression_workflow():
    device_workflow("progression-workflow")
    return "progression-workflow-check.txt"


CHECKS = [
    schema_upgrade,
    onboarding,
    today_screen,
    set_up_blocks_in_ui,
    website_block,
    shorts_block,
    start_focus_in_ui,
    focus_survives_restart,
    focus_blocks_app,
    home_lock_returns_to_focus,
    strict_mode_protects_settings,
    pause_resume,
    ending_focus_frees_app,
    gentle_limit,
    storage_workflow,
    progression_workflow,
    notification_workflow,
    planned_focus_workflow,
]


def main():
    global APK
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", help="comma-separated check names")
    parser.add_argument("--apk", type=pathlib.Path, help="APK to install, such as a signed release build")
    args = parser.parse_args()
    if args.apk:
        APK = args.apk.resolve()
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
            print("Failure window:", front(), flush=True)
            try:
                nodes = list(screen().iter("node"))
                visible = [node.get("text") or node.get("content-desc") for node in nodes if node.get("visible") != "false"]
                print("Failure visible text:", [text for text in visible if text][:80], flush=True)
                print("Failure accessibility node count:", len(nodes), flush=True)
            except Exception as diagnostic_error:
                print("Cannot inspect failure screen:", diagnostic_error, flush=True)
        print(f"{results[-1][1]} {check.__name__}: {results[-1][2]}", flush=True)
        if results[-1][1] == "FAIL" and check in (onboarding, set_up_blocks_in_ui, start_focus_in_ui):
            break

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
