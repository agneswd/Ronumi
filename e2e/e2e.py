#!/usr/bin/env python3
"""
End-to-end test for Stillpoint on a running emulator or phone.

It installs the debug APK, grants the permissions with adb, and drives the real UI.
Each check saves a screenshot. The run writes e2e/artifacts/<run>/report.md.

Usage: ./gradlew assembleGithubDebug && python3 e2e/e2e.py [--only name,name]
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
APK = ROOT / "app/build/outputs/apk/github/debug/app-github-debug.apk"
RUN = ROOT / "e2e/artifacts" / datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
CLOCK = "com.google.android.deskclock"
CHROME = "com.android.chrome"
YOUTUBE = "com.google.android.youtube"
# A public Short for the cold deep-link check. Replace it if YouTube removes the video.
SHORT_URL = "https://www.youtube.com/shorts/YzOtX_BpoME"
# View ids of the YouTube Shorts player. They match the guard's detector table.
REEL_IDS = ("reel_recycler", "reel_player_page_container", "reel_watch_player", "reel_watch_fragment_root", "reel_player_overlay", "shorts_container")

results: list[tuple[str, str, str]] = []
shots = 0


def adb(*args: str, check: bool = True) -> str:
    out = subprocess.run(["adb", "-s", SERIAL, *args], capture_output=True, text=True)
    if check and out.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {out.stderr.strip()}")
    return out.stdout


def sh(cmd: str) -> str:
    return adb("shell", cmd, check=False)


# Google images ship Google Contacts. Older images, such as Android 9, ship the AOSP app.
CONTACTS = "com.google.android.contacts" if "package:com.google.android.contacts" in sh("pm list packages com.google.android.contacts") \
    else "com.android.contacts"


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


def disable_guard():
    # Android 9 can retain a dead binding if an APK update or data reset interrupts a bind.
    # Wait for Android to release the connection before stopping or replacing the app.
    # The wait is best effort: after a crash, newer Android versions keep a restarting record
    # of the service. rebind() checks the real binding afterwards.
    sh("settings delete secure enabled_accessibility_services")
    end = time.time() + 10
    while "GuardService" in sh(f"dumpsys activity services {PKG}/.guard.GuardService") and time.time() < end:
        time.sleep(0.2)


def fresh_install():
    disable_guard()
    adb("install", "-r", "-t", str(APK))
    adb("install", "-r", "-t", str(ROOT / "e2e-driver/build/outputs/apk/debug/e2e-driver-debug.apk"))
    sh(f"pm clear {PKG}")
    sh(f"appops set {PKG} GET_USAGE_STATS allow")
    sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    sh(f"pm grant {CONTACTS} android.permission.POST_NOTIFICATIONS")
    sh(f"cmd notification allow_listener {PKG}/{PKG}.notify.HoldListener")
    # Skip the Chrome welcome pages so the address bar shows at once.
    sh(f"am set-debug-app --persistent {CHROME}")
    sh("echo 'chrome --disable-fre --no-default-browser-check --no-first-run' > /data/local/tmp/chrome-command-line")
    sh(f"am force-stop {CHROME}")
    sh(f"pm grant {CHROME} android.permission.POST_NOTIFICATIONS")
    sh("logcat -c")
    rebind()


# Checks. Each one starts from the state the one before it left.
#
# The UI reader preserves other accessibility services. Process-death checks still
# rebind the guard because Android can mark a force-stopped service as crashed.
# Enforcement checks use dumpsys and the block log instead of changing app state.

def rebind():
    # Android can keep a crashed service unbound although the setting lists it, and a slow
    # emulator can take long to bind after boot. Toggle the setting once more before failing.
    for attempt in range(2):
        disable_guard()
        sh(f"settings put secure enabled_accessibility_services {PKG}/{PKG}.guard.GuardService")
        sh("settings put secure accessibility_enabled 1")
        end = time.time() + 20
        while time.time() < end:
            if "label=Stillpoint" in sh("dumpsys accessibility"):
                return
            time.sleep(1)
    raise AssertionError("Android did not bind the guard. Reboot the emulator.")


def top_activity() -> str:
    return sh("dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity'").strip()


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


def device_workflow(scenario: str, extras: str = "", receiver: str = ".StorageCheckReceiver"):
    sh(f"run-as {PKG} rm -f files/device-check.txt")
    sh(f"am broadcast -f 0x20 -n {PKG}/{receiver} --es scenario {scenario} {extras}")
    end = time.time() + (120 if scenario in ("storage", "backup") else 30)
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
    disable_guard()
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


def active_focus_snapshot(name: str):
    """Read the current row through the debug receiver without stopping the session."""
    lines = device_workflow("focus-state")
    result = json.loads("\n".join(lines))
    (RUN / f"{name}.json").write_text(json.dumps(result, indent=2) + "\n")
    return result



def active_focus_controls():
    before = active_focus_snapshot("focus-before")
    assert before.get("startedAt", 0) > 0, "Expected one active session"
    open_stillpoint("HOME")
    wait_for("Return to focus", exact=True)
    assert not find("Start focus", exact=True), "Home offers a second session"
    shot("active-focus-home")
    tap("Return to focus", exact=True)
    tap("Pause", exact=True)
    open_stillpoint("HOME")
    wait_for("Return to focus", exact=True)
    assert not find("Start focus", exact=True), "Paused focus offers a second session"
    tap("Return to focus", exact=True)
    wait_for("Resume", exact=True)
    tap("Resume", exact=True)
    assert active_focus_snapshot("focus-after") == before, "Returning changed the active session"
    return shot("same-focus-resumed")


def plan_intent_from_other_app():
    """Another app cannot start a planned focus. The plan notification still can."""
    home()
    plan = device_workflow("plan-intent")[0]
    # The shell plays the other app. MainActivity is exported, so it receives this intent.
    sh(f"am start -n {PKG}/.ui.MainActivity --es tab FOCUS --el planId {plan} >/dev/null")
    time.sleep(3)
    assert active_focus_snapshot("focus-after-foreign-intent") == {}, "Another app started a planned focus"
    shot("foreign-plan-intent-ignored")
    # Android lets an app open its own activity only from the foreground, as a notification tap does.
    open_stillpoint("HOME")
    device_workflow("plan-notification", f"--el planId {plan}")
    end = time.time() + 15
    while active_focus_snapshot("focus-after-plan-notification").get("tag") != "Plan intent check":
        assert time.time() < end, "The plan notification did not start its focus"
        time.sleep(1)
    name = shot("plan-notification-started-focus")
    open_stillpoint("FOCUS")
    tap("GIVE UP", exact=True)
    tap("End session", exact=True)
    home()
    # The plan has no days, which backups reject. Later checks export a backup.
    device_workflow("plan-intent-cleanup")
    return name


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


def browser_recovery(pkg: str):
    sh("logcat -c")
    sh(f"am force-stop {pkg}")
    # Bind the guard first, like a phone that has it on all day. It must see the page load.
    rebind()
    sh(f"am start -a android.intent.action.VIEW -d https://example.com/some/page -p {pkg} >/dev/null")
    if find("No thanks", exact=True):
        tap("No thanks", exact=True)
    wait_block("example.com is blocked", timeout=30)
    name = shot(f"site-blocked-in-{pkg}")
    tap("Back to the browser", exact=True)
    wait_top(pkg)
    stays_open(pkg, 3)
    shot(f"{pkg}-after-site-block")
    tree = screen()
    (RUN / f"{pkg}-recovered.xml").write_text(ET.tostring(tree, encoding="unicode"))
    visible = " ".join((n.get("text", "") + " " + n.get("content-desc", "")) for n in tree.iter("node"))
    assert "Example Domain" not in visible, "The blocked page is still visible"
    assert not any(n.get("visible") == "true" and n.get("resource-id", "").endswith(
        ("/omnibox_results_container", "/omnibox_suggestions_dropdown", "ADDRESSBAR_SEARCH_BOX"))
        for n in tree.iter("node")), "The browser is still editing instead of showing the cleared page"
    home()
    open_app(pkg)
    stays_open(pkg, 3)
    return name


def website_block():
    return browser_recovery(CHROME)


def guard_reconnect():
    """The guard turns on while a blocked page is open. It must block without another window change."""
    home()
    sh("settings delete secure enabled_accessibility_services")
    sh(f"am force-stop {CHROME}")
    sh(f"am start -a android.intent.action.VIEW -d https://example.com/reconnect -p {CHROME} >/dev/null")
    time.sleep(4)
    sh("logcat -c")
    sh(f"settings put secure enabled_accessibility_services {PKG}/{PKG}.guard.GuardService")
    wait_block("example.com is blocked", timeout=10)
    name = shot("blocked-after-reconnect")
    tap("Back to the browser", exact=True)
    wait_top(CHROME)
    home()
    return name


def browser_matrix():
    packages = ("com.brave.browser", "com.brave.browser_beta", "org.mozilla.firefox")
    installed = [pkg for pkg in packages if sh(f"pm path {pkg}").startswith("package:")]
    if not installed:
        raise Skip("Install Brave, Brave Beta, or Firefox and complete its welcome pages")
    for pkg in installed:
        browser_recovery(pkg)
    return ", ".join(installed)


class Skip(Exception):
    """The device cannot run this check. The report says why."""


def require_shorts():
    """YouTube added Shorts in version 16. Older builds, such as the Android 9 image's, cannot run these checks."""
    version = re.search(r"versionName=(\d+)", sh(f"dumpsys package {YOUTUBE}"))
    if not version or int(version.group(1)) < 16:
        raise Skip(f"YouTube {version.group(1) if version else 'is not installed'} on this device has no Shorts")


