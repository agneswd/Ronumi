# Google Play launch checklist

Research checked on 2026-10-05. Owner: agneswd. Package: `dev.agneswd.stillpoint`.
This checklist is preparation only. No developer account, Play product, test track, or store release was created for this task.
The audited source is `6589341`, before Play flavors and Billing. Complete the implementation gates before uploading a candidate.

## 1. Prepare the account

- [ ] Use a Google account controlled by the owner. Enable two-step verification and save recovery methods privately.
- [ ] Register a new **personal** Play Console developer account. The owner must be at least 18.
- [ ] Pay the one-time US$25 registration fee with an accepted payment method.
- [ ] Accept the Developer Distribution Agreement. See [account setup](https://support.google.com/googleplay/android-developer/answer/6112435?hl=en).
- [ ] Supply developer name, legal name, legal address, contact email, phone, and public developer email.
- [ ] Verify email and phone codes. Complete identity-document checks requested for the account's country.
- [ ] Link a payments profile with accurate legal details.
- [ ] Expect the legal name, country, and developer email to be public. Monetization also makes the full address public.
- [ ] Create the merchant payments profile, add the payout account, and complete bank and tax verification.

Use a monitored public address in place of every `SUPPORT_EMAIL` placeholder.
Do not publish invented contact details. See [personal account information and payments verification](https://support.google.com/googleplay/android-developer/answer/13628312?hl=en).
Answer trader and regional identity questions from the owner's real activity. A personal account does not establish non-trader status.
See [country-specific distribution requirements](https://support.google.com/googleplay/android-developer/answer/6223646?hl=en).

- [ ] Complete the Console mobile-app device check as the owner on a non-rooted physical device with Android 10 or later.
- [ ] Confirm the device-verification task disappears. An emulator cannot satisfy this requirement.

See [device verification](https://support.google.com/googleplay/android-developer/answer/14316361?hl=en).

## 2. Resolve the implementation gates

- [ ] Merge and review the `github` and `play` flavors under the `distribution` dimension.
- [ ] Keep the same application ID and version code across both flavors, as required by the brief.
- [ ] Remove system settings and uninstall interception from the shared guard behavior.
- [ ] Restrict picture-in-picture recovery to the blocked app. Verify unrelated video and system controls are unchanged.
- [ ] Implement and record the consent flows in [disclosure-spec.md](disclosure-spec.md).
- [ ] Keep `isAccessibilityTool` false. Validate the service's required capabilities.
- [ ] Verify the merged Play manifest has no `INTERNET`, `ACCESS_NETWORK_STATE`, or `REQUEST_INSTALL_PACKAGES`.
- [ ] Verify Play contains no updater UI, job, provider, download, or installer path.
- [ ] Verify the Play build includes Billing; verify GitHub contains no billing or paywall code.
- [ ] Confirm every free and Plus feature in the brief, including the extra wardrobe and two scenes.
- [ ] Confirm refund handling preserves data and lets an already-started strict session finish.
- [ ] Resolve foreground-service stopping, paused lifecycle, audio types, and Live Update promotion.
- [ ] Resolve the health declaration against the final copy and breathing guidance.
- [ ] Publish the privacy policy and add its in-app link.

The ranked evidence and policy sources are in [permissions.md](permissions.md).
The current phone-app target requirement is API 36 for new apps and updates, effective August 31, 2026.
The baseline already targets 36. `compileSdk 37` does not replace the target requirement.
See [target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en).

- [ ] Use supported Play Billing Library 8 or later. Version 7's normal submission deadline passed on August 31, 2026.
- [ ] Check [Billing's current support table](https://developer.android.com/google/play/billing/deprecation-faq) again before release.
- [ ] Inspect bundled native libraries, including transitive dependencies, for current 16 KB page-size support.

See [16 KB compatibility](https://developer.android.com/guide/practices/page-sizes).
Do not infer compliance from Kotlin-only application source.

## 3. Choose signing keys before the first upload

Play App Signing keeps the app signing key used for installed APKs. An upload key identifies bundles sent by the owner.
They serve different purposes. Losing an upload key can be handled through a reset; it must not replace the installed app identity.
See [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en).

| Choice | Effect | Recommendation |
| --- | --- | --- |
| Supply the existing GitHub release signing key to Play App Signing through Google's encrypted PEPK flow | Keeps the original signing identity, subject to Play's later signing upgrades and version rules. | Prefer this if switching builds without removing app data is an owner requirement. Back up the key first. |
| Let Google generate a different app signing key | Play and GitHub packages have different signing identities despite the same application ID. | Valid only if the owner accepts reinstall and backup/restore when switching distributions. |

Make this choice explicitly. Matching package names do not make differently signed APKs interchangeable.
Matching keys also do not guarantee automatic switching at equal version codes or through every installer.
Do not enable a signing-key upgrade without checking GitHub compatibility across supported Android versions.
The existing GitHub updater verifies signer sets. Keep the direct APK signing key safe and test any signing-lineage change.

- [ ] Record the GitHub signer fingerprint privately from the actual published APK.
- [ ] Choose the Play app signing identity before production or open testing fixes that choice.
- [ ] Generate a separate upload key outside the repository, using a password prompt or secret manager.
- [ ] Save encrypted offline backups of keys, aliases, certificates, and recovery instructions.
- [ ] Register the upload certificate in Play Console. Record both app-signing and upload SHA-256 fingerprints.

`app/build.gradle.kts` already reads these variables for release signing:

| Variable | Play bundle value |
| --- | --- |
| `STILLPOINT_KEYSTORE` | Absolute path to the private Play **upload** keystore |
| `STILLPOINT_STORE_PASSWORD` | Upload-keystore password from private secret storage |
| `STILLPOINT_KEY_ALIAS` | Upload-key alias |
| `STILLPOINT_KEY_PASSWORD` | Upload-key password from private secret storage |

For GitHub APK builds, those same variables must reference the existing GitHub signing key instead.
Do not overwrite a shared shell or CI secret set without checking which distribution it signs.
Do not commit keystores, passwords, shell history containing passwords, or exported private keys.

## 4. Create the app and register its package

- [ ] In Play Console, select Create app.
- [ ] Set the recommended name from [listing.md](listing.md) and default language `en-US`.
- [ ] Choose App, Free, and the required developer-policy declarations.
- [ ] Keep the base app free. Monetize only the `stillpoint_plus` unlock.
- [ ] Confirm `dev.agneswd.stillpoint` from the first bundle before accepting the package association.
- [ ] Finish Android developer verification and package registration shown on the account Home page.
- [ ] Check registration for the direct GitHub signing identity if that distribution also needs registration.

The September 30, 2026 package-registration deadline is already in effect.
Do not assume a new app inherited the automatic registration of older Play apps.
See [policy deadlines](https://support.google.com/googleplay/android-developer/table/12921780?hl=en)
and [Android developer verification](https://developer.android.com/developer-verification).
Follow the current Console signing-proof workflow; it can vary for packages already distributed outside Play.
See [app creation](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en).

## 5. Build the signed Play bundle

Load the upload signing variables privately. Use JDK 17 and the existing Android SDK configuration.
Run these commands after flavors exist:

```sh
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew assembleGithubDebug assemblePlayDebug assembleGithubRelease assemblePlayRelease lintGithubRelease lintPlayRelease bundlePlayRelease --max-workers=4 -Dorg.gradle.jvmargs=-Xmx2g
jarsigner -verify app/build/outputs/bundle/playRelease/app-play-release.aab
sha256sum app/build/outputs/bundle/playRelease/app-play-release.aab
./gradlew --stop
```

- [ ] Confirm the expected bundle path exists and the bundle is signed by the registered upload key.
- [ ] Inspect the merged manifest, dependencies, version code, and manifest Billing version metadata.
- [ ] Use a version code never previously uploaded for a different bundle. Increase both flavors together for each new release.
- [ ] Save the source commit, AAB hash, signing fingerprints, lint reports, and device evidence in the private release record.
- [ ] Retain the release mapping file so Play crash reports can be read.
- [ ] Upload only `app-play-release.aab`. Never upload the GitHub APK or the `e2e-driver` application.

The initial docs branch has no `bundlePlayRelease` task. Its baseline validation uses `assembleDebug assembleRelease lintRelease`.
That build does not prove the final Play package or Billing integration.

## 6. Create Stillpoint Plus

Current Console uses **Monetize with Play > Products > One-time products**.
The old In-app products help page redirects readers to the new model.
Use one Buy purchase option. Do not configure Rent, a subscription, a trial, or multi-quantity purchasing.
See [one-time product setup](https://support.google.com/googleplay/android-developer/answer/16430488?hl=en).

| Field | Value |
| --- | --- |
| Product ID | `stillpoint_plus` |
| Name | `Stillpoint Plus` - 15 characters |
| Description | `Unlock unlimited blocks and limits, website and short-video blocking, strict mode, notification inbox, full history, and extra Pebble items and scenes. One purchase. No subscription.` |
| Description count | 182 characters, below the legacy 200-character limit |
| Purchase option ID | `buy` |
| Purchase type | Buy |
| Content or service | Digital content, for an enduring software feature unlock; confirm the tax category in Console |
| Quantities and limits | One entitlement; no multi-quantity checkout |
| Trial, rent, subscription, introductory offer | None |
| Product and purchase option status | Active in every selected launch country where Plus is offered |
| Suggested Sweden price | SEK 59, one time |
| Suggested US local price | USD 5.99, one time; this is a deliberate local price, not a stated exchange conversion |

The suggested price supports a small utility while leaving a useful free core.
There is no server cost in the brief and no recurring service to justify a subscription.
These are launch recommendations, not a competitor-price survey or revenue forecast. The owner chooses the final price.

- [ ] Create the product and its Buy option with the exact immutable product ID.
- [ ] Set availability to match the app's launch countries.
- [ ] In regional pricing, select countries and use the price-generation tool with the chosen reference price.
- [ ] Inspect each generated local price, currency, rounding, and applicable tax treatment.
- [ ] Override local prices when needed for affordability or a clear price point.
- [ ] Save and activate the purchase option. Confirm product availability after propagation.
- [ ] Ensure the app uses the returned ProductDetails price and correct offer token. Never hard-code `59 kr` in the purchase button.
- [ ] Confirm the app acknowledges a completed purchase and never consumes `stillpoint_plus`.
- [ ] Confirm pending or cancelled purchases do not unlock Plus.
- [ ] Confirm the same Google Play purchaser can restore access without a Stillpoint account.

The new product model manages prices on purchase options; it does not use the old default-price field.
See [regional pricing and purchase options](https://support.google.com/googleplay/android-developer/answer/16430488?hl=en).
Completed purchases normally need acknowledgement within three days. Pending time does not start that acknowledgement period.
See [purchase processing](https://developer.android.com/google/play/billing/integrate).

## 7. License testers and internal test

- [ ] Add test Google accounts under account-level Settings > License testing.
- [ ] Add the same accounts to the internal track's tester list. These are separate settings.
- [ ] Upload the signed bundle, choose Play App Signing, enter release notes, and release to internal testing.
- [ ] Give testers the opt-in URL. Install through Google Play with the intended purchasing account.
- [ ] Confirm the purchase sheet says it is a test purchase before using a test payment method.
- [ ] Verify successful, declined, cancelled, pending-approved, and pending-declined purchases.
- [ ] Verify acknowledgement, duplicate-purchase prevention, restore, reinstall, offline use, and refund with revoke.
- [ ] Confirm lost Plus leaves data intact and Plus rules inactive. Confirm a running strict session can finish.
- [ ] Verify the free timer works without buying or granting sensitive access.

Internal testing supports up to 100 testers. It is optional and does not satisfy the personal account closed-test requirement.
Non-license testers can incur real charges, even on test tracks.
Unacknowledged license-test purchases can be refunded after about three minutes.
Use Play Console order refund and revoke to repeat a non-consumable test.
See [Billing tests](https://developer.android.com/google/play/billing/test)
and [test tracks](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en).

## 8. Prepare listing assets and public pages

- [ ] Publish [privacy.md](../privacy.md) at `PRIVACY_POLICY_URL` as public HTML.
- [ ] Confirm HTTPS, no sign-in, no geographic block, no public editing, and no PDF-only presentation.
- [ ] Replace `SUPPORT_EMAIL`; make the policy identity match the listing's app and developer.
- [ ] Open the policy from a signed-out browser and from the app's external-browser link.
- [ ] Paste the measured title and descriptions from [listing.md](listing.md).
- [ ] Choose Productivity and only valid, relevant tags from Console's picker.
- [ ] Create the following assets from the actual release UI with synthetic data.

| Asset | Required size and format | Stillpoint plan |
| --- | --- | --- |
| Store icon | 512 x 512 px; 32-bit PNG with alpha; at most 1,024 KB | Existing Pebble identity, without price or ranking claims. |
| Feature graphic | 1024 x 500 px; JPEG or 24-bit PNG without alpha | Pebble with the real focus timer. Keep text away from crop edges. |
| Phone screenshots | At least 2 overall; JPEG or 24-bit PNG without alpha; each dimension 320-3840 px; longest at most twice shortest | Produce 6 at exactly 1080 x 1920 px. |
| 7-inch tablet screenshots, if targeting tablets | Same general format and dimension bounds | Produce 2 at 1200 x 1920 px from an actual supported tablet layout. |
| 10-inch tablet screenshots, if targeting tablets | Same general format and dimension bounds | Produce 2 at 1600 x 2560 px from an actual supported tablet layout. |
| Optional public preview video | YouTube URL, public or unlisted, no ads, no age restriction | Portrait 1080 x 1920 recording is our capture target, not a mandatory Play pixel size. |
| Optional Plus product icon | 512 x 512 px; 32-bit PNG; at most 1 MB in the product form | A real Plus item or scene detail. Keep it consistent with the purchased content. |

Google permits up to eight screenshots per supported device type.
Four or more 1080 x 1920 portrait app screenshots meet the cited recommendation-format dimensions.
The chosen tablet sizes are capture targets, not mandated fixed resolutions.
Do not upload TV, Wear, Auto, or XR assets unless the app supports those form factors.
See [store asset specifications](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)
and [product asset fields](https://support.google.com/googleplay/android-developer/answer/1153481?hl=en).

Phone shot order: running focus, block rules, recent report, Pebble progress, Plus feature overview, optional inbox with dummy messages.
Label paid screens clearly as Plus. Avoid suggesting that paid features belong to the free core.
Keep the required policy videos separate from the optional marketing video.

## 9. Complete policy declarations

- [ ] Fill [Data safety](data-safety.md) from the final dependency and purchase audit.
- [ ] Fill [content rating and App content](content-rating.md), including reviewer access to Plus.
- [ ] Fill [permission declarations](permissions.md), with matching accessibility and FGS videos.
- [ ] Confirm the reviewer can open every video without requesting access.
- [ ] Verify repeatable free access to Plus for reviewers. Treat single-use promo codes as supplemental, not guaranteed reusable access.
- [ ] Include the verified restore path and any approved unused review codes in private review instructions.
- [ ] Complete all remaining Needs attention items and preserve submitted answers privately.
- [ ] Review policy status and resolve errors before closed release submission.

The privacy policy, declarations, videos, listing, and binary must describe the same behavior.
See [review preparation](https://support.google.com/googleplay/android-developer/answer/9859455?hl=en).

## 10. Run the closed test and pre-launch report

- [ ] Recruit 16-20 real testers and run [closed-testing.md](closed-testing.md).
- [ ] Keep at least 12 opted in for 14 continuous days before applying for production access.
- [ ] Keep feedback and fix evidence. Do not replace real engagement with opt-ins alone.
- [ ] Inspect the pre-launch report for each release candidate uploaded to closed testing.
- [ ] Review crashes, ANRs, accessibility, security, and device-specific layout findings.
- [ ] Supply a supported test script or entry-point instructions where automated navigation needs help.
- [ ] Check protected permission flows, Billing, browser detectors, and strict mode manually on physical test devices.
- [ ] Treat unvisited Plus or permission screens as coverage gaps. A green crawler result does not prove them.
- [ ] Keep the matching report, device evidence, and artifact identifiers in the release record.

See [pre-launch reports](https://support.google.com/googleplay/android-developer/answer/9842757?hl=en).
Google's crawler may not enable accessibility or notification access. Do not weaken consent or ship a hidden review bypass for it.

## 11. Apply, review, and launch

- [ ] Apply for production access after Console confirms closed-test eligibility.
- [ ] Answer from the actual test log using [the prepared application topics](closed-testing.md#production-access-application).
- [ ] Wait for approval. Continue testing and address requests for more evidence.
- [ ] Create the production release from the tested Play bundle. Do not silently rebuild a different candidate.
- [ ] Confirm countries, local Plus prices, reviewer access, rating, privacy URL, assets, and declarations.
- [ ] Resolve Console errors and inspect warnings. Save the complete submission evidence.
- [ ] Have the owner start production rollout when ready for publication.

The first production release has no percentage-based staged rollout. It reaches all eligible users in the selected countries after approval.
Use a deliberately small country set if the owner wants a limited first launch.
Use percentage staged rollouts for later updates, when available.
See [release and rollout rules](https://support.google.com/googleplay/android-developer/answer/9859348?hl=en).

- [ ] Confirm the live listing from an ordinary user account.
- [ ] Install from the public store and verify the installed version and signing identity.
- [ ] Confirm a real local-price purchase and restore through a suitable owner-controlled account.
- [ ] Check support links, the privacy page, Android vitals, purchase failures, and reviews during the first days.
- [ ] Uncomment the prepared Google Play README section only after the listing is public.

The public link will be `https://play.google.com/store/apps/details?id=dev.agneswd.stillpoint`.
Do not announce availability while only a test opt-in page exists.

## Owner inputs still needed

| Input | Where used |
| --- | --- |
| Legal identity, address, phone, payment profile, bank and tax details | Private Console account verification |
| `SUPPORT_EMAIL` | Listing, privacy policy, IARC, tester feedback |
| `PRIVACY_POLICY_URL` | Listing and in-app policy link |
| Existing release signer and chosen Play signing identity | App signing and distribution compatibility |
| Final local prices and countries | Product and release availability |
| Adult audience decision and health-content decision | Target audience, health form, listing |
| Final purchase implementation audit | Data safety and Billing purpose answers |
| Policy video URLs and unused review codes | Private review instructions |
| Real tester results and install forecast | Production-access application |

Recheck the [policy deadline table](https://support.google.com/googleplay/android-developer/table/12921780?hl=en) immediately before submission.
The new account does not yet exist, so this pack cannot verify its exact dashboard questions or review outcome.
