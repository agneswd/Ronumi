# Permissions and policy declarations

Research checked on 2026-10-05. Source baseline: `658934128c2280a1f0a249243ee3bb4afaf70add`.
Paths below are relative to `app/src/main/`, unless stated otherwise.
The baseline has one build and a GitHub updater. The brief requires a separate Play build with Billing and no updater.
These answers describe that intended release, subject to the explicit fixes below. Do not submit unverified declarations.

## Ranked launch blockers and review risks

| Rank | Evidence in the baseline | Risk | Required fix or proof |
| --- | --- | --- | --- |
| 1 - blocker | `GuardService.checkContent()`, `protectedScreens`, and `BlockKind.PROTECTION` block settings or installer screens containing Stillpoint's name. | Prevents users from disabling or uninstalling the service. Personal self-control is not a parental-control or enterprise exception. | Remove system settings and installer interception. Protection may lock Stillpoint's own rule editors. Keep system stop, revoke, force-stop, and uninstall routes open. |
| 2 - blocker | `ui/Access.kt` launches accessibility and notification settings from short `Allow` rows. | Missing complete, separate, affirmative disclosure for screen reads, navigation, and background message storage. | Implement [disclosure-spec.md](disclosure-spec.md) at every entry point and before data access. |
| 3 - blocker until flavor work lands | Manifest declares `REQUEST_INSTALL_PACKAGES`, `INTERNET`, `ACCESS_NETWORK_STATE`, `UpdateJob`, and `UpdateFileProvider`. | A Play app must not update its executable code through the GitHub installer. Stillpoint is not a qualifying package installer. | Exclude updater code, UI, jobs, provider, and these permissions from the merged Play artifact. Keep them only in `github`. |
| 4 - blocker until published | No privacy-policy UI link was found. | Sensitive-access apps need an accessible policy in the app and listing. A repository draft is insufficient. | Publish `docs/privacy.md`, replace the contact placeholder, add the in-app link, and verify the public URL. |
| 5 - review risk | `FocusService.notification()` hides End session in strict mode; `run()` stays foreground while paused. It plays audio under `specialUse`. | Foreground services must be stoppable and run only as needed. `specialUse` approval is not automatic. | Demonstrate Android's stop control on all supported versions. Resolve older-device stop access. Stop foreground work when paused unless needed. Audit audio typing and declare `mediaPlayback` when applicable. |
| 6 - declaration risk | `Onboarding.kt` offers better sleep and calm; `FocusUi.kt` gives breathing guidance. | A blanket no-health declaration may conflict with the app's content. | Use the health answers in [content-rating.md](content-rating.md), or have the orchestrator remove health positioning and guidance before selecting none. |
| 7 - review risk | Live Update promotion is requested whenever `liveFocusTimer` is true, including paused focus. | A paused session may not satisfy ongoing, time-sensitive activity criteria. | Request promotion only while a relevant session is running. Keep ordinary notifications as fallback. |
| 8 - release evidence gap | Plus, Billing, review access, and the extra assets are absent from this baseline. | Listing or purchase declarations would promise unverified features. | Validate the final Play build, product, reviewer access, and feature limits before submitting. |
| 9 - review risk | `dismissPip()` selects the first picture-in-picture window. `clickPipClose()` searches controls across window roots without checking the blocked package. | The recovery action can close an unrelated app's video instead of the chosen blocked content. | Match the video window to the blocked package. Recheck that match before delayed taps or clicks. Verify unrelated video and system controls remain unchanged. |

Ranks 1 and 3 follow the [sensitive API policy](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en)
and [device and network abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en).
The service concerns follow the [foreground-service declaration guidance](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en).
The health and Live Update findings are interpretations of the code, not Google rejection decisions.

All behavior fixes must follow the brief's shared-flavor rule unless the brief explicitly assigns a flavor difference.
Do not create Play-only protection semantics without the orchestrator's decision.

## AccessibilityService

Requirements: Play declaration, demonstration video, separate prominent disclosure, affirmative consent, and explanation in the store listing.
See [AccessibilityService API requirements](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en).

`res/xml/guard_service.xml` currently omits `android:isAccessibilityTool`. It does not claim to be an accessibility tool.
Set `android:isAccessibilityTool="false"` explicitly. Never set it to `true` for this general focus app.
Keep `canRetrieveWindowContent` only for the disclosed detectors.
`canPerformGestures="true"` supports browser address-control taps and taps that expose picture-in-picture controls. It is not unused.

