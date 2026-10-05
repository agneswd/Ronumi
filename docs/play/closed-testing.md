# Closed test and production access

Research checked on 2026-10-05. Applies to the owner's planned new personal developer account.
This plan starts after account, device, app, and policy setup. No test has started during this documentation task.

## Current rule

Personal accounts created after November 13, 2023 need a closed test before production access.
At application time, at least 12 testers must have remained opted in continuously for the preceding 14 days.
Internal testing does not count. Open testing becomes available after production access.
Google reviews engagement and readiness as well as the numerical threshold.
See [new personal account testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).

Invitation is not opt-in. Each tester must open the test link with the authorized Google account, join, and install from Play.
Joining a Google Group or sideloading the GitHub APK does not complete the Play opt-in.
Opting out and back in restarts that tester's continuous period.
The official rule does not specify daily launch counts, minimum minutes, or mandatory daily screenshots.
Do not buy inactive opt-ins or report invented engagement.

Recruit 16-20 people to allow for dropouts. Keep at least 12 eligible testers enrolled until access is granted.
This buffer is our recommendation, not Google's requirement.
Use real users with Android 9 or later and access to Google Play. Include different manufacturers and Android versions.
Use the [closed-track setup instructions](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en).

## Before day 1

- Finish the policy blockers in [permissions.md](permissions.md).
- Run internal testing and resolve purchase, crash, consent, and data-loss failures.
- Create one closed track. Add an email list or Google Group and a feedback address.
- Complete listing, policy forms, country availability, and the closed release review.
- Wait until the release is available. Send the opt-in URL with installation instructions.
- Ask internal testers to leave internal testing before joining closed testing. Verify their installed version and track.
- Add billing testers to account-level license testing separately. Track membership alone does not make purchases free.
- Record each opt-in time privately. Use tester aliases in shared reports.
- Start the plan when at least 12 testers have joined and can install the intended Play version.

The shared package ID can conflict with an installed GitHub APK signed with another key.
Ask affected testers to export an encrypted backup before any uninstall. Explain what that backup excludes.
Never ask testers to delete their only copy of history to satisfy a test schedule.

## Day-by-day plan

Days are measured from the qualifying opt-ins, not the upload date. Apply on day 15 or later after 14 full days have elapsed.
Use the Console eligibility result as the final timing check.
Every day, review enrollment and feedback. Keep the same track open when shipping fixes.

| Day | Tester request | Owner evidence |
| --- | --- | --- |
| 1 | Install through Play. Decline optional permissions and start a timer. Then enable chosen permissions through disclosures. | Opt-in times, version codes, device list, setup feedback. |
| 2 | Finish a timer and stopwatch. Try pause, screen off, and returning later. | Timing errors, crash reports, screenshots with dummy data. |
| 3 | Use Pomodoro with breaks. Test widgets and notification controls. | Phase-transition and widget results. |
| 4 | Select up to five focus apps. Test the sixth-app Plus boundary. | Free-core limit and purchase-prompt results. |
| 5 | Set the free app limit. Use a gentle pass and reach the daily limit. | Limit timing and pass results. |
| 6 | Set the free schedule. Reboot and test exact-alarm denial. | Scheduled-start and delayed-reminder results. |
| 7 | Use the app normally. Review reports, daily progress, wardrobe, sounds, and scenes. | Midpoint feedback summary and open issue list. |
| 8 | License testers buy Plus with test payment, cancel, and use a declined payment. | Billing screenshots, acknowledgement result, free-core survival. |
| 9 | Test pending purchases, restore, reinstall on a backed-up test device, and offline use after purchase. | Entitlement and restore evidence. |
| 10 | Test website and short-video blocks in supported installed apps. Test study mode. | App versions, successful detectors, specific compatibility gaps. |
| 11 | Hold dummy notifications, change selections, restart, read summaries, and clear the inbox. | Privacy-safe inbox and duplicate-delivery results. |
| 12 | Export and restore an encrypted backup. Check large text, dark theme, and small screens. | Restore results, layout defects, no private exported files. |
| 13 | Test strict sessions, protection, service revocation, system stop, and uninstall routes. Refund/revoke Plus on a test account. | Proof that Android controls remain open; data survives lost Plus. |
| 14 | Retest fixed issues on the latest closed build. Collect final usability feedback. | Final issue disposition, coverage gaps, matching version code. |
| 15 or later | Remain opted in and continue normal use. | Confirm 12 continuous 14-day opt-ins, then apply for production access. |

