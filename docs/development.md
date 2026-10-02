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

Backup validation checks theme and icon IDs. Missing fields in older record schemas receive the defaults.
The pet counter persists locally and controls a hidden wardrobe reward.

## Schema 6 and focus timing

Migration 5 to 6 adds these fields without removing existing rows:

| Table | Field | Default |
| --- | --- | --- |
| Settings | `freezeRewardedThrough` | Empty string |
| FocusSession, ActiveFocus | `rewardDay` | Empty string |
| FocusSession, ActiveFocus | `rewardStartHour` | `-1` |
| ActiveFocus | `phaseElapsedMillis` | `0` |
| ActiveFocus | `phaseAnchorElapsed` | `-1` |
| ActiveFocus | `bootCount` | `-1` |

The migration computes elapsed progress for an existing phase from its old wall timestamps.
Paused phases use their pause timestamp. Running phases use the migration time. Values stay within the phase duration.
This one-time conversion cannot distinguish earlier clock edits from elapsed time.

New focus phases measure elapsed time with Android's monotonic clock. The service saves checkpoints every 30 seconds and at transitions.
Wall-clock and time-zone changes do not add focus minutes. After reboot, the session resumes from its last saved checkpoint.
Powered-off time does not count. Time since the last checkpoint can be lost on reboot.

New sessions save their original reward date and start hour. Legacy sessions still derive these values from their timestamps and the current time zone.
Reward dates do not split an overnight session between days. The calendar, reports, and widgets use the saved reward date.
Freeze rewards store the latest rewarded milestone to prevent replay after a clock rollback.
Local pass counters survive backup restore, while restored active passes expire.
These checks do not make a user-controlled, offline device a trusted source of time.

## Encrypted backups

The file picker creates `.stillpoint` files. The export dialog requires a password of at least 12 characters.
Passwords stay in memory and are not saved in preferences or UI restoration state. There is no password recovery.
A password can restore the file on another device. Plaintext JSON imports are deliberately unsupported.

The version-1 envelope uses AES-256-GCM with a 128-bit authentication tag, a random 16-byte salt, and a random 12-byte nonce.
PBKDF2-HMAC-SHA256 derives the key with 600,000 iterations, following [OWASP guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
The version fixes the derivation cost. An input file cannot request more iterations.

The authenticated header contains the eight-byte `STLPBAK!` marker, one-byte version, four-byte big-endian plaintext length, salt, and nonce.
Plaintext is limited to 16 MiB. The envelope adds 57 bytes, including the authentication tag.
Authentication and record validation finish before the restore transaction changes any rows.
Wrong passwords and authentication failures share one error. Unsupported formats receive a separate explanation.

Validation bounds collection sizes and limits one session to 48 focus hours, the maximum twelve-round Pomodoro duration.
Restore remains locked during active focus or a protected schedule. One app-wide operation state prevents overlapping UI backup operations.
The debug storage fixture checks encrypted round trip, tampering, wrong passwords, invalid records, and retained local pass counts.
The Android 9 encrypted storage workflow passes, including wrong passwords, altered files, invalid records, and retained pass counts.

Encryption protects backup contents from someone without the password. A password owner can still create modified records.
Do not describe this as an anti-cheat guarantee. Export excludes held notification text, active sessions, and temporary passes.

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
