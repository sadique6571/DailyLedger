"""Pin the old/new signer identities selected for each supported Android range."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('rotate_signing', Path(__file__).parents[1] / 'tools/rotate-signing.py')
rotate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rotate)


def report(digest):
    return f'Verifies\nSigner #1 certificate SHA-256 digest: {digest}\n'.encode()


class RotateSigningTest(unittest.TestCase):
    def test_expected_signer_on_all_platform_ranges(self):
        with patch.object(rotate, 'command', side_effect=[report(rotate.PRIVATE_SHA256),
                          report(rotate.PRIVATE_SHA256)]) as command:
            rotate.verify_platforms(Path('/sdk/apksigner'), Path('/apk/release.apk'))
        self.assertEqual(2, command.call_count)
        self.assertIn('32', command.call_args_list[0].args)
        self.assertIn('37', command.call_args_list[1].args)

    def test_legacy_key_on_modern_android_is_rejected(self):
        with patch.object(rotate, 'command', return_value=report(rotate.LEGACY_SHA256)):
            with self.assertRaisesRegex(ValueError, '28-32'):
                rotate.verify_platforms(Path('/sdk/apksigner'), Path('/apk/release.apk'))

    def test_sdk_targeted_signer_reports_and_crlf_are_supported(self):
        output = (f'Signer (minSdkVersion=28, maxSdkVersion=32) #1 certificate SHA-256 digest: {rotate.PRIVATE_SHA256}\r\n'
                  f'Signer (minSdkVersion=33, maxSdkVersion=37) #1 certificate SHA-256 digest: {rotate.PRIVATE_SHA256}\r\n').encode()
        self.assertEqual(rotate.PRIVATE_SHA256, rotate.signer_digest(output))

    def test_source_stamp_does_not_replace_the_app_signer(self):
        output = report(rotate.PRIVATE_SHA256) + (
            f'Source Stamp Signer certificate SHA-256 digest: {rotate.LEGACY_SHA256}\n').encode()
        self.assertEqual(rotate.PRIVATE_SHA256, rotate.signer_digest(output))

    def test_multiple_unrelated_signers_are_rejected(self):
        with self.assertRaisesRegex(ValueError, 'exactly one'):
            rotate.signer_digest(report(rotate.LEGACY_SHA256) +
                                 report(rotate.PRIVATE_SHA256).replace(b'Signer #1', b'Signer #2'))


if __name__ == '__main__':
    unittest.main()
