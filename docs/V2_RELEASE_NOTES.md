# DailyLedger v2.0.0

DailyLedger 2.0 brings an offline Urdu/Nastaleeq interface and improved day-to-day money management while retaining existing PKR ledgers and account data.

## Highlights

- **English and اردو:** offline bundled Nastaleeq, right-to-left screens, translated built-in categories, searchable categories, and clearer progress bars. Percentages stay in familiar 0–9 format (for example, 90%).
- **Home & Settings:** custom dashboard order, compact money-space cards, visible Kameti Paid styling, more readable spending progress, searchable settings, and entrance effects (respecting Android reduced-motion preferences).
- **Navigation & search:** reliable Back history across nested screens, preserved Settings scroll position, and cross-ledger search with read-only transaction details.
- **Budgets & reminders:** category-based monthly budgets with progress and warnings; recurring bill reminders are review-only, with no automatic money deduction.
- **Kameti & comparisons:** installment/receiving calendar with partial receipts, monthly financial comparison, and clear Udhar repayment deductions.
- **Offline AI:** reviewed category suggestions learn automatically from corrected and consistent past entries. No separate manual AI-learning manager. Online AI insights refresh only on request.
- **Exports:** PDF preview, save/share through Android, and existing CSV/statement support.

## Compatibility

- Android package: `com.sadique.dailyledger`; version: `2.0.0` (versionCode `20`).
- Signed with the existing app certificate for in-place upgrades; see the included `verification.txt` for certificate details and APK SHA-256.
- This GitHub APK release is **not** a Google Play Store publication.

## Important limitations

- Budgets, recurring reminder schedules, and AI-learning preferences are stored encrypted on this device and are **not** included in the current Google Drive ledger backup. Saved transactions continue to use the existing ledger backup.
- Reminders are periodic, may need Android notification permission, and **never** save or deduct money without confirmation.
- Built-in interface text/categories support Urdu. Personal notes, system dialogs, cloud AI responses, and existing statement export language are not all automatically translated.
- App entrance animations follow Android's reduced-motion settings.

## Validation

This release is built from the merged, tested PR #7 and only published after the `main` branch GitHub Actions build, JVM tests, Android emulator tests, Firebase/backup checks, APK signature/package verification and APK upload complete successfully. The build's test reports remain in GitHub Actions artifacts.

The previous `v2.0.0-beta1` release remains available separately.
