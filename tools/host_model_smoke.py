#!/usr/bin/env python3
"""Real host inference, separate from Android runtime and physical sensors."""
import json,pathlib,time,wave
import numpy as np
ROOT=pathlib.Path(__file__).resolve().parents[1]
A=ROOT/'app/src/main/assets/models'
results={}
import tensorflow as tf
interpreter=tf.lite.Interpreter(model_path=str(A/'efficientdet_lite0.tflite'),num_threads=2)
interpreter.allocate_tensors()
inputs=interpreter.get_input_details();outputs=interpreter.get_output_details()
assert inputs[0]['shape'].tolist()==[1,320,320,3] and inputs[0]['dtype']==np.uint8
print('VISION TENSOR CONTRACT',[(o['name'],o['shape'].tolist()) for o in outputs])
assert [o['shape'].tolist() for o in outputs]==[[1,25,4],[1,25],[1,25],[1]]
interpreter.set_tensor(inputs[0]['index'],np.zeros((1,320,320,3),dtype=np.uint8))
start=time.perf_counter();interpreter.invoke();data=[interpreter.get_tensor(o['index']) for o in outputs]
assert all(np.all(np.isfinite(x)) for x in data)
results['vision']={'engine':'TensorFlow Lite host Interpreter (Android uses CompiledModel or MediaPipe)','version':tf.__version__,'input':'black image uint8','outputs':[list(x.shape) for x in data],'inference_ms':(time.perf_counter()-start)*1000,'meaning':'tensor parsing and native inference smoke only; not object accuracy'}
import subprocess
binary=ROOT/'models/downloads/sherpa-onnx-v1.13.8-osx-arm64-shared-no-tts/bin/sherpa-onnx-offline'
wav=ROOT/'models/downloads/sherpa-onnx-moonshine-tiny-ko-quantized-2026-02-27/test_wavs/0.wav'
command=[str(binary),'--moonshine-encoder='+str(A/'encoder_model.ort'),'--moonshine-merged-decoder='+str(A/'decoder_model_merged.ort'),'--tokens='+str(A/'tokens.txt'),'--num-threads=2',str(wav)]
start=time.perf_counter();run=subprocess.run(command,capture_output=True,text=True,check=True)
(ROOT/'docs/evidence/speech-host-output.log').write_text(run.stdout+run.stderr)
assert 'text' in run.stdout+run.stderr
results['speech']={'engine':'sherpa-onnx 1.13.8 official macOS ARM64 CLI','output':'speech-host-output.log','elapsed_ms':(time.perf_counter()-start)*1000,'meaning':'official Korean test wav; not microphone command accuracy'}
output=ROOT/'docs/evidence/model-host.json';output.write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(results,ensure_ascii=False,indent=2))
