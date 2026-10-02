# Local feature map

Stillpoint implements features that can work without an account or server.
This map compares product behavior. It does not claim complete Regain parity.

| Area | Implemented | Verification or remaining work |
|---|---|---|
| Focus modes | Timer, stopwatch, Pomodoro, short and long breaks | Timer blocking, pause, resume, and process recovery have device checks. Longer Pomodoro and sleep tests remain. |
| Focus controls | App lists, strict sessions, home lock, tags, notes, summaries | Device checks cover app enforcement and home lock. |
| Focus presentation | Pebble, timer scenes, transitions, generated white, pink, and brown noise | Rain and wave audio assets remain. |
| Goals and rewards | Study days, quests, XP, levels, badges, freezes | Device storage checks cover historical XP and repeated freeze application. |
| Planned focus | Local alarms, automatic start with exact access, reminders, ten-minute snooze | Device checks cover automatic start after process death and completion with the screen off. Reboot, time-zone changes, and snooze remain to test. |
| App limits | Gentle and strict limits, counted five-minute passes, reminders, limit streaks, change warnings | Device checks cover real limits and daily pass bounds. Reminder timing remains to test. |
| Block schedules | Listed apps or all except a list, chosen days, overnight windows | Overnight rollover needs a device check. Planner changes recheck active protection. |
| Temporary pause | Ten-minute block pause | Focus app blocks remain active. Protected schedules refuse pause. |
| Short videos | YouTube Shorts, Instagram Reels, Snapchat Spotlight, Facebook Reels | Current installed YouTube forces an update. Current versions of all four apps remain to test. |
| First video | One identified video per app visit | Unknown video titles stay blocked. Visible title matching needs real-app checks. |
| Websites | Domain and subdomain lists, adult-domain list, allow-list mode | Chrome block-list behavior passes. Allow-list behavior and other browsers remain to test. |
| YouTube study | Chosen channels, home-feed blocking | Unknown channels stay blocked in recognized players. Current YouTube verification remains. |
| Notifications | App selection, focus-only or all-day holding, scheduled private summaries | Real notification updates, process restart, and duplicate delivery have device checks. Alarm delivery timing remains. |
| Protection | Blocks-tab lock, supported system-settings checks, schedule-editor checks, multi-window detection | Android permissions and OEM behavior limit enforcement. Multi-window and uninstall routes remain to test. |
| Reports | Day, week, month, tags, daily average, app categories, unlocks, held counts, baseline time saved | Usage records persist locally. Time saved appears after seven recorded complete days. |
| Setup | Mascot questions, app selection, permissions, optional first focus | Device checks cover first launch and saved setup. |
| Widgets | Screen-time and focus widgets | Goal, calendar, unlock, and small usage variants remain for the next design pass. Launcher rendering remains to test. |
| Backup | Versioned local JSON, validated restore, schema migrations | Device checks cover schema 1 to 3, round trip, invalid files, and protected restore. |

Cloud sync, social focus rooms, leaderboards, friends, subscriptions, ads, and analytics are outside this offline app.

The next design pass can finish the extra widgets and audio assets.
Real-app detection and manufacturer-specific permission flows need device coverage before a stable release.