Updates on the same closed track do not inherently require a new 14-day enrollment period.
Substantial changes still need enough real testing to support a truthful readiness answer.
Do not apply while a data-loss, consent, purchase, or system-control blocker remains.

## Finding testers

Start with friends, colleagues, and adult students who already want a focus app.
Ask in Android, open-source, and productivity communities that permit test invitations.
Explain the permissions before recruiting. Testers must not need to expose private messages to participate.
Do not offer rewards for positive ratings or ask for production reviews during a closed test.

Invitation draft:

> I'm testing Stillpoint, an Android focus timer and app blocker with a companion called Pebble.
> I need people who can install it through Google Play and stay in the closed test for at least 14 days.
> Use it for real tasks and send short notes about anything confusing or broken.
> The timer works without sensitive permissions. Blocking and the optional notification inbox need separate Android access.
> Use dummy messages for inbox tests. I will explain free test purchases before you try Plus.
> Reply if you want the opt-in link and test instructions.

This is a draft for the owner. No invitations were sent.

Feedback template:

```text
Tester alias:
App version and version code:
Phone and Android version:
Feature:
Steps:
Expected result:
Actual result:
How often it happens:
Screenshot or recording with private data removed:
```

Keep a private log with date, tester alias, build, feature, report, decision, fix, and retest result.
Export a short summary for the production application. Do not put tester addresses or private notifications in the repository.

## Production access application

Open Dashboard > Apply for production after Console confirms eligibility.
The current public guidance groups questions into test experience, the app, and readiness.
Wording and character limits may vary in the live form. Prepare the following answers from actual evidence.
See [the production-access questions](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).

| Topic Google asks about | Answer or fill-in draft |
| --- | --- |
| How easy was recruitment? | Select the truthful option. Do not preselect an experience that has not happened. |
| Recruitment details, if asked | We recruited [number] adult Android users through [actual channels]. At least [number] remained opted in continuously for [days]. |
| Engagement and feature coverage | [Number] testers used [features] for [real tasks]. We also assigned purchase, restore, consent, and notification tests. [State gaps.] |
| Whether usage matched production | Regular use involved timers and app blocks during [work/study examples]. Billing failure tests were deliberate checks, not typical daily use. |
| Feedback and collection method | We received [number] reports through [actual channel]. Main findings were [findings]. We fixed [issues] and retested them on [build]. |
| Intended audience | Adults who want a local focus timer and limits on distracting apps during work, study, or personal tasks. |
| Value provided | Stillpoint combines offline focus sessions, user-set blocks, local reports, and earned companion rewards. The free core works without an account or ads. |
| Expected first-year installs | Owner estimate. A small initial launch can reasonably plan for 0-10,000 if that matches the live ranges and the owner's distribution plan. |
| Changes after testing | We changed [specific behavior] after testers reported [problem]. Retesting on [version] showed [observed result]. |
| Why ready for production | The release passed [actual device checks]. Purchase, restore, disclosure, revocation, and data-retention checks passed. [State remaining supported-app limits.] |
| What changed since a previous application, if asked | [Actual fixes, additional testers, new test duration, and retest evidence.] |

Do not paste placeholders or invent results. Read each answer back against the private log before submitting.
Google usually reviews production-access requests within seven days, but it can take longer or require more testing.
Access approval enables production submission. It does not approve the app's policies or publish the app automatically.