| Declaration topic | Answer |
| --- | --- |
| Accessibility tool | No |
| Purpose | App functionality only |
| Personal or sensitive data collected or shared through accessibility | No, under the linked Data safety definition of off-device collection. Screen access occurs locally. |
| Conditional collected/shared data categories | Not applicable for accessibility. Do not claim that no sensitive information is accessed. |
| Disclosure and consent implemented | Yes only after the required implementation and recorded verification |
| Video | `ACCESSIBILITY_VIDEO_URL` |

The form links its collection question to Data safety definitions. If the live wording asks about access, answer Yes and describe local access.
This distinction does not remove the disclosure requirement.

Paste-ready feature explanation, after removal of system-settings protection:

> Stillpoint helps users apply their own focus rules. Accessibility detects the foreground app and selected website or video screens.
> The service reads app identifiers, relevant screen text, browser addresses, video titles, channel names, and control identifiers.
> It shows a block screen when a user-defined rule matches. It can pause blocked playback and leave the blocked screen.
> It can close a blocked picture-in-picture video window. Recovery actions must stay limited to the selected blocked content.
> After the user returns from a website block, it replaces that page with a blank page in the same tab.
> Feed recovery uses fixed navigation actions in the affected app. It does not generate plans or choose new goals.
> Data is processed on the device and is not uploaded or shared. Users can decline access and use the timer.
> Users can revoke accessibility access or uninstall Stillpoint through Android Settings at any time.

Paste-ready explanation of why narrower APIs do not cover the feature:

> UsageStatsManager supplies app usage for reports and limits. It cannot identify a short-video screen or the visible browser address.
> Accessibility supplies those screen details for the user's selected blocking rules. Notification holding uses NotificationListenerService separately.

Rule-based actions remain permitted in the current guidance. Autonomous planning is prohibited.
Do not describe the deterministic recovery code as autonomous decision-making.
The current helper can press Back, select a safe tab, set `about:blank`, tap an address control, and return Home on failure.
It can also tap and close a picture-in-picture window. Fix its package-matching gap before declaring that action limited to blocked content.
The declaration and video must cover those actions.

### Accessibility video shot list

Use the actual release candidate with English text and dummy data. Upload an unlisted, reviewer-accessible video.
Verify access in a signed-out browser. Do not use debug permission grants.

1. Open Stillpoint from its launcher icon. Show its version.
2. Open the blocking permission flow. Keep the full disclosure readable; scroll slowly if needed.
3. Select Not now. Start or open the timer to demonstrate continued use.
4. Request blocking again. Accept the disclosure, then enable the service in Android Settings.
5. Select a distracting app, start a short focus session, and open that app. Show the block screen.
6. Enable website blocking. Open a harmless test domain and show the block and blank-page return.
7. Enable a supported short-video block. Show the block, paused playback, and return within the app. Demonstrate picture-in-picture handling.
8. Show YouTube study mode if it ships. Include multi-window or home locking if those options remain available.
9. Start strict focus or a protected schedule. Open Android Settings and revoke access without interception.
10. Show the route to uninstall. Do not remove the recording device's data until the last shot.

Provide short captions for actions that are difficult to see. This shot list exceeds the policy minimum to document Stillpoint's actual behavior.

## PACKAGE_USAGE_STATS

The manifest declares special usage access. `usage/UsageReader.kt` reads Android usage events and app totals.
`Rules.decide()` uses those totals for limits. Reports keep daily totals locally.

