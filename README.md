# Daily Ledger

A native Android personal-finance app for PKR income, spending, savings, loans and kameti. Version **1.8.1** adds kameti to PDF statements, read-only current-month purchase details, animated Settings with correct Back navigation, and manual Online AI refresh. Offline AI understands relative dates and learns categories from reviewed corrections and repeated saved items. The existing Google Drive backup, local encryption and Firebase Online AI systems are retained.

- Dashboard salary periods advance only when the user confirms **Salary received • Start new month**. The prior period is automatically archived and its remaining balance carries forward, even when it is not transferred into Savings.
- **Archive last month** saves a separate calendar-month summary without deleting or moving any transaction; archived period summaries can be viewed from the dashboard.
- During an active salary cycle, available balance includes salary, other income and carried balance, then subtracts expenses, kameti installments paid for their current calendar month, and savings contributions. Other income remains separately visible.
- The balance card contains an **Offline AI daily spending guide**, estimated next salary date/countdown and a red notice when the recorded balance is zero, negative or at most 10% of available funds. The daily estimate divides positive remaining balance by days to the estimated salary; upcoming bills are not automatically reserved. With no salary date, no payday is invented. Due/overdue salaries request confirmation instead of silently moving the estimate forward.
- The next salary estimate is one calendar month after the latest non-future salary transaction date, clamped for shorter months. This estimate never starts a new cycle or archives records.
- **Home · Udhar · Kameti · Savings · More** navigation; Insights, Transactions, Loans and Settings remain accessible. A top-right AI shortcut opens reviewed Auto Fill on main screens (specialized Udhar Auto Fill on the Udhar page).
- Salary, expense, other-income and savings cards open entry forms; category rows in the donut open a preselected expense. Balance opens its breakdown. Frequent-purchase cards and their totals open a read-only list of the **current calendar month** only, with each purchase date/amount and the monthly total. Detail rows do not open an entry editor.
- Donut chart includes the total, stable category colours, amounts, percentages and expandable category rows.
- Cards, including Settings sections, slide in from the left with a short stagger and fade. Amount changes do not restart their animation; no shimmer effect. Savings uses a wallet icon.
- Recording an old kameti installment now increases its paid count/total without deducting the active salary balance. An installment paid in its own month deducts once and remains charged when that salary cycle crosses into another month. Recorded payment totals remain in Kameti history; existing archive snapshots and calendar archive rules are retained.
- Settings child screens (Savings and Auto Fill) return to Settings with Back and preserve its scroll position. Back first closes expanded backup/weather details; Back from the Settings root returns Home.
- Google Drive **Restore** and **Back up now** remain available regardless of auto-backup status.
- Multiple kameti shares, partial receiving, structured member turns, contact actions and reminders.
- Borrowed/lent loans with payment methods, complete payment history and overpayment checks.
- Google/email sign-in and email/password account creation using Firebase Authentication.
- Offline Mini AI: reviewed Roman Urdu/Urdu/English quick entry and local spending/saving suggestions. Common entries require no internet, model download or paid API. It is a small rules engine, not an LLM.
- Optional Firebase Gemini fallback for complex wording; separate consent required. Online suggestions are requested only with **Refresh** on Home/Insights, never automatically on launch, enabling consent, navigation or ledger changes. Loading ignores duplicate taps and retains the existing two-insights/ten-auto-fill daily limits. Offline insights still recalculate locally. No AI chat and no automatic saving of generated entries.
- Searchable category dropdowns support grouped categories, frequently used items and custom names. Expanded built-in income/expense categories include individual pantry items and spices, household cleaning products, family medical costs, children’s fees and travel, and additional income sources; category counts are shown dynamically.
- Separate **Udhar Saman** ledger: goods bought on credit, shops/contacts, due dates, partial payments and editable offline auto-fill.
- Dated quick entry: `1 Oct 2026 doodh 150, 2 Oct 2026 petrol 1,500` prepares two entries on those exact dates. ISO and DD/MM/YYYY are supported; omitted years use the current year and omitted dates use today. Invalid dates request correction. For recorded entries, `kal`/`کل` means yesterday and `parson`/`پرسوں` means two days ago; the review screen explains this convention. Use `tomorrow`, `aane wala kal` or an exact date for future entries. `Kal raat`, `last night`, `aaj subah`, Urdu relative-day phrases and mixed dated lists are also supported.
- Offline parsing also recognizes common spelling variations, quantities such as `2 litre doodh 300`, individual vegetables/lentils, `2 din pehle`, and `last Friday`. Per-unit price expressions and ambiguous income/expense direction request clarification or use the existing optional cloud fallback.
- Review/edit every date, amount and category before saving. Repeated purchases are retained; retries do not duplicate a saved batch.
- Correcting an AI-suggested category teaches an encrypted, account-specific on-device rule. Matching item notes use the corrected category in later Offline Mini AI and Gemini drafts; learning phrases are not sent to Gemini. Learning handles spelling aliases, quantities and word-order variations, and reuses a category from at least two consistently categorized saved items in the same account. Explicit corrections take priority; conflicting history does not teach a rule. Learning never invents an amount/date and remains on this device.
- SQLCipher-encrypted local ledger; existing records migrate without a destructive database reset.
- Optional Google Drive backups store the full ledger as readable JSON in the signed-in Google account's private Daily Ledger app-data folder. There is no backup encryption key or passphrase.
- Explicit Restore / Skip on a new phone; manual **Restore data** and **Back up now** controls remain available in Settings. Background sync detects changes made from another phone and stops rather than silently overwriting them.
- Restore validates transaction IDs and confirms the imported transaction count before completing; duplicate IDs cannot silently replace one another.
- Export format cards offer CSV or a redesigned multi-page PDF with period totals, aligned amounts and category/description rows. PDF has separate income/expense and **kameti** sections with their own totals; paid installments and every partial receiving appear as individual rows. Payments are selected by installment month (day 1 for date filtering), receipts by receiving date, and undated legacy receipts appear only in All records. CSV also includes savings and loan payments. Neither is a full restore backup. City weather and transaction sounds remain available.

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

