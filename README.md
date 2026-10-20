## Compact UI — 2.0.0 build 35

Preview-inspired compact header with Search/AI actions, greeting emojis and weather chip; teal Light/Aqua and navy Dark colours; selected navigation pills. Existing card treatment is retained. AI insight cards stay at the dashboard end and no longer show “Not useful”; online refresh stays manual. Reminders remain available from More. Backup timing, data, encryption, AI logic and signing are unchanged. See [build 35](docs/BUILD_35.md).

## Category typing fix — 2.0.0 build 34

Unfinished category searches stay visible when the suggestions close, focus moves, the keyboard/back button is used, or the field's saved state is restored. Select a result or explicitly add a custom category before saving; manual entries no longer silently replace an unfinished search with Other. The shared fix covers manual/edit forms and AI review. See [build 34](docs/BUILD_34.md).

## Smaller update — 2.0.0 build 33

Release code/resources are optimized with R8. All existing CPU architectures remain supported. Receipt attachment actions are removed; old stored proof data stays compatible with schema/backup v11. Receipt OCR for reviewed AI entries remains. Category editing clears the old selection instead of restoring “Other”; only chosen categories are learned. New everyday food categories and Roman Urdu/Urdu aliases work with offline and cloud AI. See [build 33](docs/BUILD_33.md).

## Combined update — 2.0.0 build 32

Build 31's complete Money tools and full searchable category list remain. Selected `DailyLedger-2.1.0.zip` changes add shared design tokens, gradient/tonal cards, screen components, photos/PDF payment proofs and a 15-minute edit-backup queue. The existing signing certificate, Firebase sign-in/online AI and Google Drive account restore remain unchanged. See [combined update details](docs/COMBINED_BUILD_32.md).

# Daily Ledger

## Money tools and complete category list

Version **2.0.0, build 31** adds the 24 requested planning, purchase, account and intake workflows through **More → Money tools**. Normal entry forms link to quantity/rate, account and privacy controls. The category dropdown now scrolls through the complete matching catalog instead of stopping at 80.

See the [feature-by-feature guide](docs/FINANCE_TOOLS.md) for every workflow, account assumptions, validation and device-dependent OCR/voice/widget limits. Room schema and backup format are now **10**; migrations preserve existing data. Refunds reduce net expenses, transfers remain outside spending, paid plans cannot charge twice, and private individual records stay out of public reports and AI input.

**Privacy:** the phone database is SQLCipher encrypted. Drive app-data backups remain readable JSON, not end-to-end encrypted; private-entry app locks do not change that. New tools are included in the existing backup path, with the same certificate, login and providers.

AI learning now travels in **Drive backup v8**: explicit category corrections, ignored history mappings and “Not useful” suggestion feedback. Existing encrypted learning migrates into the encrypted Room database without deleting its legacy copy. Restore commits learning and ledger together; backups v1–v7 remain supported and keep existing learning intact. Drive snapshots remain readable JSON in private app-data, not end-to-end encrypted. Backups still require Drive permission and a completed verified upload.

Auto Fill supports up to **30 income/expense drafts** and 3,000 input characters. Missing amounts are requested before any Cloud call. Review shows dates, categories, amounts and separate totals; ambiguous/Cloud/future-dated entries are highlighted and must be checked or edited before final confirmation. Date words such as “5 tareekh” are masked before extracting money; ambiguous dates require review. Learning remembers corrections and known spelling aliases; it is not a chat or an on-device model-training service. Suggestion feedback suppresses matching recurring advice and can be reset in AI learning.

Loading improvements: encrypted database opening and derived summaries run in the background; category search indexes are shared by all forms, history learning is cached, unchanged query results do not trigger UI updates, and owner/date transaction reads use a composite index. Category menus use a bounded lazy list: every matching category remains reachable, without composing the full catalog at once. No percentage speed claim is made without a physical-device benchmark.

Salary dates are configured in **Settings → Salary calendar**. Select this month's salary date to suggest the same date next month (month-end dates clamp safely). Edit **Next expected salary** in the calendar at any time for irregular 20/40-day gaps. Home shows only the countdown; new recorded salary suggests its date next month. Scheduling dates never posts income or starts/archives a cycle. Salary calendars and plans are account-scoped and included in the verified Drive backup. Category selection now uses the same rounded colored input as other fields: type directly in it to search canonical categories, Urdu/Roman Urdu aliases, history and custom categories. There is no separate search field inside the dropdown.

## Reliable automatic backup and Kameti batch saves

Automatic backup keeps account-bound pending changes until a Google Drive upload is verified. It retries temporary connection failures, keeps the latest verified backup, and pauses on remote-copy conflicts or after restoring an older backup. Settings shows the actual backup state and last verified time. Keep Drive connected and check that the latest backup has completed before clearing app data or changing phones; changes that never uploaded cannot be recovered from Drive.

