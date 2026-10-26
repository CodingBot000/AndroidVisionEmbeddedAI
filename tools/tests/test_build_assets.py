"""Regression checks for runtime-only CI acquisition and its integrity boundary."""
import contextlib
import hashlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import fetch_models
import verify_models


class BuildAssetsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.aar = self.root / 'app/libs/runtime.aar'
        self.aar.parent.mkdir(parents=True)
        with zipfile.ZipFile(self.aar, 'w') as archive:
            for abi in ('arm64-v8a', 'x86_64'):
                archive.writestr(f'jni/{abi}/libsherpa-onnx-jni.so', b'test-only')
        self.manifest = {
            'files': [{'path': 'unavailable-model.ort'}],
            'runtime': {
                'path': 'app/libs/runtime.aar', 'url': self.aar.as_uri(),
                'size': self.aar.stat().st_size,
                'sha256': hashlib.sha256(self.aar.read_bytes()).hexdigest(),
                'abis': ['arm64-v8a', 'x86_64'],
            },
        }
        self.write_manifest()
        for module in (fetch_models, verify_models):
            root_patch = patch.object(module, 'ROOT', self.root)
            root_patch.start()
            self.addCleanup(root_patch.stop)
        manifest_patch = patch.object(fetch_models, 'M', self.manifest)
        manifest_patch.start()
        self.addCleanup(manifest_patch.stop)

    def write_manifest(self):
        path = self.root / 'models/model-manifest.json'
        path.parent.mkdir(exist_ok=True)
        path.write_text(json.dumps(self.manifest))

    def test_runtime_only_downloads_only_aar_without_accepting_model_license(self):
        source = self.root / 'source.aar'
        self.aar.replace(source)
        self.manifest['runtime']['url'] = source.as_uri()
        self.write_manifest()
        with patch.object(sys, 'argv', ['fetch_models.py', '--runtime-only']), \
                patch.object(fetch_models.urllib.request, 'urlopen', wraps=fetch_models.urllib.request.urlopen) as request, \
                contextlib.redirect_stdout(io.StringIO()):
            fetch_models.main()
        request.assert_called_once_with(source.as_uri(), timeout=120)
        self.assertEqual(source.read_bytes(), self.aar.read_bytes())
        self.assertFalse((self.root / 'app/src/main/assets').exists())

    def test_full_acquisition_still_requires_explicit_license_flag(self):
        with patch.object(sys, 'argv', ['fetch_models.py']), \
                patch.object(fetch_models, 'download') as download, \
                contextlib.redirect_stderr(io.StringIO()), \
                self.assertRaises(SystemExit) as result:
            fetch_models.main()
        self.assertEqual(2, result.exception.code)
        download.assert_not_called()

    def test_runtime_verification_does_not_require_model_weights(self):
        with contextlib.redirect_stdout(io.StringIO()) as output:
            verify_models.verify(runtime_only=True)
        self.assertIn('model weights NOT checked', output.getvalue())

    def test_full_verification_does_not_pass_with_missing_weights(self):
        with self.assertRaisesRegex(ValueError, 'Missing .*unavailable-model'):
            verify_models.verify()

    def test_runtime_verification_rejects_same_size_corruption(self):
        data = bytearray(self.aar.read_bytes())
        data[-1] ^= 1
        self.aar.write_bytes(data)
        with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch'):
            verify_models.verify(runtime_only=True)

    def test_runtime_verification_rejects_wrong_size(self):
        self.aar.write_bytes(b'bad')
        with self.assertRaisesRegex(ValueError, 'Size mismatch'):
            verify_models.verify(runtime_only=True)

    def test_runtime_verification_rejects_missing_abi_even_with_matching_hash(self):
        self.manifest['runtime']['abis'].append('missing-abi')
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, 'Missing sherpa JNI ABI: missing-abi'):
            verify_models.verify(runtime_only=True)

    def test_download_rejects_bad_checksum_without_installing_partial_file(self):
        source = self.root / 'source.aar'
        source.write_bytes(b'invalid download')
        target = self.root / 'downloaded.aar'
        with self.assertRaisesRegex(ValueError, 'SHA-256 mismatch'):
            fetch_models.download(source.as_uri(), target, '0' * 64)
        self.assertFalse(target.exists())
        self.assertFalse(target.with_suffix('.partial').exists())


if __name__ == '__main__':
    unittest.main()
