# Approved 2.0 batch — 16 October 2026

This checklist covers the batch approved after the Urdu/Nastaleeq discussion. It does not claim that every idea in the historical 151-item backlog shipped.

| Selected work | Implementation | Evidence gate |
| --- | --- | --- |
| Keep version 2.0 and upgrade signing | 2.0.0/code 20, existing package and signer | CI package/signature verification; existing database upgrade tests |
| Home Insights, manual online refresh | Kept on Home and reorderable; no automatic inference added | ManualInsights tests; reordered Home UI test |
| Screen history and scroll restoration | Saveable route stack and per-screen state | NavigationHistoryTest; full-app RefreshScreensTest |
| English/Urdu and Nastaleeq | Bundled OFL font, RTL, 244 category names, display-only localization | UrduPlanningUiTest, translation completeness assertion and screenshots |
| Settings search and dashboard order | Bilingual search; drag/up/down/reset | Settings search navigation test, persistence/reset UI test |
| Automatic category learning | Learn from reviewed corrections and consistent history; no manual Settings/More learning manager | PlannerStoreTest and existing category learning tests |
| Category budgets | Calendar-month limits; 80% and over-limit notices | PlannerMathTest, dark/large-Urdu budget UI test |
| Recurring reminders | Review/confirm only; stable monthly batch IDs, skip, month-end clamping | PlannerMathTest, PlannerStoreTest, explicit review UI test |
| Kameti calendar | Installment status and separate recorded receiving | Owner/date/partial receiving tests |
| Monthly comparison and global search | Calendar-month summaries; read-only record details across ledgers | Report queries and full-app nested search test |
| PDF preview and share | Native page rendering; restricted FileProvider and Android chooser | Preview UI test and existing native multi-page PDF export tests |
| Balance and data preservation | AI includes Udhar payments; underlying ledger math/schema/Drive code untouched | New AI deduction assertion plus existing financial/backup/upgrade suites |

Before delivery, the full Android CI build, JVM suite, emulator suite, backup/core/security checks and release signature verification must pass. Screenshots and PDF pages must be inspected. Until that evidence is appended to TESTING.md, this is a candidate, not a verified release.

## Explicit boundaries

- Plans and learning rules are account-scoped encrypted local preferences. The existing Drive backup contains ledger records, not those preferences.
- A reminder is not a standing payment instruction. It requires user confirmation for each occurrence; Android periodic alerts are inexact and require notification permission.
- Built-in UI/categories translate; names/notes keep the user's text. Cloud model replies, system account dialogs and the existing statement export format are not automatically translated.
- User-selected dates and category keys are never rewritten when the language changes.
- No Firebase deployment, key change, server-enforcement bypass or new paid service is part of this batch.
- Historical candidates such as custom navigation tabs, chart type switching, category-icon editing, swipe/bulk transaction actions, universal haptics and startup benchmarks are outside this selected batch. Existing main-branch favorites, quick dates/amounts, duplicate warning, undo, screenshot protection and row density are retained.
