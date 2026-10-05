# In-app disclosure and consent requirements

Research checked on 2026-10-05. Source baseline: `6589341`.
This is a behavior and copy specification. The orchestrator owns layout and interaction design.
Use the existing app components. No implementation is included here.

## Policy basis

Stillpoint is not an accessibility tool. Its accessibility disclosure must stand alone and precede consent and system settings.
The disclosure must explain accessed data, its use, and transfers. A privacy-policy link or Android service description is insufficient.
See [accessibility disclosure requirements](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en).

For unexpected sensitive access, disclose before access begins. Require an explicit choice.
Back, Home, timeout, and outside taps must not count as consent.
See [prominent disclosure and consent](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en).

Google does not prescribe an exact button label. The labels below are this project's proposed clear choices.
The orchestrator can approve equivalent explicit labels. Generic `Continue`, `OK`, or `Got it` alone are unsuitable consent labels here.

## Accessibility

Show this disclosure when the user first asks to enable blocking.
Use the same gate from onboarding, Settings, block editors, and any later permission prompt.
Show it again after a material change in data use, before the new use starts.

Title: `Allow accessibility access?`

Draft body:

> Stillpoint uses Android accessibility access to see which app is open and read relevant text and controls on its screen.
>
> This can include website addresses, video titles, and channel names. Stillpoint uses this data to apply the blocking rules you choose.
>
> While access is on, blocking can work when Stillpoint is not open. Screen data stays on your device. We do not receive it.
>
> Stillpoint can cover blocked content, pause playback, and use buttons or navigation to leave it.
> It can close a blocked picture-in-picture video window.
> Returning from a website block replaces that page with a blank page in the same tab.
>
> Stillpoint is a focus app. It is not an accessibility tool for people with disabilities.
>
> You can turn this access off in Android Settings. You can use the timer without it.

Actions:

- Accept: `Agree and open settings`.
- Decline: `Not now`.
- An optional `Privacy policy` link must not replace either action or imply consent.

The body assumes removal of system settings interception described in [permissions.md](permissions.md).
Do not add consent text to justify preventing uninstall or revocation. Consent cannot make that use compliant.
Normal strict sessions may retain their in-app end time, but Android's stop and revoke controls must remain accessible.

## Usage access

Show a separate explanation before the first usage-access settings launch or protected usage query.
This requirement follows the background app-usage behavior in `UsageReader` and `GuardService`, not a dedicated Play permission form.

Title: `Allow usage access?`

> Stillpoint reads which apps you use, how long you use them, and phone unlock events.
> It saves daily totals for screen-time reports and app limits.
>
> It checks usage while your rules run, including when Stillpoint is not open. This data stays on your device.
>
> You can turn usage access off in Android Settings. The timer works without it. Usage reports and daily app limits need this access.

Actions: `Agree and open settings` and `Not now`.
Do not make this consent part of the accessibility disclosure.

## Notification access

Show this gate when the user chooses the notification inbox, before opening notification-listener settings.
Do not request it just to show Stillpoint's own timer notification.

Title: `Allow notification access?`

> Android gives Stillpoint access to notifications. Stillpoint saves the title, text, app, and time from the apps you select.
> It uses a notification identifier to update saved messages.
>
> After saving a selected notification, Stillpoint removes it from the notification drawer. You can read it later in the inbox.
>
> Holding works during focus and schedules, or all the time if you choose that setting. It works when Stillpoint is not open.
>
> Messages stay on your device until you clear the inbox or remove app data. We do not receive them.
>
> You can decline access or turn it off in Android Settings. The timer and app blocking still work.

Actions: `Agree and open settings` and `Not now`.
Explain that granting access does not import old notification history. `HoldListener` handles notifications posted after connection.
Do not promise capture of every notification. Android can withhold sensitive content, and the listener skips ongoing notifications and group summaries.

## Installed app list

Before first loading an app picker, explain the local inventory use in the picker flow.
`AppCatalog.launchableApps()` and `Browsers.browserPackages()` must not run before the relevant notice and user action.
No Android runtime permission exists for these scoped queries.

Draft:

> Stillpoint reads app names and icons to let you choose your blocks. It identifies browsers and home-screen apps to apply your rules.
> This list stays on your device.

Actions: `Choose apps` and `Not now`.
The first action can provide consent when this explanation immediately precedes it.
This is a conservative implementation requirement under the [User Data policy's installed-app guidance](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en).

## Other permission requests

| Request | Copy before the Android request | Choice and fallback |
| --- | --- | --- |
| Notifications | `Allow Stillpoint to show your focus timer, reminders, and inbox summaries?` | `Open notification settings` or a clear permission action, plus `Not now`. Preserve timer use. |
| Alarms and reminders | `Allow precise timing for planned focus and timer rounds. Without it, Android may delay reminders or ask you to start focus yourself.` | `Open alarm settings`, `Not now`. Use the existing inexact fallback. |
| Live Updates | `Show the running focus timer in Android's status bar when supported.` | Optional setting. Respect Android's promotion setting. Stop promotion when paused or finished. |
| Boot receiver | No separate consent dialog. | Explain schedule restoration in feature help. Restore only user-enabled plans. |
| Play Billing | Show the Plus features, one-time purchase, and live local price. | Explicit purchase action; cancellation preserves free use. Google's payment sheet handles payment consent. |

These requests do not create a separate mandatory sensitive-data video.
See [notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission),
[alarm access](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms), and
[Live Update requirements](https://developer.android.com/develop/ui/views/notifications/live-update).

## Acceptance checks for the implementation

1. Fresh install: start a timer after declining usage, accessibility, notification access, and notification permission.
2. Each relevant action shows its complete disclosure before launching Android settings.
3. Decline, Back, outside tap, and Home never save consent or open a permission screen.
4. Returning from Android settings without a grant leaves the feature disabled and the rest of the app usable.
5. A grant without current in-app consent does not activate sensitive reads or blocking.
6. An externally enabled accessibility service waits for consent in Stillpoint before applying rules or reading content.
7. Permission revocation stops affected work. It does not trigger an overlay or reopen the grant screen.
8. During strict focus and protected schedules, users can reach Android's disable, force-stop, uninstall, and service-stop controls.
9. Larger text and small screens show all copy and both actions. Scrolling never auto-accepts.
10. Each consent has a local version and decision. A new material use requires new consent.
11. Backup restore does not transfer consent or permission grants to another device.
12. Plus entitlement does not imply consent. Restoring Plus cannot enable a sensitive service by itself.
13. Notification access captures only selected apps after consent. Decline preserves existing inbox records without adding new ones.
14. App selectors explain inventory access before the first query. Cached data does not bypass a withdrawn choice.
15. English recording shows decline, retry, consent, system grant, blocking, and revocation on the actual release candidate.
16. Video recovery does not close an unrelated picture-in-picture window or activate unrelated system controls.

Keep a repeatable E2E report, consent-flow recording, and screenshots for these checks.
The docs task runs no emulator. The implementation owner must produce these artifacts.

## Current gaps

`ui/Access.kt` opens settings directly from `Allow` rows. It has no versioned consent gate.
Its accessibility sentence explains foreground detection but omits content reads and navigation actions.
The notification row omits message storage, cancellation, background operation, and retention.
Onboarding offers `Skip for now`, but that alone does not fix incomplete disclosures or later entry points.
No privacy-policy entry was found in the audited UI. Add one that opens the published policy through an external browser.
