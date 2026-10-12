# Current workspace notes — Daily Ledger source 1.7.14

Checked locally on **2026-10-10**:

- `:app:testDebugUnitTest`: passed, **53 tests**, no failures, errors or skips.
- Application Kotlin compiled as part of unit testing. No APK build was run for this change; source is version code **14**, version name **1.7.14**.
- Category asset contains **160 unique categories** (145 expenses and 15 income categories); every built-in label has a canonical AI category rule.
- Category-learning matcher tests confirm remembered corrections override AI suggestions by item phrase and transaction type.
- Android instrumentation tests, including the encrypted category-memory round-trip, were not run or recompiled after these edits; no attached device or emulator is available. JVM matcher and category inference tests did run.
- Live Firebase email/password provider settings and cloud restore were not accessible from this local build; when Firebase returns `ERROR_OPERATION_NOT_ALLOWED`, the app now identifies the Email/Password provider setting that the project owner must enable.

The previous verified APK was version **1.7.13**, code **13**; it does not contain the category-learning changes. The dated sections below record earlier CI artifacts and device results.

# Testing Daily Ledger 1.6.1

Verified on **2026-10-08**, build commit **4ba852eed63a99d0f1d859772f67f44686206286**:
[successful GitHub Actions run](https://github.com/sahulatcenterdsj-maker/DailyLedger/actions/runs/37755903942).

- 46 JVM unit tests and 21 Android device tests passed, with no failures or skipped tests (Android 10 / API 29).
- Firestore/Storage security-rule checks, 11 Cloud Functions handler tests and 19 Java backup-core checks passed.
- Release APK package, version, existing signing certificate and non-debuggable status verified. The APK contains Play Integrity and excludes the Firebase debug App Check provider.
- Dashboard, separate totals, reviewed Auto Fill, Udhar date review, Settings and More screen captures inspected for layout and readable content.

Historical CI regression coverage includes supplied/mixed dates, invalid/leap dates, Urdu digits/grouped money, repeated purchases, whole-batch rejection, specific categories, Udhar partial/full payment bounds, retry idempotency, account isolation and transactional backup rollback. The v1.6.1 device run covered encrypted schema upgrades through version 5 and editing purchase dates; the v1.7.13 version-6 migration, salary-cycle, complete transaction restore and frequent-purchase detail instrumentation tests compile locally but require a device/emulator to execute. Existing backup conflict, corruption and recovery safeguards remain tested.

APK: **1.6.1**, version code **9**, package `com.sadique.dailyledger`, **29,251,281 bytes**.

SHA-256: `62dce88394d357677a94844354060ee0c4e460dc5ce1efe5339c985cc8f1e05f`.

Install over the existing app using the same package/signature; do not clear data to update. The first two CI attempts exposed an unsupported category-picker argument and a stale 93-category test expectation. Both were corrected; the final run above passes all gates against the 105-category catalog.

## Live service limits

App Check diagnostics and provider separation are implemented, but live Firebase settings/inference could not be verified: this review's Cloud Browser returned 502 / connection refused while opening the Firebase/Google sign-in flow, including a fresh canonical Firebase tab. This does not establish an outage for the Firebase service itself or prove that the phone is uncertified.

No billing change, paid resource provisioning or Functions/KMS deployment was performed. Same-account cloud recovery still requires owner activation and a real two-device check. Local encryption and offline quick entry do not depend on that optional cloud setup. See `FIREBASE-AI-SETUP.md` and `KMS-SETUP.md`.

---

# Testing Daily Ledger 1.5.1

## Automated release verification

Verified on **2026-10-07**, commit **d81a4485ecb76dc2435322d8e6a24ce613e68f5a**:
[successful GitHub Actions run](https://github.com/sahulatcenterdsj-maker/DailyLedger/actions/runs/37652728165).

- 40 JVM unit tests: passed, no skipped tests.
- 16 Android device tests: passed, no skipped tests (Android 10 / API 29).
- Firestore and Storage emulator security-rule checks: passed.
- 11 Cloud Function handler tests and 19 Java backup-core checks: passed.
- APK package and matching development signing certificate: verified.
- Captured dashboard, separate savings totals, reviewed Auto Fill, offline insights and partial kameti receiving screens: inspected.

APK: **1.5.1**, version code **7**, package `com.sadique.dailyledger`, **37,507,047 bytes**.

SHA-256: `f86a30fb7c6d46e6d9b9178d23b4fe64700a7e254ac98d7341b5ddf0f81645f0`.

The earlier attempt passed app/device tests but failed while collecting screenshots. The final run above fixes that script and completes packaging successfully.

The automated suite covers:

- Firestore/Storage owner isolation, V3-only client writes, metadata/path binding, content bounds, immutable encrypted objects and deletion.
- Function Auth/App Check declarations, anonymous rejection, UID/version KMS context and revision/fingerprint matching.
- Backup policies, stale revision conflicts, interrupted/ambiguous commits and no deletion of a potentially committed upload.
- AES-GCM round trip, wrong key/tampering, UID/revision binding and keyed change detection.
- Local database encryption, interrupted migration/retry, wrong-key preservation and Keystore namespace separation.
- Real schema 1 → 4 migration with existing records; legacy JSON restore; new loan contact/payment fields and member turns through account-isolated export/import; invalid import rollback.
- Loan overpayment/settlement, duplicate or excessive personal kameti turns, partial receiving and related-record deletion.
- Offline parsing of Roman Urdu, Urdu digits, grouped rupees and k/hazar amounts; ambiguous/dedicated/more-than-10-entry batches are not silently dropped.
- Salary/expense separation, optional-spend estimates, past-month comparisons, owner/date filtering and future savings exclusion.
- Cloud request timeouts, account/consent changes, safe failures and structured-output checks.
- Android review/edit/save UI, offline entry without cloud consent, dashboard totals and partial kameti receiving.

## Reproduce

Use Java 21, Node 22 and the Android SDK specified in the Gradle files.

```bash
npm install
npm test
npm --prefix functions install
npm --prefix functions run lint
npm --prefix functions test
bash tests/run-core-tests.sh
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease
./gradlew connectedDebugAndroidTest
```

CI uses an Android 10 (API 29) emulator. It captures UI previews and verifies the APK package/signing certificate before publishing the installable artifact. These checks do not claim coverage on every Android device.

## Live setup checks still required

The shared APK uses Play Integrity App Check, not a shared debug token. Configure the provider for installation outside Play as documented in `FIREBASE-AI-SETUP.md`. Do not disable production checks simply to make tests pass.

After owner configuration, test Google/email login, optional Gemini inference, weather/contact handlers on a real phone, and same-account encrypted backup/restore on two devices. Verify Restore/Skip, offline edits, changed remote revisions, legacy backup upgrade to envelope V3 and deleted backups. Do not erase the only local copy to test recovery.

No billing upgrade, Functions/KMS deployment or live two-device cloud restore has been performed in this review. Offline Mini AI and the local ledger do not require that optional cloud setup.
