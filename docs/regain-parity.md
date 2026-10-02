# Local feature map

Stillpoint focus features work without an account or server. Optional app updates use GitHub.
This map compares product behavior. It does not claim complete Regain parity.

| Area | Implemented | Verification or remaining work |
|---|---|---|
| Focus modes | Timer, stopwatch, Pomodoro, short and long breaks | Timer blocking, pause, resume, and process recovery have device checks. JVM checks cover eight-round timing with zero short or long breaks. Longer device checks remain. |
| Focus controls | App lists, strict sessions, home lock, tags, notes, summaries | Device checks cover app enforcement and home lock. |
| Focus presentation | Interactive Pebble, five animated scenes, smooth transitions, bundled CC0 white, pink, brown, rain, and wave recordings | Imported recordings pass duration, level, clipping, and loop-seam checks. Start and stop use fades. Dawn has solid clouds; the space planet sits above controls. Rain has pond ripples. A full demo captures app audio. Physical-phone audio checks remain. |
| Goals and rewards | Study days, four daily quests from 24 templates, XP, levels, 30 badges, freezes | Quest rules expand on Home. Progress filters badges by category or earned state. Android 9 and 14 checks cover new quest fields, completion rules, old backup defaults, and stable saved targets. Earned legacy badges remain. |
| Pebble wardrobe | 36 level rewards across levels 1 to 20, plus a hidden outfit | Preview locked items; equip one item per slot. Android 9 and 14 checks cover persistence, backup fields, and rejection of locked or invalid items. Device checks cover locked previews, equip controls, hat fit, and scrollable large-text layouts. |
| Interface | System, Light, and Dark theme choices; vector onboarding icons; saved Settings scroll position | Device checks cover scroll restoration, keyboard dismissal, and badge details. Checks also cover the compact completion summary and wardrobe at larger text sizes. |
| Planned focus | Local alarms, automatic start with exact access, reminders, ten-minute snooze | Device checks cover automatic start after process death and completion with the screen off. JVM checks cover DST and time-zone selection. Reboot, clock-change broadcasts, and snooze still need device checks. |
| App limits | Gentle and strict limits, counted five-minute passes, reminders, limit streaks, change warnings | Device checks cover real limits and daily pass bounds. JVM checks confirm strict limits ignore older gentle passes. Midnight and reminder device checks remain. |
| Block schedules | Listed apps or all except a list, chosen days, overnight windows, custom icons or time-based automatic icons | JVM checks cover overnight windows, week rollover, and end boundaries. Device rollover remains. Planner changes recheck active protection. |
| Temporary pause | Ten-minute block pause | Focus app blocks remain active. Protected schedules refuse pause. |
| Short videos | YouTube Shorts, Instagram Reels, Snapchat Spotlight, Facebook Reels | Current installed YouTube forces an update. Current versions of all four apps remain to test. |
| First video | One identified video per app visit | Unknown video titles stay blocked. Visible title matching needs real-app checks. |
| Websites | Domain and subdomain lists, adult-domain list, allow-list mode | Chrome block-list behavior passes. Allow-list behavior and other browsers remain to test. |
| YouTube study | Chosen channels, home-feed blocking | Unknown channels stay blocked in recognized players. Current YouTube verification remains. |
| Notifications | App selection, focus-only or all-day holding, scheduled private summaries, separate optional notification switches | Real notification updates, process restart, and duplicate delivery have device checks. JVM checks cover changed inbox content after delivery, repeated summaries, and backward clock changes. Alarm delivery timing remains. |
| Protection | Blocks-tab lock, supported system-settings checks, schedule-editor checks, multi-window detection | Android permissions and OEM behavior limit enforcement. Multi-window and uninstall routes remain to test. |
| Reports | Day, week, month, tags, daily average, productive, distracting, and uncategorized app time, unlocks, held counts, baseline time saved | Usage records persist locally. JVM checks cover midnight usage and category accounting. Time saved appears after seven recorded complete days. The UI shows all three categories and their total, and excludes essential apps from distracting time. |
| Setup | Mascot questions, app selection, permissions, optional first focus | Device checks cover first launch and saved setup. |
| Widgets | Done - screen-time, focus, goal, and calendar widgets | Widgets refresh after database commits. Launcher rendering appears in the demo. |
| Backup | Portable password-encrypted files, authenticated and validated restore, schema migrations | Pure JVM crypto and clock checks pass. Android 9 checks pass for encrypted round trip, failure handling, retained pass counts, and schema 1 to 6 migration. |

Cloud sync, social focus rooms, leaderboards, friends, subscriptions, ads, and analytics are outside this app.

