# Build and test Stillpoint

## Build

Use JDK 17, Android SDK 37, and Build Tools 37.0.0. Set `JAVA_HOME` for your installation.
Set `sdk.dir` in an untracked `local.properties`, or set `ANDROID_HOME`.

```sh
# GitHub APKs. Version 0.1.3 does not check for updates. Plus features stay available.
./gradlew assembleGithubDebug assembleGithubRelease lintGithubDebug lintGithubRelease
# Play APKs for checks, and the Play release bundle for upload.
./gradlew assemblePlayDebug assemblePlayRelease bundlePlayRelease lintPlayDebug lintPlayRelease
```

Both distributions use application ID `dev.agneswd.stillpoint` and the same version code.
APKs are in `app/build/outputs/apk/<flavor>/<buildType>/`.
The Play bundle is `app/build/outputs/bundle/playRelease/app-play-release.aab`.

Debug APKs use the Android debug key. Release builds are unsigned unless these environment variables are set:

```text
STILLPOINT_KEYSTORE
STILLPOINT_STORE_PASSWORD
STILLPOINT_KEY_ALIAS
STILLPOINT_KEY_PASSWORD
```

Keep the release keystore and passwords outside the repository. Keep a private backup of both.
Use the GitHub release key for `assembleGithubRelease`.
Play uses Play App Signing. The owner supplies the Play upload key through the same environment variables for `bundlePlayRelease`.
Build each signed distribution separately with its intended key. Google signs delivered Play APKs with the Play app signing key.

The GitHub APK and delivered Play build use different signing keys. Android cannot install one over the other.
To switch distributions, export an encrypted backup, uninstall the old app, install the other distribution, and restore the backup.
A backup never transfers or changes Plus ownership. Restore purchases from Google Play after switching to Play.

## Device checks

Use a disposable emulator. The test resets Stillpoint's app data and configures emulator permissions.
A complete blocking run needs Chrome, Clock, Contacts, and a compatible YouTube installation.
The browser matrix also runs Brave, Brave Beta, and Firefox when they are installed. Finish their welcome pages first.

```sh
./gradlew assembleGithubDebug :e2e-driver:assembleDebug
ANDROID_SERIAL=emulator-5554 python3 e2e/e2e.py
```

Each run saves screenshots, a report, storage-check results, and the crash log in `e2e/artifacts/<run>/`.
The Android reader module reads animated Compose screens without waiting for all animations to stop.
The reader preserves accessibility services. The test reconnects the guard after deliberate process stops.
CI checks first launch, schema upgrades, backup restore, notifications, and planned focus on Android 9 and Android 16.
It builds and lints debug and minified release variants for both distributions.
It also runs the Play fake-store workflow on each CI emulator, without extra apps.

## Plus foundation

`context.app.plus` is the process-wide `Plus` service in `dev.agneswd.stillpoint.plus`.
Compose screens can collect `plus.state` with `collectAsStateWithLifecycle()`.
Services can read `plus.state.value` or `plus.has(feature)` synchronously without I/O.

```kotlin
interface Plus {
    val state: StateFlow<PlusState>
    fun has(feature: PlusFeature): Boolean
    fun purchase(activity: Activity)
    fun restore()
}
```

`PlusState` contains `entitlement: Entitlement`, `price: String?`, and `status: PlusStatus`.
Entitlement is `UNLOCKED`, `LOCKED`, or `PENDING`. Only `UNLOCKED` grants access.
The price comes from Google Play, formatted for the buyer. It is null until known.
Status is `IDLE`, `BUSY`, `CANCELED`, `UNAVAILABLE`, or `ERROR`.
Purchase and restore return immediately. Their results arrive through `state`.
A successful billing-flow launch stays busy until a purchase callback or a foreground ownership query resolves it.