No dedicated Play declaration form or video requirement was found for this permission.
Use a clear in-app explanation and consent before background usage access. Open Android's usage-access settings after acceptance.
See [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
and [sensitive data requirements](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en).

Paste into reviewer notes if requested:

> Stillpoint reads app usage to show local screen-time reports and enforce user-set daily app limits.
> App usage stays on the device. Users grant or revoke usage access in Android Settings.

Use the draft disclosure in [disclosure-spec.md](disclosure-spec.md). No usage data goes to Billing.

## NotificationListenerService

`notify/HoldListener.kt` requires Android's `BIND_NOTIFICATION_LISTENER_SERVICE` binding permission on the service.
This is not an app request for SMS or call-log permissions.
Android grants notification-listener access separately from `POST_NOTIFICATIONS`.

No separate Play listener declaration or mandatory listener video was found.
The User Data policy still applies to background private-message access.
Use the notification disclosure in [disclosure-spec.md](disclosure-spec.md), then open Android's notification-access settings.
See [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
and [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en).

Paste-ready explanation:

> Stillpoint holds notifications only from apps the user selects. It saves the package, notification identifier, title, text, and posting time locally.
> After saving, it dismisses the notification. Users can read or clear the local inbox and schedule summaries.
> Holding runs during focus or schedules, or continuously if the user selects that setting.
> Ongoing notifications, group summaries, and Stillpoint's own notifications are excluded. The app does not send message contents off the device.

Optional supporting recording: decline access, grant after disclosure, select a test app, receive a dummy message, open the inbox, clear it, revoke access.
Do not claim access to protected OTP content or promise to bypass Android's notification redaction.

## FOREGROUND_SERVICE and FOREGROUND_SERVICE_SPECIAL_USE

Requirements: correct manifest type and permission, `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`, Console declaration, justification, and demonstration video.
No separate runtime permission exists for `FOREGROUND_SERVICE_SPECIAL_USE`.
See [FGS declarations](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en)
and [service-use policy](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en).

Current manifest subtype: `Focus timer that blocks chosen apps until the session ends`.
`FocusService` keeps session timing and sound alive, updates an ongoing notification, and advances phases.
Actual blocking belongs to `GuardService`. Avoid implying that the foreground service alone detects screens.

Proposed accurate subtype for the implementation owner:

> User-started focus timer and Pomodoro phase tracking with an ongoing countdown; stops when the session ends.

| Console topic | Paste-ready answer after stop and lifecycle validation |
| --- | --- |
| Type | Special use |
| Use case | User-started focus sessions with a visible timer and phase transitions |
| Functionality | Stillpoint maintains a focus timer or stopwatch while the screen is off. It updates an ongoing timer notification and advances Pomodoro phases. |
| Start | The user starts a session or explicitly enables a schedule that starts one. The service displays an ongoing notification. |
| Impact of delay | Delayed start makes the chosen session start late and shows the wrong phase relative to the user's plan. |
| Impact of interruption | The live session loses timely phase transitions and notification updates. Optional focus audio stops. |
| Stop | The service stops when the session ends. Users can end ordinary sessions in the app or notification and stop the app through Android controls. |
| Why special use | The ongoing session controller is not a transfer, location, health-sensor, or communications task. It also runs with focus audio off. |
| Video | `FOREGROUND_SERVICE_VIDEO_URL` |

Review risk: an alarm plus a notification may suffice for some timer behavior.
The owner must justify why continuous work is necessary rather than asserting approval.
Foreground audio fits the defined `mediaPlayback` type. Audit whether the final service must declare both types and use them only when applicable.
Do not use `specialUse` to avoid an available, accurate type.
See [Android's foreground-service types](https://developer.android.com/develop/background-work/services/fgs/service-types).
Do not claim a paused session needs perpetual foreground work without evidence.
`FocusService` currently waits indefinitely while paused and retains its notification.

Video shots: start a timer, show the notification, leave the app, turn the screen off, show a phase transition, and end it.
Record a stopwatch and scheduled start. Record strict mode and the accessible Android stop path.
If declaring media playback, record starting and stopping the bundled sound as well.

## SCHEDULE_EXACT_ALARM and USE_EXACT_ALARM

Keep `SCHEDULE_EXACT_ALARM`. It requires user-granted special access on applicable Android versions.
`schedule/Plans.kt` already checks `canScheduleExactAlarms()` and falls back to `setAndAllowWhileIdle()`.
An inexact planned start becomes a reminder rather than an unsupported background service start.
No separate restricted `USE_EXACT_ALARM` declaration is needed while that permission is absent.
See [Android exact-alarm changes](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms).

Paste-ready explanation:

> Stillpoint uses exact alarms for timer phase boundaries and focus schedules that the user enables.
> Without access, it uses delayed alarms and may show a reminder to start focus. Users can decline or revoke access.

`Plans.refresh()` also schedules notification-summary delivery using exact alarms.
Review whether those summaries need exact timing. Use inexact delivery unless the user's precise-time expectation justifies it.

`USE_EXACT_ALARM` is restricted, not universally forbidden for focus apps.
The official permitted examples include timer apps. Stillpoint has a core timer, but broad blocking and summary delivery complicate eligibility.
This research cannot grant approval. For this launch, do not add it; retain the user-granted permission.
If the owner later seeks it, justify the core timer use through the live declaration and keep unrelated work out of exact alarms.
See [restricted exact-alarm policy](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en).

## POST_PROMOTED_NOTIFICATIONS

No dedicated Play Console permission declaration or video was found for this normal, non-runtime permission.
It allows Android to consider the running timer for a Live Update. It does not guarantee promotion.
Use only ongoing, user-initiated, time-sensitive activity. Respect user demotion and manufacturer rules.
See [Live Update criteria](https://developer.android.com/develop/ui/views/notifications/live-update).

Paste-ready settings explanation:

> Show the running focus timer in Android's status bar when supported. You can turn Live Updates off in Android Settings.

`FocusService.notification()` currently calls `setRequestPromotedOngoing(live)` during paused sessions too.
Restrict that request to active sessions. A focus countdown is a reasonable interpretation of the criteria, not an explicitly guaranteed use case.
No promotions, reward offers, completed sessions, or ordinary reminders belong in Live Updates.

## POST_NOTIFICATIONS

Use Android's notification runtime request on Android 13 and later. No separate Play form or video was found.
Request it for timer status, reminders, and summaries. Declining must not become consent to notification-listener access.
The service must still meet Android's foreground notification rules when notification display is denied.
See [notification permission behavior](https://developer.android.com/develop/ui/views/notifications/notification-permission).

Paste-ready rationale: `Allow Stillpoint to show your focus timer, reminders, and inbox summaries?`

## RECEIVE_BOOT_COMPLETED

No separate Play declaration, disclosure dialog, or video is required for the receiver itself.
`PlanReceiver` rebuilds alarms and checkpoints focus after boot, app replacement, time changes, or alarm-access grants.
It does not create a new user-selected rule at boot. Check any service start against current platform restrictions.
See [background foreground-service starts](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

Paste-ready explanation: `Stillpoint restores the schedules you enabled after your phone restarts.`

## Package visibility and queries

The app has no `QUERY_ALL_PACKAGES`. Scoped intent queries do not require its restricted declaration form.
Installed app information remains sensitive data even with scoped queries.
See [package visibility](https://developer.android.com/training/package-visibility/declaring)
and [package visibility policy](https://support.google.com/googleplay/android-developer/answer/10158779?hl=en).

| Query in the baseline | Code use | Paste-ready purpose |
| --- | --- | --- |
| `MAIN` + `LAUNCHER` | `AppCatalog.launchableApps()` | Show launchable app names and icons so users can select blocks, limits, and notification rules. |
| `VIEW` + `BROWSABLE` + `https` | `Browsers.browserPackages()` | Identify general web browsers for the user's website rules. The query does not make an Internet request. |
| `MAIN` + `HOME` | `AppCatalog.launchers()` | Identify home-screen apps for essential exclusions and the optional home-lock rule. |

Do not add `QUERY_ALL_PACKAGES` to simplify discovery. Keep the query scope tied to these features.
Audit any Billing service query merged by the Billing library separately; its purpose is binding to Google Play, not app inventory.
Use the installed-app notice in [disclosure-spec.md](disclosure-spec.md).

## Billing and absent restricted APIs

The final Play manifest needs `com.android.vending.BILLING`. Google's library normally merges this permission.
Complete product setup and show the Google purchase sheet. No sensitive permission video is required just for Billing.
See [Billing integration](https://developer.android.com/google/play/billing/integrate).

Do not declare SMS, call logs, VPN, location, microphone, contacts, broad storage, Health Connect, advertising ID, or full-screen intent access.
They are absent from the baseline and not required by the brief.
The block activity is not a `USE_FULL_SCREEN_INTENT` notification. Do not add that permission to explain an ordinary activity.

## Final artifact check

Inspect the merged release manifest and packaged code, not only the source manifest.
Confirm every permission, service, query, and dependency matches this document after the other launch branches merge.
Re-record videos after material changes. Preserve the reviewed version code and the matching bundle hash.
All links above were checked during this research. Live Console wording and review decisions can differ.
