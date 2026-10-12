# Google sign-in and Drive backup

Google login now signs in to **Firebase Authentication**. A locally decoded Google token is not used as an authenticated session.

1. Follow `START-HERE-ROMAN-URDU.md` to create the Firebase Android app, register SHA-1/SHA-256 and enable Google and Email/Password.
2. A current `google-services.json` is already placed in `app/` in this prepared ZIP. If Google is not enabled yet, enable Google first and then download a fresh `google-services.json` and replace the existing file in `app/`.
3. The Google Services plugin generates `default_web_client_id`. This is the Web OAuth client ID used by Credential Manager, not the Android client ID. A `GOOGLE_WEB_CLIENT_ID` in local.properties is only a fallback; Firebase configuration is still required.
4. Enable Google Drive API in the Google Cloud project backing this same Firebase app. Ensure its Android OAuth registration matches the package and signing certificate. Configure the OAuth consent screen; when in testing mode, add the Google account as a test user.
5. Sign in, open **Settings → Google Drive backup → Back up now**, and approve the `drive.appdata` authorization. No backup passphrase is needed. The full ledger is saved as readable JSON in the Google account's private, hidden app-data folder; it does not appear in the normal Drive file list.

Restore requires the same Daily Ledger Firebase account and Google Drive account. Firebase email/password accounts can use Drive too when the matching Google account is available on the device; otherwise add/sign in to the Google account that owns the Drive backup before authorizing Drive.

Firestore backups are no longer used by this app. Old Firestore snapshots and previously encrypted `daily-ledger-backup.dlb` files are not automatically migrated or restored by the current version. Keep the phone containing the original records; export them or install an app version that can still restore the legacy backup before moving to another phone. The on-device Room database remains SQLCipher-encrypted.

If Google is linked to a Firebase account, Firebase keeps its provider identity. This app does not guess an account by email or create a fake local login. If Firebase reports an account/provider collision, sign in using the previously registered method; the app does not automatically merge separate accounts.

Official documentation:
- https://firebase.google.com/docs/auth/android/google-signin
- https://developers.google.com/identity/authorization/android/authorize-access
- https://developers.google.com/workspace/drive/api/guides/appdata
