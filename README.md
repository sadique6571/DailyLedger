# Daily Ledger

A native Android personal-finance app for PKR income, spending, savings, loans and kameti. Version **1.7.16** adds Google Drive account backup, improved statement exports and family/item-specific category coverage.

- Dashboard salary periods advance only when the user confirms **Salary received • Start new month**. The prior period is automatically archived and its remaining balance carries forward, even when it is not transferred into Savings.
- **Archive last month** saves a separate calendar-month summary without deleting or moving any transaction; archived period summaries can be viewed from the dashboard.
- During an active salary cycle, available balance includes salary, other income and carried balance, then subtracts expenses, paid kameti installments and savings contributions. Other income remains separately visible.
- Dashboard frequent-purchase items (expense notes, falling back to category) show cycle totals; tapping one lists each purchase date, note and amount, with the total shown above and below the details.
- Google Drive **Restore** and **Back up now** remain available regardless of auto-backup status.
- Multiple kameti shares, partial receiving, structured member turns, contact actions and reminders.
- Borrowed/lent loans with payment methods, complete payment history and overpayment checks.
- Google/email sign-in and email/password account creation using Firebase Authentication.
- Offline Mini AI: reviewed Roman Urdu/Urdu/English quick entry and local spending/saving suggestions. Common entries require no internet, model download or paid API. It is a small rules engine, not an LLM.
- Optional Firebase Gemini fallback for complex wording; separate consent required. No AI chat and no automatic saving of generated entries.
- Expanded built-in income/expense categories include individual pantry items and spices, household cleaning products, family medical costs, children’s fees and travel, and additional income sources; category counts are shown dynamically.
- Separate **Udhar Saman** ledger: goods bought on credit, shops/contacts, due dates, partial payments and editable offline auto-fill.
- Dated quick entry: `1 Oct 2026 doodh 150, 2 Oct 2026 petrol 1,500` prepares two entries on those exact dates. ISO and DD/MM/YYYY are supported; omitted years use the current year and omitted dates use today. Invalid dates and ambiguous `kal` request correction.
- Review/edit every date, amount and category before saving. Repeated purchases are retained; retries do not duplicate a saved batch.
- Correcting an AI-suggested category teaches an encrypted, account-specific on-device rule. Matching item notes use the corrected category in later Offline Mini AI and Gemini drafts; learning phrases are not sent to Gemini.
- SQLCipher-encrypted local ledger; existing records migrate without a destructive database reset.
- Optional Google Drive backups store the full ledger as readable JSON in the signed-in Google account's private Daily Ledger app-data folder. There is no backup encryption key or passphrase.
- Explicit Restore / Skip on a new phone; manual **Restore data** and **Back up now** controls remain available in Settings. Background sync detects changes made from another phone and stops rather than silently overwriting them.
- Restore validates transaction IDs and confirms the imported transaction count before completing; duplicate IDs cannot silently replace one another.
- CSV/PDF exports, city weather and transaction sounds.

See [v1.5 feature notes](OFFLINE-MINI-AI-LOANS-KAMETI.md). Phone contacts use the system picker; Call/SMS/WhatsApp actions require the user to send or call.

## Setup and privacy

Read [Google setup](GOOGLE-SETUP.md), [Review and setup](REVIEW-AND-SETUP.md) and [Firebase AI setup](FIREBASE-AI-SETUP.md). `KMS-SETUP.md` is historical guidance for legacy backups and is not needed for current backup/restore.

New backups are stored in each user's Google Drive app-data folder and are not encrypted separately; access is protected by the Google account and its Drive permissions. Firebase Firestore is no longer used for ledger backups. Existing Firestore snapshots and old encrypted Drive files are not automatically migrated or restored by this version—keep the old phone/local records and create a fresh Drive backup before relying on another device. The local Room database remains SQLCipher-encrypted on the phone.

Backups depend on successful uploads and access to the same Firebase and Google accounts. Check the app's last successful backup status; an enabled switch alone does not mean a backup exists. CSV/PDF exports and Drive backups are readable. AI is separately opt-in, with its own data disclosure and usage limits.

## Build and verify

```bash
bash tests/run-core-tests.sh
npm --prefix functions test
npm install
npm test
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease
./gradlew connectedDebugAndroidTest
```

GitHub Actions builds and verifies the non-debuggable release APK and publishes its test reports. Install an update over the existing app; do not clear its data to update. The fixed development signer preserves update compatibility, but is not suitable as a private production signing identity.

## Integration status

Cloud AI uses **Firebase AI Logic + Gemini**, authenticated through the existing Firebase project. This Android version does not call the old Cloudflare/Groq worker; legacy worker source is retained for older clients. Firebase Authentication handles sign-in, and the optional v4 backup uses passphrase-encrypted Firestore payloads without Cloud Storage or KMS. AI activation and backup activation are separate.

`AI_APP_CHECK` identifies an app-attestation failure, not proof that a phone is uncertified. Release builds use Play Integrity. Private `debug` builds use individually registered test tokens and must not be distributed. Use **Settings → Check app verification**, then confirm Firebase registration, the actual signing SHA-256 and outside-Play settings. Never add a universal debug token or disable server enforcement to make AI appear to work.

Source/CI checks do not prove a successful request to the live Firebase project. Firestore rules and optional AI/App Check activation require a signed-in project owner to deploy/configure in Firebase Console. Current build results and remaining live checks are recorded in [TESTING.md](TESTING.md).

## Verified APK

The current source is configured for version **1.7.16** (code **16**). Build the non-debuggable APK with `.\gradlew.bat assembleRelease`; install over the existing app without clearing data. The release uses the existing shared development signing identity for update compatibility; it is not a private production signing key. Cloud AI still requires the owner-side Firebase steps described above.
