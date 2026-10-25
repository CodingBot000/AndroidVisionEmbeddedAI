# 알려진 제한

- 실제 폰 센서·인식률·latency·가속·발열·장시간 안정성은 DEVICE_PENDING이다.
- CPU만 노출한다. GPU/NPU 최적화 성공을 주장하지 않는다.
- STT는 버튼으로 시작/종료하는 최대 8초 **non-streaming** 녹음 후 처리이다. 상시 청취/핫워드는 없다. ‘컵 찾아줘’ 등 명령 정확도는 실기기 측정 전이다.
- 한국어 Moonshine 가중치는 Community License. 평가/테스트 용도와 상업 배포 조건을 LICENSES 원문에서 구분한다. 바이너리는 Git 제외, 취득 단계에서 명시적 라이선스 검토 옵션을 요구한다.
- Vision 모델의 90개 COCO index 슬롯에는 ???가 포함된다. 실제 80개 유효 클래스 중 명령 대상으로 8개를 제공한다. tv를 모니터로 해석하지 않는다.
- LiteRT 우선 초기화 후 실제 버퍼 용량을 검사하며, 계약이 맞을 때만 warm-up을 수행한다. 선택 모델의 custom Detection_PostProcess/dynamic output가 runtime에서 실패하면 같은 모델을 공식 MediaPipe Tasks CPU로 처리하며 실제 backend/실패 이유를 UI에 표시한다.
- 모델을 번들하므로 APK가 크다. 불필요한 float32 중복 모델·test_wavs는 production APK에 넣지 않는다. STT 사본 설치로 기기 내부 저장 공간이 추가로 필요하다.
- CameraX 내부에서 드롭한 프레임 수와 sensor-to-screen 지연은 노출 API가 없어 계측하지 않는다. 분석 단계 skipped와 total latency의 정의는 ARCHITECTURE 참조.
- 초기 로드 시 SHA 검증 및 private copy 때문에 시간이 필요하다. 모델 설치 실패는 실제 오류로 표시하고 Fake로 자동 전환하지 않는다.
- 이벤트 보관은 로컬 수동 삭제 방식. 대규모 운영용 paging/자동 만료 정책은 MVP 범위 밖이다.
- 모델 입력 모양이 같은 다른 파일로 교체하는 것을 허용하지 않는다. 새 모델은 SHA/라벨/전처리/출력 계약을 함께 검증해야 한다.

- AGP 8.13.2의 R8이 LiteRT 2.3.0에 포함된 더 새로운 Kotlin metadata를 읽을 때 경고를 출력한다. native/API, Flogger stack introspection 및 protobuf 반사 접근을 보존하는 keep 규칙을 유지한다. 최적화 Release의 실제 엔진 생성 성공은 TEST_EVIDENCE에 기록했다. 경고를 테스트 PASS로 숨기지 않는다.
- MediaPipe SDK의 전이 transport 의존성이 네트워크 권한을 선언한다. 앱 Manifest의 tools:node=remove로 INTERNET을 제거하여 SDK를 포함한 프로세스의 네트워크 송신을 OS 수준에서 차단한다. ACCESS_NETWORK_STATE는 SDK 내부 상태 확인을 위해 유지되며 데이터 송신 권한이 아니다. 앱은 원격 추론/런타임 모델 다운로드를 호출하지 않는다.

- 선택 EfficientDet의 동적 출력에 대한 LiteRT warm-up은 메모리 손상을 일으킬 수 있어 capacity 검증을 native 실행 앞에 둔다. 이 모델에서는 MediaPipe fallback이 정상 경로이다. 초기 크래시 로그와 보강 후 테스트를 TEST_EVIDENCE에 구분한다.
