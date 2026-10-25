# 검증 범위와 결과

이 문서는 **2026-10-17에 수행한 검증 기록**을 외부 독자를 위해 요약한 것이다. 2026-10-25 문서 분류 시 원본의 결과·테스트 수·미검증 범위를 대조했다. 문서를 정리한 날짜를 앱 검증 날짜로 해석하면 안 된다.

원본 실행 로그에는 로컬 사용자 경로·기기 실행 식별자·개발 중 실패 이력이 혼재하므로 별도 로컬 보관한다. 이 공개 후보에는 검증 범위, 관련 소스, 재현 방법과 기술적으로 의미 있는 수정 사항을 남긴다.

## 검증 환경

- macOS ARM64 호스트, OpenJDK 21.0.10로 실행. Java/Kotlin bytecode target은 17이며 JDK 17에서 별도 실행한 결과는 아니다.
- Android SDK 36 / minSdk 26 / targetSdk 36, ARM64 Android 16 API 36 에뮬레이터, 360dp 화면.
- Gradle 8.13, AGP 8.13.2, Kotlin/Compose compiler 2.3.21, KSP 2.3.9, Hilt 2.57.2.
- 에뮬레이터 검증에서 실제 카메라·마이크 입력은 사용하지 않았다. 선택 JNI의 x86_64 포함은 확인했지만 x86_64 runtime을 실행한 것은 아니다.

## 결과와 의미

| 항목 | 보관된 결과 | 보장 범위 / 제한 |
|---|---|---|
| Debug APK 빌드 | PASS | Kotlin·Compose·Hilt·Room·실제 엔진 의존성의 Debug 빌드 |
| R8 최적화 Release APK 빌드 | PASS | Release 빌드 성공이며 production 배포 서명 완료를 뜻하지 않음 |
| JVM 테스트 | 55개 통과, 실패·오류·건너뜀 0 | 도메인·상태 전이·저장소·자원 소유권 계약 검증 |
| Android 에뮬레이터 테스트 | 6개 통과, 실패·오류·건너뜀 0 | UI/Simulation E2E 4개 + 실제 native 엔진 파일/이미지 smoke 2개 |
| Android Lint | 오류 0, 경고 37 | 경고가 없는 상태가 아니며 항목은 아래에 명시 |
| 호스트 Vision smoke | PASS | 실제 TensorFlow Lite native 호출, black image의 텐서 형상·유한값 확인. 물체 정확도 검증 아님 |
| 호스트 한국어 ASR smoke | PASS | 공식 한국어 테스트 파일을 실제 sherpa-onnx 1.13.8 native CLI로 처리. 사용자 마이크 명령 정확도 검증 아님 |
| Android 실제 엔진 smoke | PASS | MediaPipe `detect`와 sherpa-onnx JNI 파일 transcription 실행. Fake 결과로 대체하지 않음 |
| 최적화 Release 부팅·엔진 로딩·복귀 | PASS | 로컬 테스트 서명 APK의 `ENGINE READY` / MediaPipe CPU 확인. Release 센서 인식률 검증 아님 |
| 최종 APK 검사 | PASS | INTERNET 권한 제외, 정확한 모델 SHA-256, 선택 JNI ABI, test-only media/Release fixture canvas 미포함 |
| 실기기 카메라·마이크·가속기·성능 | **NOT_RUN / DEVICE_PENDING** | 에뮬레이터나 호스트 결과로 대체하지 않음 |

통합 Gradle 호출에서 UP-TO-DATE인 태스크는 직전 실제 실행 결과를 재사용했다. 추가 화면 캡처용 E2E 1회는 위 6개와 별개의 실행이며 고유 테스트 수를 7개로 부풀리지 않는다.

### JVM 테스트 구성

| 테스트 그룹 | 개수 | 주요 검증 |
|---|---:|---|
| CommandParserTest | 14 | 지원 대상, 공백·조사·구두점·모델 word marker, 미지원/unknown/중지/재검색/초기화 |
| CoordinateTest | 7 | 회전 0/90/180/270, 회전 복원, FILL_CENTER crop |
| DetectionTest | 12 | 클래스·threshold·3/5 안정화·일시 탐지·timestamp 중복·cooldown·계측 |
| NativeContractTest | 10 | 출력 계약, 실행 전 버퍼 용량, CameraSession이 사용하는 frame 소유권 helper의 정상/예외/취소 해제 |
| VisionViewModelTest | 9 | StateFlow, 검색 timeout, 성공 이벤트, 음성 오류, 배경 전환, 오래된 overlay, 오류 후 명령 처리 |
| StorageTest | 3 | Robolectric SDK 34의 실제 Room in-memory 저장/정렬/삭제 및 DataStore 설정 |
| 합계 | **55** | MockK·Turbine·가상 시간·주입 Clock 사용 |

[단위 테스트 소스](../app/src/test/java/dev/robotvision/)와 [Android 테스트 소스](../app/src/androidTest/java/dev/robotvision/)에서 조건과 assertion을 확인할 수 있다. 실제 CameraX 센서 프레임 해제·정렬은 별도 실기기 확인이 필요하다.

