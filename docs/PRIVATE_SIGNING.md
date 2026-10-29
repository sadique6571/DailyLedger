# Private APK signing

This change removes the checked-in keystore and hardcoded signing passwords.
GitHub Actions restores an encrypted PKCS#12 keystore into the runner's temporary
directory from secrets, verifies its public certificate fingerprint, and removes
the temporary file after verification. Private signing material is never packaged
into the APK, committed to Git, or included in build artifacts.

## Current signing identity and installed updates

The original certificate is retained only to authorize the update lineage and build an emulator upgrade-test baseline:

- SHA-1: `c6e19da96097e5424a34766e15746edca7c0f707`
- SHA-256: `4c2214c4d4b6e38bfa8144f141cc24a718003f5520410617766d195ccd2d231d`

Re-encrypting the existing private key with a strong random password keeps its
original certificate intact for upgrade testing. The final APK requires Android
9+ (API 28) and uses only the new private signer through APK Signature Scheme v3.
It does not include old-key v1/v2 signatures. It does **not** revoke copies of
the old private key already available in Git history or previous clones. That
key must still be treated as exposed until clients move to a new signing key.
Do not rewrite shared Git history as part of this migration.

The four legacy-signing repository Actions secrets are:

- `DAILYLEDGER_SIGNING_KEYSTORE_BASE64`
- `DAILYLEDGER_SIGNING_STORE_PASSWORD`
- `DAILYLEDGER_SIGNING_KEY_ALIAS`
- `DAILYLEDGER_SIGNING_KEY_PASSWORD`

Keep the prepared secret payloads outside the checkout. Upload them through the
GitHub secrets API or repository Settings → Secrets and variables → Actions.
Never paste values into chat, PRs, logs or tracked configuration. CI intentionally
fails before the APK build when these secrets are absent or the legacy certificate differs.
The final signing step also requires the five new-key/lineage secrets listed below.
Do not merge or dispatch this migration before uploading the required secrets.

Debug-only development without release credentials uses Android's normal local
debug key. CI injects the current app key for both debug and release so its
existing debug-to-release emulator installation checks update compatibility.
Production release tasks refuse to build without the private signing configuration.

## Prepare material once

`tools/prepare-signing.py` must be run with the old keystore still available,
using `LEGACY_KEYSTORE_PASSWORD` supplied securely through the environment:

```sh
python3 tools/prepare-signing.py \
  --legacy-store /secure/path/old-keystore \
  --legacy-alias androiddebugkey \
  --output-dir /secure/path/dailyledger-private-signing \
  --apksigner /path/to/android-sdk/build-tools/36.0.0/apksigner
```

It refuses an output directory inside the checkout or an existing directory.
It re-encrypts the current key, creates a new independent RSA-4096 key with a
random password, and generates and verifies the old-to-new signing lineage.
Output files have owner-only permissions. Back up the private material in an
owner-controlled encrypted vault before deleting the prepared workspace.
Losing the signing keys can prevent future updates; do not regenerate them per build.

The new material has already been prepared for this migration outside the checkout.
Its **public** certificate fingerprints are:

- SHA-1: `454f9c244050722bb0a29be1d2df214409fb5bf6`
- SHA-256: `28183f1c66f6430e9f98e90a9f561da0a1633a9c2a7c12037aad40f8d90f697b`

The owner confirmed that the new SHA-1 and SHA-256 were saved in Firebase.
The new key is now selected for Android 9+ release signing. The required additional
Actions secret names are `DAILYLEDGER_NEXT_SIGNING_KEYSTORE_BASE64`,
`DAILYLEDGER_NEXT_SIGNING_STORE_PASSWORD`, `DAILYLEDGER_NEXT_SIGNING_KEY_ALIAS`,
`DAILYLEDGER_NEXT_SIGNING_KEY_PASSWORD`, and `DAILYLEDGER_SIGNING_LINEAGE_BASE64`.
The workflow uses these secrets in `tools/rotate-signing.py`, verifies the selected
signer separately for API 28–32 and 33–37, then tests an old-signer release
upgrading to the rotated release on the API 29 emulator. The upgrade test verifies
that the sandbox UID and a private data marker survive installation. It subsequently
clears only the disposable emulator app data for the existing startup smoke test.

## Rotation prerequisites and remaining platform limits

1. Register the new SHA-1 and SHA-256 in the existing Firebase Android app,
   Google OAuth configuration and the relevant App Check/Play Integrity setup.
   Keep the current fingerprints registered during migration. Confirm real sign-in.
2. Implement the APK Signature Scheme v3 lineage when signing with the new key.
   Android 9+ recognizes the authorized successor key. The owner confirmed there
   are no Android 8 users, so minimum SDK is now 28 and old-key v1/v2 signatures
   are disabled. Android 8/8.1 can no longer install this release.
3. Update pinned-certificate validation deliberately for the rotation, verify
   signatures for both platform ranges, and test an existing installed release
   upgrading with retained records on supported Android 9+ devices.
4. Check Google sign-in, restore, backup and normal release installation before
   distributing a rotated APK. Do not change the package name or uninstall an
   existing installation to make an upgrade test pass.

This change does not rotate AI/provider tokens, change Firebase client configuration,
or deploy a Cloudflare worker.

## Managed GitHub integration cannot write secrets

When the cloud proxy forces the managed GitHub integration identity, a PAT in
cloud environment variables may not control the outgoing Authorization header.
Do not repeatedly regenerate the owner's token to work around that behavior.
The owner can instead add the existing fine-grained PAT directly in GitHub:
repository Settings → Secrets and variables → Actions → New repository secret,
with name `DAILYLEDGER_SIGNING_BOOTSTRAP_TOKEN`.

Re-run the **Import private DailyLedger signing secrets** workflow for the
`codex/private-app-keys` branch. Its GitHub-hosted runner authenticates with the
owner-supplied repository secret and imports the prepared current and future
signing secrets, then dispatches a separate APK verification workflow. The imported key is the one registered in Firebase; platform-specific certificate
checks and the emulator upgrade test protect installed updates.

`signing/bootstrap.enc.json` contains only an AES-256-GCM authenticated encrypted
bundle. HKDF-SHA256 derives a domain-separated wrapping key from the existing
high-entropy fine-grained PAT, with a random 32-byte salt and 12-byte nonce.
The private keys are the same already prepared for the published fingerprints;
the workflow does not generate replacement certificates. Decryption and bundle
validation occur before any remote mutation. Wrong tokens or altered ciphertext
are rejected. Plaintext payloads go to `gh secret set` over stdin, not logs,
command-line arguments, Git, or Actions artifacts. Do not enable shell tracing.
The bootstrap secret must contain that exact existing PAT, not a regenerated one.

After a successful import and APK verification, remove the temporary bootstrap
PAT secret from repository settings and revoke the PAT if no longer needed.
Retain an owner-controlled encrypted backup of the signing keystores and passwords.
