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
- A quest update changes XP from old sessions or replaces a day's saved target.
- Abandoned sessions count as completed-session quests.
- Wardrobe previews equip locked items or overwrite another slot.
- Backup or migration loses equipped items or quest-rule versions.
- Returning from a Settings page loses the previous scroll position.
- The keyboard hides focus controls or remains open after navigation.
- App usage bars use a different category from the report totals.

Real YouTube, Instagram, Snapchat, and Facebook checks need compatible installed apps.
Report an unavailable app as a validation gap. A fixture does not prove real-app detection.

## Backend checks completed

Pure-JVM checks use compiled application classes. Earlier audio checks used the former production sample generator.
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

The original procedural audio passed 20-second render checks before its replacement. Those results do not validate the new recordings.
Focus ambience now uses bundled CC0 recordings. The importer checks duration, peak, RMS, and loop seams.
All five imported loops have RMS 0.070 and no clipped samples. The player adds start and stop fades.
See [focus audio sources](../docs/focus-audio-sources.md) for measured levels and source licenses.
Short interaction sounds use quiet CC0 library recordings. See [UI audio sources](../docs/audio-sources.md).

## Device checks still required

- Check rapid sound switching and pause/resume on physical phones.
- Move the clock backward while a blocked app remains open. Content checks and block screens must continue.
- Cross midnight with a foreground app, a used pass, and an overnight protected schedule.
- Reboot with a pending plan, a snoozed plan, and a running focus session.
- Kill the process during alarm refresh. Existing alarms must remain until their replacements are set.
- Change the time zone around a pending plan and notification delivery time.
- Deliver during the spring gap and autumn overlap. Confirm one delivery per resolved occurrence.
- Apply a freeze twice and cross a week reward boundary after rest days and missed days.
- Complete multiple Pomodoro rounds, including zero short breaks and zero long breaks.
- Verify onboarding on smaller manufacturer viewports. CI uses Pixel 2 at 1080x1920 and tests the visible skip button.
- Verify report categories against real app use on a physical phone. JVM checks confirm that categories sum to the same-period total.

CI runs on the backend branch and the PR. It uploads APKs and lint reports only.
It prints crash, alarm, clock, and screen diagnostics from the disposable CI device. It does not upload e2e/artifacts/.

The device driver posts real notifications on Android 9 and later. The workflow no longer depends on a shell notification command.
It checks capture, content updates, process restart, and duplicate delivery. The fixture waits for Android's asynchronous notification post.
Local Android 9 and Android 14 runs pass this workflow.

## Interface and audio checks

The full Android 14 run covers onboarding, Chrome blocking, focus enforcement, process recovery, home lock,
protected settings, pause/resume, gentle passes, storage, notifications, and planned focus.
The first run found an oversized minimized-focus chip. The corrected layout passed a focused repeat of the affected workflows.
YouTube testing skips because the installed app requires an update.
Android 9 passed schema migration, onboarding, home, storage, notifications, and planned focus locally.
Two additional Android 9 alarm runs passed after process death and screen-off completion.

The focus layout scales its timer and mascot to the available height. Small screens and large fonts can scroll.
Theme labels sit outside image clipping. Paused Pebble has closed eyes. Strict Pebble uses curved, overlapping arms.

Use `e2e/demo.py --scrcpy /path/to/scrcpy --output /path/to/demo.mp4` to capture the screen and real app audio.
The tour resets app data. Back up an existing device first. It records setup, blocking, all five bundled focus recordings,
a real one-minute timer, rewards, and widgets. The rewards chapter uses sample history from a debug-only fixture.
A chapter file records timestamps next to the video. Promotional screenshots use fresh captures, separate from test artifacts.
The demo uses no microphone and has no three-minute recording limit.

## Current progression and interface checks

The Android 9 and Android 14 `progression_workflow` passed during development. It checks:

- Room preserves equipped item IDs and session quest versions.
- New backups retain both fields. Old backups receive compatible defaults.
- New days receive four quests. Legacy sessions retain three-quest rules.
- Completed sessions count for completion quests. Abandoned sessions do not.
- Changing the current daily goal does not change saved quest targets.
- The item resolver excludes locked and unknown items, and permits one item per slot.
- The fixture restores the original settings and session history.

Manual Android 14 checks passed for returning to the same Settings scroll position, keyboard dismissal,
locked wardrobe previews and equip controls, opening the Mountain mover badge details, and retaining the page across theme changes.
The permissions section collapses when all grants are present and expands on request.
With one notification permission revoked, only the missing permission is shown.
These checks do not replace a full pass over all items, badge filters, font sizes, and light or dark screens.

Debug assembly and lint passed. Android 9 passed migration, onboarding, home, storage, progression, notifications, and planned focus.
The first Android 9 attempt lost the accessibility tree. A device reboot restored it, and the full selected run passed.
The Android workflow runs these checks on Android 9 and Android 16. Check its result for the exact branch revision.
The UI reader preserves other accessibility services. Onboarding checks each slide and refreshes the permission state.
The complete selected workflow also passed locally on Android 14 after these driver changes.
The full demo includes all five focus recordings, rewards, wardrobe, dark theme, and launcher widgets.
A chapter file identifies sample-history sections. Partial recordings are not release evidence.

The completion summary fits a 1080x1920 display at density 420 without scrolling.
Continue and Add a note also stay visible at 1080x1640 with font scale 1.2.
Notes retain multiline input, and an outside tap dismisses the keyboard.
Wardrobe items remain reachable at font scale 2. Closed hats cover the sprout; open headwear keeps it visible.
Active streaks remain lit before today's step, on the normal light or dark card background.