### Lint 경고 구분

| 경고 | 개수 |
|---|---:|
| AndroidGradlePluginVersion / GradleDependency / NewerVersionAvailable | 25 |
| DataExtractionRules / ObsoleteSdkInt / MonochromeLauncherIcon | 3 |
| UseKtx / UseTomlInstead | 9 |
| 합계 | **37** |

## 엔진 연동 과정에서 발견한 문제와 조치

| 문제 | 원인과 조치 | 확인 범위 |
|---|---|---|
| LiteRT 동적 출력 버퍼와 모델 계약 불일치 | 선택 모델에 대해 `[1,1,1,1]` 요소의 출력 버퍼가 만들어져 필요한 `[100,25,25,1]`에 못 미쳤다. native run 이후의 예외 처리로는 heap corruption을 막을 수 없어 입력/출력 용량 검사를 실행 앞으로 이동하고 동일 모델을 MediaPipe CPU로 처리했다. | 버퍼 계약 회귀 테스트와 실제 Android native smoke. 직접 CompiledModel 추론 성공으로 주장하지 않음 |
| 최적화 Release의 로거 초기화 실패 | R8 이후 Flogger의 caller-stack 탐색이 실패했다. mapping과 예외 원인을 대조해 관련 클래스·호출을 보존했다. | 최적화 Release 엔진 로딩 |
| 최적화 Release의 Protobuf 반사 접근 실패 | `Any.typeUrl_` 필드가 보존되지 않아 graph config 초기화가 실패했다. 관련 Protobuf 클래스·필드를 보존했다. | 최적화 Release 엔진 로딩과 백그라운드 복귀 |
| 전이 의존성의 INTERNET 권한 | SDK의 transport 의존성이 권한을 추가했다. main Manifest에서 명시적으로 제거하고 binary manifest를 검사했다. | 최종 Debug/Release APK 검사. 모든 OS 프로세스의 네트워크 사용을 측정한 것은 아님 |

관련 구현은 [LiteRT 어댑터](../app/src/main/java/dev/robotvision/data/vision/LiteRtObjectDetector.kt), [엔진 선택](../app/src/main/java/dev/robotvision/data/vision/VisionController.kt), [출력 계약](../app/src/main/java/dev/robotvision/domain/EfficientDetOutputParser.kt), [R8 보존 규칙](../app/proguard-rules.pro), [APK 검사](../tools/check_apk.py)에 있다.

R8의 더 새로운 Kotlin metadata 경고는 남아 있다. 해당 경고와 Release 실행 검증 성공은 서로 구분한다. 모델의 정확한 파일·입출력·라이선스는 [모델 출처](MODEL_PROVENANCE.md)와 [고정 manifest](../models/model-manifest.json)를 참고한다.

## 재현 방법

프로젝트 루트에서 모델 라이선스를 검토하고 [README](../README.md)의 준비 절차를 따른다.

```bash
python3 tools/fetch_models.py --accept-moonshine-license
python3 tools/verify_models.py
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease
```

선택적 호스트 검증은 macOS ARM64 환경에서 수행한다.

```bash
uv venv --python 3.12 .venv
uv pip install --python .venv/bin/python tensorflow==2.20.0 numpy==2.5.4
python3 tools/prepare_host_validation.py
.venv/bin/python tools/host_model_smoke.py
```

Android instrumented test는 에뮬레이터/기기를 준비한 경우에 실행한다. 파일 기반 한국어 음성 입력은 production APK에 포함되지 않는다.

```bash
.venv/bin/python tools/prepare_test_audio.py
adb devices
./gradlew :app:connectedDebugAndroidTest
```

## 남은 검증

실제 카메라와 마이크, Preview/Bounding Box 정렬, 한국어 명령 인식률, 폰에서의 p50/p95 latency, GPU/NPU, 발열·전력·장시간 안정성은 미검증이다. 호스트의 smoke 실행 시간이나 에뮬레이터의 모델 로딩 수치를 폰 벤치마크로 사용하지 않는다. 자세한 지원 제한은 [KNOWN_LIMITATIONS](KNOWN_LIMITATIONS.md)에 명시한다.

이 문서는 보관된 검증 기록의 공개용 요약이며 현재 소스 전체에 대한 새 코드 리뷰나 이번 문서 정리 시점의 실기기 검증을 의미하지 않는다.

## 문서 정리 작업의 별도 검증 — 2026-10-25

이번 변경은 문서 분류·이동과 host 검증 도구의 로그 저장 경로 조정이다. Android 앱 구현은 변경하지 않았다. Python 구문 검사, 문서 링크, 원본 파일 SHA-256 보존, 공개 후보의 로컬 사용자 경로와 내부 문서 링크, Git 제외 상태를 확인했다.

`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline --no-watch-fs` 호출은 성공했다. assembleDebug와 testDebugUnitTest는 UP-TO-DATE로 기존 결과를 재사용했고, Lint 분석은 실행됐다. 이를 55개 단위 테스트의 신규 실행이나 실제 모델·실기기 인식 재검증으로 표기하지 않는다.
