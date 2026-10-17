# Advanced Kameti — Phase II specification and acceptance gates
Depends on PR #11 → #10 → #9. DRAFT. No claims of shipping until signed Android CI and real device validation.

## Baseline
The corrected PR #10 provides atomic multiple-row Save All, complete-request replay protection and error-preserving UI. Regression coverage includes rollback, v7 JSON round-trip and account isolation. Every corrected head must pass the linked CI checks; live second-device Drive restore remains a physical-device check. This branch introduces only read-only organizer math primitives and preliminary JVM regression cases, not a complete feature implementation.

## A. Identity, shares and rollover (after atomic batch work)
1. One contact, multiple circles: owner-scoped stable member/contact identity, multiple independent memberships, never merge names/phone alone. Migration preserves every old member row, merges only after user review. Member profile shows circle-scoped sums.
2. Organizer own shares: identify owner participation in the roster with the same slot allocation and payout entitlements; calculate personal contribution and organizer pooled cash in separate books, using an explicit linked internal transfer only once. Allow 1/2/3 distinct turns and 0.5 share.
3. Advance allocations: save one payment receipt plus immutable per-month application rows, allocate earliest due first after user preview; preserve original cash receipt timestamp; never count advance cash twice; refund/reallocation requires audited correction.
4. Rollover: create no artificial money entries; calculate due schedules idempotently on entering a new month; retain arrears, advances, and status of every previous month. Existing installment month records must not duplicate.

## B. Turns and member workflow
5. Turn swaps require proposed change, informed confirmation from both affected members (record how confirmation was obtained), organizer final approval and atomic capacity validation, no mutation once payout booked except reviewed correction; append old/new turn history.
6. Smart Kameti search: offline, owner scoped, indexed name/phone/turn/month/amount/status; results must not leak hidden account data or read sensitive text aloud. Preserve Roman Urdu + Urdu query behavior and RTL access.
7. Next payout and shortfall projection: pending due vs confirmed collections, payouts paid and pooled cash; show estimated timing when no exact agreed due date; forecast is informational, not actual money.

## C. Receipts, corrections and disputes
8. Every payment: optional payment proof, transaction reference, method, exact owner/circle/member/month; attach file via SAF and app-private storage with appropriate access controls; do not upload proof to Drive unless protected and explicit. Missing proof is not missing payment.
9. Numbered receipts: account+circle scoped monotonic unique number generated in transaction; immutable references to financial event; repeat export produces same receipt number. PDF/print share and offline view; paid/due totals match database.
10. Private member statement: review date range, hide unrelated members, redact contact info by default; restricted FileProvider grants and temporary file expiry; prevent accidental broad share.
11. Dispute/correction log: dispute reason/status/notes, review decisions and immutable before/after corrections with actor/time. Undo creates compensating journal record; never delete an already receipted payment silently.
12. Closing certificate: require all scheduled payments, pending refunds, payout obligations and disputes reconciled, auditable zero unexplained pooled balance; issue immutable signed-off snapshot with digest and PDF. Reopen only with logged approval.

## D. Security / backup and privacy
13. Optional PIN/biometric app gate via Android BiometricPrompt + Keystore (no homemade crypto, plaintext PIN, or unrecoverable encryption). Authentication controls screen access, not authorization; owner checks remain on DAO/repository operations. Handle unsupported devices, biometrics changed, locked-out users and accessibility.
14. Offline-first: all cash writes commit locally first, queued Drive upload only after durable commit; idempotent reconciliation on reboot and conflict; backups include every new table only after migration and schema validation; preserve old JSON versions; never silently overwrite remote newer data. Proof photos must have explicit secure backup policy. Paused automatic sync after historical restore stays paused pending review.

## Acceptance test matrix
- Multi-circle same-name and same-phone contacts stay separate until explicitly linked; verify deleted/recreated contacts do not reassign payments.
- 3 full personal turns and 2 half shares: unique turn capacity, monthly shares, payout dates, change history and no salary double subtraction.
- Advance covering partial current month and two future months; rollback if overpaid; retry does not create new receipt or second allocation; refund audit.
- Multiple organizers and account switches cannot view other owners' members, statements, proof, PIN or backups.
- Swap turn conflict, simultaneous edits, half-share pairing, payouts booked and declined consent.
- Due-date month-end/leap-year and grace periods, offline clock changes, next payout no-exact-date label.
- Proof permission revoked, large file, rejected MIME, device storage full, statement share destination absent.
- Unique receipt number under concurrent saves, stable PDF regeneration, edit compensation and dispute/closing failures.
- CSV is not a complete restore backup; encrypted local DB, Drive JSON v7+ future migration round-trip, Android install upgrade and second-device restore, wrong-account reject + rollback.
- Build gates: Kotlin compile, JVM tests, Room migration/emulator, PDF, localization Urdu/RTL, accessibility, signing/package CI, screenshot checks, real phone and backup/restore verification. Keep all PRs draft if any red.

## Reviewable delivery slices
S0 repository atomic batches, idempotency and account isolation (blocking)
S1 member identities, organizer ownership, multi-circle support, turnover migration
S2 advance allocation, rollover, turn approval workflow, offline search
S3 payment proofs, receipts, shared/private statements and dispute corrections
S4 PIN/biometrics, forecasts, reconciliation, closing certificates
S5 offline restore/backup hardening, full regression and signed APK verification

Current implementation on this branch: pure calculation functions for organizer month totals, advance allocation preview, due-date status and payout shortfall, plus proposed unit tests. None of the above UI or persisted financial workflows is claimed complete.
