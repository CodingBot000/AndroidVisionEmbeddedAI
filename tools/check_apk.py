#!/usr/bin/env python3
"""Inspect production package permissions, exact bundled model hashes and native ABI entries."""
import argparse,hashlib,json,os,pathlib,subprocess,zipfile
root=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('apk',type=pathlib.Path);a=p.parse_args()
m=json.loads((root/'models/model-manifest.json').read_text())
sdk=pathlib.Path(os.environ.get('ANDROID_HOME',str(pathlib.Path.home()/'Library/Android/sdk')))
aapt=sorted(sdk.glob('build-tools/*/aapt2'))[-1]
manifest=subprocess.check_output([str(aapt),'dump','permissions',str(a.apk)],text=True)
assert 'android.permission.INTERNET' not in manifest,manifest
assert 'android.permission.CAMERA' in manifest and 'android.permission.RECORD_AUDIO' in manifest
with zipfile.ZipFile(a.apk) as z:
 for f in m['files']:
  data=z.read('assets/models/'+f['asset']);assert len(data)==f['size'] and hashlib.sha256(data).hexdigest()==f['sha256']
 for abi in m['runtime']['abis']:assert f'lib/{abi}/libsherpa-onnx-jni.so' in z.namelist()
 assert not any('test_wavs' in n or 'korean16k.pcm' in n or 'screenshots/' in n for n in z.namelist())
 assert 'assets/THIRD_PARTY_NOTICES.txt' in z.namelist()
 if 'release' in a.apk.name:
  # The fixed debug simulation canvas is not part of release DEX.
  assert all(b'FIXTURE COORDINATE SPACE' not in z.read(n) for n in z.namelist() if n.endswith('.dex'))
print(manifest.strip())
print('PASS: exact model hashes, native ABI entries, notices, no INTERNET or test-only media; bytes='+str(a.apk.stat().st_size))
