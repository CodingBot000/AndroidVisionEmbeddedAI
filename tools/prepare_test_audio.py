#!/usr/bin/env python3
"""Extract only the official test waveform from the checksum-pinned archive; linear resample to Android's 16k input."""
import pathlib,tarfile,wave,io,hashlib,json
import numpy as np
root=pathlib.Path(__file__).resolve().parents[1]
m=json.loads((root/'models/model-manifest.json').read_text())
archive=root/'models/downloads/moonshine-ko.tar.bz2'
if not archive.exists():archive=root/'models/moonshine-ko.tar.bz2'
assert hashlib.sha256(archive.read_bytes()).hexdigest()==m['speech']['archive_sha256']
with tarfile.open(archive) as tar:
 raw=tar.extractfile(m['speech']['id']+'/test_wavs/0.wav').read()
with wave.open(io.BytesIO(raw)) as audio:
 rate=audio.getframerate();assert audio.getnchannels()==1 and audio.getsampwidth()==2
 samples=np.frombuffer(audio.readframes(audio.getnframes()),dtype='<i2')
output=np.interp(np.arange(round(len(samples)*16000/rate))*rate/16000,np.arange(len(samples)),samples).clip(-32768,32767).astype('<i2')
destination=root/'app/src/androidTest/assets/korean16k.pcm';destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(output.tobytes())
print(f'{destination}: {rate} Hz → 16000 Hz; {len(output)} samples, official test-only audio')