Cloud AI uses **Firebase AI Logic + Gemini**, authenticated through the existing Firebase project. This Android version does not call the old Cloudflare/Groq worker; legacy worker source is retained for older clients. Firebase Authentication handles sign-in; backups use the signed-in Google account’s private Drive app-data folder. No new ledger backup is written to Firestore, and this backup format does not require a passphrase. AI activation and backup activation are separate.

`AI_APP_CHECK` identifies an app-attestation failure, not proof that a phone is uncertified. Release builds use Play Integrity. Private `debug` builds use individually registered test tokens and must not be distributed. Use **Settings → Check app verification**, then confirm Firebase registration, the actual signing SHA-256 and outside-Play settings. Never add a universal debug token or disable server enforcement to make AI appear to work.

Source/CI checks do not prove a successful request to the live Firebase project. Optional AI/App Check activation requires the project owner to configure Firebase Console. Firebase Console does not contain new Drive ledger backups; Drive account permissions control access to them. Local encryption is not end-to-end encryption of Drive backups. Current build results and remaining live checks are recorded in [TESTING.md](TESTING.md).

## Verified APK

Version **1.8.1** (code **18**) passed [release verification on 15 October 2026](https://github.com/sadique6571/DailyLedger/actions/runs/38693036119): 71 JVM tests, 36 Android emulator tests, Firebase rule checks, 11 Cloud Functions tests and 19 backup-core checks. The run's **DailyLedger-APK** artifact contains the non-debuggable APK and its signature/checksum report; artifacts expire after 14 days. Full results and the APK SHA-256 are in [TESTING.md](TESTING.md).

Build locally with `.\gradlew.bat assembleRelease`; install over the existing app without clearing data. The release uses the existing shared development signing identity for update compatibility; it is not a private production signing key. The existing Firebase configuration is retained. Automated tests do not verify live Google sign-in, Gemini inference or two-device Drive restore.
