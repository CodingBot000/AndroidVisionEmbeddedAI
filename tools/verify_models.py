#!/usr/bin/env python3
import hashlib,json,pathlib,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
def verify():
 m=json.loads((ROOT/'models/model-manifest.json').read_text())
 for entry in m['files']+[m['runtime']]:
  p=ROOT/entry['path']
  if not p.is_file():raise ValueError(f'Missing {p}; run tools/fetch_models.py --accept-moonshine-license')
  if p.stat().st_size!=entry['size']:raise ValueError(f'Size mismatch: {p}')
  h=hashlib.sha256()
  with p.open('rb') as f:
   for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
  if h.hexdigest()!=entry['sha256']:raise ValueError(f'SHA-256 mismatch: {p}')
 labels=(ROOT/'app/src/main/assets/models/labels.txt').read_text().splitlines()
 assert len(labels)==90
 for i,name in {0:'person',43:'bottle',46:'cup',61:'chair',72:'laptop',75:'keyboard',76:'cell phone',83:'book'}.items():assert labels[i]==name,(i,name)
 with zipfile.ZipFile(ROOT/m['runtime']['path']) as aar:
  for abi in m['runtime']['abis']:assert f'jni/{abi}/libsherpa-onnx-jni.so' in aar.namelist()
 assert json.loads((ROOT/'app/src/main/assets/models/model-manifest.json').read_text())==m
 print(f'VERIFIED {len(m["files"])} model assets + sherpa AAR; checksums, labels and ABI entries')
if __name__=='__main__':
 try:verify()
 except Exception as e:raise SystemExit(f'Model verification failed: {e}')
