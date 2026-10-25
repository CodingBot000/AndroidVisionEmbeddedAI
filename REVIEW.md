# ROBOT VISION — Android 코드 리뷰 지침

이 문서는 프로젝트 전체 점검과 변경분 리뷰에 적용하는 기준이다. 리뷰 결과물이 아니며, 체크리스트의 존재가 구현이나 검증 완료를 뜻하지 않는다. 발견 사항은 한국어로 작성하고 코드 식별자는 원문을 유지한다.

## 1. 적용 원칙

- 사용자의 현재 지시와 `AGENTS.md`의 확정 사항을 우선한다. 일반적인 모범 사례와 충돌하면 프로젝트 제약을 확인하고 이유를 설명한다.
- 필수 조건, 공식 권장 사항, 선택적 개선을 구분한다. 스타일 취향이나 추상화의 개수만으로 결함을 선언하지 않는다.
- Kotlin, Coroutines/Flow, Compose, MVI, Hilt 및 단일 `:app`의 domain/data/presentation 경계를 유지한다. 다른 아키텍처나 DI 도입을 리뷰의 기본 해결책으로 삼지 않는다.
- Android 공식 문서와 선택한 라이브러리 버전의 실제 API·소스를 근거로 판단한다. 최신 예제와 다르다는 사실만으로 현재 코드를 잘못됐다고 보지 않는다.
- 숫자가 코드에 있다는 이유만으로 사용자 승인 정책이라고 간주하지 않는다. 검색·녹음 제한, threshold, 보관·삭제·비용 등 중요한 정책은 대화와 확정 명세를 확인한다. 미정이거나 충돌하면 변경하지 않고 확인 필요 항목으로 남긴다.
- 모델 바이너리를 Fake로 대체하지 않는다. Debug/Preview/Test의 fixture 사용과 실제 엔진 구현·검증을 구분한다.
- 리뷰 요청만으로 소스 수정, 의존성 업데이트, 모델 다운로드·약관 동의, 커밋·푸시를 수행하지 않는다. 수정이 별도로 요청되면 해당 범위에서 진행한다.
- 로컬 전용 문서가 없는 GitHub checkout에서도 이 지침과 추적되는 소스·설정으로 리뷰를 계속한다. 로컬 상세 명세가 있으면 `AGENTS.md`에 적힌 현재 경로를 확인한다.

## 2. 시작 전 범위 확인

리뷰 시작 시 다음을 기록한다.

1. 전체 소스 리뷰 / 미커밋 변경 / 기준 브랜치 대비 변경 / 특정 커밋 중 무엇인지.
2. 기준 커밋·브랜치, 현재 HEAD, 미커밋·미추적 변경의 포함 여부.
3. 대상 파일과 관련 호출 경로·테스트·빌드 설정.
4. 실행 가능한 검증 환경과 제약: SDK/JDK, 모델 자산, 에뮬레이터·ABI, 읽기 전용 여부.

변경분 리뷰는 diff의 문제를 중심으로 관련 코드를 추적한다. diff 밖의 기존 문제는 신규 회귀와 구분한다. 기준 브랜치와 변경이 없으면 이를 전체 소스 리뷰 완료로 해석하지 않는다.

전체 리뷰의 기본 대상:

- `app/src/main`, `app/src/debug`, `app/src/release`의 구현·Manifest·리소스.
- `app/src/test`, `app/src/androidTest`의 assertion과 누락된 실패 경로.
- Gradle 설정·Version Catalog·wrapper·R8 규칙·Room schema.
- `models/model-manifest.json`, 번들 manifest, 라벨·라이선스·취득/검증 도구.
- `tools/`의 실제 실행 경로, 출력 위치, 오류와 재실행 동작.

생성된 build/cache, Python 환경, 대형 모델·AAR 바이너리 전체를 무작정 읽지 않는다. 필요한 ABI·해시·APK 구조는 목적에 맞는 도구로 검사한다. 내부 로그는 필요한 구간만 확인하고 개인 경로·기기 식별자·인증 정보를 보고서에 그대로 복사하지 않는다.

## 3. 우선 점검할 실패 시나리오

