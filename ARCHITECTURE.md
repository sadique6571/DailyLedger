# Daily Ledger 1.7.14 architecture

## Device and identity

Firebase Authentication provides Google/email sign-in. Firebase UID scopes ledger rows and backup permissions; it is not a secret and is never used as encryption key material. SQLCipher encrypts the Room database using a random device key wrapped in Android Keystore. Existing plaintext databases are checkpointed, exported to a verified encrypted temporary file, and atomically replaced. Corruption/open failures preserve the file and stop startup safely. Android automatic backup/device transfer is excluded; account backup is the recovery route.

## Ledger model

Room schema 6 adds loan contacts, payment methods, committee members, persisted salary-cycle state and archived period summaries. Ledger JSON format 5 is independent of cloud envelope V3; formats 1–4 remain importable. Salary cycles advance only after explicit confirmation; starting a cycle archives its summary and carries its remaining balance forward. Manual calendar-month archives are snapshots only—the original transactions remain in the ledger. All cycle state and archive summaries are included in owner migration, deletion, encrypted cloud/Drive backup, and restore. Loan overpayment and duplicate/personal-limit kameti turns are rejected transactionally. Marking a member turn received updates its schedule status; the actual payout amount is recorded separately through Receive amount.

## Cloud account backup

1. Export canonical JSON for the active account.
2. Generate a random AES-256 data key, or recover the current V3 key through authenticated callable Functions.
3. Encrypt with AES-GCM and a fresh 96-bit nonce. AAD binds UID and revision. The change-detection fingerprint is HMAC-derived; ciphertext additionally has a SHA-256 integrity check.
4. Upload ciphertext to the immutable owner/revision Storage path.
5. Commit metadata using a Firestore revision transaction. A failed revision comparison cannot overwrite another phone. A timeout has unknown outcome, so it must not trigger deletion of the potentially committed object.
6. Delete the old object only after a confirmed metadata commit. Ambiguous failures may leave ciphertext orphans for later administration; preserving a usable backup takes priority.

Restore runs as a Room transaction. It rejects colliding transaction IDs and checks that the number of inserted transactions matches the snapshot before reporting success; malformed restores leave the previous local ledger intact. Manual Drive backups encrypt the same complete ledger JSON and select the latest matching app-data file.

Functions wrap/unwrap through Cloud KMS with UID/version AAD. Unwrap accepts expected revision/fingerprint/version but never a client-selected user or wrapped key: it reads the authenticated user's current Firestore metadata. Exact metadata matching prevents caching a newly rotated key under an older envelope. Firestore V1 and encrypted Storage V2 can be read; new writes use V3 only.

A fresh empty phone asks Restore / Skip. Local edits with a different remote revision require explicit restore or replace. Automatic sync never uploads an empty local ledger over an existing cloud snapshot; replace remains an explicit user action, preserving recovery after a mistaken local reset. Manual Restore and Back up now remain available independently of automatic backup and cached local backup status. Delete compares the revision and disables automatic backup on that phone. Other devices with an initialized old revision stop on a missing remote rather than silently recreating it. Storage retention/soft-delete and orphan cleanup are separate from deleting the active metadata.

Local changes queue account-backup work behind any upload already in progress instead of cancelling it. Each queued worker exports the current complete ledger snapshot, so rapid successive entries are included by a later backup attempt.

AI category corrections are saved per account in an Android-Keystore-encrypted on-device preference record. Exact learned item phrases take precedence over generated categories on later Auto Fill drafts; those private phrases are applied locally and are not sent to Gemini.

## AI and external requests

The offline rules engine handles common income/expense drafts and calculates spending insights on-device with no API. It normalizes grouped rupees and Urdu digits, accepts k/hazar/lakh forms, and rejects ambiguous batches as a whole instead of dropping lines. Loan, saving and kameti requests stay in their dedicated ledgers.

Optional Gemini through Firebase AI Logic handles fallback Auto Fill and additional suggestions only. Fresh opt-in consent, Auth/App Check preflight, bounded generation, account/consent rechecks and local parsing guard the workflow. Built-in category aggregates may be sent; custom labels, identity and transaction notes are excluded from automatic summaries. Only Auto Fill text unrecognized locally is sent to the enabled cloud fallback. Cached suggestions are Keystore-encrypted.

Play Integrity is used for the distributed APK; no shared debug token. AI limits in the app are local convenience controls, while Firebase/model quotas remain separate. KMS Functions additionally enforce 30 key operations per account per UTC day. Optional city weather calls Open-Meteo without GPS permission.

## Trust and cost

New Firestore v4 backups are encrypted on-device with a passphrase and stored in one Firestore document capped at 900 KB. Firebase stores ciphertext, but a lost passphrase cannot be recovered. This path uses Firebase Spark and does not write to Storage, Functions or KMS. Legacy v2/v3 restore still depends on the original Storage/KMS services. Gemini Developer API and App Check are separate owner-configured services; keep the project on Spark and follow `FIREBASE-AI-SETUP.md` if paid Firebase services are out of scope. A copied public development signing key is not a production signing identity.
