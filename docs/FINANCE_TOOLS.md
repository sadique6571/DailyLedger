# Money tools — 2.0.0 build 31

Open **More → Money tools**. Normal salary/expense forms also offer **Quantity, account & privacy options**, preserving the draft when switching to the detailed editor.

| Requested change | Where and resulting behaviour |
|---|---|
| Salary allocation | Salary planning: save allocations separately; no ledger deduction until an actual planned payment is saved. |
| Will money last until salary? | Spending forecast: net daily expense since salary, days covered and estimated shortfall using the chosen expected salary date. Warns on overdue salary dates. |
| Why spending rose | Rates & comparisons: same elapsed days in matching months, weighted item rates, quantity effect and rate effect. Requires quantity/rate records; does not invent a cause from amounts alone. |
| Quantity × rate | Detailed entry and shopping item: litres/kg/pieces and decimal quantity; calculate paisa once using half-up rounding. |
| Compare previous item prices | Latest two matching item/unit purchases; also show weighted month-to-date rates. |
| Shopping list to expenses | Tick purchased items, review amounts and save the selected entries in one database transaction. Paid items cannot be charged twice. |
| Pending versus paid | Salary planning and installments keep unpaid records separate; payment confirmation includes the actual payment date. Deleting a linked payment returns its plan to pending. |
| Reimbursement | Record the expense and a claim atomically. Link partial returned payments; prevent overpayment and update the claim if a returned-payment entry is deleted. |
| Installment purchase | Total price, advance, count and first due date. Divide remaining paisa exactly across monthly installments; only advance/paid installments affect spending. |
| Saving-goal feasibility | Target, saved amount and deadline; required contribution per salary. New contributions create actual savings, linked to the goal and chosen account. Removing a saving updates the goal. |
| Equal elapsed salary periods | Rates & comparisons: current salary period versus the same number of days of the prior period, bounded by its end; show both actual date ranges. |
| What-if planning | Spending forecast: hypothetical purchase changes the displayed remaining balance and allowance only; never saves a transaction. |
| Refunds | Entries & refunds: link a partial/full refund to its expense and cap the cumulative refund at the purchase amount. Refunds credit cash and reduce net expenses, rather than becoming salary/income. |
| Split a bill | Multiple reviewed categories; individual amounts must equal the bill total. Save all rows atomically with a duplicate-batch guard. |
| Cash/bank/wallet balances | Accounts & transfers: Cash, Bank, Easypaisa, JazzCash and custom accounts; select an account in detailed entry. Transfers do not become income or expense. |
| Reconcile actual balance | Save the difference between recorded and actual account balance with a required reason; it affects that account, not salary income or expenses. |
| Receipt photo | Receipt, SMS & voice: choose a photo, read it on-device with bundled Latin-script ML Kit, correct extracted text and review every entry. Optional bill-total matching prevents saving inconsistent prepared totals. No photo is retained in the backup. |
| Pasted bank SMS | Explicit paste only; select income/expense, review amount and date. Do not request SMS access or retain account/card identifiers in notes. |
| Offline voice | Android 12+ on-device recognition, only where the phone has the service and selected language pack. No network recognizer fallback. Review transcript, then prepare and review drafts. |
| Home-screen widget | Add DailyLedger using Android's widget picker. New expense goes through login/app lock. Balance is opt-in, hidden by default and suppressed for app lock, privacy mode or private entries. No plaintext balance file is cached. Android controls widget refresh timing. |
| Entry history | Record before/after details for creation, edits and deletion of income/expense entries from this upgrade onward. Private snapshots require private-section unlock. No invented history for older edits. |
| Monthly checklist | Salary, rent, bills, kameti and recurring-bill titles; monthly checked state is backed up. Ticks never post payments. |
| Custom shortcuts | Save an editable amount/category/account preset and open it for review; never save on a single shortcut tap. |
| Private entries | Phone credential/biometric unlock; standalone transaction details disappear from normal history, search, frequent purchases, charts, AI context and reports. Totals still include them. Linked plan/claim records must remain public so their names cannot bypass the lock. Private records and history remain in the full backup. |
| Complete category list | Lazy list includes every matching category, including results beyond 80, with same-field search, aliases, groups and deduplicated frequent categories. |

## Data, privacy and compatibility

- Room schema **10** adds account, quantity/unit/rate, refund link and privacy flag with defaults for existing entries. No destructive migration. Plans, movements, claims, goals, shortcuts, checklist and audit data use the account-scoped encrypted profile row.
- **Local SQLCipher encryption is retained. Google Drive app-data backups are readable JSON, not end-to-end encrypted.** Private-entry locks are UI/report protections, not encryption against the owner of the Google Drive account. Existing Firebase authentication, AI provider, Drive permission, upload verification and APK certificate are unchanged.
- Backup format **10** includes the new fields and workspace. Restore validates financial links and totals atomically. Older backups remain supported when no newer private/account/linked tool data would be lost; otherwise request a current backup. Older app builds cannot restore a v10 backup.
- Existing entries and older loan principal/savings default to Cash. Known payment methods map to matching accounts. Organizer pooled money is separate. Reconcile accounts to the real balances before relying on them; no bank connection or automatic bank sync is implied.
- Receipt OCR supports Latin script, including English/Roman Urdu; it does not support Urdu-script OCR. Recognition can miss items, taxes or discounts; correct the review and receipt total. Large receipts are limited to 50 reviewed items per batch with an explicit error, rather than silently dropping items.
- Quantity/rate attribution excludes refunds because a monetary refund alone does not establish the quantity returned. Forecasts are estimates from recorded data, not guarantees. No physical-phone performance benchmark is claimed.
- Offline voice language availability, actual home-screen launcher behaviour and biometric hardware need checking on a supported physical phone. Emulator checks cover fallback/validation/UI/data; they cannot prove recognition quality for every phone or receipt.

Implementation uses the official [ML Kit text recognition guide](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) and [Android SpeechRecognizer API](https://developer.android.com/reference/android/speech/SpeechRecognizer).