- 모델 로딩 중 화면 이탈·앱 Background·재진입·재시도.
- 추론 중 unload/close 요청과 완료 뒤 도착한 오래된 결과.
- 녹음 준비 중 중지, 녹음 취소 직후 재시작, STT 처리 중 화면 이탈.
- 타깃 변경 직전 프레임, 중복·역순 timestamp, 검색 timeout과 성공의 경합.
- 카메라/마이크 권한 거부·철회, 장치 점유, 모델 누락·손상.
- 저장 실패나 개별 작업 오류 뒤 다음 사용자 명령 처리.
- Debug에서는 동작하지만 최적화 Release에서만 발생하는 초기화·JNI·리플렉션 실패.

각 시나리오는 소스 경로와 테스트를 연결해 확인한다. 가능성만 나열하거나 실기기 미검증이라는 이유만으로 결함으로 확정하지 않는다.

## 4. 영역별 리뷰 기준

### 4.1 아키텍처·MVI·상태

- UI → Intent/동작 → 상태 처리 → 불변 UiState의 단방향 흐름과 상태 소유자가 명확한가.
- UI에서 저장소·명령 해석·검색 판정 등 비즈니스 로직을 중복 구현하지 않는가.
- CameraX/Bitmap/JNI 같은 플랫폼 세부사항이 순수 domain 모델로 유출되지 않는가.
- 검색 성공·오류·지원하지 않는 명령 같은 중요한 결과가 지속 상태나 저장 데이터에 반영되는가. Channel 발행만으로 소비·처리 완료를 보장한다고 가정하지 않는가.
- Snackbar·진동 같은 일시 효과도 소비 시점과 유실·중복 영향을 검토한다. Channel이라는 이유만으로 일괄 금지하거나, 버퍼가 있다는 이유로 전달을 보장한다고 판단하지 않는다.
- target 변경·중지·재검색·timeout·늦은 callback에서 이전 세션 상태를 덮어쓰지 않는가.
- 프로세스 재생성 시 최소 상태를 복구하되 녹음이나 native 핸들을 자동 복원하지 않는가.
- UseCase·interface·module 분리는 재사용이나 복잡성 감소의 필요성을 근거로 제안한다. 클래스 수나 파일 길이만으로 결함을 판정하지 않는다.

### 4.2 Coroutines·Flow·동시성

- `suspend` 함수라는 이유로 main-safe라고 가정하지 않는다. 파일 I/O·모델 로딩·추론·오디오 읽기의 실행 스레드를 추적한다.
- Dispatcher·Clock·Scope를 필요한 경계에서 교체해 결정적 테스트를 할 수 있는가. 스레드 고정이 필요한 엔진은 단순한 Dispatcher 주입 원칙보다 라이브러리의 thread-affinity 계약을 함께 고려한다.
- `CancellationException`을 일반 오류로 소비하지 않는가. `catch`, `runCatching`, fallback 내부까지 확인한다.
- 취소가 blocking read나 native 함수를 즉시 중단한다고 가정하지 않는가. 반환 후 결과 폐기와 자원 해제 순서가 안전한가.
- Scope·Job·executor·dispatcher의 소유자와 종료 경로가 있는가. `onCleared`에서 이미 취소된 `viewModelScope`에 정리를 맡기지 않는가.
- 별도 cleanup scope는 필요성·수명·종료를 확인한다. 존재 자체를 무조건 결함으로 분류하지 않는다.
- Channel/Flow의 버퍼·backpressure·conflate 사용이 데이터 의미와 맞는가. 표시용 프레임 생략과 중요한 결과 유실을 구분한다.
- 한 작업의 실패가 명령 소비 루프나 독립적인 카메라/음성 흐름을 종료시키지 않는가.
- 무제한 큐·공유 mutable 상태·thread 간 callback은 실제 생산 속도·상태 경합·메모리 영향으로 판단한다.

### 4.3 Compose·Navigation·Lifecycle·접근성