Kameti **Save all** commits the reviewed list in one database transaction. A failed row leaves the entire list unsaved; the form stays open for correction or retry. Repeated submission of the same saved request does not duplicate members or overwrite later contact/payment changes. Full/half-share capacity, personal share limits, receiving months and account ownership are checked again when saving.

The installed version remains **2.0.0** (build 31). Google Drive stays the backup provider, the local database remains encrypted with SQLCipher, and Drive backup JSON remains private app-data **without end-to-end encryption**. The advanced Kameti documents describe future work; payment-proof attachments, member identity linking, audited turn swaps and biometric app locking are not claimed as delivered.



## Automatic backup reliability update (2.0.0, build 21)

- Saving ledger changes records a pending state and queues account-bound Google Drive work. Offline/auth-service connection failures retry with exponential backoff without the old five-attempt cutoff. A three-hour WorkManager attempt and app-start/resume/sign-in catch-up checks recover missed work. Android can delay background work; this is not a real-time sync guarantee.
- Uploads compare the actual file checksum and returned/fetched version before acknowledging success. The checkpoint is committed atomically. A lost upload response is reconciled by content, so a retry does not falsely conflict or create another identical revision. First upload creates metadata and data together.
- The existing Drive file/JSON format remains compatible. One account-bound backup file is updated in place. After the new upload is verified, older revisions are removed; cleanup failures keep the verified backup and retry on the next check.
- A fresh/empty device, a changed remote version, a replaced/missing file or duplicate backup files cannot silently overwrite the existing backup. Account-bound jobs cannot become another account's upload. Concurrent devices are not a merged live ledger: conflicts require review.
- Settings distinguishes verified, pending, paused and failed backups, shows the last successful backup, and gives actionable access/storage errors. Resetting local data pauses backup. Restoring an older copy also pauses backup and invalidates the overwrite checkpoint; review it, then explicitly choose **Back up now** to make it current.

**Before changing phones or clearing app data:** enable Drive backup, approve access, use Back up now and wait for verified status. Restore with the same Daily Ledger login and Google account. Unuploaded local edits cannot be recovered if the phone/app data is lost. Export newer records before restoring: restore replaces the local ledger, it does not merge it. Budgets, reminder schedules, salary calendars and Money tools participate in Drive backup; display preferences remain device-only; PDF/CSV are reports, not full restore files. Local SQLCipher encryption and the readable JSON Drive backup remain as documented below.

