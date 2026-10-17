# Kameti Phase III — Organizer Operations, Safety & Member Experience

**Status:** Draft specification; not a shipped runtime feature. **Dependency:** PR #13 (Smart Kameti form) → merged PRs #8–#12. PR #13 currently offers CSV/TSV import, but user dislikes CSV UI: remove/replace that import UI in a dedicated follow-up before launch; retain only safe batch-save behavior. Nothing in this plan authorizes deleting or rewriting existing transactions.

## Constraints and invariants
- Money: Long minor units, overflow-checked; append-only financial events and correction/reversal links. Never directly mutate or delete receipted money rows.
- Organizer pooled cash != personal salary, savings or expense. Every cash movement must reconcile to one financial event; no double count for own shares, joint family payments or refunds.
- All writes: Room withTransaction, owner-scoped reads, stable IDs, idempotency keys, optimistic version/conflict checks where necessary, rollback on failure. Existing backfills reversible; old JSON imports valid.
- Any security-sensitive export must require authentication/explicit confirmation, avoid plaintext by default, and never expose other members' data. No claims of end-to-end encryption without implementation.

## Phase A — Cash integrity and payout checks (highest priority)
### 1. Daily Cash Closing
- Per circle/day and payment method: opening cash + verified collections − executed payouts − refunds + audited adjustments = expected closing.
- Record counted physical cash separately; differences require signed/annotated discrepancy note. Closing is repeat-safe, dated and immutable with adjustment events.
- Tests: duplicate close, midnight/timezone boundaries, partial/cancelled payments, mismatch, concurrent payout and cross-owner isolation.
### 2. Payment Verification Queue
- States: PENDING_PROOF, PENDING_CONFIRMATION, VERIFIED, REJECTED, REVERSED. Method and reference stored, no mark as verified purely by receipt attachment.
- Separate booked cash from reported/unverified transfers; member due and payout readiness follow clearly labelled policy. Distinguish cash acknowledged immediately from bank transfer pending settlement.
- Tests: replayed reference, out-of-order events, concurrent confirmation, refunds, account switching, offline verification.
### 3. Mistake Recovery
- Correct member, date, month, amount or method by compensating reversal + new event, linked to original with who/why/when. Require review and permissions for corrections.
- Never erase original payment, receipt or reference; receipts show voided/corrected version. Undo before actual commit can drop draft only.
- Tests: corrections after month close or payout, crash during reversal/repost, exact totals restored, multi-device conflict.
### 4. Payout Readiness Checklist
- Confirm owner, turn assignment/capacity, member approval, verified pool balance, expected entitlement, all allocated receipts and unresolved disputes.
- Distinguish warning vs hard block; do not auto-execute payout; organizer explicitly authorizes, revalidate inside transaction.
- Tests: paired half-share payout, insufficient verified cash, duplicate request, turn swap during approval, locked month.

## Phase B — Recovery + trusted member communication
### 5. Emergency Recovery Package
- Versioned manifest: schema, owner ID, circle IDs, record counts, checksums, export timestamp and restore compatibility, plus audit links and receipt indexes.
- Encrypt sensitive package with reviewed platform cryptography/key policy and user-supplied recovery mechanism; export via Storage Access Framework, never world-readable.
- Restore preview first, full integrity/ownership/foreign-key validation, all-or-nothing transaction; collision strategy without silent replacement; dry run and device-to-device tests.
- Do not include plaintext PIN, biometrics or raw access tokens. Decide separately whether proof attachments are included and encrypted.
### 6. Member View Mode
- Per-member scoped statement via authenticated share/preview or temporary one-person PDF; read-only by default.
- Organizer's full ledger and unrelated contacts hidden; revoke/expire file-grant access; no shared-screen session reuse.
- Tests: wrong-member ID, expired share, background/lock, screenshot/redaction policy and offline use.
### 7. Payment Promise Tracking
- Promise date, amount, free-text note, who recorded it, status (OPEN, KEPT, BROKEN, CANCELLED), reminder preference.
- Promise is *not* money, does not affect collected/verified figures. Optional manual WhatsApp/SMS message; no automatic coercive spam.
- Tests: partial promise, deadline shifts, clock changes, reminder repeat suppression, deletion/audit.
### 8. Family Contribution Split
- Distinct payer identity vs beneficiary member/share, multiple source payments allocated without exceeding installment.
- Record consent/relationship only if volunteered; payer information private. One transfer may cover multiple beneficiaries via itemized allocations.
- Tests: one family paying three shares, rounding half units, refund reversals, no double counting, payout unaffected.

## Phase C — Lifecycle, roles, reporting
### 9. Member Exit Settlement
- Read-only preview: total paid, received payout, future obligations, unverified sums, pending disputes and proposed refund/transfer.
- Require explicit signed-off settlement, independent replacement member acceptance, immutable transfer linkage and allocation capacity checks.
- Tests: midway exit, half-share pairing, already-paid payout, negative proposal, legacy restored records.
### 10. Multiple Organizers & Permissions
- Roles OWNER, COLLECTOR, APPROVER, VIEWER scoped to circle; least privilege. Server-side owner authorization needed before any cross-device collaboration; local UI roles alone are NOT security.
- Two-person approval for cash corrections/closures/payouts as optional policy; immutable actor/approval audit; revoke credentials and expire offline delegated write access.
- Tests: privilege escalation, role revocation offline, stale access, conflicting approvals and cross-owner data leakage.
### 11. Custom Kameti Templates
- Non-financial presets for friends/family/office (share amount, slot count, schedule, grace rules); creating a circle requires explicit confirmation.
- Never silently alter an active circle when applying a template; no hidden financial transactions.
- Tests: preset cloning, invalid amounts, language/RTL, no implicit payments or overwrite.
### 12. Kameti Health Report
- Per-circle month scoped factual metrics: verified collection ratio, unverified amounts, overdue installments, payout coverage, disputes, daily close variance, reconciliation errors.
- Explain denominator, grace/date assumptions and verified vs recorded distinction. Do not produce opaque member reputational scores.
- Tests: half shares, multiple turns, reopened/corrected ledgers, reports matching receipts/exports and zero-division.

## Proposed PR sequence and exit gates
- **PR #14**: domain contracts, immutable event/audit foundation; Room migration + JSON backup restore round-trip. NO new financial UI until this passes.
- **PR #15**: cash close, payment verification and corrective reversal; concurrent/idempotent transactional tests.
- **PR #16**: payout checklist, promises, family payment allocation; screen, accessibility and Urdu/RTL tests.
- **PR #17**: encrypted emergency recovery, private member view, member exits; physical device validation.
- **PR #18**: circle roles/permissions (requires real auth model), templates and health reporting; privacy/threat-model review.
- Each PR remains Draft until Kotlin compile, JVM tests, Room migrations, emulator data/UI suite, Drive backup/restore regression and release signing CI pass. Live two-device Drive/permissions/restore smoke test remains mandatory before a release. Do not auto-publish from feature branches.

## Baseline sanity before coding
- Confirm existing PR #13 UI approach against latest user preference (CSV import rejected).
- Inventory Room version, migration, Ledger JSON version, DAO operations, existing reminders, organizer collections/payouts.
- Introduce additive schema only with migration tests from all supported database versions, preserved IDs and backups.
