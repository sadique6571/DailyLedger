#!/usr/bin/env python3
"""Restore and verify CI signing material without exposing private values."""
import base64
import hashlib
import os
from pathlib import Path
import subprocess

# Public certificate fingerprint, deliberately pinned to preserve installed-app updates.
CURRENT_CERT_SHA256 = '4c2214c4d4b6e38bfa8144f141cc24a718003f5520410617766d195ccd2d231d'
REQUIRED = (
    'DAILYLEDGER_SIGNING_KEYSTORE_BASE64', 'DAILYLEDGER_SIGNING_STORE_PASSWORD',
    'DAILYLEDGER_SIGNING_KEY_ALIAS', 'DAILYLEDGER_SIGNING_KEY_PASSWORD',
)


def main():
    missing = [name for name in REQUIRED if not os.environ.get(name)]
    if missing:
        raise SystemExit('Configure the GitHub Actions secrets: ' + ', '.join(missing))
    target = Path(os.environ['RUNNER_TEMP']) / 'dailyledger-signing.p12'
    os.umask(0o077)
    try:
        decoded = base64.b64decode(os.environ[REQUIRED[0]], validate=True)
    except (ValueError, base64.binascii.Error):
        raise SystemExit('Signing keystore secret is not valid base64') from None
    created = False
    try:
        with target.open('xb') as output:
            created = True
            output.write(decoded)
        result = subprocess.run([
            'keytool', '-exportcert', '-keystore', str(target),
            '-storetype', 'PKCS12', '-alias', os.environ['DAILYLEDGER_SIGNING_KEY_ALIAS'],
            '-storepass:env', 'DAILYLEDGER_SIGNING_STORE_PASSWORD',
        ], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode:
            raise ValueError('Signing keystore could not be opened with the configured alias and password')
        if hashlib.sha256(result.stdout).hexdigest() != CURRENT_CERT_SHA256:
            raise ValueError('Signing certificate changed; refusing to break existing app updates')
        with open(os.environ['GITHUB_ENV'], 'a') as output:
            output.write(f'DAILYLEDGER_SIGNING_KEYSTORE_PATH={target}\n')
        print('Private signing key restored; existing app certificate verified.')
    except Exception:
        if created:
            target.unlink(missing_ok=True)
        raise


if __name__ == '__main__':
    main()