- Flow를 Lifecycle에 맞게 수집하고, 화면/앱의 종료·복귀와 자원 관리가 일치하는가.
- Composable 본문에서 모델 초기화나 저장 작업을 실행하지 않는가. Effect의 key와 cleanup, 오래된 lambda 캡처를 확인한다.
- 상태 끌어올리기와 `remember`/`rememberSaveable`/SavedStateHandle 사용 목적이 적절한가. native 자원을 저장 가능한 UI 상태처럼 다루지 않는가.
- 고빈도 overlay·metrics 읽기가 불필요하게 화면 전체를 재구성하거나 무거운 계산·할당을 반복하지 않는가.
- `derivedStateOf`, 안정성 annotation, 별도 화면 ViewModel은 실제 필요성으로 제안한다. 모든 상태에 기계적으로 적용하지 않는다.
- Navigation 재진입·뒤로 가기·화면 회전·앱 복귀에서 중복 수집이나 중복 카메라 바인딩이 없는가.
- 360dp·큰 글씨·화면 방향 변화에서 핵심 조작에 접근 가능한가. system bar/insets와 콘텐츠가 충돌하지 않는가.
- 터치 영역, 접근성 의미, 상태 설명, 한글 줄바꿈과 색상 외 상태 구분을 확인한다. 장식용 아이콘의 null contentDescription은 의도와 주변 semantics로 판단한다.

### 4.4 Hilt·의존성 수명

- Application/Activity/ViewModel 진입점과 qualifier가 실제 주입 경로에 연결되는가.
- Singleton/컴포넌트 scope가 공유 상태와 생성 비용·수명에 맞는가. 모든 객체에 scope를 붙이도록 요구하지 않는다.
- 수명이 긴 객체가 Activity·View·LifecycleOwner를 잘못 보유하지 않는가.
- DI scope와 Camera/Audio/JNI 자원 수명을 혼동하지 않는가. Hilt가 native close를 자동으로 호출한다고 가정하지 않는다.
- 테스트에서 저장소·시간·Dispatcher·음성 경계를 바꿀 수 있고, Release에 fixture binding이나 묵시적 Fake fallback이 없는가.

### 4.5 CameraX·프레임 소유권·좌표

- Preview와 ImageAnalysis가 적절한 Lifecycle·ViewPort/crop 계약에 연결되는가.
- KEEP_ONLY_LATEST 등 backpressure가 Preview 응답성과 분석 처리량에 맞는가.
- 성공·예외·취소·세션 종료의 모든 경로에서 `ImageProxy.close()`가 정확히 한 번 호출되는가. 내부 `Media.Image.close()`로 대체하지 않는가.
- 비동기 작업이 닫힌 ImageProxy나 재활용된 Bitmap을 참조하지 않는가. 복사본과 원본의 소유권·해제 시점을 추적한다.
- 센서 회전·crop·upright 이미지·모델 입력 resize·정규화 박스·Preview FILL_CENTER의 좌표계를 일관되게 연결하는가. 회전이나 crop을 두 번 적용하지 않는가.
- 회전 0/90/180/270, 다른 화면 비율, 대표 box와 경계 밖 좌표를 테스트하는가.
- 오래된 결과를 현재 탐지처럼 계속 표시하지 않는가. 센서 timestamp와 앱 clock을 섞을 때 기준과 단위를 확인한다.
- CameraX 내부 drop 수와 앱에서 관측한 skipped 수를 구분하는가.

### 4.6 AudioRecord·한국어 STT

- 모델이 요구하는 sample rate·channel·PCM 형식·정규화와 실제 입력이 일치하는가.
- 권한, 초기화·recording 상태, buffer 크기, read 반환값·오류를 처리하는가.
- 최대 녹음 시간·짧은 입력·무음·중지·취소·장치 점유가 UI 상태와 연결되는가.
- read 중 stop/release, 재시작, 늦은 finally가 새 세션을 중단하는 등 경합을 검토하는가.
- finally 등 종료 경로에서 AudioRecord와 native stream을 해제하며 처리 후 음성 원본을 불필요하게 유지하지 않는가.
- non-streaming을 streaming이라고 표시하지 않는가. STT 오류 때 수동 대상 선택과 카메라 사용이 가능한가.
- 원본 음성·transcript를 로그·DB·원격 서버에 의도치 않게 남기지 않는가.

### 4.7 실제 AI 엔진·모델 계약

