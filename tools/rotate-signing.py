#!/usr/bin/env python3
"""Rotate release APK signing for Android 9+ while preserving old-app upgrade lineage."""
import argparse
import base64
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

LEGACY_SHA256 = '4c2214c4d4b6e38bfa8144f141cc24a718003f5520410617766d195ccd2d231d'
PRIVATE_SHA256 = '28183f1c66f6430e9f98e90a9f561da0a1633a9c2a7c12037aad40f8d90f697b'
REQUIRED = (
    'DAILYLEDGER_SIGNING_KEYSTORE_PATH', 'DAILYLEDGER_SIGNING_STORE_PASSWORD',
    'DAILYLEDGER_SIGNING_KEY_ALIAS', 'DAILYLEDGER_SIGNING_KEY_PASSWORD',
    'DAILYLEDGER_NEXT_SIGNING_KEYSTORE_BASE64', 'DAILYLEDGER_NEXT_SIGNING_STORE_PASSWORD',
    'DAILYLEDGER_NEXT_SIGNING_KEY_ALIAS', 'DAILYLEDGER_NEXT_SIGNING_KEY_PASSWORD',
    'DAILYLEDGER_SIGNING_LINEAGE_BASE64',
)


def command(*args):
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        message = result.stderr.decode(errors='replace') or result.stdout.decode(errors='replace')
        for name in REQUIRED:
            value = os.environ.get(name)
            if value:
                message = message.replace(value, '<REDACTED>')
        raise RuntimeError(f'{Path(args[0]).name} failed: {message[:4000]}')
    return result.stdout


def find_apksigner():
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
    versions = sorted((sdk / 'build-tools').glob('*'),
                      key=lambda p: [int(n) for n in re.findall(r'\d+', p.name)])
    return next(p / 'apksigner' for p in reversed(versions) if (p / 'apksigner').is_file())


def signer_digest(output):
    # Recent build-tools annotate signers with SDK ranges and may repeat the
    # same certificate for different ranges. Compare the unique certificate
    # identities, not a particular version's display label or line endings.
    values = set()
    certificate_lines = []
    for line in output.decode().splitlines():
        if not re.match(r"^\s*Signer\b", line) or "lineage" in line.lower():
            continue
        if "certificate SHA-256 digest:" not in line:
            continue
        certificate_lines.append(line.strip())
        match = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F]{64})\s*$", line)
        if match:
            values.add(match.group(1).lower())
    if len(values) != 1:
        # Certificate fingerprints are public; include only these report lines
        # when diagnosing unsupported formats, never signing-key values.
        raise ValueError('Expected exactly one platform-selected APK signing certificate; '
                         + repr(certificate_lines))
    return values.pop()


def verify_platforms(apksigner, apk):
    for minimum, maximum, expected in ((28, 32, PRIVATE_SHA256), (33, 37, PRIVATE_SHA256)):
        result = command(str(apksigner), 'verify', '--verbose', '--print-certs',
                         '--min-sdk-version', str(minimum), '--max-sdk-version', str(maximum), str(apk))
        if signer_digest(result) != expected:
            raise ValueError(f'Unexpected APK signer on Android API {minimum}-{maximum}')
        print(f'APK signatures verified for Android API {minimum}-{maximum}.')


def certificate(store, alias, password_variable):
    return command('keytool', '-exportcert', '-storetype', 'PKCS12', '-keystore', str(store),
                   '-alias', alias, '-storepass:env', password_variable)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--verify-only', action='store_true')
    args = parser.parse_args()
    apksigner = find_apksigner()
    if args.verify_only:
        verify_platforms(apksigner, args.apk)
        return
    missing = [name for name in REQUIRED if not os.environ.get(name)]
    if missing:
        raise SystemExit('Configure required signing secrets: ' + ', '.join(missing))
    if not args.apk.is_file():
        raise SystemExit('Release APK is missing')
    old = Path(os.environ['DAILYLEDGER_SIGNING_KEYSTORE_PATH'])
    old_cert = certificate(old, os.environ['DAILYLEDGER_SIGNING_KEY_ALIAS'], 'DAILYLEDGER_SIGNING_STORE_PASSWORD')
    if hashlib.sha256(old_cert).hexdigest() != LEGACY_SHA256:
        raise SystemExit('Legacy certificate changed; refusing to break installed updates')
    # Keep private files and the candidate APK away from uploaded artifact directories.
    with tempfile.TemporaryDirectory(prefix='dailyledger-rotation-', dir=os.environ['RUNNER_TEMP']) as temp:
        directory = Path(temp)
        new_store, lineage = directory / 'private.p12', directory / 'lineage.bin'
        for path, variable in ((new_store, 'DAILYLEDGER_NEXT_SIGNING_KEYSTORE_BASE64'),
                               (lineage, 'DAILYLEDGER_SIGNING_LINEAGE_BASE64')):
            data = base64.b64decode(os.environ[variable], validate=True)
            if not data:
                raise ValueError('Empty signing material')
            with path.open('xb') as output:
                path.chmod(0o600)
                output.write(data)
        new_cert = certificate(new_store, os.environ['DAILYLEDGER_NEXT_SIGNING_KEY_ALIAS'],
                               'DAILYLEDGER_NEXT_SIGNING_STORE_PASSWORD')
        if hashlib.sha256(new_cert).hexdigest() != PRIVATE_SHA256:
            raise SystemExit('Private certificate does not match the registered Firebase fingerprints')
        candidate = directory / 'rotated.apk'
        command(str(apksigner), 'sign', '--rotation-min-sdk-version', '28',
                '--min-sdk-version', '28', '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'false',
                '--v3-signing-enabled', 'true', '--v4-signing-enabled', 'false',
                '--lineage', str(lineage), '--out', str(candidate),
                '--ks', str(new_store),
                '--ks-key-alias', os.environ['DAILYLEDGER_NEXT_SIGNING_KEY_ALIAS'],
                '--ks-pass', 'env:DAILYLEDGER_NEXT_SIGNING_STORE_PASSWORD',
                '--key-pass', 'env:DAILYLEDGER_NEXT_SIGNING_KEY_PASSWORD', str(args.apk))
        verify_platforms(apksigner, candidate)
        # Preserve this build's non-debuggable, old-signer APK for the emulator upgrade test.
        legacy_apk = Path(os.environ['RUNNER_TEMP']) / 'dailyledger-legacy-release.apk'
        shutil.copyfile(args.apk, legacy_apk)
        shutil.copyfile(candidate, args.apk)
        # The old unrotated sidecar/profile checksums are not distributed as signatures.
        args.apk.with_name(args.apk.name + '.idsig').unlink(missing_ok=True)
    print('Private release signer activated for Android 9+; old-app upgrade lineage verified.')


if __name__ == '__main__':
    main()
