# ROBOT VISION

**Offline Perception Console · Powered by Moonshine AI**

한국어 음성 명령과 후면 카메라 객체 탐지를 연결하는 Android 온디바이스 AI 앱입니다. 서버·API 키·클라우드 음성 API를 사용하지 않습니다. Kotlin, Coroutines/Flow, Jetpack Compose, MVI, Dagger Hilt, CameraX, LiteRT/MediaPipe Tasks, sherpa-onnx, Room, DataStore로 구현했습니다.

소스 구현·빌드·단위·모델 호스트·에뮬레이터 검증과 실제 폰 센서 검증을 구분합니다. **실기기는 DEVICE_PENDING**입니다.

개발계획서·상세 검증 로그·스크린샷이 있는 `docs/` 및 `doc/`는 사용자 결정에 따라 로컬에만 보존하며 GitHub 커밋에서 제외합니다. 공개 저장소의 빌드·모델 정보는 이 README, `models/model-manifest.json`, `NOTICE.md`를 기준으로 확인하세요.

## 빌드와 실행

Android SDK 36 / build tools, JDK 17 이상(이번 실행 호스트는 JDK 21, 소스·바이트코드 target 17), Python 3, 네트워크가 가능한 개발 호스트가 필요합니다. Gradle 8.13 wrapper를 포함합니다. SDK 경로는 개인 `local.properties`의 `sdk.dir` 또는 `ANDROID_HOME`으로 설정합니다.

한국어 모델은 **Community License**입니다. [원문](LICENSES/MOONSHINE-LICENSE.txt)을 읽고 intended use를 확인한 후 취득합니다. 이 프로젝트의 개발 검증은 평가/테스트 목적이며, 상업 배포 동의를 대신하지 않습니다.

```bash
python3 tools/fetch_models.py --accept-moonshine-license
python3 tools/verify_models.py
./gradlew --version
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleRelease
```

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (`dev.robotvision.debug`)
- Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk` (`dev.robotvision`, 배포 서명은 사용자 키로 수행)
- minSdk 26 / compileSdk·targetSdk 36, arm64-v8a 및 x86_64 JNI 패키징.
- 모델과 AAR 대용량 바이너리는 Git 제외. 위 스크립트가 고정 SHA-256으로 재취득합니다. 이미 준비된 파일은 확인 후 재사용합니다.
- 번들 모델 약 73 MiB 외에 양쪽 ABI의 native runtime이 포함되어 APK가 큽니다. 앱 첫 엔진 로딩 시 private model 사본 설치 공간도 필요합니다.

## 사용 흐름

1. Dashboard의 실제 모델 준비 상태를 확인하고 START LIVE SCAN으로 이동합니다.
2. 카메라 권한을 허용합니다. 마이크 없이도 컵·물병·사람·의자·노트북·키보드·책·휴대폰을 직접 선택할 수 있습니다.
3. 말하기 버튼을 누르고 ‘컵 찾아줘’라고 말한 후 **녹음 종료**를 누릅니다. 최대 8초에 자동 종료하며 non-streaming 한국어 STT를 수행합니다.
4. 목표 클래스가 최근 5개 분석 결과 중 3개에서 threshold 이상이면 TARGET FOUND와 이벤트를 표시합니다. 검색은 20초 제한, 결과 박스는 800ms 미갱신 시 숨깁니다.
5. 검색 중지·재검색·대상 초기화, History 전체 삭제, Diagnostics, Settings를 사용할 수 있습니다.

지원 예문: ‘물병 어디 있어’, ‘의자 검색해줘’, ‘검색 중지’, ‘다시 찾아줘’, ‘대상 초기화’. 지원하지 않는 물체는 명확히 안내합니다.

## 실제 엔진과 모델

- Vision: **EfficientDet-Lite0 detection metadata v1**, uint8 RGB 320×320, COCO 80개 유효 클래스/90 index 슬롯. LiteRT **2.3.0 CompiledModel CPU** 우선. native 초기화 후 **추론 전 버퍼 용량 검사**를 수행하며, 용량·출력 계약 실패 시 동일 모델을 **MediaPipe Tasks Vision 0.10.32 CPU**로 실행하고 UI에 실제 backend와 원인을 표시합니다. 에뮬레이터에서 MediaPipe fallback 추론을 확인했습니다.
- STT: **sherpa-onnx 1.13.8 + sherpa-onnx-moonshine-tiny-ko-quantized-2026-02-27**, Moonshine v2 encoder/merged decoder ORT + tokens. AudioRecord 16 kHz mono PCM, CPU 2 threads.
- GPU/NPU는 미검증으로 선택 UI를 제공하지 않습니다. 실제 가속 지원이나 성능을 주장하지 않습니다.
- [모델 출처·정확한 입출력·SHA-256](models/model-manifest.json), [NOTICE](NOTICE.md).

## Debug / 자동 테스트

Debug Diagnostics에서 시뮬레이션을 **직접** 켤 수 있습니다. `SIMULATION / FAKE DATA` 배지, 고정 cup 좌표, 명령 fixture로 MVI/Room/UI를 확인합니다. Fake latency는 생성하지 않습니다. 실제 엔진 실패 시 자동 전환하지 않으며 Release에는 fixture 데이터/시뮬레이션 진입점이 없습니다.

JVM: 명령 파서, 안정화, cooldown, timeout, 좌표 회전/FILL_CENTER, output contract, proxy 소유권, Room/DataStore, MVI/Flow/음성 오류/백그라운드. Compose와 실제 JNI 엔진 테스트는 에뮬레이터에서 수행합니다. 테스트가 AI 정확도를 입증하는 것은 아닙니다.

```bash
# Optional host model verification on macOS ARM64
uv venv --python 3.12 .venv
uv pip install --python .venv/bin/python tensorflow==2.20.0 numpy==2.5.4
python3 tools/prepare_host_validation.py
.venv/bin/python tools/host_model_smoke.py

