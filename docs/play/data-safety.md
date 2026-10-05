# Data safety answers

Research checked on 2026-10-05. Source baseline: `6589341`.
This is the answer sheet for the planned `play` flavor. It excludes the GitHub-only updater.
The baseline contains no Billing dependency or purchase implementation. Billing rows are release requirements, not a completed source audit.
Before submission, verify the exact Billing SDK, merged manifest, purchase token path, and exported backup behavior.

## Scope and billing decision

Google's form covers off-device collection, including SDK traffic, and transfers to other apps.
Local processing is not collection. Certain user-requested transfers are exempt from sharing.
The declaration covers all versions distributed through Play, not the separate GitHub distribution.
See [Data safety definitions and payment-service guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).

Use **Yes, Purchase history only** for collection in this launch pack.
This is a conservative interpretation for Billing purchase tokens and acknowledgement exchanged with Google Play.
Google's payment exception excludes card information the app never accesses. It is not a blanket exemption for purchase history.
The no-INTERNET manifest does not establish that Billing has no data exchange; the Google Play app uses its own connection.

Use **No** for sharing. The purchase flow is user-requested and limited to providing the purchase and entitlement.
Do not treat Google as a service provider for every independent purpose without evidence.
If the final SDK transmits additional diagnostics or identifiers outside payment processing, add those types and purposes before submission.
If its only relevant processing qualifies for the payment exception, document that evidence before changing collection to No.
There is no reviewed SDK-specific disclosure in this baseline that justifies a blanket no-data answer.

