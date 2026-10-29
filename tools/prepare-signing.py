#!/usr/bin/env python3
"""Prepare private signing material outside Git; never print secret values."""
import argparse
import base64
import hashlib
import os
from pathlib import Path
import secrets
import subprocess


def run(*args, env):
    result = subprocess.run(args, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError(f'{args[0]} failed (exit {result.returncode}); no secret values were logged')
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--legacy-store', type=Path, required=True)
    parser.add_argument('--legacy-alias', required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--apksigner', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    dest = args.output_dir.resolve()
    if dest == root or root in dest.parents:
        parser.error('Signing material must be stored outside the checkout')
    if dest.exists():
        parser.error('Output directory already exists; refusing to overwrite signing keys')
    if not os.environ.get('LEGACY_KEYSTORE_PASSWORD'):
        parser.error('Set LEGACY_KEYSTORE_PASSWORD securely in the process environment')
    os.umask(0o077)
    dest.mkdir(parents=True, mode=0o700)
    current_password, next_password = secrets.token_urlsafe(48), secrets.token_urlsafe(48)
    env = dict(os.environ, CURRENT_STORE_PASSWORD=current_password, NEXT_STORE_PASSWORD=next_password)
    current = dest / 'dailyledger-current.p12'
    future = dest / 'dailyledger-next.p12'
    alias, next_alias = 'dailyledger-release', 'dailyledger-next'
    run('keytool', '-importkeystore', '-noprompt', '-srckeystore', str(args.legacy_store.resolve()),
        '-srcalias', args.legacy_alias, '-srcstorepass:env', 'LEGACY_KEYSTORE_PASSWORD',
        '-srckeypass:env', 'LEGACY_KEYSTORE_PASSWORD', '-destkeystore', str(current),
        '-deststoretype', 'PKCS12', '-destalias', alias,
        '-deststorepass:env', 'CURRENT_STORE_PASSWORD', '-destkeypass:env', 'CURRENT_STORE_PASSWORD', env=env)
    run('keytool', '-genkeypair', '-noprompt', '-keystore', str(future), '-storetype', 'PKCS12',
        '-alias', next_alias, '-keyalg', 'RSA', '-keysize', '4096', '-sigalg', 'SHA256withRSA',
        '-validity', '10000', '-dname', 'CN=DailyLedger Release, O=DailyLedger',
        '-storepass:env', 'NEXT_STORE_PASSWORD', '-keypass:env', 'NEXT_STORE_PASSWORD', env=env)
    old_cert = run('keytool', '-exportcert', '-keystore', str(args.legacy_store.resolve()),
                   '-alias', args.legacy_alias, '-storepass:env', 'LEGACY_KEYSTORE_PASSWORD', env=env)
    current_cert = run('keytool', '-exportcert', '-keystore', str(current), '-alias', alias,
                       '-storepass:env', 'CURRENT_STORE_PASSWORD', env=env)
    assert current_cert == old_cert, 'Current signing identity must remain unchanged'
    next_cert = run('keytool', '-exportcert', '-keystore', str(future), '-alias', next_alias,
                    '-storepass:env', 'NEXT_STORE_PASSWORD', env=env)
    assert next_cert != old_cert, 'New key must have a different certificate'
    lineage = dest / 'dailyledger-signing-lineage.bin'
    run(str(args.apksigner.resolve()), 'rotate', '--out', str(lineage),
        '--old-signer', '--ks', str(current), '--ks-key-alias', alias,
        '--ks-pass', 'env:CURRENT_STORE_PASSWORD', '--key-pass', 'env:CURRENT_STORE_PASSWORD',
        '--new-signer', '--ks', str(future), '--ks-key-alias', next_alias,
        '--ks-pass', 'env:NEXT_STORE_PASSWORD', '--key-pass', 'env:NEXT_STORE_PASSWORD', env=env)
    run(str(args.apksigner.resolve()), 'lineage', '--in', str(lineage), '--print-certs', env=env)
    payloads = {
        'DAILYLEDGER_SIGNING_KEYSTORE_BASE64': base64.b64encode(current.read_bytes()).decode(),
        'DAILYLEDGER_SIGNING_STORE_PASSWORD': current_password,
        'DAILYLEDGER_SIGNING_KEY_ALIAS': alias,
        'DAILYLEDGER_SIGNING_KEY_PASSWORD': current_password,
        'DAILYLEDGER_NEXT_SIGNING_KEYSTORE_BASE64': base64.b64encode(future.read_bytes()).decode(),
        'DAILYLEDGER_NEXT_SIGNING_STORE_PASSWORD': next_password,
        'DAILYLEDGER_NEXT_SIGNING_KEY_ALIAS': next_alias,
        'DAILYLEDGER_NEXT_SIGNING_KEY_PASSWORD': next_password,
        'DAILYLEDGER_SIGNING_LINEAGE_BASE64': base64.b64encode(lineage.read_bytes()).decode(),
    }
    for name, value in payloads.items():
        (dest / name).write_text(value)
    for prefix, cert in [('current', current_cert), ('next', next_cert)]:
        (dest / f'{prefix}-certificate.der').write_bytes(cert)
        print(f'{prefix} certificate SHA-1: {hashlib.sha1(cert).hexdigest()}')
        print(f'{prefix} certificate SHA-256: {hashlib.sha256(cert).hexdigest()}')
    print('Private signing material prepared outside the checkout; new key is NOT active.')


if __name__ == '__main__':
    main()
