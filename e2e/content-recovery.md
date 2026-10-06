# Content recovery checks

Run these checks on a disposable emulator. Save screenshots, the activity trace, and the crash log.

- Start focus, minimize it, and return to Home. Home must offer the current session, including while paused.
- Tap Start twice quickly. One session must exist, with its original start time and settings.
- Open focus setup, then start a planned session. Setup must close without offering a second session.
- Block example.com in Brave, Brave Beta, Chrome, and Firefox. A subdomain must also match.
- Type a blocked address without submitting it. Editing must stay available.
- Put a blocked domain in an allowed page's text. Page content must not count as the address bar.
- Dismiss a website block. Stay in the browser with a usable address bar and no blocked page visible.
- Reopen that browser, then visit an allowed site. No repeated block or launcher detour may occur.
- Try a browser toolbar with a different resource namespace. Detection must use native toolbar IDs.
- Open Shorts from YouTube search. Return to the existing results without restarting YouTube.
- Open Reels from an Instagram profile. Back should restore that profile before trying Home.
- Leave the target app during recovery. Pending actions must not click or navigate the new app.
- Disable a content rule while its block is open. Returning must not apply the old rule again.
- Traverse onboarding and leave during a cue sequence. No onboarding audio or delayed background haptics may play.
- Disable system touch feedback. Onboarding must respect that setting.

Third-party apps can hide their address bars and change their accessibility controls.
Record the installed app versions. Say whether the check used the real app.

## Checks in e2e.py

- `active_focus_controls`: Home offers the running session and the paused session. The session keeps its start time and settings.
- `website_block`, `browser_matrix`: Chrome, Brave, Brave Beta, and Firefox block example.com, open about:blank in the same tab, and stay open after a reopen. The check fails if the address editor or its suggestions stay open.
- `guard_reconnect`: the guard blocks a page that was open before it connected.
- `shorts_block`, `shorts_from_search`, `shorts_deep_link`: the Shorts tab returns to Home. A Short opened from search returns to the same results and does not restart YouTube. A cold Short link stays in YouTube.
- `plan_intent_from_other_app`: an intent with a plan id from another app starts nothing. The plan notification's intent starts the plan.
