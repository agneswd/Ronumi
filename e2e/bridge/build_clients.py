#!/usr/bin/env python3
"""Build tiny external callers with the SDK. Outputs and test keys stay in artifacts."""
import os
import pathlib
import subprocess
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "e2e/artifacts/bridge-clients"
SDK = pathlib.Path(os.environ.get("ANDROID_HOME", pathlib.Path.home() / "Android/Sdk"))
TOOLS = SDK / "build-tools/37.0.0"
ANDROID = SDK / "platforms/android-37/android.jar"
if not ANDROID.exists():
    ANDROID = SDK / "platforms/android-37.0/android.jar"


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    classes = OUT / "classes"
    classes.mkdir(exist_ok=True)
    run("javac", "--release", "8", "-cp", ANDROID, "-d", classes, ROOT / "e2e/bridge/Client.java")
    run(TOOLS / "d8", "--lib", ANDROID, "--min-api", "28", "--output", OUT, *sorted(classes.rglob("*.class")))
    different_key = OUT / "different.keystore"
    if not different_key.exists():
        run("keytool", "-genkeypair", "-keystore", different_key, "-storepass", "android", "-keypass", "android",
            "-alias", "androiddebugkey", "-keyalg", "RSA", "-validity", "30", "-dname", "CN=Bridge test only")
    for kind, key in [("allowed", pathlib.Path.home() / ".android/debug.keystore"), ("denied", different_key)]:
        manifest = OUT / f"{kind}.xml"
        manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.agneswd.bridge.{kind}">
    <uses-sdk android:minSdkVersion="28" android:targetSdkVersion="36" />
    <uses-permission android:name="dev.agneswd.stillpoint.permission.MIGRATE" />
    <queries><provider android:authorities="dev.agneswd.stillpoint.migrate" /><package android:name="dev.agneswd.bridge.denied" /></queries>
    <application android:debuggable="true" android:label="Bridge test">
        <activity android:name="bridge.client.Client" android:exported="true" />
    </application>
</manifest>''')
        unsigned = OUT / f"{kind}-unsigned.apk"
        run(TOOLS / "aapt2", "link", "-I", ANDROID, "--manifest", manifest, "-o", unsigned)
        with zipfile.ZipFile(unsigned, "a") as apk:
            apk.write(OUT / "classes.dex", "classes.dex")
        run(TOOLS / "zipalign", "-f", "4", unsigned, OUT / f"{kind}-aligned.apk")
        run(TOOLS / "apksigner", "sign", "--ks", key, "--ks-pass", "pass:android", "--key-pass", "pass:android",
            "--out", OUT / f"{kind}.apk", OUT / f"{kind}-aligned.apk")
    print(OUT)


if __name__ == "__main__":
    main()
