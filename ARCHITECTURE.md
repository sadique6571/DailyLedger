# Daily Ledger 1.7.16 architecture

## Device and identity

Firebase Authentication provides Google/email sign-in. Firebase UID scopes ledger rows and account-bound Drive backup filenames; it is not a secret and is never used as encryption key material. SQLCipher encrypts the Room database using a random device key wrapped in Android Keystore. Existing plaintext databases are checkpointed, exported to a verified encrypted temporary file, and atomically replaced. Corruption/open failures preserve the file and stop startup safely. Android automatic backup/device transfer is excluded; Google Drive backup is the recovery route.

## Ledger model

Room schema 6 adds loan contacts, payment methods, committee members, persisted salary-cycle state and archived period summaries. Ledger JSON format 5 is independent of the former cloud envelope; formats 1–4 remain importable. Salary cycles advance only after explicit confirmation; starting a cycle archives its summary and carries its remaining balance forward. Manual calendar-month archives are snapshots only—the original transactions remain in the ledger. All cycle state and archive summaries are included in owner migration, deletion, Drive backup, and restore. Loan overpayment and duplicate/personal-limit kameti turns are rejected transactionally. Marking a member turn received updates its schedule status; the actual payout amount is recorded separately through Receive amount.

## Google Drive account backup

1. Export canonical ledger JSON and verify the included Firebase UID matches the signed-in account.
2. Request the Google `drive.appdata` scope and save the JSON in the user's private, hidden `appDataFolder`. No separate backup encryption or passphrase is used; Google account security protects the file.
3. Store the Drive file version locally. Background uploads proceed only if the remote version still matches the last version seen on this phone; otherwise sync stops and asks the user to restore or intentionally replace from Settings.
4. Manual **Back up now** requires confirmation and replaces the previous account-scoped backup. Restore validates the embedded owner UID before importing.

Restore runs as a Room transaction. It rejects colliding transaction IDs and checks that the number of inserted transactions matches the snapshot before reporting success; malformed restores leave the previous local ledger intact. Firebase Firestore is not used for current backups. Legacy Firestore snapshots and old passphrase-encrypted Drive files are not automatically migrated or restored by the current client; preserve the prior phone/local data and export it before switching devices.

AI category corrections are saved per account in an Android-Keystore-encrypted on-device preference record. Exact learned item phrases take precedence over generated categories on later Auto Fill drafts; those private phrases are applied locally and are not sent to Gemini.

## AI and external requests

The offline rules engine handles common income/expense drafts and calculates spending insights on-device with no API. It normalizes grouped rupees and Urdu digits, accepts k/hazar/lakh forms, and rejects ambiguous batches as a whole instead of dropping lines. Loan, saving and kameti requests stay in their dedicated ledgers.

Optional Gemini through Firebase AI Logic handles fallback Auto Fill and additional suggestions only. Fresh opt-in consent, Auth/App Check preflight, bounded generation, account/consent rechecks and local parsing guard the workflow. Built-in category aggregates may be sent; custom labels, identity and transaction notes are excluded from automatic summaries. Only Auto Fill text unrecognized locally is sent to the enabled cloud fallback. Cached suggestions are Keystore-encrypted.

Play Integrity is used for the distributed APK; no shared debug token. AI limits in the app are local convenience controls, while Firebase/model quotas remain separate. Optional city weather calls Open-Meteo without GPS permission.

## Trust and cost

Drive backups are readable JSON, so anyone with access to the Google account or the app's Drive permission may read them. Protect that Google account and device accordingly. SQLCipher encryption of the local on-device Room database is unchanged; this migration removes only backup encryption/passphrases. Firebase Authentication and optional Gemini/App Check remain separate owner-configured services. A copied public development signing key is not a production signing identity.
