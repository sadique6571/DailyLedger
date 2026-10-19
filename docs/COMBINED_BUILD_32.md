# Combined ZIP update — version 2.0.0 build 32

Based on the verified build 31 Money tools branch, not the older ZIP ledger implementation.

## Included

- All 24 Money tools workflows, complete lazy category list, privacy/history and learning protections from build 31.
- ZIP UI refinements: common spacing/corner/elevation/animation tokens, tonal/gradient cards, separate top/bottom bars, and smaller dashboard/transactions/credit/kameti/organizer components.
- Payment proofs on loan, udhar, personal kameti payments and organizer collections. Photos (JPEG/PNG/WebP) and PDFs open in a selected compatible viewer. Actual content signatures and owner/payment links are validated.
- Full attachment content is stored in the SQLCipher database and exported in backup v11; no 1 MB silent omission. Maximum 10 MB per file, 20 MB and 200 files per account, with explicit errors. External viewers receive a temporary cache copy only when opened.
- Edits queue one backup after 15 minutes; existing 3-hour periodic and app-open catch-up checks remain. Android/network restrictions can delay execution.
- Room schema 10→11 adds a separate proof table without replacing build 31 transaction columns; older migrations remain supported.
- Backup v11 restores proofs and financial records atomically, validates linked records and rejects malformed content. Restoring pre-v11 backups over current proofs is blocked to protect attachments.
- Cleartext network requests are disabled through the network security configuration.

## Deliberately retained compatibility

The APK stays **2.0.0**, build **32**, with the existing certificate for in-place updates. The ZIP's new release key, SDK/plugin changes and R8 configuration were not copied into the established build.

Local database encryption remains. **Drive backups are readable JSON, not E2EE**; they include private records and proofs. The ZIP's recovery-key-dependent encryption was not enabled because it would break login-only recovery after reinstall or phone loss. A secure account-recovery key service would require a separately configured backend; a UID alone is not an encryption secret. No new Firebase deployment is required by this update.

Only selected UI/security/attachment changes are taken from the ZIP. The category cutoff and older finance, backup and calculation logic are not imported. Physical-device external viewer, voice, widget and biometric behaviour still needs user-device verification.
