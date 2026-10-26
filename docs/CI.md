# Android CI

[실행 이력과 수동 실행](https://github.com/CodingBot000/AndroidVisionEmbeddedAI/actions/workflows/android-ci.yml) · [워크플로 소스](../.github/workflows/android-ci.yml)

## 자동 실행

| 이벤트 | 대상 |
|---|---|
| `push` | `main` 브랜치 |
| `pull_request` | `main`을 대상으로 하는 PR의 생성·추가 커밋·재개 |
| `workflow_dispatch` | Actions 화면의 **Run workflow** |

파일 경로에 따른 실행 제외는 없다. 같은 브랜치나 PR에 새 실행이 시작되면 진행 중인 이전 실행을 취소한다. GitHub의 외부 기여자 워크플로 승인 정책은 그대로 적용된다.

## 수행하는 검증

Ubuntu 24.04의 새 작업 디렉터리에서 Temurin JDK 21, 프로젝트 Gradle 8.13 wrapper, Android SDK 36와 Build Tools 35.0.0을 사용한다. Actions는 커밋 SHA로 고정하고 토큰 권한은 `contents: read`로 제한한다. 별도의 인증 키·서명 키·저장소 secret은 필요하지 않다.

1. 자산 취득 도구의 Python 회귀 테스트 8개를 실행한다. 모델 동의 없이 전체 취득이 진행되지 않는지, runtime-only 경로가 모델을 요청하지 않는지, 크기·해시·JNI ABI 오류를 거부하는지 확인한다.
2. manifest에 고정된 **실제 sherpa-onnx 1.13.8 AAR**를 취득하고 크기·SHA-256·arm64-v8a/x86_64 JNI 항목을 검사한다.
3. 다음 명령으로 Debug APK 빌드·기존 JVM 테스트 55개·Android Lint를 실행한다. 실패한 태스크가 있어도 독립적인 태스크는 계속 시도하며 최종 결과는 실패로 유지한다.

```bash
python3 -m unittest discover -s tools/tests -v
python3 tools/fetch_models.py --runtime-only
./gradlew --no-daemon --stacktrace --console=plain --no-build-cache --continue \
  :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Gradle 의존성 캐시를 재사용하지만 빌드 결과 캐시는 비활성화한다. 새 작업 디렉터리에 이전 테스트 출력이 없으므로 JVM 테스트를 직접 실행한다. 실행 환경과 개별 태스크의 실제 상태는 해당 run 로그를 기준으로 판단한다.

## 결과 확인과 실패 대응

- Actions의 **Android CI → Debug build, JVM tests and Lint**에서 실패한 단계와 Gradle 오류를 확인한다.
- 성공·실패 시 생성된 JVM HTML/JUnit XML과 Lint HTML/XML/TXT를 `android-ci-reports` artifact로 7일간 보관한다. 초기 준비 단계에서 실패하면 보고서가 생성되지 않을 수 있다.
- 보고서에 저장된 테스트 실패·건너뜀과 Lint 경고를 함께 확인한다. 녹색 run은 Lint 경고 0개나 실기기 검증 완료를 뜻하지 않는다.
- 다운로드·저장소 서비스 장애는 로그를 확인한 뒤 **Re-run failed jobs**로 재시도한다. 해시 오류를 우회하거나 manifest의 해시를 임의로 바꾸지 않는다.

## 검증 범위와 모델 라이선스

이 CI는 모델 가중치를 다운로드하거나 Moonshine 한국어 모델 라이선스 동의를 자동 처리하지 않는다. `--runtime-only`는 AAR만 준비한다. 기본 전체 취득 명령의 명시적 라이선스 확인 요건과 전체 모델 검증은 유지한다.

실제 엔진 어댑터와 JNI 의존성은 컴파일·패키징하지만, 모델 가중치가 없는 CI Debug APK는 앱 실행·인식 검증용 완성 패키지가 아니다. APK를 artifact나 Release로 업로드하지 않는다. 실행 가능한 앱을 준비할 때는 [README의 전체 모델 취득 절차](../README.md#빌드와-실행)를 따른다.

이 워크플로에서 `connectedDebugAndroidTest`, 실제 native 추론, Release/R8·배포 서명, 카메라·마이크·GPU/NPU·인식률·latency·발열·장시간 테스트는 실행하지 않는다. 기존 [검증 기록](TEST_EVIDENCE.md)과 실제 run의 범위를 구분한다. CI 자동 실행 연결은 브랜치 보호나 필수 병합 검사 설정을 변경하지 않는다.

## 설정 참고 자료

- [GitHub: Gradle 빌드와 테스트](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-gradle)
- [공식 setup-java 설정](https://github.com/actions/setup-java/tree/v6.1.0)
- [GitHub Ubuntu 24.04 실행 환경](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md)
