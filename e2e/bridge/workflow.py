"""Run the signed-client bridge checks through the standard E2E driver."""
import json
import os
import subprocess
import time


def run(e):
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
        e.open_stillpoint("HOME")
        e.wait_for("Daily quests")
        e.shot("bridge-before")
        e.sh(f"am force-stop {e.PKG}")
        e.adb("install", "-r", "-t", str(e.APK))
    e.open_stillpoint("HOME")
    e.wait_for("Stillpoint is now Ronumi.")
    after_shot = e.shot("bridge-after")
    e.tap("GET RONUMI", exact=True)
    activities = e.sh("dumpsys activity activities")
    assert "https://github.com/agneswd/Stillpoint/releases/latest" in activities, "Wrong download destination"
    (e.RUN / "bridge-download-intent.txt").write_text(activities)
    e.open_stillpoint("HOME")
    e.tap("Dismiss", exact=True)
    e.gone("Stillpoint is now Ronumi.")
    e.sh(f"am force-stop {e.PKG}")
    e.open_stillpoint("HOME")
    e.wait_for("Daily quests")
    assert not e.find("Stillpoint is now Ronumi."), "Dismissal was lost after process restart"
    e.shot("bridge-dismissed-after-restart")
    return after_shot
