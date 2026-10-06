# Behavior checks

The Android tests must cover these failures.
Each run must save screenshots, a report, and the crash log.

- First-launch setup cannot finish, loses answers, or repeats after a restart.
- Focus blocks the wrong app, ignores pause, or loses its timer after process death.
- Two completion actions save the same session twice.
- Timer and Pomodoro phases count breaks as focus time.
- A schema upgrade deletes limits, schedules, settings, or focus history.
- Backup restore accepts invalid values, changes data on failure, or loses history.
- Overnight schedules use the wrong day, or remain locked after they end.
- A temporary pass bypasses a strict block or exceeds the daily allowance.
- App reminders repeat continuously or survive leaving the app.
- Site allow-lists admit unrelated suffixes or block permitted subdomains.
- Short-video detection blocks the main app or repeatedly permits the first video.
- Study-channel detection admits an unknown channel.
- Held notification updates duplicate messages or disappear before storage succeeds.
- Notification delivery repeats messages or fails after a process restart.
- Planned sessions lose alarms after reboot, time changes, or settings changes.
- Historical XP changes when the current daily goal changes.
- A changed app budget rewrites past limit streak results.
- Streak freezes are spent twice or badges disappear after a missed day.
- Widgets start multiple sessions or show stale focus state after a session ends.
- The minified release crashes, or local focus features require a network connection.
- A quest update changes XP from old sessions or replaces a day's saved target.
- Abandoned sessions count as completed-session quests.
- Wardrobe previews equip locked items or overwrite another slot.
- Backup or migration loses equipped items or quest-rule versions.
- Returning from a Settings page loses the previous scroll position.
- The keyboard hides focus controls or remains open after navigation.
- App usage bars use a different category from the report totals.

Real YouTube, Instagram, Snapchat, and Facebook checks need compatible installed apps.
If an app is not installed, record that in the run. A fixture does not prove real-app detection.

## JVM checks

Pure-JVM checks use compiled application classes.
They cover these failures:

- Rain and waves clip, have a DC offset, differ greatly in volume, or cannot render faster than playback.
- Output buffers reset noise filters or the wave envelope. Stop and restart omit the short fade.
- DST local-time comparison selects a past autumn alarm or skips a future spring alarm.
- A DST gap changes candidate order. Alarm selection must compare resolved instants.
- Overnight windows use the end day's mask, include the end minute, or fail at the Sunday-to-Monday boundary.
- Midnight screen-off and pause events count the same foreground interval twice.
- A saved gentle pass bypasses a limit after its mode becomes strict.
- Disabling short Pomodoro breaks also removes the fourth-round long break.
- Notification summaries omit changed text on an existing inbox row, or repeat an unchanged row.
- Rest days break streaks, frozen days count as focus days, or a missed day removes an earned badge.
- Daily usage boundaries assume every local day has 24 hours.
- Allow-list focus mode labels allowed apps as distracting, or category totals omit uncategorized usage.
- Productive choices and essential exclusions duplicate time or make a category total negative.

Focus ambience uses bundled CC0 recordings. The importer checks duration, peak, RMS, and loop seams.
See [focus audio sources](../docs/focus-audio-sources.md) for measured levels and source licenses.
Short interaction sounds use quiet CC0 library recordings. See [UI audio sources](../docs/audio-sources.md).

## Device checks

Check these cases on a device:

- Switch sounds quickly, and pause and resume, on a physical phone.
- Move the clock backward while a blocked app stays open. Content checks and block screens must continue.
- Cross midnight with a foreground app, a used pass, and an overnight protected schedule.
- Reboot with a pending plan, a snoozed plan, and a running focus session.
- Kill the process during alarm refresh. Existing alarms must remain until their replacements are set.
- Change the time zone around a pending plan and a notification delivery time.
- Deliver during the spring gap and the autumn overlap. Confirm one delivery for each resolved occurrence.
- Apply a freeze twice, and cross a week reward boundary after rest days and missed days.
- Complete multiple Pomodoro rounds, including zero short breaks and zero long breaks.
- Check onboarding on a smaller manufacturer screen. At 1080x1920, the skip button must stay visible.
- Compare report categories with real app use on a physical phone. JVM checks confirm that categories sum to the same-period total.

The device driver posts real notifications on Android 9 and later.
It does not use a shell notification command.
It checks capture, content updates, process restart, and duplicate delivery.
The fixture waits until Android posts the notification.

The focus layout scales its timer and mascot to the available height. Small screens and large fonts can scroll.
Theme labels sit outside image clipping. Paused Ronumi has closed eyes. Strict Ronumi uses curved, overlapping arms.

Use this command to capture the screen and app audio:

```sh
python3 e2e/demo.py --scrcpy /path/to/scrcpy --output /path/to/demo.mp4
```

The tour resets app data. Back up an existing device first.
It records setup, blocking, all five bundled focus recordings, a real one-minute timer, rewards, and widgets.
The rewards chapter uses sample history from a debug-only fixture.
A chapter file records timestamps next to the video.
Take promotional screenshots as fresh captures, separate from test artifacts.
The demo uses no microphone, and it has no three-minute recording limit.

