# Google Play listing

Research checked on 2026-10-05. This copy describes the planned Play release in the shared brief.
The source audit used `658934128c2280a1f0a249243ee3bb4afaf70add`, before flavors and billing were added.
Do not publish this copy until the release build passes [the release checklist](release-checklist.md).

## Title

| Option | Characters | Decision |
| --- | ---: | --- |
| Stillpoint: Focus & Block Apps | 30 | Recommended. Names both core uses. |
| Stillpoint: Focus with Pebble | 29 | Emphasizes the companion. |
| Stillpoint: Timer & Block Apps | 30 | Emphasizes the timer. |

Google permits 30 characters for the title, 80 for the short description, and 4,000 for the full description.
Counts include spaces, punctuation, and line breaks inside the copy blocks. They exclude the final newline and Markdown fences.
See [store listing limits](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en).

## Short description

```text
Focus with Pebble. Use timers, block distracting apps, and build a daily habit.
```

Character count: 79 / 80.

## Full description

```text
Make time for a task, with a little company from Pebble.

Stillpoint is a focus timer and app blocker. Choose a task, set your blocks, and start a session. Earn clothes for Pebble as you build your focus habit.

Free core
- Countdown timer, stopwatch, and Pomodoro sessions with breaks.
- Focus blocking for up to 5 apps, 1 block schedule, and 1 daily app limit.
- Pebble, quests, badges, levels, 36 level wardrobe items, and a hidden outfit.
- Five focus scenes and all standard focus sounds.
- Home-screen widgets and reports for the last 7 days.
- Password-protected backups.

Stillpoint Plus
One purchase unlocks Plus and supports development. There is no subscription or trial.
- Unlimited focus apps, schedules, and app limits.
- Website blocking and supported short-video blocking.
- Strict sessions, strict limits, and protection for your in-app rules.
- YouTube study mode for your chosen channels.
- A notification inbox that holds selected notifications and gives you summaries.
- Reports older than 7 days.
- A Plus wardrobe collection and two extra focus scenes.

You can keep using the free core without buying Plus. If Plus becomes unavailable, your saved data stays on your phone. Plus-only rules stay saved but inactive. A strict session already in progress runs to its end.

Your phone, your data
No Stillpoint account, no ads, and no tracking. Focus, blocking, and reports work offline. Your settings, focus history, app usage, and held notification text stay on your device. Google Play handles Plus purchases and purchase checks. Purchases and restoring Plus need Google Play and a connection.

Permissions you choose
Stillpoint uses Android AccessibilityService to detect the app on screen, browser addresses, and supported video screens. It uses the rules you choose to block distractions. It can cover blocked content, pause playback, and navigate away from it. Returning from a website block replaces that page with a blank page in the same tab. This screen data is processed on your device and is not sent to us.

Usage access measures app time for reports and limits. Optional notification access saves and dismisses notifications from apps you select. You can decline these permissions and still use the timer. You can turn access off in Android Settings.

Blocking depends on Android permissions and the screens other apps expose. Websites and short-video screens may stop matching after those apps change. Strict mode cannot prevent force-stop or uninstall.

Stillpoint is not a medical device. It does not diagnose, treat, cure, or prevent any medical condition. Consult a healthcare professional for medical advice.
```

Character count: 2647 / 4,000.

The accessibility paragraph is required in the listing. See [AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en).
It describes the audited screen reads, overlays, playback control, and return actions in `guard/**`.
The final paragraph covers the current relaxation guidance. See [health content requirements](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en).
See [content-rating.md](content-rating.md) before selecting the health declaration.

## Console fields

| Field | Value |
| --- | --- |
| Default language | English, United States, `en-US` |
| App or game | App |
| Category | Productivity |
| Tags | Select `Productivity` if offered. Add `Time management` or `Personalization` only if the current picker offers them and they describe the release. |
| Contact email | `SUPPORT_EMAIL` - replace with a monitored owner address |
| Website | `https://github.com/agneswd/Stillpoint` |
| Privacy policy | `PRIVACY_POLICY_URL` - host [privacy.md](../privacy.md) as public HTML |
| Pricing | Free app with one optional in-app purchase |

Tags come from Google's controlled list. A new account is not available to inspect that picker.
Do not invent a tag or choose unrelated tags to fill five slots. See [category and tag guidance](https://support.google.com/googleplay/android-developer/answer/9859673?hl=en).

## What's new template

```text
Version [version]
- Added [user-visible feature].
- Fixed [specific problem].
- Improved [specific behavior].
```

Character count: 109 before replacements. Keep the final text within 500 characters per language.
Delete unused lines. Include only changes present in that release. See [release notes](https://support.google.com/googleplay/android-developer/answer/9859348?hl=en).

## Copy gates

- Verify every free limit and Plus feature against the signed Play build.
- Confirm the new wardrobe and two scenes exist before publishing this description.
- Implement consent and timer-only use before claiming that users can decline permissions.
- Remove Android Settings and uninstall interception before using the protection wording above.
- Do not promise every browser, every social app, or tamper-proof blocking.
- Use task and timer screenshots first. Pebble supports the product; it must not market the app as a children's game.
- Keep prices out of the listing. The purchase screen must show Google's current local price.