def youtube_state(name: str):
    """Saves the YouTube tree. Returns the visible nodes, and whether the Shorts player or tab shows."""
    tree = screen()
    (RUN / f"{name}.xml").write_text(ET.tostring(tree, encoding="unicode"))
    nodes = [n for n in tree.iter("node") if n.get("visible") == "true"]
    player = any(n.get("resource-id", "").split("/")[-1] in REEL_IDS for n in nodes)
    tab = any(n.get("selected") == "true" and n.get("content-desc", "").startswith("Shorts") for n in nodes)
    return nodes, player, tab


def leave_shorts_block(name: str):
    """Taps the block's return button. YouTube must stay in front without the Shorts feed."""
    tap("Back to the app", exact=True)
    wait_top(YOUTUBE)
    stays_open(YOUTUBE, 3)
    shot(name)
    nodes, player, tab = youtube_state(name)
    assert not player, "The Shorts player is still on screen"
    assert not tab, "The Shorts tab is still selected"
    return nodes


def shorts_block():
    require_shorts()
    sh("logcat -c")
    rebind()
    open_app(YOUTUBE)
    try:
        tap("Shorts", exact=True)
        wait_block("YouTube Shorts is blocked", timeout=30)
    except AssertionError:
        if "NewVersionAvailable" in sh("dumpsys activity activities | grep -E 'youtube'"):
            sh("input keyevent KEYCODE_BACK")
            raise Skip("YouTube on this device forces an update and opens no video")
        raise
    name = shot("shorts-blocked")
    leave_shorts_block("youtube-after-shorts-tab")
    return name