- 실제 라이브러리 호출이 제품 경로에 연결되는가. 패키지 의존성이나 모델 파일 존재만으로 실제 추론을 검증했다고 하지 않는다.
- input dtype/shape/channel order/양자화·정규화와 output shape/order/label index가 선택한 파일의 계약과 일치하는가.
- 실제 버퍼 용량을 native run/warm-up 이전에 검사하는가. native memory corruption을 Kotlin 예외로 복구할 수 있다고 가정하지 않는다.
- NMS가 모델에 포함된 경우 이중 적용하지 않는가. 지원하지 않는 라벨을 존재하는 것처럼 매핑하지 않는가.
- LiteRT CompiledModel의 생성·실행·종료가 같은 전용 백그라운드 스레드에서 수행되는가. Mutex만으로 thread affinity가 보장된다고 판단하지 않는다.
- TensorBuffer·모델·환경·MediaPipe 이미지·sherpa stream의 소유권과 부분 초기화 실패 시 정리 순서를 확인한다.
- CPU fallback이 실제 엔진으로 이어지고, UI/이벤트의 backend와 사유가 실제 선택과 일치하는가.
- GPU/NPU 지원을 근거 없이 가정하지 않는다. 현재 CPU 전용이라는 이유만으로 범위 누락을 선언하거나 가속을 임의 도입하지 않는다.
- manifest의 출처·SHA-256·파일 크기·라벨·라이선스와 실제 로딩 파일이 일치하는가. 다운로드·부분 파일·설치·손상 복구 동작을 확인한다.
- Moonshine 한국어 가중치와 런타임 라이선스를 구분한다. 라이선스 조건을 리뷰어가 임의로 완화하거나 사용자 대신 상업 동의를 선언하지 않는다.

### 4.8 Room·DataStore·프라이버시

- DB 접근과 모델/화면 DTO의 경계, 정렬·저장·삭제·실패 처리가 의도대로 동작하는가.
- schema 변경이 있으면 version/export된 schema/migration/데이터 보존을 함께 검토한다. 첫 schema라는 이유만으로 존재하지 않는 과거 버전 migration을 요구하지 않는다.
- 같은 파일에 DataStore 인스턴스를 중복 생성하지 않는가. 설정 값의 유효 범위와 읽기/쓰기 오류 처리를 확인한다.
- 사용자 확인 없는 destructive migration, 자동 삭제, 보관 한도 변경을 제안된 수정에 섞지 않는다.
- CAMERA/RECORD_AUDIO 권한을 필요한 시점에 요청하고 거부·영구 거부·일회성 권한·철회에 대응하는가.
- source Manifest뿐 아니라 merged/binary Manifest의 INTERNET 권한과 전이 SDK 구성을 확인한다. 앱 권한 확인을 기기 전체의 네트워크 미사용 증명으로 확대하지 않는다.
- 원본 이미지·음성을 저장/전송하지 않는 원칙, 최소 이벤트 메타데이터, backup/transfer 설정과 삭제 확인 UI가 유지되는가.
- Debug fixture·검증용 음성·개인 경로·인증 정보가 Release APK나 공개 결과물에 포함되지 않는가.

### 4.9 빌드·R8·네이티브 배포

- 고정 버전과 선택 API, AGP/Kotlin/KSP/Hilt·SDK·JDK 조합이 실제 빌드와 일치하는가.
- R8/JNI/리플렉션/호출 스택 탐색 경로를 확인하고 최적화 Release로 검증하는가. Debug 성공으로 Release를 보장하지 않는다.
- keep/dontwarn 범위의 근거와 회귀를 검토한다. 광범위 규칙은 개선 후보지만, 입증 없이 제거하거나 오류를 숨기는 blanket suppression을 추가하지 않는다.
- ABI 필터·AAR의 실제 .so·중복 native 라이브러리·STL 선택·모델 압축 설정과 APK 크기를 확인한다.
- 네이티브 라이브러리의 16KB 호환성은 ELF segment 정렬·APK ZIP 정렬·해당 환경 실행으로 구분한다. ABI 파일 존재나 4KB 에뮬레이터 실행만으로 PASS를 선언하지 않는다.
- 배포 서명 전 APK와 로컬 테스트 키로 서명한 사본을 구분한다. 리뷰 중 배포 키 생성·업로드·APK 공개를 자동 수행하지 않는다.

### 4.10 테스트·성능·증거

- 정상 경로뿐 아니라 취소·오류·재시도·권한·상태 경합·자원 해제의 assertion을 검토한다.
- 시간 의존 로직에는 주입 Clock·테스트 Dispatcher·가상 시간을 활용한다. 외부 scope와 반복 작업이 테스트 종료 후 남지 않는가.
- Fake 기반 MVI/UI, 실제 Room/DataStore, 실제 모델 파일 smoke, 센서 입력 E2E의 보장 범위를 구분한다.
- 테스트 수, coverage, 빌드 성공만으로 AI 정확도·경합 부재·실기기 성능을 증명했다고 판단하지 않는다.
- 지연시간의 시작/종료 구간과 clock, cold/warm 조건, 결과 Hz, p50/p95 표본·window, 메모리 측정 범위를 명시하는가.
- native 실행 시간에는 필요한 출력 readback 완료를 포함하는가. Fake 표본과 실제 추론 표본을 섞지 않는가.
- 성능 개선은 측정 근거로 제안한다. 실기기 없이 폰의 인식률·latency·GPU/NPU·발열·전력을 추정해 PASS로 표기하지 않는다.

