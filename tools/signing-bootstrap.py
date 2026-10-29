#!/usr/bin/env python3
"""Transfer prepared signing payloads to GitHub using an authenticated encrypted bundle."""
import argparse
import base64
import json
import os
from pathlib import Path
import secrets
import subprocess

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

AAD = b'DailyLedger signing bootstrap v1'
NAMES = (
    'DAILYLEDGER_SIGNING_KEYSTORE_BASE64', 'DAILYLEDGER_SIGNING_STORE_PASSWORD',
    'DAILYLEDGER_SIGNING_KEY_ALIAS', 'DAILYLEDGER_SIGNING_KEY_PASSWORD',
    'DAILYLEDGER_NEXT_SIGNING_KEYSTORE_BASE64', 'DAILYLEDGER_NEXT_SIGNING_STORE_PASSWORD',
    'DAILYLEDGER_NEXT_SIGNING_KEY_ALIAS', 'DAILYLEDGER_NEXT_SIGNING_KEY_PASSWORD',
    'DAILYLEDGER_SIGNING_LINEAGE_BASE64',
)


def wrapping_key(token, salt):
    return HKDF(algorithm=hashes.SHA256(), length=32, salt=salt, info=AAD).derive(token.encode())


def seal(payloads, token):
    salt, nonce = secrets.token_bytes(32), secrets.token_bytes(12)
    encrypted = AESGCM(wrapping_key(token, salt)).encrypt(
        nonce, json.dumps(payloads, sort_keys=True).encode(), AAD)
    return {'version': 1, 'salt': base64.b64encode(salt).decode(),
            'nonce': base64.b64encode(nonce).decode(), 'ciphertext': base64.b64encode(encrypted).decode()}


def unseal(bundle, token):
    if bundle.get('version') != 1:
        raise ValueError('Unsupported signing bundle version')
    salt, nonce, ciphertext = (base64.b64decode(bundle[k], validate=True)
                              for k in ('salt', 'nonce', 'ciphertext'))
    if len(salt) != 32 or len(nonce) != 12:
        raise ValueError('Invalid signing bundle parameters')
    payloads = json.loads(AESGCM(wrapping_key(token, salt)).decrypt(nonce, ciphertext, AAD))
    if set(payloads) != set(NAMES) or any(not isinstance(v, str) or not v for v in payloads.values()):
        raise ValueError('Incomplete signing payloads')
    return payloads


def gh(*args, env, value=None):
    result = subprocess.run(['gh', *args], input=value, env=env,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError('GitHub operation failed: ' + ' '.join(args[:3]))
    return result.stdout


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--bundle', type=Path, required=True)
    p.add_argument('--seal-directory', type=Path)
    p.add_argument('--repo')
    args = p.parse_args()
    token = os.environ.get('DAILYLEDGER_SIGNING_BOOTSTRAP_TOKEN', '')
    if not token.startswith('github_pat_'):
        raise SystemExit('Set DAILYLEDGER_SIGNING_BOOTSTRAP_TOKEN to the existing fine-grained PAT securely')
    if args.seal_directory:
        root = Path(__file__).resolve().parents[1]
        directory = args.seal_directory.resolve()
        if directory == root or root in directory.parents:
            raise SystemExit('Prepared private material must be outside the checkout')
        payloads = {name: (directory / name).read_text() for name in NAMES}
        bundle = seal(payloads, token)
        if unseal(bundle, token) != payloads:
            raise SystemExit('Encrypted bundle verification failed')
        with args.bundle.open('x') as output:
            json.dump(bundle, output, indent=2)
            output.write('\n')
        print('Authenticated encrypted signing bundle created and verified. No plaintext keys were written to Git.')
        return
    if not args.repo:
        p.error('--repo is required for GitHub import')
    # Authenticate the whole ciphertext before attempting any remote secret mutation.
    try:
        payloads = unseal(json.loads(args.bundle.read_text()), token)
    except Exception:
        raise SystemExit('Cannot authenticate signing bundle. Check the existing PAT; no secrets were changed.') from None
    env = dict(os.environ, GH_TOKEN=token)
    existing = {s['name'] for s in json.loads(gh('secret', 'list', '--repo', args.repo, '--json', 'name', env=env))}
    conflicts = existing.intersection(NAMES)
    if conflicts:
        raise SystemExit('Existing signing secrets require review; refusing to overwrite: ' + ', '.join(sorted(conflicts)))
    uploaded = []
    try:
        for name in NAMES:
            gh('secret', 'set', name, '--repo', args.repo, env=env, value=payloads[name].encode())
            uploaded.append(name)
            print('Stored Actions secret: ' + name)
    except Exception:
        for name in reversed(uploaded):
            try:
                gh('secret', 'delete', name, '--repo', args.repo, '--yes', env=env)
            except Exception:
                print('Rollback could not remove newly created secret: ' + name)
        raise SystemExit('Signing import failed; newly created secrets were rolled back where possible') from None
    print('Current and future signing secrets imported. The future key is NOT active.')


if __name__ == '__main__':
    main()
