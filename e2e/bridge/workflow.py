"""Run the signed-client bridge checks through the standard E2E driver."""
import json
import os
import re
import subprocess
import time

# Android 14 prints "Registered <count> jobs:". Some builds print "Registered jobs:".
_REGISTERED = re.compile(r"^Registered(?: \d+)? jobs:$")
_REQUIRED = ("Pending queue:", "Active jobs:")


def job_sections(dump: str) -> str:
    """Keep registered, pending, and active JobScheduler rows.

    History and the component catalog stay out. A cancelled job or a declared
    service must not look like a scheduled job.
    """
    lines = dump.splitlines()
    keep = []
    capture = False
    saw = set()
    for line in lines:
        stripped = line.strip()
        registered = _REGISTERED.fullmatch(stripped) is not None
        if registered or stripped in _REQUIRED:
            capture = True
            saw.add("Registered jobs:" if registered else stripped)
            keep.append(line)
            continue
        if capture and line and not line[0].isspace() and stripped.endswith(":"):
            capture = False
        if capture:
            keep.append(line)
    missing = [name for name in ("Registered jobs:", *_REQUIRED) if name not in saw]
    if missing:
        preview = "\n".join(lines[:80])
        raise AssertionError("dumpsys jobscheduler missed " + ", ".join(missing) + ":\n" + preview)
    return "\n".join(keep) + "\n"


def settings_update_section(e):
    e.open_ronumi("PROGRESS")
    e.tap("Settings", exact=True)
    e.wait_for("Daily goal")
    e.scroll_to("GET RONUMI", tries=14)
    tree = "\n".join(
        (node.get("text") or "") + "\n" + (node.get("content-desc") or "")
        for node in e.screen().iter("node")
    )
    (e.RUN / "settings-update-text.txt").write_text(tree)
    for banned in ("Check for updates", "App updates", "Check for updates automatically"):
        if banned in tree:
            raise AssertionError(f"Retired updater text is still on screen: {banned}")
    if "Stillpoint is now Ronumi." not in tree or "GET RONUMI" not in tree:
        raise AssertionError("Settings does not show the Ronumi notice")
    shot = e.shot("settings-update")
    e.tap("GET RONUMI", exact=True)
    activities = e.sh("dumpsys activity activities")
    if "https://github.com/agneswd/Stillpoint/releases/tag/v1.0.0" not in activities:
        raise AssertionError("Settings opened the wrong page")
    (e.RUN / "settings-download-intent.txt").write_text(activities)
    e.sh(f"am force-stop {e.PKG}")
    e.open_ronumi("HOME")
    e.wait_for("Daily quests")
    sections = job_sections(e.sh("dumpsys jobscheduler"))
    (e.RUN / "jobscheduler-after-start.txt").write_text(sections)
    if "UpdateJob" in sections or "/64021" in sections:
        raise AssertionError("Update job is scheduled after start:\n" + sections[:2000])
    plant = e.sh(f"run-as {e.PKG} cat files/update-job-plant.txt")
    (e.RUN / "update-job-plant.txt").write_text(plant)
    if not plant.startswith("scheduled=1"):
        raise AssertionError("The debug fixture did not plant job 64021:\n" + plant)
    return shot


def run(e):
    # fresh_install hides the notice for the other checks. This check needs it.
    e.sh(f"am force-stop {e.PKG}")
    e.sh(f"run-as {e.PKG} rm -f shared_prefs/ronumi_bridge.xml")
    e.device_workflow("demo-wardrobe")
    e.device_workflow("backup", "--ez seed true", receiver=".BridgeFixture")
    e.home()
    e.sh(f"am force-stop {e.PKG}")
    for label, kind in (("denied", "denied"), ("allowed", "allowed"), ("denied-after-grant", "denied")):
        package = f"dev.agneswd.bridge.{kind}"
        if label != "denied-after-grant":
            e.adb("install", "-r", str(e.ROOT / f"e2e/artifacts/bridge-clients/{kind}.apk"))
        e.sh(f"run-as {package} rm -f files/result.txt")
        e.sh(f"am start -W -n {package}/bridge.client.Client --ez denied {str(kind == 'denied').lower()}")
        deadline = time.time() + 60
        while time.time() < deadline:
            result = e.sh(f"run-as {package} cat files/result.txt")
            if result.startswith(("PASS", "FAIL")):
                (e.RUN / f"bridge-{label}.txt").write_text(result)
                assert "FAIL" not in result, result
                break
            time.sleep(0.5)
        else:
            raise AssertionError(f"{kind} client did not finish")
    exported = json.loads(e.sh("run-as dev.agneswd.bridge.allowed cat files/bridge.json"))
    reference = json.loads(e.sh(f"run-as {e.PKG} cat files/bridge-reference.json"))
    assert exported == reference, "Provider data differs from decrypted backup"
    assert exported["version"] == 2
    assert len(exported["sessions"]) == 28
    assert exported["settings"]["pebbleItems"]
    assert all(exported[name] for name in ("limits", "schedules", "sites", "usageDays"))
    assert "BRIDGE_PRIVATE" not in json.dumps(exported), "Held notification text leaked"
    for name, value in (("bridge.json", exported), ("bridge-reference.json", reference)):
        (e.RUN / name).write_text(json.dumps(value, indent=2) + "\n")
    encrypted = subprocess.run(["adb", "-s", e.SERIAL, "exec-out", "run-as", e.PKG, "cat", "files/bridge-reference.stillpoint"], capture_output=True, check=True).stdout
    (e.RUN / "bridge-reference.stillpoint").write_bytes(encrypted)
    # A second encrypted export proves rejected writes left the database unchanged.
    e.device_workflow("backup", receiver=".BridgeFixture")
    after = json.loads(e.sh(f"run-as {e.PKG} cat files/bridge-reference.json"))
    assert after == reference, "Rejected operations changed the database"
    (e.RUN / "bridge-comparison.txt").write_text("PASS: provider JSON equals the decrypted and normalized Backup document.\nPASS: rejected operations left backup data unchanged.\n")
    baseline = os.environ.get("STILLPOINT_BRIDGE_BASELINE_APK")
    if baseline:
        e.sh(f"am force-stop {e.PKG}")
        e.adb("install", "-r", "-t", baseline)
        e.open_ronumi("HOME")
        e.wait_for("Daily quests")
        e.shot("bridge-before")
        e.sh(f"am force-stop {e.PKG}")
        e.adb("install", "-r", "-t", str(e.APK))
    e.open_ronumi("HOME")
    e.wait_for("Stillpoint is now Ronumi.")
    after_shot = e.shot("bridge-after")
    e.tap("GET RONUMI", exact=True)
    activities = e.sh("dumpsys activity activities")
    assert "https://github.com/agneswd/Stillpoint/releases/tag/v1.0.0" in activities, "Wrong download destination"
    (e.RUN / "bridge-download-intent.txt").write_text(activities)
    e.open_ronumi("HOME")
    e.tap("Dismiss", exact=True)
    e.gone("Stillpoint is now Ronumi.")
    e.sh(f"am force-stop {e.PKG}")
    e.open_ronumi("HOME")
    e.wait_for("Daily quests")
    assert not e.find("Stillpoint is now Ronumi."), "Dismissal was lost after process restart"
    e.shot("bridge-dismissed-after-restart")
    settings_shot = settings_update_section(e)
    return after_shot + "; " + settings_shot
