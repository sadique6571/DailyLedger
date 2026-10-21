# Daily Ledger

An Android personal finance app for income, expenses, savings, loans, udhar and kameti. Entries remain usable offline; Google sign-in, opt-in AI suggestions and verified Google Drive backups provide the connected features.

**Current version: 2.0.0 · build 50.** The application ID and existing signing certificate are retained for in-place updates.

Build 49 fixes the category arrow touch target: the down arrow opens suggestions and the up arrow closes them in both entry sheets and floating dropdowns.

Build 50 keeps fullscreen forms fixed while scrolling at either end. The dedicated top handle still expands/reduces the form; compact-mode dismissal and keyboard behavior remain available.

## Current interface

- **Home:** compact balance and salary/expense cards, spending donut, recent records and AI insights. Search and AI actions stay in the header.
- **Dashboard Plus:** opens exactly five labelled shortcuts—AI, Expense, Salary, Kameti and Udhar—in a narrow glass panel. Actions enter one at a time. The global Plus appears only on Home; leaving Home closes the menu. Other screens retain their own relevant Add actions.
- **Consistent cards:** shared accent-to-surface gradients, rounded corners, borders, shadows and compact spacing. Weather uses the same soft green gradient as Salary, with temperature prominent and city/condition secondary. Dark themes use the corresponding dark surface.
- **Empty screens:** transactions, savings, loans, udhar and personal kameti explain what belongs there and offer a relevant Add action. Transaction search offers Clear filters when appropriate.
- **Entry sheets:** scrollable fields and a pinned, consistently sized Cancel/Save footer. Pull the top handle upward or tap it to expand; pull downward to reduce an expanded form or close a compact form through its normal draft/save guards.
- **Keyboard:** single-line fields use Next; dates use Done. Notes do not insert a new line. Scrolling while the keyboard is visible does not drag the sheet. Category search stays inside the form.
- **Save protection:** transaction and detailed-entry saves wait for the commit, block repeated submission and retain an editable draft after failure. Successful saves show a confirmation; privacy mode masks amounts.
- **Simplified settings:** compact styling is the default. Appearance controls and the Money tools destination are removed. Existing stored records are retained. Language, privacy, screenshot blocking, weather, sounds, account security and backup settings remain available.

Expense/salary forms no longer show View history, quick-note presets or Today/Yesterday buttons. Dates remain editable through the date field and calendar. Transaction-list date filters are separate and remain available.

## Core features

- Expense/income history, salary scheduling, category search with Urdu/Roman Urdu aliases and custom categories.
- Separate savings, borrowed/lent loans and udhar purchase/payment records.
- Personal and organizer kameti tracking, installments, partial receiving, full/half shares and reviewed batch saves.
- Spending summaries, monthly comparisons, budgets and bill reminders.
- Offline Auto Fill and opt-in online AI suggestions. This is an entry/suggestions workflow, not a chat interface. Review AI-generated dates, categories and amounts before saving.
- English/Urdu interface, amount privacy and PDF/CSV reports.

Build 48 removes the unreachable Money tools screen, its receipt/SMS/voice intake panel and unused dialogs. The bundled OCR dependency and microphone permission are removed. The active quantity/account/private entry editor remains available. The old floating-button helper lives only in instrumentation tests.

Historical Money tools data stays compatible with the existing storage and backup paths, although its menu destination is no longer shown. The detailed feature history is in [docs/](docs/); older build notes describe their own versions rather than the current interface.

## Data, backup and privacy

The local Room database is encrypted with SQLCipher. Current ledger backups use the signed-in Google account's private **Drive app-data** folder. The Drive backup JSON is readable and is **not end-to-end encrypted**. PDF/CSV exports are also readable reports and are not full restore files.

Automatic backup records pending changes and retries eligible uploads. An enabled switch alone does not prove a successful backup: check Settings for a verified upload and last successful time. Android may delay background work.

Before clearing app data or changing phones:

1. Connect Drive and approve access.
2. Use **Back up now** and wait for verified success.
3. Restore using the same app login and Google account.

Restore replaces the local ledger rather than merging records. Unuploaded local changes cannot be recovered from Drive. Older encrypted Drive files and historical Firestore snapshots are not automatically migrated by the current backup workflow; keep the old device/records until a fresh Drive backup is verified.

UI polish does not change the database schema, financial calculations, encryption, backup/restore behavior, AI engines or signing identity. Device appearance preferences are separate from ledger records. System accessibility font scaling continues to apply.

## Connected services and setup

- **Sign-in:** Firebase Authentication with Google.
- **Online AI:** Firebase AI Logic/Gemini with App Check; the legacy Cloudflare/Groq backend source is retained for older clients and is not the current Android AI provider.
- **Ledger backup:** Google Drive app-data; new ledger backups are not written to Firestore.

Read [Google setup](GOOGLE-SETUP.md), [review/setup](REVIEW-AND-SETUP.md) and [Firebase AI setup](FIREBASE-AI-SETUP.md). [KMS setup](KMS-SETUP.md) is historical guidance for legacy backups.

Release attestation uses Play Integrity. Debug builds use individually registered test tokens and must not be distributed. Live Firebase/Drive behavior depends on the project configuration and account permissions; a CI pass alone does not verify a live account operation.

## Build and verify

Use Java 21 and an Android SDK supporting the project's compile SDK (currently 37). The Gradle wrapper is included.

```bash
bash tests/run-core-tests.sh
npm install
npm test
npm --prefix functions install
npm --prefix functions test
./gradlew --no-daemon testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease
./gradlew --no-daemon connectedDebugAndroidTest
```

Windows builds can use `gradlew.bat`. The Android tests require an emulator or connected device.

[GitHub Actions](https://github.com/sadique6571/DailyLedger/actions) runs unit/instrumented tests, a release smoke check and APK verification. A successful run uploads:

- **DailyLedger-APK:** the non-debuggable release `DailyLedger.apk` and `verification.txt` with version/certificate/checksum evidence.
- **DailyLedger-test-reports:** test reports and captured UI previews.

Install the APK over the existing app; uninstalling or clearing data is not required for an update. The existing shared development signing identity is retained for compatibility and is not a private production signing identity. Existing published releases are not replaced by normal CI builds.

See [TESTING.md](TESTING.md) for additional historical verification details. The latest Actions run is the authoritative result for its exact commit.

## Device checks for build 50

1. Open the five-action Plus menu on Home, switch tabs and return: it should be hidden off Home and closed on return. Each tab's own Add action should still work.
2. Compare Weather and Salary gradients in light/dark modes. Check long names, large amounts and the phone's larger font setting.
3. Try empty lists and filtered transaction results: Add/Clear filters must open the relevant form or reset the filter without creating a record.
4. Check consistent Save/Cancel placement, keyboard Next/Done, scrolling to the end of a form and handle expansion without losing the draft.
5. Save one entry and confirm the amount/date/category and success notice. Repeated taps must not duplicate it.
6. Reopen the app and compare loading time on your own device. Startup avoids a redundant full-profile decode; no percentage speed claim is made without a device benchmark.

## Font license

[Noto Nastaliq Urdu](https://github.com/notofonts/nastaliq) is distributed under the SIL Open Font License 1.1. Its license is bundled at `app/src/main/assets/OFL-NotoNastaliqUrdu.txt`.
