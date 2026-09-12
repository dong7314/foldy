# 0.14.2: 네이티브 프레임 준비와 출력 복귀 분리

2026-09-12, Windows 재개. versionCode 40, Shizuku service version 40.

## 문제와 변경

0.14.1은 `DisplayControl.requestMode`의 모니터를 잡은 채 실제 프로필 확인과 최소 650ms 대기를 수행했다. 캡처 Binder도 같은 모니터가 필요해 새 프레임을 받을 수 없었다. `FoldService`는 대기 중 반대 패널에 현재 화면을 그려서, 내부에서 외부용 앱 이미지를 보관한 snapshot이 노출될 수 있었다.

- 요청에는 앱의 전환 generation을 전달한다. 실제 물리 ID/기본 크기를 짧게 확인하고 대기는 모니터 밖에서 수행한다. 프로필 확인 후 즉시 반환해 캡처를 진행한다.
- private shield는 요청 반환과 별도로 유지한다. 앱은 대상 프레임 2개, 양 패널 표시 transaction 완료를 확인한 뒤 `finishMode`를 요청한다. shell은 같은 generation, 목표 물리 프로필, 최소 650ms 보호 시간, 관측상 80ms 안정 구간, 잠금/임대를 추가 확인한다.
- 빠른 반전은 새 generation으로 이전 요청과 finish를 무효화한다. 이전 shield가 남아 있으면 새 요청이 보호를 이어받는다. CLOSED의 동일 외부 프로필 재요청은 idle 투명 패널을 private stack으로 옮기지 않는다.
- `TaskProfileReader`는 캡처 전후의 사용자·task ID·top component·화면 구성을 비교한다. 메타데이터는 `CapturedFrame` parcel과 `ControlBridge.FrameInfo`로 앱에 전달한다. 작업이 바뀌거나 식별할 수 없으면 양쪽 캐시와 보관 이미지를 폐기한다.
- `PanelFrameCache`는 같은 작업의 내부/외부 네이티브 프레임만 보관하며 15초 이후 만료한다. 캐시 참조는 `FrameRetirement.collect`에서 추적한다. 캐시가 없으면 원본 크기의 불투명한 회청색 준비 화면을 사용한다. 외부 이미지를 내부 해상도로 확대하지 않는다.
- `NativePanel`은 다른 패널 크기의 콘텐츠를 거부한다. null 콘텐츠도 준비 화면으로 그리며 GPU snapshot을 지원한다. 새 콘텐츠는 기존 240ms handoff로 이어진다.
- HAL 각도 입력, AngleSmoother, FoldOptics, FoldPlane, FoldRenderer의 기존 효과는 변경하지 않았다. secure/protected REDACT 정책도 유지했다.

## 확인한 근거

- USB 승인 후 SM-F971N / R5KL8036E1D 연결. 시작 상태 CLOSED, 실행 FoldService 없음, 크기 override 없음, density override 360 유지. 설치본은 versionCode 38.
- `tools/TaskApi.java`를 `javac --release 17` → D8 → app_process로 실행했다. 실제 `getTasks(int, boolean, boolean, int)`와 `TaskInfo.configuration.semDisplayDeviceType` 조회 성공. 외부 fullscreen 구성의 dt/s와 1248×1972 확인.
- Java 테스트 70개, Python 테스트 7개 통과. Lint 오류 0 / 경고 29. APK 빌드 성공.
- `tools/NativeHandoffProbe.java`를 새 APK 클래스와 함께 shell에서 실행했다. 앱 설치/화면 상태 변경/앱 픽셀 캡처 없이 offscreen으로 확인했다.
  - 실제 `TaskProfileReader`: ownerKnown=true, inner=false, ready=true.
  - GPU 버퍼 parcel의 owner/generation/inner/nativeReady 왕복: 통과.
  - 내부 2448×1848, 외부 1248×1972의 null 콘텐츠 snapshot: 통과. 중앙 픽셀 ff252c35, 불투명·비검정.
  - 잘못된 콘텐츠 크기 거부: 통과.
- 로컬 근거: `measurements/task-api/result.txt`, `measurements/native-v40-probe/result.txt`, `measurements/native-v40-build/status.json`.

## 실기 전환 검증은 별도

위 GPU 검증은 실제 접힘 전환의 무깜빡임 증거가 아니다. task config와 캡처 전후 동일성은 실제 앱 버퍼의 재배치 완료를 완전히 보장하지 않는다. 650ms와 80ms도 시스템 traversal이 더 이상 발생하지 않는다는 보장이 아니다. 내부/외부 홈 dt/m·dt/s, 다른 앱 첫 전환, 중간 반전, 입력, 잠금/중지/만료/프로세스 사망 복구를 설치 후 확인해야 한다.

화면 구성 검증은 현재 검증된 ROTATION_0 fullscreen 크기를 요구한다. 분할 화면·회전 등 대상 구성을 1.8초 동안 확인하지 못하면 준비 화면에 멈춰 있지 않고 시험을 종료한다.

## Windows 설치 이관

기존 Mac APK와 Windows debug APK의 서명이 달랐다. 기존 APK 및 앱 데이터 65개 항목은 `measurements/before-windows-reinstall/`에 백업하고 tar 읽기 검증했다. 사용자에게 Poldy 삭제/재설치 승인을 받아 0.14.2 설치 및 실행을 완료했다. Shizuku·overlay·알림 권한도 연결했다.

자동 왕복 시험에서 사용자는 내부에 외부용 화면이 잠깐 보이는 문제는 사라졌고 접기·펼치기의 검은 깜빡임은 남았다고 확인했다. 후속 시스템 로그에서 물리 패널의 약 140~184ms OFF/ON이 원인으로 드러났으며, 수정은 `power-pin-apple-motion-0.15.md`에서 이어간다.