## 5. 검증 실행과 결과 분리

읽기 전용 리뷰에서는 소스와 기존 증거를 확인한다. Gradle은 build/cache에 쓰기가 필요하므로, 해당 실행 권한이 없는 경우 미실행 사유를 기록하고 독립적으로 가능한 점검을 계속한다. 권한 제약을 우회하지 않는다.

소스 수정이 승인되어 수행된 경우에는 관련 검증과 `AGENTS.md`의 필수 빌드·단위·Lint를 수행한다. 리뷰만 요청된 경우 검증은 범위·환경·위험에 맞게 선택하며 반복 실행을 위한 반복은 하지 않는다.

프로젝트 루트에서 사용하는 기본 명령:

```bash
python3 tools/verify_models.py
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

조건별 추가 명령:

```bash
# R8/JNI/패키징/Release 동작에 영향이 있을 때
./gradlew :app:assembleRelease
python3 tools/check_apk.py app/build/outputs/apk/release/app-release-unsigned.apk

# 에뮬레이터/기기가 있고 테스트용 모델·음성 준비가 완료된 경우
adb devices
./gradlew :app:connectedDebugAndroidTest
```

실기기 부재로 리뷰를 멈추거나 사용자에게 실기기 테스트를 요구하지 않는다. 필요한 센서·가속기·장시간 검증은 로컬 체크리스트와 보고서에 DEVICE_PENDING으로 남긴다. 테스트 환경 준비나 라이선스가 미완료이면 해당 검증만 미실행으로 구분한다.

각 검증에는 명령, variant, 환경/API/ABI, 실행 날짜, 종료 코드 또는 근거를 남긴다.

| 상태 | 의미 |
|---|---|
| PASS | 해당 검증을 실제 실행해 성공했고 그 범위를 설명할 수 있음 |
| FAIL | 실제 실행이 실패함. 코드 문제와 환경 문제를 구분해 기록 |
| CACHED / UP-TO-DATE | 기존 결과 재사용. 새 테스트 실행으로 표기하지 않음 |
| NOT_RUN | 미실행 사유를 명시. 과거 성공 기록으로 대체하지 않음 |
| DEVICE_PENDING | 실제 센서·지원 기기·가속기·성능·장시간 검증이 남음 |

보관된 검증 기록의 날짜·대상 소스·환경을 확인한다. 과거 결과를 현재 변경의 검증으로 승격하지 않는다. 수정 뒤에는 관련 검증을 재실행하고 신규 실패나 미해결 우려가 없으면 불필요한 재실행을 늘리지 않는다.

## 6. 발견 사항과 우선순위

확정 결함은 코드 경로 또는 실행 증거와 사용자 영향을 설명할 수 있어야 한다. 조건부 문제는 전제를 적고, 근거가 부족하면 확인 필요 항목으로 분리한다.

| 분류 | 기준 |
|---|---|
| P0 | 광범위하게 발생하는 치명적 데이터 손실·침해 등 즉시 대응이 필요한 문제. 강한 근거 없이 사용하지 않음 |
| P1 | 주요 흐름의 crash/ANR, native 메모리 오류, 중대한 자원 누수·녹음 지속·데이터 손실 |
| P2 | 특정 재현 가능한 조건의 잘못된 상태·결과·복구 실패·중복 이벤트 등 기능 결함 |
| P3 | 사용자나 유지보수에 구체적인 영향이 있는 낮은 우선순위 문제 |
| 권장 개선 | 대안 설계·정리·최적화 제안. 확정 버그와 분리 |
| 확인 필요 | 증거·요구사항·실행 환경이 부족한 항목. 결함으로 확정하지 않음 |

발견 사항별 형식:

```text
[P1/P2/P3] 문제를 나타내는 짧은 제목
- 위치: 저장소 상대 파일 경로와 정확한 1-based line
- 발생 조건: 실행 순서·입력·Lifecycle·전제
- 사용자 영향: 실제로 깨지는 기능 또는 자원·데이터 영향
- 근거: 호출 경로·관련 상태/계약·실행 결과
- 수정 방향: 요구사항을 유지하는 최소한의 방향
- 검증 방법: 재현/회귀 테스트 또는 필요한 추가 검증
```

우선순위 순으로 나열하고 같은 원인의 중복 지적은 합친다. 문제가 없으면 만들어내지 말고 검토 범위와 남은 검증을 보고한다. 별도 동의가 필요한 제품·보관·용량·비용 정책의 변경은 해결책으로 단정하지 않는다.

## 7. 보고서와 실행 방법

보고서는 다음 순서로 작성한다.

1. 대상 범위·기준 커밋·미커밋 변경과 제외 범위.
2. 확정 발견 사항: 우선순위·위치·조건·영향·근거·수정·검증.
3. 권장 개선과 확인 필요 항목.
4. 실제 검증 명령·결과·캐시 재사용·미실행 구분.
5. 실기기 후속 검증과 남은 위험.

로컬 결과·원본 로그는 `docs/_private/reviews/` 아래에 보관한다. `docs/`가 없는 checkout에서는 필요할 때 해당 출력 폴더를 생성한다. 보고서와 내부 증거를 지침 파일에 누적하거나 자동 커밋하지 않는다. `REVIEW.md`는 공유 기준이며 자체적으로 실행되거나 자동 발견되는 파일이라고 가정하지 않는다. `AGENTS.md`에서 참조하고 실행 프롬프트에서도 범위를 지정한다.

전체 소스 정적 리뷰를 시작하는 CLI 예시:

```bash
mkdir -p docs/_private/reviews
codex exec --sandbox read-only \
  --output-last-message docs/_private/reviews/full-review.md \
  'AGENTS.md와 REVIEW.md를 읽고 현재 전체 소스·설정·테스트·모델 manifest·도구를 리뷰해라. 소스를 수정하지 말고 확정 결함·권장 개선·확인 필요·실행한 검증을 구분해 보고해라.'