Billing receives purchase objects, checks ownership, and acknowledges purchases.
See [Billing purchase processing](https://developer.android.com/google/play/billing/integrate).
Google handles payment details under [its privacy policy](https://policies.google.com/privacy).

## Main form and security questions

The public help page and its sample CSV do not expose every current account-specific branch of the form.
The table covers the published questions and known account, deletion, and badge branches.
Match the live meaning if Console changes its labels. Do not submit the example CSV's sample answers.

| Question or field | Answer to select or enter | Reason |
| --- | --- | --- |
| Required data types collected or shared? | Yes | The planned Play purchase and acknowledgement path exchanges purchase information with Google. |
| All collected data encrypted in transit? | Yes, after release verification | The only declared off-device flow is Google Play Billing; no custom cleartext purchase endpoint is allowed. |
| Account creation methods | My app does not allow users to create an account | Stillpoint has no registration, login, password, OAuth, or app account backend. |
| Can users log in with an account created outside the app? | No | A Play Store account is used by Google Play; Stillpoint offers no account login. |
| Account deletion web link | Not applicable; leave hidden or blank | Stillpoint has no app accounts. Do not create a fictitious account-deletion service. |
| Data deletion request mechanism | No | No service exists to erase Google's purchase records. Clearing local storage does not delete those records. |
| Automatically delete all collected data within 90 days? | No | Google retains transaction records; Stillpoint cannot promise their removal within 90 days. |
| Data deletion URL, if requested only after Yes | Not applicable | No developer-operated purchase-record deletion endpoint exists. |
| Privacy policy URL | `PRIVACY_POLICY_URL` | Publish [privacy.md](../privacy.md) with the owner's real contact address first. |
| Independent security review | No | No Google-authorized MASA review is evidenced. |
| Families policy commitment badge | No; do not opt in | The recommended audience is adults; see [content-rating.md](content-rating.md). |
| UPI verification badge | No; do not opt in | Stillpoint is not a UPI payment app. |
| Final accuracy confirmation | Accept only after the final artifact audit | This document cannot certify code that has not been integrated. |

Account deletion requirements apply to apps offering account creation.
Local data remains removable through Android. This is different from promising deletion of payment-provider records.
See [account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en).

## Every data type

`No / No` means do not select that type as collected or shared.
The reasons below describe Stillpoint's app paths. Review new SDK paths separately before release.

| Category | Data type | Collected / shared | Code-grounded reason |
| --- | --- | --- | --- |
| Location | Approximate location | No / No | No location permission or geolocation code; no app-side IP-location profiling. |
| Location | Precise location | No / No | No location sensor access. |
| Personal info | Name | No / No | Optional onboarding name stays in local settings and encrypted backups. |
| Personal info | Email address | No / No | No login or email collection in the app. User-initiated external support is separate. |
| Personal info | User IDs | No / No | No Stillpoint account identifier. Classify purchase tokens under purchase history for this integration. |
| Personal info | Address | No / No | Stillpoint does not receive billing addresses through BillingClient. |
| Personal info | Phone number | No / No | No phone-number read or upload. |
| Personal info | Race and ethnicity | No / No | No structured collection or export to a server. |
| Personal info | Political or religious beliefs | No / No | No structured collection or export to a server. |
| Personal info | Sexual orientation | No / No | No structured collection or export to a server. |
| Personal info | Other personal info | No / No | Goals and local settings are not uploaded. |
| Financial info | User payment info | No / No | Google collects card and bank details directly; Stillpoint does not access them. |
| Financial info | Purchase history | Yes / No | Planned Billing flow checks and acknowledges the one-time Plus purchase through Google Play. |
| Financial info | Credit score | No / No | No credit feature or API. |
| Financial info | Other financial info | No / No | No income, debt, or other financial feature. |
| Health and fitness | Health info | No / No | Wellness prompts do not upload health records; notification text also stays local. |
| Health and fitness | Fitness info | No / No | No physical-activity sensor or Health Connect integration. |
| Messages | Emails | No / No | Email notification snippets can enter `HeldNotification`; they remain on the device. |
| Messages | SMS or MMS | No / No | Message notification snippets can be held locally; no SMS API or transmission. |
| Messages | Other in-app messages | No / No | `HoldListener` saves selected titles and text locally; no message upload. |
| Photos and videos | Photos | No / No | No photo upload or media-library access. |
| Photos and videos | Videos | No / No | Screen detection reads UI nodes, not recorded video. |
| Audio files | Voice or sound recordings | No / No | No recording permission or microphone use. |
| Audio files | Music files | No / No | Focus audio is bundled with the app, not read from the user's library. |
| Audio files | Other audio files | No / No | No user audio collection. |
| Files and docs | Files and docs | No / No | Only user-selected encrypted backup import/export; no developer-readable transfer. |
| Calendar | Calendar events | No / No | Schedules use Room, not the Android calendar provider. |
| Contacts | Contacts | No / No | No address-book access. A held notification may contain a name but stays local. |
| App activity | App interactions | No / No | Sessions, progression, and daily totals remain in Room; no analytics SDK. |
| App activity | In-app search history | No / No | App-picker filtering and rule editing do not transmit searches. |
| App activity | Installed apps | No / No | `AppCatalog` uses scoped package queries locally. |
| App activity | Other user-generated content | No / No | Task names, notes, domains, and channel lists remain local. |
| App activity | Other actions | No / No | `GuardService` evaluates user rules locally. |
| Web browsing | Web browsing history | No / No | `Browsers.browserHost()` reads an address for matching; no browsing-history upload. |
| App info and performance | Crash logs | No / No | No crash-reporting SDK or upload endpoint in the audited app. |
| App info and performance | Diagnostics | No / No | No app telemetry upload. Verify the final Billing SDK does not add a separate reportable flow. |
| App info and performance | Other app performance data | No / No | No performance collection service. |
| Device or other IDs | Device or other IDs | No / No | No advertising ID or device-tracking SDK. Verify final dependencies. |

The table's sensitive local fields still need the privacy policy and consent described in [disclosure-spec.md](disclosure-spec.md).
No-data entries never mean that Stillpoint cannot see those fields on the phone.
Android or Google Play can collect platform diagnostics under their own settings. Do not confuse those with an app-installed telemetry SDK.

## Purchase history follow-up questions

| Question | Exact answer | Reason |
| --- | --- | --- |
| Collected, shared, or both? | Collected | Purchase state and acknowledgement travel through Play Billing. |
| Processed ephemerally? | No | The purchase remains a durable entitlement and transaction record. |
| Required or optional? | Users can choose whether this data is collected | Users can use the free core without buying Plus. Purchase records remain necessary after a purchase. |
| Collection purpose: App functionality | Select | Grant, acknowledge, check, and restore Plus. |
| Collection purpose: Analytics | Do not select | The brief prohibits analytics. |
| Collection purpose: Developer communications | Do not select | Purchase data does not drive messages from the developer. |
| Collection purpose: Advertising or marketing | Do not select | No ads or marketing profiles. |
| Collection purpose: Fraud prevention, security, and compliance | Select | The release must validate purchase authenticity before granting Plus. Confirm this use in the final integration. |
| Collection purpose: Personalization | Do not select | Plus access is a purchase entitlement, not profiling. |
| Collection purpose: Account management | Do not select | No Stillpoint account exists. |
| Sharing purposes, all seven options | Not applicable; leave unselected | The declared purchase transfer falls under the user-requested sharing exception. |

Release owner action: verify purchase validation in the final implementation before submitting these two selected purposes.
Google's independent fraud checks alone would not establish a Stillpoint data purpose.

## Backup and support boundaries

`data/Backup.kt` exports encrypted files through Android's file picker. `BackupCrypto.kt` uses a password-derived key.
The developer does not possess that key. A user can select a cloud file provider.
This is not a silent upload to Stillpoint. Confirm that the final export remains encrypted and user-requested.
Held message text is excluded from export. Automatic Android backup is disabled in the manifest.

Do not add an in-app support uploader without updating this sheet.
Email and GitHub issue links open external services at the user's request.
Ask users not to include notification text, private backups, or payment credentials in reports.

## Submission evidence

- Save the exact version code, source commit, dependency list, and merged Play manifest.
- Record purchase, restore, acknowledgement, and refund behavior with license testers.
- Confirm no analytics, crash, advertising, server-verification, or external update endpoint appears in Play dependencies.
- Inspect the Billing SDK's current provider guidance through the [Google Play SDK Index](https://play.google.com/sdks).
- Verify the draft privacy policy matches real local storage and the final Billing exchange.
- Preview the label, submit the form, then export its CSV for the private launch record.
- Keep the CSV current when any shipped Play version changes its data behavior.
