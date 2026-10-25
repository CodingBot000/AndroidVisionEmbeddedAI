# 모델 출처와 재현 계약

확보 일시: 2026-10-17T09:26:40.933774+00:00. 네트워크는 개발 호스트의 취득 단계에만 사용한다. 앱은 번들 파일 및 private copy만 사용하고 INTERNET 권한이 없다.

## Vision

- 모델: EfficientDet-Lite0 detection metadata v1, int8 weights / **uint8 input**.
- [공식 TensorFlow 예제 설치 스크립트](https://github.com/tensorflow/examples/blob/master/lite/examples/object_detection/raspberry_pi/setup.sh)가 지정하는 [정확한 모델 파일](https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/object_detection/rpi/lite-model_efficientdet_lite0_detection_metadata_1.tflite).
- [EfficientDet upstream](https://github.com/google/automl/tree/master/efficientdet), Apache-2.0 원문을 LICENSES에 보존.
- 입력 [1,320,320,3] NHWC RGB uint8, quantization scale=1/128, zero point=127. RGB 0..255 bytes를 그대로 입력하며 float 정규화를 중복 적용하지 않는다.
- **호스트 allocate_tensors 및 invoke로 확인한 출력 순서:** boxes [1,25,4] (ymin,xmin,ymax,xmax), classes [1,25], scores [1,25], count [1], 전부 float32.
- graph 안에 TFLite_Detection_PostProcess/NMS가 있으므로 별도 NMS를 하지 않는다. FlatBuffer에는 출력 형상이 동적으로 비어 있어 직접 CompiledModel 출력 할당과 충돌 가능.
- labels.txt는 이 모델 ZIP의 labelmap.txt를 byte-for-byte 추출. 90 index 슬롯 중 80 유효 COCO 클래스. cup=46, bottle=43, person=0, chair=61, laptop=72, keyboard=75, cell phone=76, book=83. ??? 슬롯은 제외.
- 최초 Google MediaPipe int8 v1 후보 파일은 실제 검사 결과 [1,19206,90], [1,19206,4] raw output 2개였다. 후처리를 가정하지 않고 4개 출력의 공식 task-library 배포물로 선택을 변경했다. 이 후보는 APK에서 제외했다.
- Android는 LiteRT **2.3.0 CompiledModel CPU**를 먼저 생성하고, native 추론 **전에** 입력/출력 버퍼 크기를 검사한다. 기대값은 307200 bytes / [100,25,25,1] float elements이다. 충분한 용량일 때만 warm-up을 호출한다. 동적 scalar 출력에 warm-up을 쓰면 native heap corruption이 발생하는 것을 앱 부팅 테스트에서 확인하여 이 검사를 추가했다. 출력 계약 실패 시 실제 **MediaPipe Tasks Vision 0.10.32 ObjectDetector CPU**를 사용하며 화면에 이유를 노출한다. 에뮬레이터에서 실제 이 fallback 경로로 native detect 호출을 검증했다. 정확한 최종 로그는 TEST_EVIDENCE 참조.

## 한국어 STT

- 모델: sherpa-onnx-moonshine-tiny-ko-quantized-2026-02-27. [공식 sherpa Moonshine v2 목록](https://k2-fsa.github.io/sherpa/onnx/moonshine/models-v2.html#sherpa-onnx-moonshine-tiny-ko-quantized-2026-02-27-korean), [정확한 아카이브](https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-moonshine-tiny-ko-quantized-2026-02-27.tar.bz2).
- Android runtime: 공식 sherpa-onnx **1.13.8** AAR. API는 같은 release Kotlin source와 AAR javap를 대조했다. arm64-v8a/x86_64 native JNI 포함을 검사했다.
- Moonshine **v2 두 파일**: encoder_model.ort / decoder_model_merged.ort + tokens.txt. v1의 preprocess/uncached/cached decoder 4파일 구성을 적용하지 않는다.
- AudioRecord: 16000 Hz mono PCM16 → normalized float samples. greedy_search, CPU 2 threads, non-streaming. 공식 host test_wav는 원본 22050 Hz이며 Android instrumentation에서는 16000 Hz로 선형 resample한 test-only PCM을 사용한다.
- 호스트와 Android 에뮬레이터에서 공식 테스트 음성의 한국어 텍스트를 실제 반환했다. ‘컵 찾아줘’ 마이크 명령 품질을 검증한 것은 아니다.
- **가중치 라이선스는 Moonshine AI Community License. 영어 MIT와 구별.** 아카이브 LICENSE 원문을 LICENSES/MOONSHINE-LICENSE.txt 및 APK models/LICENSE로 포함한다. 평가/테스트 목적의 개발이다. 상업 이용 시 원문의 등록·매출 등 조건을 별도로 검토한다. 사용자 대신 상업 동의를 선언하지 않는다.
- Required attribution UI: **Powered by Moonshine AI**. NOTICE와 APK THIRD_PARTY_NOTICES에 원문 필수 저작권 고지를 포함한다.

## 무결성 및 배치

`models/model-manifest.json`이 source URL, archive SHA, AAR SHA, 각 실제 APK asset SHA/size와 텐서 계약의 기준이다. 모델/런타임 바이너리는 Git에서 제외한다. 아래 취득은 라이선스 검토 후 수행한다.

```bash
python3 tools/fetch_models.py --accept-moonshine-license
python3 tools/verify_models.py
```

취득기는 SHA 불일치 파일을 재다운로드하고 .partial 파일의 SHA 검증 후 rename한다. 아카이브는 필요한 네 파일만 정확한 member name으로 읽어 path traversal 없이 배치한다. test_wavs 및 미사용 모델은 production assets에 없다. native 로딩 전 ModelAssetManager가 private 사본의 size와 SHA를 검사하며 손상 시 검증된 번들에서 다시 설치한다.

| APK asset | bytes | SHA-256 |
|---|---:|---|
| LICENSE | 13344 | `6148d7574a6554b7379b633cfd4c4fe5840c3f548d13bc83e00b52dc6fa00abd` |
| decoder_model_merged.ort | 58327336 | `95aa9f2e764b80625d2889d6ec9f05c965808e540ac50c16abd10c7ea33fe44b` |
| efficientdet_lite0.tflite | 4563519 | `2e04c53bfeac0ac2a30c057c7e2a777594ce39baaac35a92f74fb1e8c4fc4e0b` |
| encoder_model.ort | 13238176 | `947260d46252f48eada86a34986b3f70c01d68a343959949a77375b94debd055` |
| labels.txt | 661 | `f8803ef7900160c629d570848dfda4175e21667bf7b71f73f8ece4938c9f2bf2` |
| tokens.txt | 549350 | `2870d843e14c1e187bf1913a521562a63b53933814bd7f2145120468f494a049` |

AAR SHA-256: `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`. 공식 release digest: `sha256:633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`.
