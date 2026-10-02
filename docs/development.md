# Build and test Stillpoint

## Build

Use JDK 17 and Android SDK 37. Set `JAVA_HOME` for your installation.
Set `sdk.dir` in an untracked `local.properties`, or set `ANDROID_HOME`.

```sh
./gradlew assembleDebug assembleRelease lintRelease
```

Debug APKs use the Android debug key. Release APKs are unsigned unless these environment variables are set:

```text
STILLPOINT_KEYSTORE
STILLPOINT_STORE_PASSWORD
STILLPOINT_KEY_ALIAS
STILLPOINT_KEY_PASSWORD
```

Keep the release keystore and passwords outside the repository. Keep a private backup of both.

## Device checks

Use a disposable emulator. The test resets Stillpoint's app data and configures emulator permissions.
A complete blocking run needs Chrome, Clock, Contacts, and a compatible YouTube installation.

```sh
./gradlew assembleDebug
ANDROID_SERIAL=emulator-5554 python3 e2e/e2e.py
```

Each run saves screenshots, a report, storage-check results, and the crash log in `e2e/artifacts/<run>/`.
The Android reader module reads animated Compose screens without waiting for all animations to stop.
The reader preserves accessibility services. The test reconnects the guard after deliberate process stops.
CI checks first launch, schema upgrades, backup restore, notifications, and planned focus on Android 9 and Android 16.
It also builds the minified release and runs release lint.

## Progression and appearance checks

Run the focused storage check on a disposable emulator:

```sh
ANDROID_SERIAL=emulator-5554 python3 e2e/e2e.py --only onboarding,progression_workflow
```

This checks wardrobe fields in Room and backups, old backup defaults, quest completion rules,
and rejection of locked, unknown, or duplicate-slot items.
The outer test command resets app data even when you select one workflow.

Database version 4 adds `Settings.pebbleItems` and `FocusSession.questVersion` with defaults.
Old sessions retain their original quest rules and XP. New session days use four quests.
If a day already contains an old session, that day keeps the original three quests.

Check Settings > Appearance with System, Light, and Dark. Forced modes must ignore the phone theme.
System mode must follow it without resetting navigation or scroll position.
Check locked item previews, item removal, large text, keyboard dismissal, and scroll restoration on the device.
See [behavior coverage](../e2e/coverage.md) for completed checks and gaps.

## Audio and demo capture

The app bundles CC0 sound recordings. Imports run on the development machine, never inside the app.
See [UI audio sources](audio-sources.md) and [focus audio sources](focus-audio-sources.md) for sources and rebuild commands.
Both importers require Python 3 and ffmpeg.

Use scrcpy with audio support to capture Android output:

```sh
python3 e2e/demo.py --scrcpy /path/to/scrcpy --output /path/to/demo.mp4
```

The demo resets app data and uses debug-only sample history for rewards.
Back up an existing device first. Keep the recording and chapter file outside the repository unless publication is approved.
Do not upload `e2e/artifacts/`. Capture promotional screenshots separately and remove private data before publication.

## Schema 5

Migration 4 to 5 adds columns without replacing existing data:

| Table | Field | Default |
| --- | --- | --- |
| Settings | `notifyFocusEvents` | `true` |
| Settings | `notifyPlanReminders` | `true` |
| Settings | `notifyInboxSummaries` | `true` |
| Settings | `petTapCount` | `0` |
| Settings | `themeMode` | `SYSTEM` |
| Settings | `autoUpdateChecks` | `true` |
| Schedule | `icon` | `auto` |

Backup validation checks theme and icon IDs. Missing fields in old backups receive the defaults.
The pet counter persists locally and controls a hidden wardrobe reward.

## Built-in updates

The app now declares Internet access for its optional GitHub updater. Focus, blocking, reports, and audio remain local.
Update requests contain no focus history, held messages, or other app data.
Android's persisted job scheduler checks roughly daily when a network is available. It does not promise an exact delivery time.
Disabling automatic checks cancels the job. Manual checks remain available.

The updater accepts stable numeric release tags and APK assets from this repository.
Downloads require a user action. Installation requires another action and Android confirmation.
The app checks the APK's size, digest when supplied by GitHub, package name, version, and signing certificate.
Debug installations cannot install release APKs signed with a different key.

Test offline failures, unavailable releases, rejected APKs, background-check settings, and the Android install permission flow.
The full signed update path needs a real compatible release asset. A metadata check alone does not prove installation.
