# Local feature map

Stillpoint implements features that can work without an account or server.
This map compares product behavior. It does not claim complete Regain parity.

| Area | Implemented | Verification or remaining work |
|---|---|---|
| Focus modes | Timer, stopwatch, Pomodoro, short and long breaks | Timer blocking, pause, resume, and process recovery have device checks. JVM checks cover eight-round timing with zero short or long breaks. Longer device checks remain. |
| Focus controls | App lists, strict sessions, home lock, tags, notes, summaries | Device checks cover app enforcement and home lock. |
| Focus presentation | Pebble, timer scenes, transitions, generated white, pink, brown, rain, and wave sounds | Twenty-second JVM renders pass peak, RMS, fade, and buffer-continuity checks. Device listening and rapid pause/resume checks remain. |
| Goals and rewards | Study days, quests, XP, levels, badges, freezes | Device storage checks cover historical XP and repeated freeze application. |
| Planned focus | Local alarms, automatic start with exact access, reminders, ten-minute snooze | Device checks cover automatic start after process death and completion with the screen off. JVM checks cover DST and time-zone selection. Reboot, clock-change broadcasts, and snooze still need device checks. |
| App limits | Gentle and strict limits, counted five-minute passes, reminders, limit streaks, change warnings | Device checks cover real limits and daily pass bounds. JVM checks confirm strict limits ignore older gentle passes. Midnight and reminder device checks remain. |
| Block schedules | Listed apps or all except a list, chosen days, overnight windows | JVM checks cover overnight windows, week rollover, and end boundaries. Device rollover remains. Planner changes recheck active protection. |
| Temporary pause | Ten-minute block pause | Focus app blocks remain active. Protected schedules refuse pause. |
| Short videos | YouTube Shorts, Instagram Reels, Snapchat Spotlight, Facebook Reels | Current installed YouTube forces an update. Current versions of all four apps remain to test. |
| First video | One identified video per app visit | Unknown video titles stay blocked. Visible title matching needs real-app checks. |
| Websites | Domain and subdomain lists, adult-domain list, allow-list mode | Chrome block-list behavior passes. Allow-list behavior and other browsers remain to test. |
| YouTube study | Chosen channels, home-feed blocking | Unknown channels stay blocked in recognized players. Current YouTube verification remains. |
| Notifications | App selection, focus-only or all-day holding, scheduled private summaries | Real notification updates, process restart, and duplicate delivery have device checks. JVM checks cover changed inbox content after delivery, repeated summaries, and backward clock changes. Alarm delivery timing remains. |
| Protection | Blocks-tab lock, supported system-settings checks, schedule-editor checks, multi-window detection | Android permissions and OEM behavior limit enforcement. Multi-window and uninstall routes remain to test. |
| Reports | Day, week, month, tags, daily average, app categories, unlocks, held counts, baseline time saved | Usage records persist locally. JVM checks prevent midnight screen-off events from counting usage twice. Time saved appears after seven recorded complete days. |
| Setup | Mascot questions, app selection, permissions, optional first focus | Device checks cover first launch and saved setup. |
| Widgets | Screen-time and focus widgets | Goal, calendar, unlock, and small usage variants remain for the next design pass. Launcher rendering remains to test. |
| Backup | Versioned local JSON, validated restore, schema migrations | Device checks cover schema 1 to 3, round trip, invalid files, and protected restore. |

Cloud sync, social focus rooms, leaderboards, friends, subscriptions, ads, and analytics are outside this offline app.

The next design pass can finish the extra widgets and adjust the sound picker for six choices.
Real-app detection and manufacturer-specific permission flows need device coverage before a stable release.

Plans resolve local times before comparing instants. A time in a missing hour shifts forward by the clock-change gap.
An overlapping local time runs once, at its first occurrence. Notification delivery uses the same time selection.
Snooze stores a ten-minute absolute deadline. Reboot and time-change broadcasts rebuild the next alarm.
Guard throttling uses elapsed time, so a backward clock edit cannot suppress enforcement until the old wall time returns.

The backend review found these remaining data and timing limits:

- Active focus timestamps use wall time. Manual clock edits can change the remaining duration. A durable elapsed-time design needs a separate change.
- Focus reports assign a session to its start date in the current time zone. Midnight sessions and time-zone changes can change daily totals.
- Backup validation caps each app's daily usage at 24 hours. A 25-hour DST day can exceed that cap.
- Held-message counts increase only for a new inbox key. Updating an existing key on another day does not increase that day's count.

The current data model has no per-day focus intervals or durable elapsed-time anchor.
Backup validation and held-message counting need changes to existing data behavior. Those changes remain for a separate review.
