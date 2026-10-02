# Behavior checks

The Android tests must cover these failures before release.
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
- The minified release crashes or gains network permission.

Real YouTube, Instagram, Snapchat, and Facebook checks need compatible installed apps.
Report an unavailable app as a validation gap. A fixture does not prove real-app detection.

## Backend checks completed

Pure-JVM checks use the production sample generator and compiled application classes.
They cover these behavior gaps:

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

Rain and waves use mono 16-bit PCM at 22,050 Hz. The 20-second renders have no clipped samples.
Rain peak/RMS is 0.587/0.131. Waves peak/RMS is 0.817/0.140. Existing noise RMS is 0.138 to 0.144.
The throwaway audio harness was removed. No audio files are needed by the app.

## Device checks still required

- Render sound controls and listen for start, stop, switch, and rapid pause/resume clicks.
- Move the clock backward while a blocked app remains open. Content checks and block screens must continue.
- Cross midnight with a foreground app, a used pass, and an overnight protected schedule.
- Reboot with a pending plan, a snoozed plan, and a running focus session.
- Kill the process during alarm refresh. Existing alarms must remain until their replacements are set.
- Change the time zone around a pending plan and notification delivery time.
- Deliver during the spring gap and autumn overlap. Confirm one delivery per resolved occurrence.
- Apply a freeze twice and cross a week reward boundary after rest days and missed days.
- Complete multiple Pomodoro rounds, including zero short breaks and zero long breaks.
- Verify onboarding on smaller manufacturer viewports. CI uses Pixel 2 at 1080x1920 and tests the visible skip button.
- Show productive, distracting, and uncategorized report time. Their sum must equal screen time for the same period.

CI runs on the backend branch and the PR. It uploads APKs and lint reports only.
It prints crash, alarm, clock, and screen diagnostics from the disposable CI device. It does not upload e2e/artifacts/.

The Android 9 shell has no notification-post command. CI reports that workflow as SKIP when the command is unavailable.
Android 16 continues to run the real notification workflow. Android 9 notification behavior still needs another posting fixture.
See the [Android 9 shell implementation](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-9.0.0_r61/services/core/java/com/android/server/notification/NotificationManagerService.java#L7044).

An Android 16 PR run captured the inbox but failed while reading notification 10 immediately after posting it.
The device fixture must wait for that notification before checking its post time. Backend CI has passed this workflow.
