# Firebase integration status

The Android app uses Firebase Authentication, Firestore, Firebase AI Logic and Play Integrity App Check. Firebase AI Logic is already wired to the Gemini Developer API in source; the owner still needs to activate it in Firebase Console and configure App Check. See `FIREBASE-AI-SETUP.md`.

## Backup

New cloud snapshots use encrypted Firestore format v4: AES-256-GCM passphrase encryption happens on-device and ciphertext is stored in `users/{uid}/backups/latest`. Its content fingerprint is passphrase-keyed. Keep the project on Spark and deploy only Firestore rules:

```powershell
firebase deploy --only firestore:rules
```

New v4 backup does not use Storage, Functions or KMS. Its payload limit is 900 KB; larger ledgers should use the separately encrypted Google Drive backup or local CSV/PDF export. The same passphrase must be saved on every device before restore. A lost passphrase cannot be recovered by the app owner.

Existing v1 Firestore backups remain readable and migrate to v4 after restore. Legacy v2/v3 backups use the older Storage/KMS architecture and may need those original services available for restore. Do not delete legacy key versions while an old backup still depends on them.

Building the APK does not deploy Firestore rules, activate AI Logic, configure App Check, or perform a live cloud restore. No billing upgrade or paid resource provisioning is required or performed for the new Spark backup path.
