# Stillpoint

An offline Android app that limits screen time and blocks distractions.
It has no account, no ads and no `INTERNET` permission. All data stays on the phone.
Every feature is free.

## Features

- **Today.** Screen time, unlocks, the last 7 days and the most used apps. Tap an app to set a limit.
- **App limits.** A daily budget per app. A gentle limit lets you take 5 more minutes after a 10 second wait. A strict limit holds until midnight.
- **Schedules.** Block a list of apps, or all apps except a list, in a time window on chosen days.
- **Focus.** A timer with rounds and breaks. It blocks the chosen apps, can lock the home screen, can refuse to end early, and can play white, pink or brown noise. Sessions have tags, notes, a daily goal and a streak.
- **Short videos.** Closes YouTube Shorts, Instagram Reels, Snapchat Spotlight and Facebook Reels, and keeps the rest of the app.
- **Websites.** Blocks domains and their subdomains in common browsers, with an optional adult-site list.
- **Held notifications.** Removes notifications from chosen apps during focus and schedules, or all day, and keeps them in a list.
- **Strict mode.** While a block runs, the settings pages that can turn off or remove Stillpoint close at once, and the Blocks tab is locked.
- **Widgets.** Screen time today, and a one-tap focus start.
- **Backup.** Save and restore limits, schedules, sites, settings and history as a JSON file.

## How it works

| Part | File |
|---|---|
| Blocking rules, as plain functions | `guard/Rules.kt` |
| Accessibility service that finds the app in front and applies the rules | `guard/GuardService.kt` |
| View ids for Shorts feeds and browser address bars | `guard/Detectors.kt` |
| Screen time and unlocks from usage events | `usage/UsageReader.kt` |
| Focus session state machine | `focus/Focus.kt` |
| Room database, one `Settings` row | `data/` |

Apps change their view ids. If a Shorts feed or a browser stops being detected, update the table in `guard/Detectors.kt`.

## Build

```sh
./gradlew assembleDebug
```

On Android 13 and later, a sideloaded app needs "Allow restricted settings" in App info before you can turn on its accessibility service.

## End-to-end test

Start an emulator or connect a phone, then:

```sh
./gradlew assembleDebug && python3 e2e/e2e.py
python3 e2e/demo.py   # optional: records e2e/artifacts/demo.mp4
```

The test grants the permissions with adb and drives the real UI. It saves screenshots, `report.md` and `crash.log` to `e2e/artifacts/<run>/`.

A UI dump attaches a second accessibility client, and Android pauses other accessibility services while it runs. So the test sets things up through the UI first. It then turns the guard on again and checks enforcement with `dumpsys` and the `Stillpoint` log tag only.
