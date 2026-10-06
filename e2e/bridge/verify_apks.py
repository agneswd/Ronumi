#!/usr/bin/env python3
"""Verify the bridge boundary in packaged APKs, including minified releases."""
import json
import os
import pathlib
import subprocess
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
SDK = pathlib.Path(os.environ.get("ANDROID_HOME", pathlib.Path.home() / "Android/Sdk"))
ANALYZER = SDK / "cmdline-tools/latest/bin/apkanalyzer"
A = "{http://schemas.android.com/apk/res/android}"
PERMISSION = "dev.agneswd.stillpoint.permission.MIGRATE"
AUTHORITY = "dev.agneswd.stillpoint.migrate"


def analyze(*args):
    return subprocess.check_output([str(ANALYZER), *map(str, args)], text=True)


def main():
    out = ROOT / "e2e/artifacts/bridge-apks"
    out.mkdir(parents=True, exist_ok=True)
    reports = []
    for flavor in ("github", "play"):
        for kind in ("debug", "release"):
            variant = flavor + kind.title()
            directory = ROOT / f"app/build/outputs/apk/{flavor}/{kind}"
            metadata = json.loads((directory / "output-metadata.json").read_text())
            apk = directory / metadata["elements"][0]["outputFile"]
            manifest_text = analyze("manifest", "print", apk)
            manifest = ET.fromstring(manifest_text)
            provider = next((p for p in manifest.findall("application/provider") if p.get(A + "authorities") == AUTHORITY), None)
            permission = next((p for p in manifest.findall("permission") if p.get(A + "name") == PERMISSION), None)
            (out / f"{variant}-manifest.xml").write_text(manifest_text)
            if flavor == "github":
                assert provider is not None and permission is not None, variant
                assert provider.get(A + "exported") == "true", variant
                assert provider.get(A + "permission") == PERMISSION, variant
                assert provider.get(A + "grantUriPermissions") == "false", variant
                assert permission.get(A + "protectionLevel") in ("signature", "0x00000002", "0x2"), variant
            else:
                assert provider is None and permission is None, variant
                classes = analyze("dex", "packages", "--defined-only", apk)
                assert "MigrationProvider" not in classes and "MigrationNoticeKt" not in classes, variant
                if kind == "release":
                    mapping = (ROOT / f"app/build/outputs/mapping/{variant}/mapping.txt").read_text()
                    assert "MigrationProvider" not in mapping and "MigrationNoticeKt" not in mapping, variant
                resources = analyze("resources", "names", "--type", "string", "--config", "default", apk)
                assert "bridge_notice" not in resources and "bridge_get_ronumi" not in resources, variant
            if kind == "release":
                assert "BridgeFixture" not in manifest_text, variant
            reports.append(f"PASS {variant}: expected bridge provider, permission, and notice isolation")
    (out / "report.txt").write_text("\n".join(reports) + "\n")
    print("\n".join(reports))


if __name__ == "__main__":
    main()
