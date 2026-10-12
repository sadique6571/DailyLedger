# Current backup changes — 1.7.16

- New ledger backups use the signed-in user's private Google Drive app-data folder and readable JSON; there is no backup passphrase or separate cloud-backup encryption.
- Firebase Firestore backup writes are disabled. Existing owner backups are read-only legacy data and are not automatically migrated.
- Background Drive sync checks the last-seen file version and refuses to overwrite a newer remote copy. A confirmed manual **Back up now** can intentionally replace it.
- The local SQLCipher-encrypted database is unchanged.

The notes below describe historical behavior in earlier versions and are not instructions for the current backup flow.

## Historical fixes

- Migrated AI runtime to Firebase AI Logic (`firebase-ai`) with a current Gemini Flash model and structured response schemas.
- App Check debug/release providers remain initialized at app startup; callable key Functions enforce App Check.
- V1 backup restore now forces a fresh DEK for safe V2 migration.
- V2 restore retries once with a freshly unwrapped DEK when the device cache is stale.
- New/missing backup metadata can no longer reuse an unrelated cached DEK without a wrapped key.
- Ciphertext upload / Firestore metadata commit order preserves the previous usable backup until the new revision commits and cleans failed new uploads.
- Firestore and Storage rules validate owner paths, exact metadata fields and immutable encrypted objects.
- Fresh empty devices show Backup found -> Restore / Skip instead of auto-restoring.
- Delete Cloud Backup turns automatic backup off so it is not immediately recreated.
- Sign-out clears the cached account DEK.
- Removed obsolete server/build references and updated CI to current Firebase rules/Functions tests.
- Added redesigned UI, selected-city weather, remaining-balance percentage/progress, and optional retro transaction sounds.