```

위 예시의 출력 디렉터리 생성과 결과 저장은 호출 측 작업이다. 읽기 전용 에이전트가 Gradle 쓰기 작업까지 할 수 있다고 가정하지 않는다. 지침 파일을 추가한 뒤에는 새 실행에서 로드됐는지 확인한다.

변경분 리뷰 예시:

```bash
# 미커밋 변경: staged / unstaged / untracked
codex review --uncommitted

# 기능 브랜치에서 main 대비 변경
codex review --base main
```

리뷰 명령의 세부 옵션은 설치된 CLI의 `--help`로 확인한다. 위 명령은 실행 예시이며 지침 파일 추가만으로 리뷰나 테스트를 실행한 것은 아니다.

## 8. 공식 참고 자료

아래 자료는 판단 근거이며 프로젝트의 확정 요구사항을 자동 대체하지 않는다. 버전별 API·계약은 실제 사용 버전에 맞춰 확인한다.

- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
- [UI events](https://developer.android.com/topic/architecture/ui-layer/events)
- [Coroutines best practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
- [Compose state](https://developer.android.com/develop/ui/compose/state), [Side-effects](https://developer.android.com/develop/ui/compose/side-effects), [Performance](https://developer.android.com/develop/ui/compose/performance/bestpractices)
- [Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
- [CameraX image analysis](https://developer.android.com/media/camera/camerax/analyze), [Output transforms](https://developer.android.com/media/camera/camerax/transform-output)
- [AudioRecord](https://developer.android.com/reference/android/media/AudioRecord)
- [LiteRT CompiledModel guidance](https://developer.android.com/agents/skills/device-ai/litert/litert-runtime/skill)
- [Room migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions), [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- [Runtime permissions](https://developer.android.com/training/permissions/requesting), [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- [R8 keep-rule best practices](https://developer.android.com/topic/performance/app-optimization/keep-rules-best-practices), [16KB page sizes](https://developer.android.com/guide/practices/page-sizes)
- [Android testing fundamentals](https://developer.android.com/training/testing/fundamentals)
- [Codex AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md), [Review guidance](https://learn.chatgpt.com/guides/best-practices#improve-reliability-with-testing-and-review)
