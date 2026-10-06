# Feature map

Stillpoint is a focus app for Android. The mascot is Pebble.
Focus, blocking, reports, and rewards run on the phone. There is no account and no Stillpoint server.
Stillpoint has no cloud sync, social focus rooms, leaderboards, friends, subscriptions, ads, or analytics.

## Focus modes

You can start a countdown timer, a stopwatch, or a Pomodoro session.
A Pomodoro session uses focus rounds, a short break, and a long break after every fourth round.
You can choose 1 to 12 rounds. A short break can be 0 to 60 minutes. A long break can be 0 to 120 minutes.
A break length of zero skips that break.
You can pause a session that is not strict, and you can resume it.
If Android stops the process, the session continues from the last saved checkpoint.

Active focus measures time with the monotonic clock.
The service saves a checkpoint every 30 seconds and at each phase change.
A reboot can drop the time since the last checkpoint. Time while the phone is powered off does not count.
A later change to the wall clock cannot add focus minutes.

A new session keeps the reward date and the start hour from when it began.
An older session still takes those values from its timestamps and the current time zone.
An overnight session counts toward one reward date. Stillpoint does not split that session across calendar days.

## Planned focus

You can plan a session. Stillpoint starts it with a local alarm when Android allows exact alarms.
You can set a reminder. Snooze stores a deadline 10 minutes ahead.
Stillpoint turns each local time into an instant before it compares times.
A time that falls in a missing hour moves forward by that clock-change gap.
A local time that occurs twice runs once, at the first occurrence.
Notification delivery uses the same rule.
A reboot or a time change rebuilds the next alarm.
When Stillpoint refreshes alarms, it reads the next deadline first, then replaces each pending alarm.

A planned session can start again after the process stops. It can also finish while the screen is off.

## Focus controls

You choose the apps a session blocks, or you choose the apps it allows and block the rest.
You can name the session with a tag, write a note when it ends, and read a short summary.
Home lock sends you back to the timer when you open the launcher.
A strict session does not pause, and it does not end early.

## Scenes and sounds

The focus screen has five animated scenes: Lake, Dawn, Forest, Space, and Rain.
A scene change uses a short transition.
Dawn draws solid clouds. In Space, the planet sits above the controls. Rain draws pond ripples.
Theme labels sit outside the rounded scene images.

The sound list has six choices, in rows of three: off, white noise, pink noise, brown noise, rain, and waves.
The five recordings are bundled CC0 audio, so playback works offline.
Starting a sound fades it in. Stopping a sound fades it out.
A change fades the old sound out before the new sound starts.

## Goals and rewards

Study days, focus minutes, and finished sessions earn XP and levels.
Home shows four daily quests, chosen from 24 templates. Open a quest to read its rules.
A new day gets four quests. A day that already has an older session keeps that day's original three quests.
Each new session stores the quest-rule version. Older history keeps its old rules and its original XP.
Stillpoint adds the quest XP when the quest is complete.

There are 33 badges for focus time, sessions, and habits.
Progress can filter them by category, or by whether you have earned them.
Three badges stay hidden until you earn them.
A badge you have earned stays earned. A missed day does not remove it.
Rest days and streak freezes do not count as missed study days.

## Mascot wardrobe

Levels 1 to 20 unlock 36 colors, clothes, hats, and accessories.
One more outfit stays hidden until you pet Pebble enough times. The pet count is saved on the phone.
You can preview a locked item. You can wear one unlocked item in each slot. Wearing an item does not spend XP.
Stillpoint stores the worn item IDs in settings.
Before it equips an item, it checks the current level inside the same database transaction.
A closed hat covers Pebble's sprout. Open headwear leaves the sprout visible.

Pebble reacts when you tap.
The poses are idle, happy, proud, thoughtful, sad, sleeping, and waving. Each pose moves in its own way.
Recent focus habits set the mood on Home.

## Blocking

### Apps

During focus, Stillpoint blocks the apps you chose.
You can instead allow only those apps and block the others.
Essential apps stay available in that mode.

The Blocks screen can pause schedules and app limits for 10 minutes.
Focus blocks stay in place during that pause.
Stillpoint refuses the pause during a strict focus session.
It also refuses the pause while protection is on during focus or a schedule.
A pause that started earlier does not open a schedule while protection is on.

The block throttle uses elapsed time.
A backward clock change cannot suppress enforcement until the old wall time returns.

### Schedules

A schedule blocks a list of apps, or every app except a list, on the days you choose.
A schedule can cross midnight.
You can pick an icon, or let the icon follow the start time.
When you change a schedule, Stillpoint checks the active protection again.

### Limits

A limit can be gentle or strict.
A gentle limit can grant a counted pass of five minutes.
A strict limit ignores a pass, including a pass saved while the limit was gentle.
You can set a reminder. Stillpoint tracks a streak of days inside the limit.
If you raise the limit, or switch it from strict to gentle, Stillpoint asks you to confirm.

