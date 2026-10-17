# Kameti Organizer & Personal Turns — implementation plan
Status: DRAFT / NOT RELEASE APPROVED. Base: PR #10 (depends on PR #9). This is a phased engineering specification, NOT a claim that every feature is implemented.

## Current baseline and risks (17 Oct 2026)
- PR #10 implements UI queue-and-save for multiple personal/organizer turn rows and personal installment progress. Existing organizer monthly collected-money progress is on PR #9.
- Existing callbacks save rows separately: **Save all is NOT atomic**. Must not declare batch feature done until repository transaction + conflict checks are wired into UI.
- OrganizerCircle/Member/Collection/Payout data is account-scoped in Room; individual collection and payout operations validate remaining amount in transactions.
- Always distinguish personal contribution from organizer pooled money. Never add organizer cash collection to salary income.
- Live Drive second-device restoration, Android CI, APK signing and migration tests are not yet verified for this change.

## P0 — data integrity before additional UI
1. Add shared domain batch DTOs for organizer roster and personal-turn roster; replace the current per-row loop callbacks with one suspend repository call.
2. In ONE Room withTransaction, reload owner and circle, validate every staged row against existing and staged rows, duplicate slot limits (max 2 half units/turn), sum of total shares, personal shares allocated, names, dates and UTF-8 bounds. Insert all or nothing.
3. Idempotent request ID stored/checked transactionally to prevent double-submit; lock Save while operation in flight; success/failure/snackbar after result, preserve draft on failure. Do not silently report success.
4. Define validation for same named contact owning multiple rows: allowed, with unique record IDs; do not deduplicate on name/phone. One full share or two half shares per turn; turns need not be consecutive.
5. Fix legacy single-payout-field vs structured personal turns display to avoid showing two payouts for same share. Do not destroy legacy records.
6. Add tests for rollback on row N failure, retries, concurrent writes, owner isolation, overflow, conflicting turns, migration V6→V7, and unchanged personal balance math.

## P1 — import and organizer setup
- Reusable multi-row form with editable inline rows, paste from spreadsheet tab-delimited text, import SAF document CSV (UTF-8, quoted delimiters/newlines, Excel UTF-8 BOM); .xlsx support via a reviewed parser or explicit conversion flow, never assume it is CSV.
- Preview first, map column headings Name, Phone, WhatsApp, Full/Half Share, Turn, Amount, Notes; show row-level errors and duplicate suggestions; user confirms changes.
- Define circle-level monthly full-share price with 0.5 share exactly half; reject member-specific amounts inconsistent with circle pricing unless an explicitly reviewed and modeled override is added.
- Calculate turn month = startMonth + (turnNumber-1) months; 0 is unassigned and has no receiving month.
- Example: 20 turns at PKR 5,000/full share => PKR 100,000 monthly pool; person with 3 full turns pays PKR 15,000/month and receives 3 separately scheduled PKR 100,000 turn payouts, subject to actual collections.

## P2 — payment and progress UX
- Three distinctly labelled bars: monthly collection by money (collected/target), member installment status (fully paid / active members), cycle position (elapsed months/total months). Define partial-member count separately and do not count two half-share rows as two unique people without a member identity key.
- Member statuses Unpaid / Partial / Paid / Overdue, due month + grace policy configurable, no overdue before configured due date. Summarize paid, outstanding, late, advance and month-scoped history.
- Personal Kameti: separate installment progress vs received-payout progress, each with numerator/denominator and accessible text. Never infer financial completion from time elapsed.
- Next payout countdown: show confirmed assigned turn's month, estimate only if date unknown; don't falsely assert exact payment day.

## P3 — financial operations and reconciliation
- Partial installment collection and partial payouts stay separate, linked to member/share/turn/month; record date, amount, method, reference and note, immutable IDs.
- Organizer cash-in/out per month and all-time: opening pooled cash + collections - payouts +/- explicitly recorded adjustments. Flag negative cash, unpaid member receivables and payout shortfalls. Never commingle pooled cash with personal income/expense.
- Edits use corrective entries or audited versioned edits, with before/after snapshot, timestamp and owner; never delete cash history by ordinary UI Undo.
- Duplicate warnings for same member/month/amount/date, allow legitimate partial payments with explicit confirmation; allow corrections only with reversible audit trail.

## P4 — communications and reporting
- WhatsApp/SMS message templates: pending amount, circle, due month, optional exact due date, payment method; redact data in lock-screen notification; sending is user initiated via phone intents, not automatic bulk spam.
- Optional scheduled local reminders; notifications never create ledger payments.
- Full history: filter by member, turn, month, method, type; issue stable receipt numbers, receipt preview and native multi-page PDF, Android share chooser, CSV export. Validate PDF amount totals match repository calculations.
- Export/import and restore must distinguish operational CSV from complete JSON backup; never restore from an incomplete PDF/CSV statement.

## P5 — Google Drive backup & migration safety
- Confirm ledger JSON v7 includes all 4 organizer entity types, half-share, turn, collection and payouts; import validates foreign-key references, ownership, duplicate IDs, amount/date ranges and totals before transaction commit.
- Round-trip fixture: old V5/V6 + V7, multi-turn owner, paired 0.5 members, historical partial collections/payouts. Assert exactly equal data after export→import, correct statement totals, no duplicates on second restore.
- Test rollback on malformed/cross-account backup; preserve original on error; test multi-device stale-copy conflict and old-revision restore pause. Document backup JSON is not end-to-end encrypted.
- Never reset databases, modify app package/signing/credentials, change legacy income calculations or overwrite real remote data in tests.

## Release gates
- Kotlin compile, JVM unit suite, Android emulator UI/data tests, migration and upgrade regression suite, PDF fixture checks, Firebase/backup regression, offline behavior, Urdu/RTL font and accessibility, small screen, Android contacts/send intents.
- Real device smoke tests: signed in/out, backup upload, second-device restore, reconnect/retry, account switching, permissions denial, old APK upgrade.
- Confirm signed APK identity and upgrade compatibility; screenshots + CI run references appended to TESTING.md before moving from draft.
- Never merge or publish while Save All is non-atomic, migration/restore fails, or signed-APK CI is missing.

## Delivery sequence
A: atomic batch + validations + tests → B: bulk CSV/paste + turns → C: progress and payments → D: reminders/history/PDF → E: reconciliation, backups, full release gate.
Each stage is a separate reviewable PR with explicit known limitations.