def shorts_from_search():
    """A Short opened from search returns to the same results, without a YouTube restart."""
    require_shorts()
    query = "cats shorts"
    sh("logcat -c")
    sh(f"am force-stop {YOUTUBE}")
    rebind()
    open_app(YOUTUBE)
    tap("Search", exact=True)
    sh("input text " + shlex.quote(query.replace(" ", "%s")))
    sh("input keyevent KEYCODE_ENTER")
    wait_for("play Short", timeout=20)
    shot("youtube-search-results")
    pid = sh(f"pidof {YOUTUBE}").strip()
    tap("play Short")
    wait_block("YouTube Shorts is blocked", timeout=20)
    name = shot("short-from-search-blocked")
    nodes = leave_shorts_block("youtube-search-after-block")
    texts = [n.get("text", "") for n in nodes]
    assert query in texts, f"The search for {query!r} is gone: {[t for t in texts if t][:20]}"
    assert any("play Short" in n.get("content-desc", "") for n in nodes), "The search results are gone"
    assert sh(f"pidof {YOUTUBE}").strip() == pid, "YouTube restarted"
    return name


def shorts_deep_link():
    """A Short opened from another app has no page under it. Recovery must stay in YouTube."""
    require_shorts()
    sh("logcat -c")
    sh(f"am force-stop {YOUTUBE}")
    rebind()
    sh(f"am start -a android.intent.action.VIEW -d {SHORT_URL} -p {YOUTUBE} >/dev/null")
    wait_block("YouTube Shorts is blocked", timeout=30)
    name = shot("deep-link-short-blocked")
    leave_shorts_block("youtube-after-deep-link-block")
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
    # The block goes home, and the home lock then brings focus back. Wait for it, or it covers the Blocks tab.
    wait_for("Pause", exact=True)
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


