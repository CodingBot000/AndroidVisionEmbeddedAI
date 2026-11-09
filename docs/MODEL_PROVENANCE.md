# 모델 출처와 재현 계약

Lite3 확보일: 2026-11-09. 음성 모델은 기존 2026-10-17 확보 자산이다. 네트워크는 개발 호스트의 취득 단계에만 사용한다. 앱은 번들 파일 및 private copy만 사용하고 INTERNET 권한이 없다.

## Vision

- 모델: **EfficientDet-Lite3 detection metadata v1**, [TensorFlow Hub 원본](https://tfhub.dev/tensorflow/lite-model/efficientdet/lite3/detection/metadata/1?lite-format=tflite), Google AutoML Apache-2.0. 모델 SHA-256은 아래 표와 manifest에 고정한다.
- TensorFlow Lite 호스트 Interpreter로 확인한 입력은 `[1,512,512,3]` NHWC RGB uint8, quantization scale 1/128, zero point 127이다. 출력은 boxes `[1,25,4]`, classes `[1,25]`, scores `[1,25]`, count `[1]`의 float32 순서이며 graph 안에서 NMS를 수행한다.
- 동봉된 labelmap은 기존 파일과 SHA-256이 같고 90개 슬롯이다. 정확한 target/class 매핑은 manifest와 `verify_models.py`에서 확인한다.
- Android LiteRT CompiledModel CPU는 실행 전 입력 786432 bytes와 출력 `[100,25,25,1]` 요소 용량을 검사한다. 불충분한 동적 버퍼에는 native run을 호출하지 않고 MediaPipe Tasks CPU로 전환한다. ARM64 API 36 에뮬레이터에서 Lite3도 출력이 `[1,1,1,1]`로 할당되어 MediaPipe CPU fallback과 실제 black-image 추론을 확인했다. 실기기 성능·인식률은 미검증이다.
- Lite0의 동적 출력 버퍼 때문에 native heap corruption이 발생했던 이력은 `TEST_EVIDENCE.md`에 보존한다. 과거 Lite0 검증을 Lite3 검증으로 간주하지 않는다.

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
| efficientdet_lite3.tflite | 11953887 | `b17218c2d9ca9d513378fff862f15af907276af309e6b141d564afe96b6c712c` |
| encoder_model.ort | 13238176 | `947260d46252f48eada86a34986b3f70c01d68a343959949a77375b94debd055` |
| labels.txt | 661 | `f8803ef7900160c629d570848dfda4175e21667bf7b71f73f8ece4938c9f2bf2` |
| tokens.txt | 549350 | `2870d843e14c1e187bf1913a521562a63b53933814bd7f2145120468f494a049` |

AAR SHA-256: `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`. 공식 release digest: `sha256:633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`.
