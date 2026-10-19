#!/usr/bin/env python3
"""Optional macOS ARM64 host validation tools; not part of the Android app."""
import pathlib,platform,tarfile
from fetch_models import M,ROOT,download
if platform.system()!='Darwin' or platform.machine()!='arm64':raise SystemExit('This pinned host runtime is for macOS ARM64 only; Android tests remain available.')
host=M['host_validation'];cache=ROOT/'models/downloads/sherpa-host.tar.bz2'
download(host['runtime_url'],cache,host['sha256'])
with tarfile.open(cache) as archive:archive.extractall(ROOT/'models/downloads',filter='data')
model_archive=ROOT/'models/downloads/moonshine-ko.tar.bz2'
if not model_archive.exists():model_archive=ROOT/'models/moonshine-ko.tar.bz2'
import hashlib
assert hashlib.sha256(model_archive.read_bytes()).hexdigest()==M['speech']['archive_sha256']
with tarfile.open(model_archive) as archive:
 member=archive.getmember(M['speech']['id']+'/test_wavs/0.wav')
 destination=ROOT/'models/downloads'/member.name;destination.parent.mkdir(parents=True,exist_ok=True)
 destination.write_bytes(archive.extractfile(member).read())
print('Prepared checksum-pinned official sherpa-onnx host runtime and reference waveform')
