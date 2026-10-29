#!/usr/bin/env python3
"""Upload prepared current signing secrets via stdin, without logging values."""
import argparse
import json
from pathlib import Path
import subprocess

NAMES = (
    'DAILYLEDGER_SIGNING_KEYSTORE_BASE64', 'DAILYLEDGER_SIGNING_STORE_PASSWORD',
    'DAILYLEDGER_SIGNING_KEY_ALIAS', 'DAILYLEDGER_SIGNING_KEY_PASSWORD',
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--repo', required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    directory = args.directory.resolve()
    if directory == root or root in directory.parents:
        parser.error('Signing payloads must be outside the checkout')
    if any(not (directory / name).is_file() for name in NAMES):
        parser.error('Required prepared signing payloads are missing')
    result = subprocess.run(['gh', 'secret', 'list', '--repo', args.repo, '--json', 'name'],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise SystemExit('GitHub secret access is unavailable; no secrets were uploaded')
    existing = {secret['name'] for secret in json.loads(result.stdout)}
    conflicts = existing.intersection(NAMES)
    if conflicts:
        raise SystemExit('Refusing to overwrite existing secret bindings: ' + ', '.join(sorted(conflicts)))
    for name in NAMES:
        with (directory / name).open('rb') as payload:
            result = subprocess.run(['gh', 'secret', 'set', name, '--repo', args.repo],
                                    stdin=payload, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode:
            raise SystemExit(f'Upload failed for {name}; inspect secret names/status before resuming')
        print(f'Stored Actions secret: {name}')
    print('Current signing secrets uploaded; verify CI before merging. New key remains inactive.')


if __name__ == '__main__':
    main()
