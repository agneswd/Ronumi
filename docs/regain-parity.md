# Local feature map

Stillpoint implements features that can work without an account or server.
This map compares product behavior. It does not claim complete Regain parity.

| Area | Implemented | Verification or remaining work |
|---|---|---|
| Focus modes | Timer, stopwatch, Pomodoro, short and long breaks | Timer blocking, pause, resume, and process recovery have device checks. JVM checks cover eight-round timing with zero short or long breaks. Longer device checks remain. |
| Focus controls | App lists, strict sessions, home lock, tags, notes, summaries | Device checks cover app enforcement and home lock. |
| Focus presentation | Interactive Pebble, five animated scenes, smooth transitions, bundled CC0 white, pink, brown, rain, and wave recordings | Imported recordings pass duration, level, clipping, and loop-seam checks. Start and stop use fades. Dawn has solid clouds; the space planet sits above controls. Rain has pond ripples. A full demo captures app audio. Physical-phone audio checks remain. |
| Goals and rewards | Study days, four daily quests from 24 templates, XP, levels, 30 badges, freezes | Quest rules expand on Home. Progress filters badges by category or earned state. Android 9 and 14 checks cover new quest fields, completion rules, old backup defaults, and stable saved targets. Earned legacy badges remain. |
| Pebble wardrobe | 36 colors, clothes, hats, and accessories across levels 1 to 20 | Preview locked items; equip one item per slot. Android 9 and 14 checks cover persistence, backup fields, and rejection of locked or invalid items. Device checks cover locked previews, equip controls, hat fit, and scrollable large-text layouts. |
| Interface | System light and dark themes, Pebble pet reactions, saved Settings scroll position | Device checks cover scroll restoration, keyboard dismissal, and badge details. Checks also cover the compact completion summary and wardrobe at larger text sizes. |
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
| Reports | Day, week, month, tags, daily average, productive, distracting, and uncategorized app time, unlocks, held counts, baseline time saved | Usage records persist locally. JVM checks cover midnight usage and category accounting. Time saved appears after seven recorded complete days. The UI shows all three categories and their total, and excludes essential apps from distracting time. |
| Setup | Mascot questions, app selection, permissions, optional first focus | Device checks cover first launch and saved setup. |
| Widgets | Done - screen-time, focus, goal, and calendar widgets | Widgets refresh after database commits. Launcher rendering appears in the demo. |
| Backup | Versioned local JSON, validated restore, schema migrations | Android 9 checks cover schema 1 to 4. Storage checks cover round trip, invalid files, and protected restore. Schema 4 adds wardrobe and quest fields. |

Cloud sync, social focus rooms, leaderboards, friends, subscriptions, ads, and analytics are outside this offline app.

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

- Active focus timestamps use wall time. Manual clock edits can change the remaining duration. A durable elapsed-time design needs a separate change.
- Focus reports assign a session to its start date in the current time zone. Midnight sessions and time-zone changes can change daily totals.
- Held-message counts increase only for a new inbox key. Updating an existing key on another day does not increase that day's count.

The current data model has no per-day focus intervals or durable elapsed-time anchor.
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
