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
