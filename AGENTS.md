# ROBOT VISION 작업 규칙

- 최상위 구현 명세: `docs/init/RobotVision_Android_EmbeddedAI_Codex_Development_Plan_KR.md`.
- 구현 전에 관련 대화·문서·코드·설정을 확인한다. 중요한 미결 정책은 확정하지 않고 선택지와 영향을 사용자에게 묻는다. 문서의 이미 승인된 기본안은 다시 묻지 않는다.
- Kotlin, Coroutines/Flow, Compose, MVI, Hilt 유지. 단일 :app 모듈의 domain / data / presentation 경계 유지.
- 실제 엔진을 Fake로 대체하지 않는다. Fixture는 src/debug, Preview, test, androidTest만 허용. Release는 CPU 실엔진만 사용한다.
- 실기기 부재로 중단하지 않는다. 센서·GPU/NPU·인식률·지연·발열은 DEVICE_TEST_PENDING에 남긴다.
- 빌드/단위/호스트/에뮬레이터/실기기 검증은 구분한다. 실행하지 않은 검증을 PASS라 하지 않는다.
- 모델은 manifest SHA-256을 검증하고 빌드 전에 준비한다. 다운로드 URL·계약·라이선스가 바뀌면 모델 manifest와 출처 문서를 함께 갱신한다.
- Moonshine 한국어 가중치는 MIT가 아니다. `LICENSES/MOONSHINE-LICENSE.txt` 확인. 사용자 대신 상업 라이선스 동의를 선언하지 않는다.
- 이미지·음성 원본을 저장·전송하지 않는다. INTERNET 권한 추가 금지. 이벤트 메타데이터만 Room에 저장하며 전체 삭제 확인 UI를 유지한다.
- 모델/오디오는 main thread 밖에서 실행. JNI 생성/호출/종료를 전용 worker로 직렬화. ImageProxy는 finally에서 한 번 닫는다.
- 코드 변경 후 관련 테스트, assembleDebug/testDebugUnitTest/lintDebug를 실행하고 실제 결과를 TEST_EVIDENCE에 기록한다.

## GitHub 배포 기준 (2026-10-19 사용자 확정)

- 원격 저장소 이름: `AndroidVisionEmbeddedAI`, 공개 범위: Public.
- 프로젝트 루트의 `doc/`, `docs/`는 로컬 전용으로 Git에서 제외한다. 문서 원본을 삭제하지 않는다.
- 모델·AAR·APK·개인 SDK 설정·캐시는 기존 .gitignore 제외 정책을 유지한다. 모델은 manifest와 취득 스크립트로 재현한다.
- GitHub README는 제외된 문서/스크린샷에 링크하지 않고 빌드·모델·라이선스·검증 경계를 자체 안내한다.
