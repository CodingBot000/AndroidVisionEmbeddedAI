# Architecture

## 데이터 흐름

Compose → VisionIntent Channel → VisionViewModel → 순수 VisionReducer → StateFlow<VisionUiState>.
CameraSession → ImageProxy crop / clockwise rotation → VisionController worker → DetectionFrame → stabilization → FOUND → Room repository.
마이크 버튼 → AudioRecordInput(16 kHz mono PCM16, 최대 8초) → SherpaOnnxSpeechRecognizer(Moonshine v2, non-streaming) → ParseVoiceCommand → 검색 상태.

낮은 빈도의 제품 상태, 고빈도 OverlayState, Metrics, Room history를 별도 Flow로 노출한다. 검색 성공/오류는 지속 상태와 이벤트 기록에 남기고 Snackbar/진동은 Channel effect로 전달한다. SavedStateHandle에는 대상 label만 저장하며 진행 중 세션은 재실행하지 않는다.

## 경계 / Hilt

`domain`은 Android 의존성 없는 DTO, Clock, 저장소/음성 인터페이스, 명령 파서, 안정화, 좌표 매핑이다. `data`는 Android Bitmap/ImageProxy/JNI/Room/DataStore를 소유한다. Hilt Application/Activity/ViewModel과 AppModule이 Clock, Dispatcher qualifier, DB/DAO, repositories, AudioInput, SpeechRecognizerEngine을 주입한다. VisionController는 ViewModel별 소유이며 각 native 엔진의 핸들은 전용 single-thread executor에서 생성/실행/종료한다.

## 프레임 좌표와 수명

CameraX Preview + ImageAnalysis는 하나의 UseCaseGroup/ViewPort와 lifecycle에 바인딩한다. 후면 카메라만 사용, PreviewView FILL_CENTER. 분석 bitmap은 ImageProxy.cropRect 적용 후 rotationDegrees로 회전하며, 도메인 박스는 이 **upright crop**에 대한 normalized 좌표이다. CompiledModel 경로는 전체 crop을 320×320으로 resize하고, 모델의 정규화 좌표를 upright crop 기준으로 해석한다. MediaPipe는 내부 전처리를 사용하고 반환 pixel box를 upright 크기로 나눈다. OverlayCoordinateMapper.fillCenter가 source/view 비율 차이를 보정한다. rotate 함수는 센서 좌표 테스트용이며 이미 회전한 결과에 다시 적용하지 않는다.

ImageProxy는 analyzer가 동기 소유하고 필요한 Bitmap 처리·추론이 끝난 뒤 finally에서 정확히 한 번 닫는다. runBlocking은 **카메라 전용 executor**에서만 사용하여 native worker 완료까지 proxy 수명을 보존한다. KEEP_ONLY_LATEST는 CameraX 내부의 오래된 프레임을 버리고 Preview 흐름을 유지한다. 800ms 이상 결과가 갱신되지 않으면 overlay를 비운다.

## 검색 정책

계획서 기본안: confidence .50, 설정 .30–.90; 최근 5개의 유일·증가 timestamp 프레임 중 목표 클래스 3회; 대표 최고 score; target+FOUND 5초 cooldown; 검색 20초 timeout. 타깃 변경/중지/재검색 시 안정화 증거와 타이머를 초기화한다. timeout 뒤 Preview는 유지한다. Fixture 이벤트에는 simulated 메타데이터를 넣고 실제 성능에는 포함하지 않는다.

## Lifecycle / 취소

Activity ON_STOP은 audio 취소·검색 중지·overlay 제거·vision unload. CameraX는 lifecycle과 화면 DisposableEffect에서 unbind한다. 화면 이탈은 녹음과 검색을 중지한다. AudioRecord blocking read는 stop()으로 깨우고 finally에서 release한다. native STT는 중간 취소를 보장하지 않으며 완료 이후 취소된 결과를 적용하지 않는다. ViewModel clear 후 별도 cleanup scope가 native worker에서 close/release하고 executor를 닫는다. 모델 초기화는 단일 worker에서 직렬화되어 중복 로딩이 없다.

## 저장과 프라이버시

Room schema v1, destructive migration 없음. 시간/타입/대상/score/backend/latency/오류 설명/simulated만 저장. raw transcript는 화면 메모리에만, 음성 samples는 처리 후 zero fill. 이미지·음성 원본은 저장하지 않는다. 모델 사본은 noBackupFilesDir에 SHA별 경로로 설치하며 SHA 검증 후 원자 rename한다. Android backup 비활성화. 이벤트는 사용자가 전체 삭제할 때까지 로컬 보관하며 자동 삭제는 구현하지 않는다. INTERNET 권한 없음.

## 계측 정의

inference = 실제 detect 호출 및 출력 readback 완료. total = analyzer가 프레임 처리를 시작한 순간부터 detection DTO 완성까지(카메라 센서→렌더 지연과 다름). result Hz = 최근 최대 120개 실제 결과의 timestamp 간격. P50/P95 = 같은 rolling window의 nearest rank. load = 무결성 검증/모델 설치/컴파일/CompiledModel warm-up을 포함. STT = 녹음 종료 후 transcribe 호출부터 결과까지(모델은 녹음 전에 로드). processed = 성공한 실제 추론 수. skipped = 엔진이 준비되지 않거나 세션 종료 후 analyzer에 전달된 프레임 수. **CameraX 내부 드롭/FPS는 측정했다고 주장하지 않는다.** PSS는 Diagnostics 진입 순간 Debug.getPss KiB 스냅샷.

## APK 오프라인 경계

Manifest merger에 transitive MediaPipe transport의 INTERNET 선언이 들어오기 때문에 main Manifest에서 명시적으로 제거한다. tools/check_apk.py는 최종 Debug/Release binary manifest와 실제 번들 SHA를 검사한다. 앱 프로세스 전체가 외부 송신 권한을 갖지 않는다. ACCESS_NETWORK_STATE 및 SDK 전이 서비스는 라이브러리 선언이며 앱이 원격 모델/서버 기능을 사용한다는 의미가 아니다.