### Websites

You can block domains and their subdomains, block a built-in list of adult domains, or allow only the domains you list.
When you leave a blocked site, Stillpoint opens a blank page in the same tab.
Stillpoint reads the address bar in Chrome, Brave, Firefox, Edge, Samsung Internet, Opera, Vivaldi, DuckDuckGo, and other browsers that open web links.
Detection uses the text and controls those apps expose to the accessibility service. An app update can change that.

### Short videos

Stillpoint can close YouTube Shorts, Instagram Reels, Snapchat Spotlight, and Facebook Reels without leaving the app.
If the feed is a tab, or it is the first screen of this visit, Stillpoint selects another tab.
Otherwise it goes back to the page that opened the feed, such as YouTube search results.

You can allow one identified video on each visit to an app.
Snapchat and Facebook do not expose a title id, so they block every video.
Any feed without a title id does the same.
A video that still has no title after 1.5 seconds is blocked.

YouTube study mode lets you choose channels and can block the home feed.
An unknown channel stays blocked in a player Stillpoint recognizes.

### Strict mode

Strict mode locks the Blocks tab, the schedule editor, and supported system settings while focus or a schedule is running.
You can also block picture-in-picture and a second app window during that lock.
Android permissions and the device manufacturer limit what this can stop.
Strict mode cannot stop force-stop, safe mode, or every way to uninstall the app.

### Notification inbox

You choose which apps to hold.
Stillpoint can hold their notifications during focus and schedules, or all day.
You can schedule a private summary.
Focus notifications, planned reminders, and inbox summaries each have their own switch.
A held-message count increases only for a new inbox key.
An update to an existing key on a later day does not increase that day's count.
Notification text stays on the phone until you clear it or remove app data.

## Reports

Reports cover a day, a week, or a month, and they can use your session tags.
They show a daily average, productive time, distracting time, other app time, unlocks, held-message counts, and time saved against a baseline.
Time saved appears after seven recorded complete days.
Usage records stay on the phone.

The screen shows all three categories and their total.
You choose which apps count as productive. Productive choices take priority over the focus block list.
The focus block list defines distracting apps.
In allow-list mode, the apps you chose count as allowed. Other apps count as distracting, except essential apps.
Usage with no category, and usage of essential apps, stays uncategorized.
The report still shows the other apps and the full screen-time total for the period.
Category time and the screen-time total can differ, because the total includes uncategorized time.

On Progress, productive apps use green bars, distracting apps use red bars, and other apps use neutral bars.
The total screen-time chart is neutral, because it includes every category.
You can tap a day in the last week to see which apps you used.

## Widgets

Stillpoint has screen-time, focus, goal, and calendar widgets.
A widget refreshes after a database commit.
On Android 16 and later, the focus timer also shows in the status bar.

## Backups

You can export a portable backup protected by a password.
The export asks for at least 12 characters. Stillpoint does not save the password, and it cannot recover it.
Restore checks the file authentication and the records before it changes stored data.
A restore keeps the pass counts already on that phone, expires active passes, and does not refill the daily pass budget.
A person who knows the password can still edit the records. Encryption does not make an offline clock trustworthy.
Stillpoint accepts only its encrypted backup format. It does not import older plaintext JSON files.
The backup omits held message text, the active session, and temporary passes.

Validation accepts a local day longer than 24 hours, up to 48 hours.
The schema 6 migration copies an existing phase's wall-clock progress once.
After that copy, a wall-clock change cannot add focus minutes.
A freeze milestone is not granted again after the clock moves backward.

## Setup and appearance

Setup asks a few questions, lets you choose apps, explains the permissions, and can start an optional first session.
Onboarding uses vector icons.

You can follow the phone theme, or choose Light or Dark.
A theme change applies to the app, to dialogs, and to the block screen. It does not recreate the activity.
Settings keeps its scroll position.

## Privacy and network use

Stillpoint keeps settings, focus history, screen time, and held messages on the phone.
It does not upload them. Automatic Android backup is off.

The GitHub version can check GitHub for an update. The check is optional.
Automatic checks start on, and they run about once a day when Android allows. You can turn them off.
A download starts only when you choose Download.
Installation starts only when you choose Install, and Android asks you to confirm.
An update request does not include settings, focus history, or held messages.
Focus and blocking work with no connection.

The Play version uses Google Play for the purchase only. It does not check GitHub for updates.

## GitHub and Google Play

The GitHub APK includes every feature.
The Google Play version is free, with a one-time Plus unlock that supports development.
The two builds use the same application id. They are signed with different keys, so Android cannot install one over the other.
To switch, export an encrypted backup, uninstall the old app, install the other build, and restore the backup.
A backup does not move Plus ownership. After you install the Play build, restore the purchase from Google Play.
