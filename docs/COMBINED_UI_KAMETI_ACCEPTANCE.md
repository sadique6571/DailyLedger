# DailyLedger — Combined Modernization Acceptance Checklist

The user selected all 15 upgrades **plus one Single Design System**. Keep them grouped on PR #14 for integration testing. A checkbox must only be checked when working implementation and acceptance evidence exist.

## Design
- [x] Reusable card tokens and surface components in `LedgerDesignSystem.kt`.
- [x] Light Kameti tile and card treatment for transaction, loan, credit and Kameti histories.
- [ ] Replace remaining unboxed secondary lists and hard-coded card decorations app-wide with common components; visual audit light/dark/Urdu.
- [ ] Transaction history timeline date groups with stable keys and existing filters.
- [ ] Cross-feature search/filter UX.
- [ ] Quick actions and empty-state affordances.

## Kameti
- [x] Month-wise organizer payment calendar with per-month full/partial/unpaid counts and progress (read-only; new JVM tests).
- [x] Stable original-payment-ID receipt reference and consent-based text sharing; PDF and server-confirmed numbering remain pending.
- [x] Turn-month-start countdown (local date estimate; not a payout promise).
- [x] Manual pending/overdue reminder review and WhatsApp compose (assumed day 10; no automatic sending). Persistent reminders, due-date settings and dedupe are pending.
- [ ] Persisted daily cash close and adjustment history (read-only pooled-balance overview now added).

## App reliability and accessibility
- [ ] Compensating correction/undo journal with transaction boundaries.
- [ ] Long-history pagination/performance.
- [ ] Offline replay/idempotence safeguards for queued actions.
- [ ] Privacy controls for member-scoped exports and sensitive previews.
- [ ] Accessibility, dynamic type, touch targets, Urdu/RTL tests.

## Financial rollout gates
- Baseline Room database v7 and backup format must be migrated additively with end-to-end round-trip tests; never silently overwrite legacy records.
- Run Kotlin compile, unit, Android instrumented, backup/restore, permissions and verification regressions together on final branch head.
- Complete feature behavior before checking boxes. Shared design components alone do not implement payment or security features.
- PR #13 contains CSV UX that user rejected; replace before merging the combined stack.