def plus_workflow():
    """Drive playDebug through real entitlement policy without a Play Store installation."""
    sh(f"am force-stop {PKG}")
    sh(f"run-as {PKG} mkdir -p no_backup")
    sh(f"run-as {PKG} touch no_backup/plus-fake-store")
    open_stillpoint("settings")
    evidence = []

    def expect(mode, entitlement, status="IDLE"):
        lines = device_workflow(mode, receiver=".plus.PlusCheckReceiver")
        assert lines[0] == entitlement, (mode, lines)
        assert lines[1] == status, (mode, lines)
        assert lines[2] == "$4.99", (mode, lines)
        assert lines[4] == f"allFeatures={str(entitlement == 'UNLOCKED').lower()}", lines
        if mode == "unlocked":
            assert lines[3] == "acknowledged=true", lines
        evidence.append({"mode": mode, "state": lines})

    expect("locked", "LOCKED")
    expect("canceled", "LOCKED", "CANCELED")
    expect("purchase-error", "LOCKED", "ERROR")
    expect("already-owned", "UNLOCKED")
    expect("refunded", "LOCKED")
    expect("purchase-pending", "PENDING")
    expect("pending-offline", "PENDING", "ERROR")
    expect("complete-pending", "UNLOCKED")
    expect("unlocked", "UNLOCKED")
    expect("offline", "UNLOCKED", "ERROR")
    sh(f"am force-stop {PKG}")
    open_stillpoint("settings")
    expect("read", "UNLOCKED", "ERROR")
    expect("unlocked", "UNLOCKED")
    expect("refund-on-foreground", "UNLOCKED")
    home()
    open_stillpoint("HOME")
    expect("read", "LOCKED")
    sh(f"am force-stop {PKG}")
    open_stillpoint("settings")
    expect("read", "LOCKED")
    expect("backup", "UNLOCKED")
    (RUN / "plus-states.json").write_text(json.dumps(evidence, indent=2) + "\n")
    return "plus-states.json; " + shot("plus-workflow")

CHECKS = [
    schema_upgrade,
    onboarding,
    today_screen,
    set_up_blocks_in_ui,
    website_block,
    guard_reconnect,
    browser_matrix,
    shorts_block,
    shorts_from_search,
    shorts_deep_link,
    start_focus_in_ui,
    active_focus_controls,
    focus_survives_restart,
    focus_blocks_app,
    home_lock_returns_to_focus,
    strict_mode_protects_settings,
    pause_resume,
    ending_focus_frees_app,
    plan_intent_from_other_app,
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
    parser.add_argument("--flavor", choices=("github", "play"), default="github")
    parser.add_argument("--apk", type=pathlib.Path, help="APK to install, such as a signed release build")
    args = parser.parse_args()
    APK = ROOT / f"app/build/outputs/apk/{args.flavor}/debug/app-{args.flavor}-debug.apk"
    if args.apk:
        APK = args.apk.resolve()
    available = CHECKS if args.flavor == "github" else [plus_workflow]
    requested = set(args.only.split(",")) if args.only else {c.__name__ for c in available}
    unknown = requested - {c.__name__ for c in available}
    if unknown:
        parser.error("Unknown checks for this flavor: " + ", ".join(sorted(unknown)))
    checks = [c for c in available if c.__name__ in requested]

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
