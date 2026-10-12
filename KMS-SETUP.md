> **Historical / legacy only.** Current Daily Ledger versions use Google Drive app-data backup and do not call Cloud KMS, Cloud Functions, or Firestore for new backups. Do not enable billing or create these resources for current backup. Legacy Firestore and older encrypted Drive backups are not automatically restored by the current client. See `GOOGLE-SETUP.md` and `REVIEW-AND-SETUP.md`.

# Cloud KMS + Firebase Functions setup

Daily Ledger uses one Cloud KMS symmetric KEK to wrap per-account random backup DEKs. The KEK key material never belongs in the APK, Firestore or this repository. The **KMS resource name is not secret**.

Production Functions deployment requires the Firebase project to use the Blaze plan, and Cloud KMS requires billing to be enabled.

## Billing and activation status

First check whether the existing project is on Spark or Blaze. Obtain owner approval for the budget before upgrading or creating paid resources. No billing upgrade, KMS provisioning or live deployment was performed in this repository review.

For the manually created SOFTWARE key below, listed KMS prices are about USD 0.06 per active key version/month and USD 0.03 per 10,000 cryptographic operations, prorated. Functions, builds, artifact storage, Firestore, Storage and network usage have separate prices. No-cost allowances do not guarantee a zero bill; budget alerts and request limits are not spending caps. Optional Gemini billing is a separate consideration when linking billing.