`PlusFeature` defines `UNLIMITED_FOCUS_APPS`, `UNLIMITED_SCHEDULES`, `UNLIMITED_APP_LIMITS`, `WEBSITES`, `SHORT_VIDEOS`,
`STRICT_MODE`, `YOUTUBE_STUDY`, `NOTIFICATION_INBOX`, `FULL_REPORTS`, `PLUS_WARDROBE`, and `PLUS_SCENES`.
`FreeLimits` defines `FOCUS_APPS = 5`, `SCHEDULES = 1`, `APP_LIMITS = 1`, and `REPORT_DAYS = 7`.
This foundation does not enforce limits, gate features, or show a paywall.

GitHub always reports unlocked. Its purchase and restore methods do nothing. Its dependency graph contains no billing library.
Play uses Billing Library 9.1.0 and the non-consumable product `stillpoint_plus`.
The Play build keeps the Internet and network-state permissions that the library's diagnostic transport dependency adds. Removing them can crash that library when it checks the network in the background. Stillpoint code makes no network requests in the Play build.
Billing uses the Play Store service. The billing AAR supplies its own consumer R8 rules.
Configure one permanent buy option, without rental or preorder offers, in Play Console.
Purchases are acknowledged after `PURCHASED`, including purchases recovered at startup.
Pending purchases never unlock Plus. Failed acknowledgements retry and are retried again on a later ownership query.

Play queries purchases at process startup and when an activity resumes.
A successful full query without a purchased Plus product removes cached access, including after a refund.
Failed queries preserve the cached entitlement. Purchase callbacks cannot revoke it because their lists can be partial.
The `plus_entitlement` SharedPreferences file holds the last confirmed access state.
Encrypted backups read and restore Room records, never this file. Android automatic backup is disabled.
An offline app cannot discover a refund until a successful store query.

Run the pure entitlement cases and device workflow:

```sh
./gradlew testGithubDebugUnitTest testPlayDebugUnitTest assemblePlayDebug :e2e-driver:assembleDebug
ANDROID_SERIAL=emulator-5554 python3 e2e/e2e.py --flavor play --only plus_workflow
```

The fake store exists only in `playDebug`. By default, even Play debug uses real billing.
The driver enables the fake with a `no_backup/plus-fake-store` marker through `run-as`, then restarts the app.
The shell-protected receiver uses the existing broadcast and result-file protocol.
The workflow covers locked, pending, purchased, refunded, offline restart, cancellation, errors, already-owned purchases, and backup isolation.
It writes `plus-states.json`, a screenshot, a report, and a crash log under `e2e/artifacts/<run>/`.
After building all four variants, run `python3 .github/scripts/verify_distributions.py`.
It compares merged and packaged permissions and inspects dex classes with `apkanalyzer` and each release R8 mapping.
It confirms that only `playDebug` contains the fake store and saves evidence in `app/build/reports/distributions/`.
CI runs this check and uploads its reports.
The fake store does not run a Google Play checkout.

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
See [behavior coverage](../e2e/coverage.md) for the cases these checks cover.

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
Do not upload the full `e2e/artifacts/` directory. CI uploads only selected text reports and Plus state results.
Capture promotional screenshots separately and remove private data before publication.

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

Encryption protects backup contents from someone without the password. A password owner can still create modified records.
Do not describe this as an anti-cheat guarantee. Export excludes held notification text, active sessions, and temporary passes.

## Schema 8

Migration 7 to 8 adds `Settings.clockFormat` with the default `SYSTEM`.
`SYSTEM` follows the phone clock. `H12` shows AM and PM. `H24` shows a 24-hour clock.
Older backups omit the field and restore as `SYSTEM`.

## Built-in updates

Version 0.1.3 retires the GitHub updater. It is the last release of package `dev.agneswd.stillpoint`.
The github build no longer schedules the daily update job, and it cancels the job (id 64021) that older builds scheduled.
Settings shows the Ronumi notice instead of update controls. The app makes no network requests.
The download and install code stays in the github source set, but nothing calls it.
The Play flavor declares Internet access only through the Billing library and makes no network requests of its own.
Focus, blocking, reports, and audio remain local.
