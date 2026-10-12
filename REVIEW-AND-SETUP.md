# Daily Ledger 1.7.16 — current setup and data handling

Daily Ledger is a personal offline-first finance ledger. Sign-in and optional AI use Firebase; ledger backup uses the signed-in user's Google Drive. No paid Firebase backup service is required.

## One-time setup

1. Configure Firebase Authentication for Google and/or Email/Password and install the matching `google-services.json`.
2. Enable Google Drive API in the same Google Cloud project. Ensure the Android OAuth client/package/signing certificate and consent-screen test users are correct.
3. Sign in to Daily Ledger, open **Settings → Google Drive backup → Back up now**, and approve Drive access. The permission is `drive.appdata`, which stores the backup in a private hidden app-data folder.
4. Confirm the successful backup status. On a second device, sign in to the same Daily Ledger account, authorize the Google account holding the backup, and choose Restore.

See `GOOGLE-SETUP.md` for Google account/OAuth notes and `FIREBASE-AI-SETUP.md` for optional Gemini/App Check setup.

## Backup behavior and limitations

- Backups are plain JSON in the user's Google Drive app-data folder. There is no backup passphrase and no separate backup encryption. Anyone with access to the Google account or this app's Drive permission may read the backup.
- Firebase Firestore is no longer used for ledger backup. The deployed rules leave the old owner document readable to that owner but block client create/update/delete.
- Legacy Firestore snapshots and prior passphrase-encrypted Drive files are not automatically migrated or restored by this version. Keep the old phone and its records; export them or restore with a compatible old app version before relying on a new-phone restore.
- Automatic sync checks the saved Drive file version and stops if another device changed it. Use Restore to take the remote copy, or use the confirmed **Back up now** action to intentionally replace it with this phone's full ledger.
- The local Room database remains encrypted with SQLCipher and a device Keystore-protected key. Removing cloud-backup encryption does not remove local database encryption.
- CSV/PDF exports are readable files saved only when explicitly requested. Cloud AI is a separate opt-in; consult its consent notice before sending text or summaries.

Do not clear or uninstall the only phone containing records until a Drive backup is confirmed and a test restore has been verified. Avoid testing restore against the only copy of real data.

## Build and verification

```powershell
.\gradlew.bat testDebugUnitTest assembleRelease
npm test
```

The release APK keeps the existing development signing identity for in-place upgrades; it is not a private production signing key. Building does not configure OAuth, deploy Firebase rules, enable AI or prove a live Drive restore.