Implementation references: [Android retry/background constraints](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work), [Google Drive retained revisions](https://developers.google.com/workspace/drive/api/guides/manage-revisions). Automated tests use simulated Drive responses; they do not establish a live Google account upload or second-phone restore.

A native Android personal-finance app for PKR income, spending, savings, loans, kameti and shop credit (Udhar). The **2.0.0** candidate adds an offline Urdu interface with bundled Nastaleeq, planning tools, full screen history and reviewed PDF sharing. Insights stays on Home; online suggestions refresh only when requested. Existing Google Drive backup, local SQLCipher encryption, Firebase sign-in/AI and APK signing are retained.

## What's new in 2.0

- **English / اردو:** change language in Settings. Noto Nastaliq Urdu is bundled for offline use, with right-to-left layout, Urdu category search and translated labels for all 244 built-in category names. Stored category keys, transaction dates and past records are unchanged. Custom notes and names stay as entered. Cloud-generated replies and Android/Google system dialogs retain their own language; PDF/CSV statement data and headings keep the existing export format.
- **Navigation:** nested screens return through the actual path (for example More → Settings → Savings → Search → Transactions). Back restores the previous screen and saved list position. Bottom tabs deliberately start a fresh path.
- **Settings and Home:** search Settings in English or Urdu; drag dashboard handles or use accessible up/down buttons, then reset the card order if needed. Home keeps Insights and the manual online Refresh button. Existing visibility, privacy, size and theme controls remain.
- **Category budgets:** monthly category limits, progress and an 80%-used notice; over-limit categories are highlighted. These are calendar-month expense totals, not salary-cycle balances.
- **Bills & reminders:** monthly rent, fees, salary or other reminders appear when due. Day 29–31 clamps to the end of short months. Missed occurrences remain pending until reviewed or skipped. A reminder never writes a ledger entry or deducts money on its own: review the amount, category and date, then press Confirm & save. Per-occurrence IDs protect retry saves from duplication. Android notifications require permission and are periodic, not exact-time alarms.
- **Automatic AI learning:** reviewed corrections and consistent saved entries teach category mappings automatically. No separate manual AI-learning manager is shown in Settings or More; category learning does not change amounts or dates. The AI summary now includes recorded Udhar repayments and its prompt uses the same deduction formula; historical date cutoffs remain unchanged.
- **Kameti calendar:** scheduled monthly installments, paid/pending amounts, expected personal turns and separate partial receiving records. This view never changes balances.
- **Monthly comparison and search:** compare income, expenses, savings and expense categories between any two calendar months; search transactions, savings, loans/payments, kameti/payments/receiving and Udhar/payments together. Search results show read-only details with an Open ledger action.
- **PDF preview & sharing:** preview the existing complete statement page by page, then save or choose an Android share destination. Opening or closing the preview does not change ledger records. Existing kameti sections, date filtering and CSV output remain.

Budgets, reminder schedules and AI learning are encrypted and account-scoped **on this device**. They are not added to the existing Drive backup format. Confirmed recurring transactions are ordinary ledger records and use the existing backup path. See [the release checklist](docs/V2_RELEASE_CHECKLIST.md) and [testing evidence](TESTING.md).

- Dashboard salary periods advance only when the user confirms **Salary received • Start new month**. The prior period is automatically archived and its remaining balance carries forward, even when it is not transferred into Savings.
- **Archive last month** saves a separate calendar-month summary without deleting or moving any transaction; archived period summaries can be viewed from the dashboard.
- During an active salary cycle, available balance includes salary, other income and carried balance, then subtracts expenses, kameti installments paid for their current calendar month, savings contributions and recorded Udhar repayments. Other income remains separately visible.
- The balance card contains an **Offline AI daily spending guide**, estimated next salary date/countdown and a red notice when the recorded balance is zero, negative or at most 10% of available funds. The daily estimate divides positive remaining balance by days to the estimated salary; upcoming bills are not automatically reserved. With no salary date, no payday is invented. Due/overdue salaries request confirmation instead of silently moving the estimate forward.
- The next salary estimate is one calendar month after the latest non-future salary transaction date, clamped for shorter months. This estimate never starts a new cycle or archives records.
- **Home · Udhar · Kameti · Savings · More** navigation; Insights, Transactions, Loans and Settings remain accessible. A top-right AI shortcut opens reviewed Auto Fill on main screens (specialized Udhar Auto Fill on the Udhar page).
- Salary, expense, other-income and savings cards open entry forms; the donut and its category rows are read-only. Balance opens its breakdown. Frequent-purchase cards and their totals open a read-only list of the **current calendar month** only, with each purchase date/amount and the monthly total. Detail rows do not open an entry editor.
- Donut chart includes the total, distinct category colours, amounts and percentages. Its legend cannot add or edit entries.
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

## APK and verification

The 2.0.0 candidate uses version code **21** with the same application ID and signing certificate for in-place updates. Build and regression evidence is recorded in [TESTING.md](TESTING.md); a source change is not a claim that a live Firebase or Drive operation was tested.

Build locally with `./gradlew testDebugUnitTest assembleRelease` (Windows: `gradlew.bat`). Install over the existing app; do not uninstall or clear data to update. The release uses the existing shared development signing identity for compatibility. Earlier release artifacts remain historical releases.

The Urdu font is [Noto Nastaliq Urdu](https://github.com/notofonts/nastaliq), distributed under the SIL Open Font License 1.1. Its license is bundled at `app/src/main/assets/OFL-NotoNastaliqUrdu.txt`.

Dashboard spending guide compares the remaining daily allowance with actual expenses per calendar day since the latest recorded salary (salary day and today included, including no-spend days). Savings and kameti are excluded from this expense average. The category donut always draws every category; its legend starts with three rows and can be expanded or collapsed.

### Unified surfaces and complete planning backup (2.0.0 build 28)
Cards and rectangular boxes share a rounded 20dp edge, themed border and soft 4dp shadow; input fields use the same geometry. Category visuals are cached, and prepared food has dedicated searchable categories/icons. Local insights prioritize monthly budget limits, bills due within seven days and actual spending pace versus remaining salary allowance. Tip boxes respect privacy mode, and a compact tip uses the spare space beside the donut legend.

Database migration 8→9 adds an encrypted account-bound planning snapshot. Drive JSON v9 includes category budgets, recurring bill settings, handled/skipped months and salary calendar together with ledger/AI learning. Invalid restores roll back together; older backups preserve local planning settings. Legacy planning files migrate once and remain recovery copies; reset/owner-migration markers prevent resurrection. Device sign-in credentials, biometric enrollment, notification permissions and Cloud-AI consent are not restored.

Automatic backup uses one coalesced delayed job plus a unique three-hour periodic job per account; opening/resuming the app checks for overdue work. Connection errors retry; Google permission/account conflicts remain visible and require resolution. Android battery/network/force-stop restrictions can delay execution. This is not an exact clock alarm, zero-loss guarantee, or live multi-device merge. Manual Back up now remains available. The private Drive JSON remains readable, not end-to-end encrypted.
