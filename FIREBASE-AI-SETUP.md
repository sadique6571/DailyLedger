# Firebase AI setup — Daily Ledger 1.7.15

This version uses the official Firebase AI Logic Android SDK with the **Gemini Developer API**. Cloud Functions, Groq keys and Cloudflare are not required by the new app. Features remain automatic spending suggestions and reviewed Auto Fill, with no AI chat.

## Existing project and APK

- Firebase project: `daily-ledger-4d8ef`
- Android package: `com.sadique.dailyledger`
- Version: `1.7.15`, version code `15`
- Model: `gemini-3.5-flash-lite` (stable, free-tier eligible in current official docs, checked 2026-10-07)
- Existing signing certificate SHA-256: `4C:22:14:C4:D4:B6:E3:8B:FA:81:44:F1:41:CC:24:A7:18:00:3F:55:20:41:06:17:76:6D:19:5C:CD:2D:23:1D`

The app uses its existing `google-services.json` Firebase configuration. Do not put a Gemini Developer API secret key in Android source, the APK, chat, or GitHub. Firebase AI Logic's setup workflow creates/manages the required provider-side resources.

## Owner activation

1. Open the existing project in [Firebase Console](https://console.firebase.google.com/project/daily-ledger-4d8ef/overview). Keep the **Spark** plan for the Gemini Developer API free tier; do not link billing for this setup. If the project is already linked to billing, do not assume Gemini requests are free or change the plan without reviewing other services.
2. Open **AI Services → AI Logic → Get started**. Select **Gemini Developer API**, then complete the guided API/resource activation. Do not select the billing-required Agent Platform/Vertex route.
3. Register this Android app in **App Check → Apps → Play Integrity**, using the SHA-256 above. Enable/link the Play Integrity API to the same Firebase/Cloud project as required by the console. Firebase AI Logic setup enforces App Check in the current workflow; complete registration rather than sharing a debug token.
4. This APK is distributed **outside Google Play**. Use Firebase's documented outside-Play settings: `PLAY_RECOGNIZED` not required, `LICENSED` not required, minimum **Device integrity** required. This is the supported sideloading configuration, not a disabled App Check policy. The phone must meet the selected integrity level and have functioning Google Play services. A Play-distributed release should use the corresponding Play settings instead.
5. Check the Firebase AI Logic API's **Generate content requests** per-user quota. The documented default is 100 RPM; the owner can lower it (for example, 5 RPM) to protect a small app's shared free allowance. Gemini model/project limits apply separately and may be lower. Do not treat client counters as a security boundary.
6. Install the updated APK over the existing app. Do not uninstall or clear data. Enable Cloud AI after reviewing the new Gemini consent. Try `aaj doodh 300 sabzi 200 fruit 350`; review three expense drafts totaling PKR 850 and discard test drafts. Do not save fictitious entries in a real ledger.

The source change does not itself activate Firebase AI Logic, register App Check, change cloud quotas, or prove live inference. Those are console/device verification steps.

## Privacy and limits

Gemini's unpaid-service terms allow submitted content and responses to be used to improve Google services, including human review. Do not submit personal, confidential or sensitive information. Automatic summaries exclude names from custom categories, account identity and transaction notes; unknown category labels are grouped as `Other expenses`. Amounts/periods are still submitted and can be private to a user, so opt-in consent remains required. Typed Auto Fill text is sent as entered.

The app logs no prompts, responses, keys or tokens. Firebase/Google's service-side processing and retention are governed by their terms. Model output is constrained to JSON and strictly validated on-device. It cannot execute tools or write directly to the ledger. Users review/edit/remove drafts before explicitly saving.

AI defaults off. Upgrading from Groq requires fresh consent. Local counters limit each account on this device to 10 Auto Fill and 2 suggestion attempts per UTC day; suggestions also have a one-hour cooldown. Setup/App Check failures before the provider request do not consume those counters. These counters can reset after clearing data and are not cross-device enforcement. The Firebase/Gemini project limits and App Check provide server-side controls. No paid fallback is configured.

The current APK preserves the development signing identity to update existing installations. It is not a private production signing identity. Prepare a private release/signing migration before wider public distribution; never distribute a shared App Check debug token.

## Error codes shown by the app

| Code | Meaning / next check |
| --- | --- |
| `AI_SIGN_IN` | Current Firebase account/token could not be verified. Sign in again. |
| `AI_CONSENT` | Enable Gemini AI consent for this account. |
| `AI_APP_CHECK` | Check App Check registration, certificate, outside-Play settings and device integrity. |
| `AI_SETUP` | Finish Firebase AI Logic / Gemini Developer API activation. |
| `AI_CONFIG` / `AI_ACCESS` | Check Firebase API configuration/restrictions and permissions. |
| `AI_NETWORK` | A network/transport connection failed. |
| `AI_TIMEOUT` | Verification or generation exceeded its time limit. |
| `AI_QUOTA` | Firebase/Gemini usage limit reached; wait for its reset. |
| `AI_DEVICE_LIMIT` | Local daily request allowance is exhausted. |
| `AI_REGION` | Provider reports that the account/region is unsupported. |
| `AI_RESPONSE` | Blocked, incomplete or invalid output; no entries saved. |
| `AI_SERVICE` / `AI_UNAVAILABLE` | A provider/service failure needs checking; no raw private error is exposed. |

## Official references

- [Firebase AI Logic Android setup](https://firebase.google.com/docs/ai-logic/get-started)
- [App Check with Play Integrity, including outside-Play apps](https://firebase.google.com/docs/app-check/android/play-integrity-provider)
- [Firebase AI pricing and billing](https://firebase.google.com/docs/ai-logic/pricing)
- [Supported models](https://firebase.google.com/docs/ai-logic/models)
- [Rate limits](https://firebase.google.com/docs/ai-logic/quotas)
- [Gemini API data terms](https://ai.google.dev/gemini-api/terms)

## Backup plan and cost

The current client backup path uses **Firebase Spark Firestore only**. It encrypts backups on-device with the passphrase saved in Settings and writes ciphertext to the owner-scoped Firestore document (maximum 900 KB). New backup writes do not use Storage, Functions or KMS. Deploy only Firestore rules with `firebase deploy --only firestore:rules`. The old KMS/Functions/Storage design remains documented in `KMS-SETUP.md` for legacy v2/v3 backups; restoring those may require their original paid services.

Keep the Firebase project on Spark if paid services are out of scope. Firebase AI Logic uses the Gemini Developer API free tier when available, but quotas, eligibility and provider terms can change; do not link billing or select the billing-required Vertex/Agent Platform route for this setup. The APK uses `gemini-3.5-flash-lite`; app limits are per device and do not replace server quotas.

## Current build variants and diagnosis

The downloadable APK is `assembleRelease`, non-debuggable, and contains only the Play Integrity provider. Its existing certificate is retained for upgrades. `assembleDebug` is private testing only and contains Firebase's debug provider: every installation needs its own console-registered token. Never share a token in an APK, repository, report or public log.

Settings → Check app verification forces a fresh App Check token request without sending ledger entries. Success confirms attestation only; model activation/quota can still fail separately. App details show the installed package, version, provider and public signing SHA-256. Compare that fingerprint with Security → App Check → Apps and Project settings → Your apps. Keep production enforcement active.

For the directly installed release APK, use the official outside-Google-Play profile: PLAY_RECOGNIZED not required, LICENSED not required, and minimum Device integrity. This still verifies device integrity. Confirm the linked Cloud project/Play Integrity API and registration before changing settings. A Play-distributed app needs its corresponding profile. Updating the APK cannot silently alter those project settings.