Sources: [KMS pricing](https://cloud.google.com/kms/pricing), [Functions deployment](https://firebase.google.com/docs/functions/get-started), [Firebase pricing plans](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans).

With an authenticated Google Cloud CLI, this read-only command checks billing linkage:

```bash
gcloud billing projects describe daily-ledger-4d8ef --format='value(billingEnabled)'
```

An access error means the billing status is unknown. `True` does not verify working backup or approve any expenditure.

## Recommended resources

Project: `daily-ledger-4d8ef`

- Key ring: `dailyledger-keyring`
- Location: `global`
- Symmetric key: `backup-kek`
- KMS resource name:
  `projects/daily-ledger-4d8ef/locations/global/keyRings/dailyledger-keyring/cryptoKeys/backup-kek`

After resource/billing approval, enable KMS. Inspect the existing key first; reuse it if present. Do not treat a permission/API error as proof that it is absent. Only create the key ring and key below if they do not already exist:

```bash
gcloud services enable cloudkms.googleapis.com --project=daily-ledger-4d8ef
gcloud kms keys describe backup-kek \
  --project=daily-ledger-4d8ef --location=global --keyring=dailyledger-keyring
```

Creation commands for missing resources:

```bash
gcloud kms keyrings create dailyledger-keyring \
  --project=daily-ledger-4d8ef --location=global
gcloud kms keys create backup-kek \
  --project=daily-ledger-4d8ef \
  --location=global \
  --keyring=dailyledger-keyring \
  --purpose=encryption --protection-level=software
```

For default 2nd-gen Firebase Functions, the runtime service account is normally the Compute Engine default service account. For this project number it is:

`876166304446-compute@developer.gserviceaccount.com`

Verify the actual runtime account in the deployed Function configuration before granting access. These read-only commands can be used after the Functions deployment below:

```bash
gcloud functions describe wrapBackupKey --gen2 \
  --project=daily-ledger-4d8ef --region=us-central1 \
  --format='value(serviceConfig.serviceAccountEmail)'
gcloud functions describe unwrapBackupKey --gen2 \
  --project=daily-ledger-4d8ef --region=us-central1 \
  --format='value(serviceConfig.serviceAccountEmail)'
```

If both use the default account above, grant only encrypt/decrypt use on the CryptoKey:

```bash
gcloud kms keys add-iam-policy-binding backup-kek \
  --project=daily-ledger-4d8ef \
  --location=global \
  --keyring=dailyledger-keyring \
  --member="serviceAccount:876166304446-compute@developer.gserviceaccount.com" \
  --role="roles/cloudkms.cryptoKeyEncrypterDecrypter"
```

If Firebase shows a different runtime service account for the deployed functions, grant the role to that service account instead.

Do not grant project-wide KMS admin access. Do not delete or disable key versions needed by existing backups; rotating to a new version does not remove the need for earlier versions during recovery.

## Functions parameter

`functions/index.js` uses Firebase parameterized configuration:

`defineString("KMS_KEY_NAME")`

The repository includes the project default and a public configuration example. From the repository root, copy the example once. If the project environment file already exists, preserve it and check its `KMS_KEY_NAME` instead of overwriting it:

```bash
cp -n functions/.env.example functions/.env.daily-ledger-4d8ef
```

Actual environment files remain ignored by Git. Never put raw encryption keys or service-account private keys in these files. Firebase loads the project parameter file and prompts if the value is missing. Expected value:

`projects/daily-ledger-4d8ef/locations/global/keyRings/dailyledger-keyring/cryptoKeys/backup-kek`

With Node.js 22 and the Firebase CLI available and authenticated, deploy only the two backup Functions. They use `us-central1`, matching the Android client's current default:

```bash
npm --prefix functions install
npm --prefix functions test
firebase deploy --project daily-ledger-4d8ef \
  --only functions:wrapBackupKey,functions:unwrapBackupKey
```

## App Check

Both callable key Functions declare `enforceAppCheck: true`. In Firebase Console, also register the Android app with App Check. Distributed builds use Play Integrity; no shared debug token is included.

Use package `com.sadique.dailyledger`. The v1.6.1 release APK retains the existing development signing certificate for upgrades. Its SHA-256 is:

```text
4C:22:14:C4:D4:B6:E3:8B:FA:81:44:F1:41:CC:24:A7:18:00:3F:55:20:41:06:17:76:6D:19:5C:CD:2D:23:1D
```

This is the signing-certificate fingerprint, not the APK file checksum. A future production signing key needs its own registration. For distribution outside Play, use the documented [outside-Play settings](https://firebase.google.com/docs/app-check/android/play-integrity): do not require Play recognition/licensing, and retain device integrity checks. Do not turn off callable App Check enforcement to bypass a setup error.

Firebase AI Logic should likewise be set to App Check **Enforced** in Firebase Console before production use.

## Rules

Ensure the project's Firestore database and default Firebase Storage bucket exist. Inspect the current rules first: deployment replaces them. If other applications share the project, preserve their required rules through a reviewed merge. Deploy the owner-scoped backup rules:

```bash
firebase deploy --project daily-ledger-4d8ef --only firestore:rules,storage
```

## Verify recovery before relying on it

Keep the first phone's data intact and use a test account plus a second phone.

1. Sign in, create a test ledger entry, enable cloud backup and wait for the app's successful backup status.
2. Check `users/<uid>/backups/latest` for V3 metadata and a wrapped key. The matching Storage object contains ciphertext. Auth profile fields and backup metadata remain visible to authorized administrators.
3. On the second phone, sign into the same account and choose Restore. Verify amounts, categories, loans and kameti records without an extra backup passphrase.
4. Confirm another account cannot access this backup and that cancelling restore leaves local data unchanged.
5. Verify a later successful backup and a stale second-phone revision conflict choice.

A deployed Function or an enabled backup switch does not prove recovery works. Live two-phone verification remains pending. Encryption does not retroactively remove legacy plaintext backups or encrypt readable CSV/PDF exports.

## Security properties

- Function UID always comes from `request.auth.uid`.
- `unwrapBackupKey` ignores client-supplied UID/wrapped-key values and reads Firestore metadata server-side.
- Cloud KMS AAD is `uid:v3` for new writes (`uid:v2` for legacy decryption); decryption requires the same AAD.
- The DEK is random 256-bit key material and is never derived from UID, email, password or auth token.
- This design is account-based encrypted backup, not zero-knowledge E2EE.
