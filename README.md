# Stillpoint

Stillpoint is an offline Android app for focus and screen-time limits.
It has no account, ads, analytics, or `INTERNET` permission.
Pebble, the animated mascot, guides setup and focus sessions.

This is an early release. [The feature map](docs/regain-parity.md) lists the remaining work and verification gaps.

## Features

- Timer, stopwatch, and Pomodoro sessions, with tags, notes, themes, and generated white, pink, or brown noise.
- Daily goals, chosen study days, quests, XP, badges, streaks, and automatic streak freezes.
- Daily app budgets, strict limits, usage reminders, and a limited number of five-minute passes.
- Recurring block schedules, planned focus sessions, and ten-minute reminder snooze.
- Short-video detection, first-video allowance, YouTube channel lists, and YouTube home-feed blocking.
- Website block lists, website allow lists, subdomain matching, and an optional adult-domain list.
- A local notification inbox, scheduled notification summaries, and allowed notification apps.
- Daily, weekly, and monthly focus reports, tags, app categories, unlocks, and saved usage history.
- Screen-time and focus widgets, plus local JSON backup and restore.

Short-video and browser detection use accessibility view IDs. App updates and device software can change them.
The new YouTube controls still need tests against a current YouTube installation.

## Install and permissions

Android 9 or later is required. Install the APK from [Releases](https://github.com/agneswd/Stillpoint/releases).

Allow usage access to measure screen time. Allow accessibility to apply blocks.
On Android 13 or later, App info may require **Allow restricted settings** before accessibility can be enabled.
Notification access is optional. It lets Stillpoint store and remove notifications from chosen apps.
Notification permission shows timer events and inbox summaries.
Exact alarm access starts planned focus automatically. Without it, plans send reminders when Android delivers the alarm.

Strict sessions refuse pause and early exit. Protection closes supported Stillpoint system-settings pages during active blocks.
These controls depend on Android permissions. They cannot prevent force-stop, safe mode, adb, or every manufacturer's uninstall flow.

## Data and privacy

Room stores preferences, focus history, app usage, and held notification text on the phone.
Stillpoint sends no network requests. It disables automatic Android backup.
You can export an unencrypted JSON backup to a location you choose.
Backups include preferences and history. They exclude held notification text, running sessions, and temporary passes.
Restore validates the file before replacing stored data. Restore is locked during active focus or protected schedules.

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
The test reconnects accessibility before checking enforcement.
CI checks first launch, schema upgrades, backup restore, notifications, and planned focus on Android 9 and Android 16.
It also builds the minified release and runs release lint.

## License

Stillpoint code and original artwork use [GPL-3.0-only](LICENSE).
Nunito uses the [SIL Open Font License](licenses/Nunito-OFL.txt).
See [NOTICE](NOTICE) for attribution. License texts are included in the APK.
