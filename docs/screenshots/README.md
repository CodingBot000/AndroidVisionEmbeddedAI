# 화면 자료 안내

2026-10-17에 Android 16 API 36 ARM64 에뮬레이터의 360dp 화면에서 캡처한 Compose UI다. 파일을 분류하면서 6개 이미지의 표시 내용과 PNG 메타데이터를 확인했다. 사진·마이크 입력·실물 객체 인식 결과를 촬영한 자료는 아니다.

| 파일 | 설명 | 공개 시 함께 표시할 주의점 |
|---|---|---|
| [release-dashboard.png](release-dashboard.png) | 최적화 Release의 실제 MediaPipe CPU 엔진 준비 화면 | 대표 이미지로 권장. ENGINE READY는 모델 초기화 상태이며 실기기 센서 검증 완료가 아님 |
| [dashboard.png](dashboard.png) | Debug Dashboard의 구조와 실제 backend 표시 | 위 Release 화면과 유사하므로 간단한 소개에서는 한 장만 선택 가능 |
| [vision-simulation.png](vision-simulation.png) | 고정 cup 박스와 검색 성공 상태를 보여주는 Debug Simulation | `SIMULATION / FAKE DATA` 배지를 유지. 94%는 fixture confidence이며 실제 인식 정확도가 아님 |
| [history-simulation.png](history-simulation.png) | Simulation 결과의 이벤트 기록과 구분 표시 | 실제 센서 발견 이력으로 소개하지 않음. 지연 수치는 미측정으로 표시 |
| [diagnostics.png](diagnostics.png) | 실제 backend, capacity 검사 결과, MediaPipe CPU 전환 이유 | LOAD는 캡처 당시 에뮬레이터의 값이며 폰 성능 지표가 아님. p50은 미측정 |
| [settings.png](settings.png) | 인식 임계값과 진동 등 설정 UI | Simulation 세션 중 캡처한 화면임을 배지로 확인 가능 |

이력서에는 Release Dashboard를 대표로, 기술 소개에는 Diagnostics를 보조로 사용하는 구성을 권장한다. Simulation 이미지는 UI·상태 흐름을 설명할 때만 사용한다. 배지를 자르거나 다른 배경으로 합성하여 실제 카메라 탐지처럼 보이게 하지 않는다.