The sound picker wraps six choices in rows of three. Theme labels remain outside rounded image clips.
Real-app detection and manufacturer-specific permission flows need device coverage before a stable release.

Plans resolve local times before comparing instants. A time in a missing hour shifts forward by the clock-change gap.
An overlapping local time runs once, at its first occurrence. Notification delivery uses the same time selection.
Snooze stores a ten-minute absolute deadline. Reboot and time-change broadcasts rebuild the next alarm.
Alarm refresh replaces each pending alarm after reading its next deadline. It no longer cancels alarms before database reads.
Guard throttling uses elapsed time, so a backward clock edit cannot suppress enforcement until the old wall time returns.

Report categories use the current productive choices and focus blocking mode. Productive choices take priority.
Allow-list mode treats chosen apps as allowed. Other apps count as distracting, except essential apps supplied by the caller.
Unassigned and essential usage stays uncategorized. Two distracting minutes can be part of 57 total screen minutes.
The report shows other apps and the complete screen-time total for the chosen period.

The backend review found these remaining data and timing limits:

- Active focus now uses monotonic elapsed time with saved checkpoints. A reboot can lose time since the last checkpoint; powered-off time does not count.
- New sessions retain their original reward date and hour. Legacy history still depends on the current time zone.
- Overnight sessions still count toward one reward date. The model has no per-day focus intervals.
- Held-message counts increase only for a new inbox key. Updating an existing key on another day does not increase that day's count.

Held-message counting needs a separate review. Backup validation now accepts civil days longer than 24 hours, with a bounded 48-hour allowance.

The wardrobe stores item IDs in settings. It rechecks the current level inside a database transaction before equipping an item.
Each new session stores its quest-rule version. Existing history keeps its old rules instead of receiving a new XP calculation.
New quest days have four tasks. A day with existing legacy sessions keeps its original three tasks.

Progress app bars share the report's category helper. Productive apps use green, distracting apps use red, and other apps use neutral bars.
The total screen-time chart is neutral because it includes every category.

Current validation includes successful debug builds and lint, plus progression checks on Android 9 and 14.
Android 9 also passed onboarding, home, storage, notifications, and planned focus.
CI results are recorded for each branch revision in GitHub Actions.
The previous Android workflow at `f9a10ac` passed both Android 9 and Android 16 jobs.

## Changes after the last green revision

Revision `c9b935d` passed Android 9 and Android 16 CI. That result does not cover the newer changes listed below.

- Pebble's idle, happy, proud, thoughtful, sad, sleeping, and waving poses have different motion.
- Recent focus habits determine the home mood. Rest days and freezes do not count as missed study days.
- Petting progress is saved locally and can unlock a hidden outfit.
- Schedule icons can be selected manually. Automatic icons still follow the start time.
- Theme changes apply to the app, dialogs, and block screen without recreating the activity.
- Optional focus notifications, planned reminders, and inbox summaries have separate switches.
- GitHub update checks are optional. Downloads and installation each require a user action.

Internet access now supports updates only. Focus features remain offline, and update requests do not upload app data.
Local debug build and lint passed. Android 14 checks passed for schema 5 backups, pet unlock persistence, and muted inbox delivery.
Pure JVM checks passed for mood rules and update URL, version, and signer policies.
The live GitHub check reports no public release. The update switch cancels and restores the daily job.
The complete signed update installation still needs a compatible release asset. Final revision CI remains to run.

## Encrypted backups and stable focus timing

Backups now require a password and use authenticated AES-256-GCM encryption. Plaintext JSON imports are no longer supported.
The export dialog requires at least 12 characters. Passwords are not persisted, and there is no recovery mechanism.
A restore keeps local pass-use counters and expires active passes. It cannot refill that device's daily pass budget.
Encryption does not stop a password owner from editing records or make an offline clock trustworthy.

Schema 6 adds monotonic phase checkpoints and stable reward dates. The migration preserves an existing phase's wall-time progress once.
Later wall-clock changes cannot award extra elapsed focus time. New freeze milestones cannot replay after a backward clock change.
Android 9 checks pass for schema migration, encrypted storage, progression, notifications, and a real scheduled focus session.
Pure JVM checks cover clock jumps, pause, reboot checkpoints, stable reward dates, XP bounds, and freeze replay.
Physical-device reboot and clock-change behavior remain unverified.
The pure JVM crypto harness passed round trip, wrong-password, header and ciphertext tampering, truncation, size limits, and unsupported formats.
Debug build and lint pass. CI for this batch is pending.
