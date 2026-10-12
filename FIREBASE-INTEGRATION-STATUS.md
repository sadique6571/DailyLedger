# Firebase integration status

The Android app uses Firebase Authentication and Firebase AI Logic. Firestore is not used for ledger backup; current backups are stored in each user's Google Drive app-data folder. SQLCipher continues to encrypt the Room database on the device.

## Backup migration

- New backups are plain UTF-8 ledger JSON in the private, hidden Google Drive `appDataFolder`; the Drive REST API uses the Google `drive.appdata` authorization scope.
- No backup passphrase or client-side cloud-backup encryption is used. Access relies on the Google account and Drive permission. Treat the Drive account as sensitive.
- Firebase Firestore rules allow an authenticated owner to read their legacy `users/{uid}/backups/latest` document, but block all client create/update/delete requests. The current app has no Firestore backup or restore client.
- Existing Firestore snapshots and legacy encrypted Drive files are not automatically copied or restored. Retain the original phone/data and export or use a legacy app version to migrate records before relying on a new-device restore.

The owner deployed the read-only Firestore rules to project `daily-ledger-4d8ef`; deployments should still be reviewed and verified in Firebase Console. Rule tests can be run with:

```powershell
npm test
```

## Firebase AI

Firebase AI Logic with Gemini is optional and separate from backup. The project owner must configure AI Logic and App Check in Firebase Console; see `FIREBASE-AI-SETUP.md`. Building the APK does not activate inference or change billing.
