"""CI signing restores must reject missing, malformed or unexpected key material."""
import base64
import hashlib
import importlib.util
import os
from pathlib import Path
import stat
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('restore_signing', Path(__file__).parents[1] / 'tools/restore-signing.py')
restore = importlib.util.module_from_spec(spec)
spec.loader.exec_module(restore)


class RestoreSigningTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.target = self.root / 'dailyledger-signing.p12'
        self.environment = {
            'RUNNER_TEMP': self.temp.name, 'GITHUB_ENV': str(self.root / 'github-env'),
            'DAILYLEDGER_SIGNING_KEYSTORE_BASE64': base64.b64encode(b'keystore fixture').decode(),
            'DAILYLEDGER_SIGNING_STORE_PASSWORD': 'test-only-store-password',
            'DAILYLEDGER_SIGNING_KEY_ALIAS': 'test-alias',
            'DAILYLEDGER_SIGNING_KEY_PASSWORD': 'test-only-key-password',
        }
        self.addCleanup(os.umask, os.umask(0o077))

    def test_missing_secrets_fail_before_creating_a_key_file(self):
        with patch.dict(os.environ, {}, clear=True), self.assertRaises(SystemExit):
            restore.main()
        self.assertFalse(self.target.exists())

    def test_invalid_base64_never_creates_a_key_file(self):
        self.environment['DAILYLEDGER_SIGNING_KEYSTORE_BASE64'] = 'not valid!'
        with patch.dict(os.environ, self.environment, clear=True), self.assertRaises(SystemExit):
            restore.main()
        self.assertFalse(self.target.exists())

    def test_wrong_certificate_is_rejected_and_removed(self):
        result = subprocess.CompletedProcess([], 0, stdout=b'wrong certificate', stderr=b'')
        with patch.dict(os.environ, self.environment, clear=True), patch.object(restore.subprocess, 'run', return_value=result):
            with self.assertRaisesRegex(ValueError, 'certificate changed'):
                restore.main()
        self.assertFalse(self.target.exists())
        self.assertFalse((self.root / 'github-env').exists())

    def test_bad_password_is_rejected_and_removed(self):
        result = subprocess.CompletedProcess([], 1, stdout=b'', stderr=b'private diagnostics')
        with patch.dict(os.environ, self.environment, clear=True), patch.object(restore.subprocess, 'run', return_value=result):
            with self.assertRaisesRegex(ValueError, 'could not be opened'):
                restore.main()
        self.assertFalse(self.target.exists())

    def test_existing_file_is_not_deleted_or_overwritten(self):
        self.target.write_bytes(b'existing key')
        with patch.dict(os.environ, self.environment, clear=True), self.assertRaises(FileExistsError):
            restore.main()
        self.assertEqual(b'existing key', self.target.read_bytes())

    def test_verified_key_is_private_and_only_path_is_exported(self):
        cert = b'certificate fixture'
        result = subprocess.CompletedProcess([], 0, stdout=cert, stderr=b'')
        with patch.dict(os.environ, self.environment, clear=True), \
             patch.object(restore, 'CURRENT_CERT_SHA256', hashlib.sha256(cert).hexdigest()), \
             patch.object(restore.subprocess, 'run', return_value=result) as run:
            restore.main()
        self.assertEqual(b'keystore fixture', self.target.read_bytes())
        self.assertEqual(0o600, stat.S_IMODE(self.target.stat().st_mode))
        self.assertEqual(f'DAILYLEDGER_SIGNING_KEYSTORE_PATH={self.target}\n', (self.root / 'github-env').read_text())
        command = run.call_args.args[0]
        self.assertNotIn(self.environment['DAILYLEDGER_SIGNING_STORE_PASSWORD'], command)
        self.assertIn('DAILYLEDGER_SIGNING_STORE_PASSWORD', command)


if __name__ == '__main__':
    unittest.main()