The completion summary fits a 1080x1920 display at density 420 without scrolling.
Continue and Add a note stay visible at 1080x1640 with font scale 1.2.
Notes keep multiline input. A tap outside the field dismisses the keyboard.
Wardrobe items stay reachable at font scale 2. A closed hat covers the sprout. Open headwear leaves it visible.
An active streak stays lit before today's step, on the normal light or dark card background.

The fixture waits for the guard to bind before setup completes.
Before it replaces the APK or clears app data, it disables the guard and waits until the connection is gone.
Android 9 can keep an interrupted binding after either operation, even when the app did not crash.
Changing the enabled-service setting does not clear that connection.
Onboarding still checks that the guard is bound.

## Progression checks

`progression_workflow` checks:

- Room preserves equipped item IDs and session quest versions.
- New backups retain both fields. Old backups receive compatible defaults.
- New days receive four quests. Older sessions retain three-quest rules.
- Completed sessions count for completion quests. Abandoned sessions do not.
- Changing the current daily goal does not change saved quest targets.
- The item resolver excludes locked and unknown items, and permits one item per slot.
- The fixture restores the original settings and session history.

The permissions section collapses when every grant is present, and it expands on request.
When one notification permission is missing, the screen shows only that permission.
The UI reader preserves other accessibility services. Onboarding checks each slide and refreshes the permission state.

## Updater, theme, and schema 5

Also cover these failures:

- Schema 5 loses prior data or assigns incorrect preference defaults.
- Old backups fail without the new fields. Invalid theme or schedule icon IDs enter saved settings.
- System mode ignores a phone theme change. Forced Light or Dark follows the phone instead.
- A theme change resets navigation or the Settings scroll position, or leaves dialogs and the block screen in the old theme.
- Notification switches change enforcement, stop automatic planned sessions, or erase held messages.
- A schedule icon changes after an edit, a restart, or a restore. Automatic icons stop following the start time.
- Pet progress resets after a restart, or grants the hidden reward before its requirement is met.
- Rest days or frozen days lower Ronumi's mood. Partial focus fails to acknowledge a return.
- New animation loops snap at their boundary, or move clothing separately from the body.
- Automatic update checks continue after being disabled, or download an APK without a user action.
- Network failure prevents local focus, or leaves the update screen stuck.
- A wrong-package, older, modified, oversized, or differently signed APK reaches the installer.
- Installation bypasses Android confirmation, or fails to recover after install permission is granted.

The GitHub flavor has Internet access for updates. Test local focus and blocking with the network off.
A GitHub metadata response does not prove that a signed update installs.
A same-signed previous app can bring its progress into Ronumi. A different signature skips that screen.
An encrypted backup from that app restores. Start fresh leaves the previous app unchanged.

## Encrypted backups and schema 6

The storage workflow covers encrypted export and restore, one authenticated invalid record, wrong passwords,
changed ciphertext, plaintext rejection, and retained local pass counts.
Other invalid records are checked in validation directly.
The fixture restores the device's original pass rows. It uses a debug-only password.

JVM crypto checks cover:

- The correct password restores the original bytes, including Unicode text.
- Repeated exports produce different encrypted bytes.
- Wrong passwords, a modified salt or nonce, and changed ciphertext fail authentication.
- Truncated, appended, oversized, plaintext, and unsupported-version input is rejected.
- The caller's password array stays unchanged, and an empty password is rejected.

Also cover:

- Schema 1 through 5 upgrades preserve records and progress in a running or paused phase.
- Passwords do not survive activity recreation. A running backup stays marked busy after you leave Settings and return.
- Wrong passwords, altered files, and invalid authenticated records leave stored data unchanged.
- Restoring an older backup preserves used passes and expires active passes.
- Wall-clock jumps do not add focus minutes or complete a phase early.
- Pause, resume, process death, and reboot keep the expected checkpoint and remaining duration.
- A reboot does not count powered-off time. Any lost time is limited to the unsaved interval.
- New reward dates stay stable across time-zone changes. Older history keeps its compatibility behavior.
- Returning to an old date cannot grant an already rewarded freeze milestone again.
- Large or invalid focus totals cannot overflow XP arithmetic or hang level calculation.

The JVM crypto checks do not cover the Android provider, file-picker access, or the activity lifecycle.

## Distribution and Plus

The entitlement JVM cases cover pending purchases, unacknowledged purchases, failed queries, refunds, duplicate tokens,
completed or canceled pending purchases, unrelated products, and partial purchase callbacks.
The Play debug workflow checks the same app-wide service used by feature gates.
It checks cache survival after an offline restart, acknowledgement, purchase errors, cancellation, already-owned recovery,
and encrypted backup restore in both directions.
Each run saves `plus-states.json` and the normal report and crash log.
The fake store and its receiver exist only in `playDebug`.
The fake store does not cover a real Google Play checkout, a purchase that stays pending on Play, a refund from Play, or a billing service reconnect.

Emulator checks for the rest of the app use `githubDebug`. Plus checks use `playDebug`.
