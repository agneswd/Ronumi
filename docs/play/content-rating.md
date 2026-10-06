# Content rating and App content answers

Research checked on 2026-10-05. Source baseline: `6589341`, with the Play product brief applied.
Use these answers for the final release only after checking its text, artwork, purchases, and linked content.
Console and IARC questions change according to previous answers. The owner must answer any new branch from the actual release.

## IARC content rating

Start App content > Content ratings. Use `SUPPORT_EMAIL` for rating correspondence.
Select the utility category, usually `All Other App Types`. Stillpoint is not a game, social network, or entertainment-content service.
Google obtains territory-specific ratings from IARC answers. Do not promise an ESRB or PEGI result in advance.
See [Google's rating questionnaire guidance](https://support.google.com/googleplay/android-developer/answer/9859655?hl=en).

| Question topic | Answer | Reason |
| --- | --- | --- |
| Downloaded content includes violence or injury | No | Pebble and the focus scenes contain no combat or injury content. |
| Blood or gore | No | None in bundled artwork or text. |
| Fear or horror | No | Calm focus scenes and mascot reactions are not horror content. |
| Sexual content or nudity | No | No such images, stories, or videos are supplied. |
| Sexual references | No for supplied content | The adult-site filter prevents access; it does not supply adult entertainment. Check the final visible strings and examples. |
| Profanity or crude language | No | No such authored content is present in the audited UI. |
| Drugs, tobacco, or alcohol | No | No related feature or promotional content. |
| Gambling, simulated gambling, or betting | No | Badges and clothes are earned from deterministic progress, not wagering. |
| Real-money prizes or cash-out | No | XP and wardrobe items have no cash value. |
| Criminal or discriminatory content | No | No such bundled material or functionality. |
| Users exchange content or communicate through the app | No | No messaging, social feed, sharing server, or chat. The inbox is a private local copy of Android notifications. |
| User interaction through voice or video | No | No calls or recording. |
| Shares the user's precise location with other users | No | No location permission or location sharing. |
| Unrestricted web access or web browser | No | Stillpoint detects another browser's address. It does not embed an unrestricted browser. |
| Online content not included in the download | No for developer-provided content | Scenes and audio are bundled. Plus unlocks local features; it does not fetch a media catalog. |
| Access to user-provided or external content, if phrased broadly | Yes | The inbox displays notifications from other apps; notes and imported backups can contain user text. |
| Digital goods or in-app purchases | Yes | One fixed, non-consumable `stillpoint_plus` unlock. |
| Paid randomized items or loot boxes | No | The purchase has known features and wardrobe content. |
| Subscription or recurring purchase | No | No subscription or trial. |
| Ads | No | No advertising SDK or ad placement. |
| Generative content | No | Pebble uses authored text and local rules. |

The notification inbox is the main rating ambiguity.
Do not answer a broad external-content question No merely because the app makes no network requests of its own.
If IARC groups private notification display with messaging or unfiltered user content, use that branch and explain the local-only behavior.
Use this note when seeking a rating clarification:

> Stillpoint is a local focus utility. It has no chat or public content feed.
> With permission, it saves text from selected Android notifications for the same device owner to read later.
> It does not let users send messages or access a hosted content catalog.

Keep the completed questionnaire and rating certificate. Update the answers when features or content change.

## Target audience

Recommended launch selection: **18 and over only**, for adults managing work, study, and personal phone use.
This is a recommendation for the owner, not an age restriction present in the code.
Do not select younger groups merely to maximize distribution.
Do not select adults solely to avoid Families rules if the actual product or marketing targets children.

| Console field | Proposed answer |
| --- | --- |
| Ages 5 and under | Not selected |
| Ages 6-8 | Not selected |
| Ages 9-12 | Not selected |
| Ages 13-15 | Not selected for the adult launch |
| Ages 16-17 | Not selected for the adult launch |
| Ages 18 and over | Selected |
| Designed primarily for children | No |
| Teacher Approved / Families participation | Do not opt in |
| Restrict Minor Access | Leave off unless the owner intends an enforced adult-only distribution policy; not required by this utility's content. |

Pebble, rewards, and bright artwork can attract children. Lead the listing with adult tasks, app rules, and the timer.
Use no school-child claims or child-directed promotional images.
If the owner targets younger students, revise this choice and meet the applicable Families requirements first.
Local definitions of a child vary. See [target-audience guidance and Restrict Minor Access](https://support.google.com/googleplay/android-developer/answer/9867159?hl=en).

## Other App content declarations

| Declaration | Answer | Reason or dependency |
| --- | --- | --- |
| Ads | No, the app does not contain ads | Plus is an in-app upgrade, not an ad placement. |
| App access / sign-in details | Some functionality is restricted | Plus requires a purchase despite the absence of a Stillpoint account. Supply reviewer access below. |
| Privacy policy | `PRIVACY_POLICY_URL` | Publish [privacy.md](../privacy.md) and link it inside the app. |
| Data safety | Use [data-safety.md](data-safety.md) | Includes planned Billing purchase handling. |
| Accessibility | Not an accessibility tool; app functionality | Use [permissions.md](permissions.md), consent, and the recorded demonstration. |
| Foreground services | Special use, subject to final audio-type audit | Use the exact manifest types and matching videos. |
| Advertising ID | No | No `AD_ID` permission or ad SDK. |
| News app | No | No reporting, news feed, or news aggregation. |
| Government app | No | No government affiliation or public-service representation. |
| Financial features | My app doesn't provide any financial features | Selling a feature unlock is not banking, lending, investment, a wallet, or financial advice. |
| Health apps | See the current-code answer below | Breathing guidance and sleep/calm onboarding make a blanket none answer unsafe. |
| Health Connect | Not used | No API or permissions. |
| COVID-19 contact tracing or status | No, if asked | No matching functionality. |
| VPN service | Not used | Accessibility-based website matching is not a VPN. |
| SMS and call logs | Not used | Notification snippets do not create SMS or call-log permission use. |
| All-files access | Not used | Backup uses the system file picker. |
| Broad package visibility | Not used | Scoped `<queries>` only; no `QUERY_ALL_PACKAGES`. |
| Full-screen intent | Not used | A block activity is not a full-screen notification intent. |
| Exact alarm restricted permission | Not used | Manifest uses `SCHEDULE_EXACT_ALARM`, not `USE_EXACT_ALARM`. |
| Gambling or real-money games | No | No wagers, prizes, or cash-out. |
| Blockchain content | No | No tokenized assets or cryptocurrency feature. |
| Social/dating child-safety declaration | Not applicable | No social, dating, anonymous-chat, or random-chat category or feature. |
| Generative content declaration | Not applicable | No generative model in the app. |

See [App content setup](https://support.google.com/googleplay/android-developer/answer/9859455?hl=en)
and [financial declaration guidance](https://support.google.com/googleplay/android-developer/answer/13849271?hl=en).
Complete every item Console places under Needs attention. A missing row here is not an exemption.

### Current-code health answer

Select `Stress Management, Relaxation, Mental Acuity` for the breathing tip and calm guidance.
Select `Sleep Management` if the current `Sleep better` onboarding positioning remains.
These are conservative interpretations of Google's broad categories, not a claim that Stillpoint treats a health condition.
Do not select medical devices, disease treatment, or mental-health treatment categories.
See [health declaration categories](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en).

Paste-ready explanation:

> Stillpoint is a focus timer and app blocker. It includes a brief breathing tip and optional goals about calm and bedtime phone use.
> It does not measure sleep, collect health sensor data, diagnose conditions, or provide treatment.

The listing includes a non-medical disclaimer. Keep it while these wellness claims remain.
Do not claim that Stillpoint treats ADHD, addiction, anxiety, or insomnia.
See [health content policy](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en).
If the orchestrator removes the health positioning and guidance, re-audit the final app before selecting no health features.

### Review access to Plus

No login credentials are needed for the free core. Reviewers still need a reliable path to all Plus features.
Do not select all functionality unrestricted merely because there is no Stillpoint account.

Possible no-code aid: create several unused Play promo codes for `stillpoint_plus` and put them only in private review instructions.
Confirm redemption and restore with the actual product before submission. Replace used or expired codes before every review.
Codes are single-use. They are not proof of durable, reusable reviewer access and must not be the only untested access plan.
Do not assume Google reviewers can use the owner's license-test accounts or buy Plus without payment.
See [Play promotions](https://support.google.com/googleplay/android-developer/answer/6321495?hl=en)
and [review access requirements](https://support.google.com/googleplay/android-developer/answer/15748846?hl=en).

Draft reviewer instructions:

> Stillpoint has no app login. Skip optional permission setup to test the free timer.
> Plus is a one-time Google Play purchase. Redeem one unused code below through Google Play, then reopen Stillpoint and restore the purchase.
> Review codes: [insert unused codes privately].
> For blocking, accept the in-app disclosure and enable Stillpoint in Android's Accessibility settings. Usage access enables reports and limits.
> Notification access is optional and enables the local inbox. Use test notifications without private information.
> You can disable access and uninstall the app in Android Settings, including during strict focus.

Replace the restore wording with the final button path. Record it in the review video.
Release gate: the orchestrator must verify dependable access for repeated reviews, without payment, location limits, or owner intervention.
Confirm any proposed promo-code workflow with Play support after account creation. Otherwise provide an explicit, documented review access path.
It must run the same release code and data handling as the purchased version. Do not hide behavior from reviewers.
