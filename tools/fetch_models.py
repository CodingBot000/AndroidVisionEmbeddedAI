#!/usr/bin/env python3
"""Acquire pinned build-time assets. Never used by the Android application."""
import argparse,hashlib,json,pathlib,shutil,tarfile,tempfile,urllib.request
ROOT=pathlib.Path(__file__).resolve().parents[1]
M=json.loads((ROOT/'models/model-manifest.json').read_text())
def digest(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  for chunk in iter(lambda:f.read(1024*1024),b''):h.update(chunk)
 return h.hexdigest()
def download(url,path,expected):
 if path.exists() and digest(path)==expected:return
 path.parent.mkdir(parents=True,exist_ok=True)
 tmp=path.with_suffix('.partial')
 try:
  with urllib.request.urlopen(url,timeout=120) as response,tmp.open('wb') as out:shutil.copyfileobj(response,out)
  if digest(tmp)!=expected:raise ValueError(f'SHA-256 mismatch: {url}')
  tmp.replace(path)
 finally:tmp.unlink(missing_ok=True)
def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--accept-moonshine-license',action='store_true',help='Confirm review of LICENSES/MOONSHINE-LICENSE.txt for your intended use')
 args=p.parse_args()
 if not args.accept_moonshine_license:
  p.error('Read LICENSES/MOONSHINE-LICENSE.txt; Korean weights use Community License. Then pass --accept-moonshine-license.')
 assets=ROOT/'app/src/main/assets/models';assets.mkdir(parents=True,exist_ok=True)
 byname={x['asset']:x for x in M['files']}
 download(M['vision']['url'],assets/'efficientdet_lite0.tflite',byname['efficientdet_lite0.tflite']['sha256'])
 # Labels are part of the verified model, not a separately mutable URL.
 import zipfile
 if not (assets/'labels.txt').exists() or digest(assets/'labels.txt')!=byname['labels.txt']['sha256']:
  with zipfile.ZipFile(assets/'efficientdet_lite0.tflite') as z:(assets/'labels.txt').write_bytes(z.read('labelmap.txt'))
 cache=ROOT/'models/downloads/moonshine-ko.tar.bz2'
 download(M['speech']['url'],cache,M['speech']['archive_sha256'])
 wanted=['encoder_model.ort','decoder_model_merged.ort','tokens.txt','LICENSE']
 with tarfile.open(cache) as archive:
  for name in wanted:
   if (assets/name).exists() and digest(assets/name)==byname[name]['sha256']:continue
   member=archive.getmember(M['speech']['id']+'/'+name)
   if not member.isfile():raise ValueError('Unexpected archive member')
   with archive.extractfile(member) as f,(assets/name).open('wb') as out:shutil.copyfileobj(f,out)
 runtime=M['runtime'];download(runtime['url'],ROOT/runtime['path'],runtime['sha256'])
 if not (assets/'model-manifest.json').exists() or (assets/'model-manifest.json').read_bytes()!=(ROOT/'models/model-manifest.json').read_bytes():
  shutil.copyfile(ROOT/'models/model-manifest.json',assets/'model-manifest.json')
 import verify_models
 verify_models.verify()
if __name__=='__main__':
 try:main()
 except Exception as e:raise SystemExit(f'Model acquisition failed: {e}')
