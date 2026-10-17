# GitHub integration checks — 2026-10-17

Candidate: version **2.0.0**, build **23**, existing package and signing identity.

Verified baselines: automatic backup PR #8 passed [run 38986493692](https://github.com/sadique6571/DailyLedger/actions/runs/38986493692); organizer/half-share PR #9 passed [run 39004798671](https://github.com/sadique6571/DailyLedger/actions/runs/39004798671). The original PR #12 run failed one obsolete UI label assertion (`1 full`, now `1 full share`); its compilation and JVM tests passed.

The corrected stack adds transactional personal/organizer Save All, complete-request replay checks, in-flight UI locking and retained input on failure. Regression cases exercise database failure on the second insert (complete rollback), date/share conflicts, concurrent writes, foreign owners, UTF-8 bounds, oversized pools, v7 restore, and asynchronous UI failure/retry. PR #12 also tests owner-scoped planning summaries. No additional database migration is introduced; existing V6→V7 and upgrade tests remain in the full suite.

The workflow now tests pull requests targeting feature branches as well as main. Each active PR's Checks tab records its exact tested commit; see [PR #10](https://github.com/sadique6571/DailyLedger/pull/10), [PR #11](https://github.com/sadique6571/DailyLedger/pull/11), and [PR #12](https://github.com/sadique6571/DailyLedger/pull/12). Build gates include Firebase rules, Cloud Functions, 19 Java backup-core checks, Kotlin/JVM, Android emulator data/UI, APK identity, release signature and Play Integrity checks.

Live Google Drive upload and restore on a second physical phone have not been exercised in this environment. The emulator uses isolated test databases and fake transports, never users' remote backups. Drive JSON is not end-to-end encrypted. Unsynced edits still require a completed backup before app data is cleared.

# Daily Ledger 2.0.0 candidate — validation in progress

Base: `7491aee223f68b398a7481479b6037e1aeb28a36` (2.0.0-beta1/code 19).
Candidate: version **2.0.0**, code **20**, package `com.sadique.dailyledger`.

The feature batch is implemented on `codex/v2-urdu-complete-20261016`. CI results and the APK checksum will be recorded after the complete release build succeeds.

New regression cases cover:
- Full screen-history Back paths, bottom-tab reset, Settings → Savings → global search → Transactions → Back, Settings search and automatic-only AI-learning UI (no manual manager).
- All 244 built-in category translations, bundled Nastaleeq, RTL Urdu dropdown selection returning the original stored key, English/Urdu switching and compact dark/large-text rendering.
- Dashboard order persistence/reset and keeping Home Insights with an explicit Refresh action.
- Monthly budget owner/type/month isolation and 80%/100% thresholds; recurring month-end/leap dates, overdue/skip behavior and stable retry identities.
- Encrypted per-account planner persistence, corruption preservation, recurring batch idempotency and explicit review-before-save.
- Automatic learning, correction and suppression persistence; owner-scoped kameti calendar and partial receipts; calendar-month comparison; Udhar deductions in the AI summary.
- Native PDF preview page rendering, close without saving and save/share controls, alongside the existing multi-page transaction/kameti export tests.

Existing encrypted-database upgrade, Drive backup/restore, account-isolation, financial-calculation, AI-date, manual-refresh and release-signing gates remain required. Database schemas/migrations, Drive serialization, Firebase configuration and signing files are unchanged in this batch.

Limits: no live Google sign-in, Gemini request, real Drive upload or second-device restore is implied by emulator tests. Monthly plans/learning remain on-device; the existing Drive backup is readable JSON in its private app-data folder. Android decides when periodic reminders run. Comparison/budgets use all recorded dates in a calendar month, while AI historical insights keep their through-today cutoff and Home uses the active salary cycle.

# Historical release — Daily Ledger 1.8.1

