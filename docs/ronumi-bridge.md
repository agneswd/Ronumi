# Ronumi data bridge

The final Stillpoint GitHub build exports data to the new Ronumi package.
The Play build has no migration provider, permission declaration, or Home notice.
The old app keeps its data after every read. The provider cannot import or delete data.

## Provider contract

| Field | Value |
| --- | --- |
| Authority | `dev.agneswd.stillpoint.migrate` |
| Permission | `dev.agneswd.stillpoint.permission.MIGRATE` |
| Protection level | `signature` |
| URI | `content://dev.agneswd.stillpoint.migrate/backup` |
| MIME type | `application/json` |
| File mode | `r` |
| Encoding | UTF-8 |
| Format version | `2`, explicitly included as the top-level `version` field |
| Maximum stream size | 16 MiB |

The importer must request the permission and use the same signing certificate as Stillpoint.
For Android package visibility, declare the provider authority in a `<queries>` entry.

```xml
<uses-permission android:name="dev.agneswd.stillpoint.permission.MIGRATE" />
<queries>
    <provider android:authorities="dev.agneswd.stillpoint.migrate" />
</queries>
```

Open the exact URI with `ContentResolver.openInputStream` on a background dispatcher.
The result is a pipe. Do not seek or expect a known file length.
Read until EOF and close the stream. Limit the input to 16 MiB before parsing.
A missing provider means the old GitHub app is absent or needs an update.
A permission failure means that Android did not grant the signature permission.
A truncated or invalid document must not change destination data.

The importer must decode the existing `Backup` model and validate it before its restore transaction.
It must reject unsupported versions. It must not pass this plaintext stream to the password-backup importer.
The encrypted file importer continues to accept only password-protected backups.

The provider permits file, asset-file, and compatible typed-asset reads of this one URI.
`getType` returns the MIME type for a permitted caller and the exact URI.
Unknown paths, query parameters, fragments, and trailing slashes are rejected.
Write modes are rejected, including `rw`. Query, insert, update, delete, bulk insert, batch, custom call,
canonicalization, and refresh operations are unsupported. The provider grants no URI permissions.

## Data

The stream uses the serializer from `data/Backup.kt`. It is not a separate migration schema.
It includes default-valued fields so the version is explicit. Encrypted backups can omit those same defaults.
Compare decoded documents, not their raw bytes.

One Room transaction reads settings, limits, schedules, sites, focus sessions, and daily usage records.
Settings and focus history preserve wardrobe, quest rules, and the inputs used to calculate badges and levels.
Held notification text, active sessions, and temporary pass rows are excluded, as in ordinary backups.
No plaintext export file is written. Closing a reader early does not change the database.

The provider authorizes any app with the signature permission, rather than one destination package name.
Release migration therefore requires matching release signing keys. Debug-key checks cannot prove release-key compatibility.
The rename task must keep this old authority and permission as importer constants.
It must not ship this export declaration under the new package.

## Repeatable device check

Use the assigned disposable emulator. Run all builds before starting it.
Use the repository brief's JDK, worker, memory, and emulator limits.

```sh
python3 e2e/bridge/build_clients.py
./gradlew assembleGithubDebug :e2e-driver:assembleDebug --init-script e2e/bridge/fixture.gradle --no-configuration-cache --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m
./gradlew --stop
/home/elias/Projects/DevSoftware/Stillpoint-wt/emu.sh start pixel_7_api34 5554
ANDROID_SERIAL=emulator-5554 python3 e2e/e2e.py --only bridge_workflow
/home/elias/Projects/DevSoftware/Stillpoint-wt/emu.sh stop 5554
```

The opt-in fixture adds a DUMP-protected receiver only to this GitHub debug build.
Normal builds and every release build exclude it. The two client APKs stay in ignored artifacts.
The check seeds the demo wardrobe history, rules, usage, and a held-message sentinel.
It saves the provider document, encrypted reference, decrypted reference, client results, screenshot, and crash log.
The separate-key client must fail with `SecurityException`. Rejected write operations must leave backup data unchanged.

Run `python3 e2e/bridge/verify_apks.py` after assembling all four normal variants.
It checks the packaged permission and provider, plus the absence of bridge code and text in Play.
Set `STILLPOINT_BRIDGE_BASELINE_APK` to an APK built from the target commit.
The workflow then captures matching before and after Home screenshots.

## Notice link

The Home button opens `https://github.com/agneswd/Stillpoint/releases/tag/v1.0.0`.
This bridge release stays the latest GitHub release, so older apps can still update to it.
Ronumi 1.0.0 is published at the same time and is not the latest release.
After the repository is renamed to Ronumi, GitHub redirects that URL in the browser.

The workflow also checks the v1.0.0 release URL and persistent dismissal.
