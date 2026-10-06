#!/usr/bin/env python3
"""Inspect shipped permissions and dex classes after assembling all four variants."""
import json
import os
import pathlib
import subprocess
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
BUILD = ROOT / "app/build"
SDK = pathlib.Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
ANALYZER = SDK / "cmdline-tools/latest/bin/apkanalyzer"
ANDROID = "{http://schemas.android.com/apk/res/android}"
UPDATER_PERMISSIONS = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.REQUEST_INSTALL_PACKAGES",
}
# Play keeps INTERNET and ACCESS_NETWORK_STATE: the Billing library bundles a diagnostics library that needs them.
PLAY_FORBIDDEN_PERMISSIONS = {"android.permission.REQUEST_INSTALL_PACKAGES"}
MIGRATE = "dev.agneswd.stillpoint.permission.MIGRATE"
BILLING = "com.android.vending.BILLING"


def analyze(*args):
    return subprocess.check_output([str(ANALYZER), *map(str, args)], text=True)


def permissions(manifest):
    return {node.attrib[ANDROID + "name"] for node in manifest.findall("uses-permission")}


def has_text(apk, text):
    raw = apk.read_bytes()
    return text.encode() in raw or text.encode("utf-16le") in raw


def inspect(flavor, build_type):
    variant = flavor + build_type.title()
    directory = BUILD / f"outputs/apk/{flavor}/{build_type}"
    metadata = json.loads((directory / "output-metadata.json").read_text())
    apk = directory / metadata["elements"][0]["outputFile"]
    manifest = ET.fromstring(analyze("manifest", "print", apk))
    actual = permissions(manifest)
    merged = ET.parse(BUILD / f"intermediates/merged_manifests/{variant}/process{variant[0].upper() + variant[1:]}Manifest/AndroidManifest.xml")
    assert actual == permissions(merged.getroot()), f"{variant}: merged and packaged permissions differ"
    assert manifest.attrib["package"] == "dev.agneswd.ronumi", variant

    args = ["dex", "packages", "--defined-only"]
    if build_type == "release":
        args += ["--proguard-mappings", BUILD / f"outputs/mapping/{variant}/mapping.txt"]
    classes = analyze(*args, apk)
    report = BUILD / "reports/distributions"
    report.mkdir(parents=True, exist_ok=True)
    (report / f"{variant}-classes.txt").write_text(classes)
    (report / f"{variant}-manifest.xml").write_text(ET.tostring(manifest, encoding="unicode"))

    providers = manifest.findall("application/provider")
    authorities = " ".join(node.attrib.get(ANDROID + "authorities", "") for node in providers)
    assert "stillpoint" not in authorities, f"{variant}: exported the previous provider"
    printed = ET.tostring(manifest, encoding="unicode")
    if flavor == "play":
        assert not actual & PLAY_FORBIDDEN_PERMISSIONS, f"{variant}: updater permissions {actual & PLAY_FORBIDDEN_PERMISSIONS}"
        assert MIGRATE not in actual, variant
        assert "stillpoint" not in printed, variant
        assert BILLING in actual, variant
        for forbidden in ("dev.agneswd.ronumi.update", "UpdatesActivity", "UpdateSettingsKt", "LegacyImport", "MigrationProvider"):
            assert forbidden not in classes, f"{variant}: isolated class {forbidden}"
        for text in ("Welcome back. Bring your progress", "dev.agneswd.stillpoint", "MigrationProvider"):
            assert not has_text(apk, text), f"{variant}: contains {text}"
    else:
        assert UPDATER_PERMISSIONS <= actual, variant
        assert MIGRATE in actual, variant
        assert "dev.agneswd.stillpoint" in printed, variant
        assert "dev.agneswd.stillpoint.migrate" in printed, variant
        assert BILLING not in actual, variant
        for forbidden in ("com.android.billingclient", "com.google.android.gms.internal.play_billing", "MigrationProvider"):
            assert forbidden not in classes, f"{variant}: isolated class {forbidden}"
        assert has_text(apk, "Welcome back. Bring your progress"), variant

    for name in ("FakeStore", "PlusCheckReceiver"):
        assert (name in classes) == (variant == "playDebug"), f"{variant}: incorrect presence of {name}"
    if build_type == "release":
        # Check the full mapping too, so an inlined fake cannot evade the defined-class check.
        mapping = (BUILD / f"outputs/mapping/{variant}/mapping.txt").read_text()
        assert "FakeStore" not in mapping and "PlusCheckReceiver" not in mapping, variant
    return {"variant": variant, "apk": str(apk.relative_to(ROOT)),
            "versionCode": manifest.attrib[ANDROID + "versionCode"], "permissions": sorted(actual)}


def main():
    results = [inspect(flavor, build_type) for flavor in ("github", "play") for build_type in ("debug", "release")]
    assert len({result["versionCode"] for result in results}) == 1, "Distribution version codes differ"
    target = BUILD / "reports/distributions/results.json"
    target.write_text(json.dumps(results, indent=2) + "\n")
    print(f"Verified manifest permissions and dex isolation for all four variants: {target}")


if __name__ == "__main__":
    main()