Verified on **2026-10-15**, build commit **fe2ce6e1702e8214b85f3318a4df072c563db203**:
[successful GitHub Actions run](https://github.com/sadique6571/DailyLedger/actions/runs/38693036119).

- **71 JVM tests** and **36 Android device tests** passed with no failures or skipped tests (Android 10 / API 29).
- Firestore/Storage emulator security-rule checks, **11 Cloud Functions handler tests** and **19 Java backup-core checks** passed.
- Debug, instrumentation and release APKs compiled. Release package, version, existing signing certificate and non-debuggable status verified. The release contains Play Integrity and excludes the Firebase debug App Check provider.
- Settings navigation was exercised through the full app: Settings → Savings → Back → Settings, restored scroll position, Settings → Auto Fill → Back → Settings, weather details closing before leaving Settings, then Settings root → Back → Home. Settings export controls remain reachable after the new lazy card transitions.
- Frequent purchases show only the current calendar month, even when an old purchase belongs to the active salary cycle. The card/total opens read-only history; individual rows have no click action and cannot open editing.
- Manual Online AI tests verify no request on initial context, changed records or enabling consent; explicit Refresh bypasses cached suggestions, duplicate taps do not duplicate requests, stale results are discarded, and errors do not auto-retry. Existing daily quotas and Firebase transport remain intact.
- Offline date and learning checks cover `kal`, `parson`, mixed dated lists, Urdu relative phrases, year/leap boundaries, spelling aliases, quantities and word order. Consistent saved history teaches only its own account; explicit reviewed corrections take priority and conflicting history is ignored. Amounts and dates are never learned or replaced by category rules.
- Native Android PDF export was tested from an owner-scoped Room repository, including 13 paid kameti installments and two partial receipts across two kameti pages; another owner's receipt is excluded. Transaction and kameti totals remain separate. All six pages across the transaction-only and mixed-ledger sample PDFs were visually inspected for rows, amounts, headers, footers and pagination. Empty statements, date filtering and undated legacy receipts are also covered.
- Settings/export, manual Refresh and current-month read-only purchase screenshots were inspected. Existing encrypted database upgrades, Drive backup/restore validation, account isolation, rollback, salary-cycle and ledger tests continue to pass.

APK: **1.8.1**, version code **18**, package `com.sadique.dailyledger`, **26,483,733 bytes**.

SHA-256: `407dd6be957c2cc9d48061ba74905acd1e3cc1296db4a6201b6f2bc9ce6daa23`.

Signing certificate SHA-1: `c6e19da96097e5424a34766e15746edca7c0f707` (existing shared development identity).

Install over the existing app using the same package/signature; do not uninstall or clear data to update. Google Drive backup, SQLCipher local encryption, Firebase Authentication and AI transport retain the existing implementation. Current Drive backups are readable JSON in the user's private app-data folder, not end-to-end encrypted backups. Learning rules remain encrypted and account-scoped on this device.

No Firebase deployment, billing change or live account operation was performed for this release. Automated checks do not verify live Google sign-in, Gemini inference, a real Drive upload or a second-device restore. An initial CI run exposed a navigation test trying to scroll to an off-screen lazy item; the test was corrected to use the visible Settings toolbar button, and the final run above passes every gate. The dated sections below are historical evidence.

# Historical release — Daily Ledger 1.8.0

Verified on **2026-10-14**, build commit **cb2f7625035c78be38d8e1ddf287646c2b15024d**:
[successful GitHub Actions run](https://github.com/sahulatcenterdsj-maker/DailyLedger/actions/runs/38544427519).

- **57 JVM tests** and **33 Android device tests** passed with no failures or skipped tests (Android 10 / API 29).
- Firestore/Storage emulator security-rule checks, **11 Cloud Functions handler tests** and **19 Java backup-core checks** passed.
- Debug, instrumentation and release APKs compiled. Release package, version, existing signing certificate and non-debuggable status verified. The release contains Play Integrity and excludes the Firebase debug App Check provider.
- New regression coverage includes salary countdown/month-end handling, daily spending estimates, hidden balance amounts, historical versus current-month kameti deductions, quantity/date/spelling parsing, category learning and searchable category selection.
- Existing encrypted-database upgrades, version-6 backup round trips, account isolation, rollback, salary-cycle carry-forward and frequent-purchase date/history checks pass. Stale device-test expectations were aligned with the existing version-6 backup format and current Drive backup labels; the backup implementation was not changed.
- Balance guide, donut/category breakdown, reviewed Auto Fill and More previews were inspected. PDF export was rendered on Android and all three sample statement pages were inspected for headers, amounts, rows and pagination; empty statements were also tested.

APK: **1.8.0**, version code **17**, package `com.sadique.dailyledger`, **26,467,349 bytes**.

SHA-256: `bcb929c5068f77bb695f8eabf4ba32f3dea0b1edd791745680c7839eb415f72f`.

Signing certificate SHA-1: `c6e19da96097e5424a34766e15746edca7c0f707` (existing shared development identity).

Install over the existing app using the same package/signature; do not uninstall or clear data to update. Online AI, Google Drive backup and local SQLCipher encryption retain the existing implementation. Current Drive backups are readable JSON in the user's private app-data folder, not end-to-end encrypted backups.

No Firebase deployment, billing change or live account operation was performed for this release. These automated checks do not verify live Google sign-in, Gemini inference, a real Drive upload or a second-device restore. The dated sections below are historical evidence, including legacy cloud-backup mechanisms that are no longer used for new backups.

# Historical workspace notes — Daily Ledger source 1.7.16

Verified locally on **2026-10-12**:

- `:app:testDebugUnitTest assembleRelease`: passed. **47 JVM tests** passed, with no failures, errors or skips; debug and release Kotlin compiled and release APK packaged.
- `npm.cmd test`: passed. Firestore emulator verified owner-only legacy reads and denied client create/update/delete; Storage emulator regression checks also passed.
- Firestore rules deployed to Firebase project `daily-ledger-4d8ef`. Legacy backup documents remain owner-readable; client writes/deletes are blocked.
- Release APK is version **1.7.16**, code **16**, package `com.sadique.dailyledger`, **26,418,197 bytes**.
- APK SHA-256: `DA855E71295C73A6618B8200ECBA988D09F45F717BB7A00FA4C05A62C8C52AFD`.
- Automated tests do not verify live Google OAuth, Drive authorization, a real upload, or a second-device restore; verify those using non-critical test records before relying on recovery.
- Firestore backups and prior encrypted Drive backups are not migrated by this release. Preserve the original local records and make a new Drive backup before moving to another phone.

The dated sections below record earlier CI artifacts and device results.

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
