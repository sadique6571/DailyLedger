"""The committed bootstrap bundle must require the owner's exact token and resist tampering."""
import base64
import importlib.util
from pathlib import Path
import unittest
from cryptography.exceptions import InvalidTag

spec = importlib.util.spec_from_file_location('signing_bootstrap', Path(__file__).parents[1] / 'tools/signing-bootstrap.py')
bootstrap = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bootstrap)


class SigningBootstrapTest(unittest.TestCase):
    def setUp(self):
        self.payloads = {name: 'test-only-value-' + name for name in bootstrap.NAMES}
        self.token = 'test-only-owner-token'
        self.bundle = bootstrap.seal(self.payloads, self.token)

    def test_roundtrip_preserves_every_payload(self):
        self.assertEqual(self.payloads, bootstrap.unseal(self.bundle, self.token))
        self.assertNotIn(self.token, str(self.bundle))
        self.assertNotIn('test-only-value', str(self.bundle))

    def test_wrong_token_is_rejected(self):
        with self.assertRaises(InvalidTag):
            bootstrap.unseal(self.bundle, 'different-owner-token')

    def test_modified_ciphertext_is_rejected(self):
        data = bytearray(base64.b64decode(self.bundle['ciphertext']))
        data[0] ^= 1
        self.bundle['ciphertext'] = base64.b64encode(data).decode()
        with self.assertRaises(InvalidTag):
            bootstrap.unseal(self.bundle, self.token)

    def test_incomplete_payloads_are_rejected(self):
        payloads = dict(self.payloads)
        payloads.pop(bootstrap.NAMES[-1])
        with self.assertRaisesRegex(ValueError, 'Incomplete'):
            bootstrap.unseal(bootstrap.seal(payloads, self.token), self.token)


if __name__ == '__main__':
    unittest.main()