# Android tests use test-only audio extracted from the pinned archive
.venv/bin/python tools/prepare_test_audio.py
adb devices
./gradlew :app:connectedDebugAndroidTest
```

`connectedDebugAndroidTest`는 에뮬레이터/기기가 있을 때만 실행합니다. 실제 마이크나 카메라가 없어도 Compose UI·공식 음성 파일·native black-image 추론 검증은 가능합니다.

## 검증 상태

2026-10-17 개발 검증 결과입니다. 이번 Git 설정·커밋 작업에서 앱 테스트를 새로 실행한 결과는 아닙니다.

| 항목 | 기록된 결과 |
|---|---|
| Debug / R8 최적화 Release 빌드 | PASS |
| JVM 테스트 | 55 PASS |
| 에뮬레이터 UI·실제 엔진 테스트 | 6 PASS |
| Lint | 오류 0, 경고 37 |
| 호스트 모델 smoke | 실제 Vision·한국어 ASR 파일 추론 PASS |
| Release 부팅·엔진 준비·백그라운드 복귀 | 로컬 테스트 서명으로 PASS |
| 최종 APK 검사 | INTERNET 없음, 모델 SHA·JNI ABI 확인 |

실제 카메라·마이크·박스 정렬·한국어 명령 인식률·폰 지연시간·GPU/NPU·발열·장시간 안정성은 미검증입니다. 호스트·에뮬레이터 결과를 실제 폰 성능이나 AI 정확도로 해석하면 안 됩니다. R8의 Kotlin metadata 경고가 남아 있으며, native API·Flogger 호출 스택·Protobuf 반사 접근 보존 규칙을 적용했습니다.

시뮬레이션 결과는 실제 카메라 장면이나 객체 정확도·성능의 증거가 아닙니다. 추가 모델 취득과 테스트에는 위 재현 명령을 사용하세요.

## 문서와 프라이버시

- [모델 manifest](models/model-manifest.json)
- [라이선스 및 고지](NOTICE.md)
- [한국어 모델 라이선스 원문](LICENSES/MOONSHINE-LICENSE.txt)
- [Compose 디자인 및 시안 프롬프트](design/IMAGE_PROMPTS.md)

상세 개발 문서는 로컬 전용입니다. 이미지와 음성 원본은 저장·전송하지 않습니다. Room 이벤트에는 시각·유형·대상·score·backend·latency 등 최소 메타데이터만 저장하며 사용자가 삭제할 때까지 로컬 보관합니다. INTERNET 권한과 Android backup/device transfer를 비활성화합니다.
