# Poldy 개발 인계 및 다음 작업 계획

작성: 2026-09-11 17:27 KST. 사용자가 다른 장소·세션에서 이어서 작업하기 위해 개발을 중단하고 이 문서를 요청했다. **이 문서를 읽고 현재 소스를 확인한 뒤 이어서 작업한다. 원인 설명만 하고 멈추지 말고 실제 수정·설치·검증까지 진행하는 것이 사용자의 요구다.**

## 1. 재개 시 가장 먼저 알아야 할 상태

| 구분 | 상태 |
| --- | --- |
| 프로젝트 | `/Users/ldong-yeop/Desktop/private/poldy` |
| 마지막으로 휴대전화에 설치·실행 확인한 버전 | **0.14.0-native-home, versionCode 38** |
| 현재 작업 폴더 소스 | **0.14.1-native-home, versionCode 39** |
| 최신 빌드 | `artifacts/poldy-0.14.1-native-home.apk` — **빌드 성공, 아직 설치하지 않음** |
| Shizuku 서비스 버전 | `ControlBridge.java`의 `.version(39)` |
| 검증 | Java 테스트 57개 통과, Lint 오류 0 / 경고 28, assembleDebug 성공 |
| USB | 인계 시 `adb devices -l`에 기기 없음. 사용자가 이동하며 연결을 해제한 것으로 보이나 이유를 확인한 것은 아님 |
| 실행 작업 | 확인한 범위에서 별도 로컬 녹화·Gradle·진단 실행 프로세스 없음 |
| 휴대전화 애니메이션 | 마지막 시험은 약 17:21:36 시작, 5분 자동 종료 예정. 연결이 해제돼 **실제 종료·원복은 최종 확인하지 못함** |
| Git | 이 폴더는 Git 저장소가 아니었음. 이번 작업에서 커밋·브랜치 생성하지 않음 |

최신 APK SHA256:

```text
b317fd2a9545ee4c5c675b6caf87cfdd7585248f8844c228f8ad3a0a457b906c
```

**현재 작업은 완료 상태가 아니다.** 홈 구성을 정상화하는 구조 변경은 구현·실기 재현했지만, 짧은 검은 깜빡임과 전환 초기에 반대 화면용 이미지가 보이는 문제를 추가로 수정해야 한다. 0.14.1은 아래의 작은 재요청 문제만 추가로 고쳤으며, 마지막에 계획한 캐시·대기 구조 개선은 아직 구현하지 않았다.

`README.md`와 `research/home-layout-0.13.md`에는 0.14 변경 전 또는 일부 단계의 기록이 남아 있다. 버전·완료 여부는 우선 이 문서와 실제 소스·빌드 결과를 기준으로 판단한다.

## 2. 사용자가 만들고자 하는 기능

- macOS에서 개발하고 USB 디버깅으로 실제 갤럭시 폴더블에서 검증한다.
- 사용자가 전달한 아이폰 Duo 접힘 애니메이션 영상처럼, 접고 펼치는 각도에 따라 효과가 연속적으로 변화해야 한다.
- 홈 화면뿐 아니라 **다른 앱을 사용 중일 때에도** 동작해야 한다.
- 펼치기 시작·접기 시작부터 효과가 반응하고, 중간에 멈추거나 방향을 바꿔도 따라와야 한다.
- 블러만 적용하는 효과로 끝내지 않는다. 화면 내용의 미세한 이동·원근 변형, 기울기에 따른 위아래 음영·여백, 부드러운 경계와 점진적인 선명도 복귀가 필요하다.
- 내부·외부 화면을 조기에 준비하고 전환 시 검은 화면, 저해상도 확대, 잘못된 화면 비율, 마지막에 효과가 한꺼번에 진행되는 현상을 없애려는 프로젝트다.
- 마지막 시각 효과 선호: 사다리꼴 변형은 절제하고 검은 경계는 부드럽게, 블러는 조금 더 강하게. 0.13.1에서 블러 반경을 0.13.0 대비 8% 늘렸다.
- 다른 작업에서 만든 과거 데모를 베끼지 말고 전달한 영상에 근거해 새로 구현하라는 요청이 있었다. 이 프로젝트 소스·측정 기록은 현재까지 이어온 새 구현의 기록이다.

USB 디버깅, 진단 앱 설치, Shizuku 사용, 복구 가능한 짧은 표시 실험은 세션에서 이미 허용됐다. 일상적인 빌드·설치마다 다시 허락을 물을 필요는 없다. 다만 손으로 폰을 접거나 특정 각도에서 유지하는 작업은 사용자에게 실제 준비 상태를 확인해야 한다. 사용자 의사 없이 삼성 등에 문의·메시지를 보내지 않는다. 루팅·부트로더 잠금 해제·공장 초기화·홈 데이터 삭제는 승인된 작업이 아니다.

## 3. 마지막 사용자 피드백 — 아직 해결해야 하는 내용

홈 화면 문제가 처음 제기된 내용:

> 홈 화면에서 왜 외부 화면용 화면이 내부 화면에서 그냥 좌우로 넓게 해서 보이는데?

원인 설명만 한 뒤 사용자가 명시적으로 수정을 요구했다:

> 수정일 진행해줘야지

0.14.0을 설치한 후 가장 최근 피드백:

> 검은 깜빡임이 아직 남아있는데 잠깐 사이에 지나가고 또 문제점이 있는데 만약 외부 화면에서 내부 화면으로 펼칠 때 들어갈 때 내부 화면이 90도를 넘어서 보일 때 외부 화면이 보였다가 내부 화면으로 보이는 이슈가 있어

따라서 다음 작업의 초점은 다음 두 가지다.

1. 내부/외부 홈 구성을 정상적으로 유지하면서 남아 있는 짧은 검정을 줄이거나 제거한다.
2. 대상 패널에 반대 화면용 앱 이미지가 잠깐 보이는 현상을 없앤다. 단순히 외부 화면 이미지를 내부 해상도로 늘리거나 블러로 덮는 것으로 해결됐다고 판단하면 안 된다.

사용자는 이후 개발을 잠시 중단하고 인계 문서 작성을 요청했다. **이 문서 작성 이후 추가 기능 개발이나 휴대전화 실험을 진행하지 않았다.**

## 4. 개발 환경 및 실기 식별

- OS: macOS, 셸 zsh.
- 프로젝트: `/Users/ldong-yeop/Desktop/private/poldy`.
- ADB: `/Users/ldong-yeop/Library/Android/sdk/platform-tools/adb`.
- Android SDK: `/Users/ldong-yeop/Library/Android/sdk`.
- JDK: `/Applications/Android Studio.app/Contents/jbr/Contents/Home`.
- Build Tools: `36.0.0`; compileSdk 36, targetSdk 36, minSdk 33.
- 휴대전화 serial: `R5KL8036E1D`.
- 실제 식별: **SM-F971N / h8q, Android 17 SDK 37, One UI 90000**. 사용자는 갤럭시 폴드 8이라고 부른다.
- 내부 물리 display ID: `4630947004648141459`, 원본 크기 **2448×1848**.
- 외부 물리 display ID: `4630947123231501204`, 원본 크기 **1248×1972**.
- 물리 기본 density 420, 사용자 적용 density **360**이 정상 상태와 실험 양쪽에서 확인됐다. 이를 이번 문제의 원인으로 보고 임의로 초기화하지 않는다.
- 기본 세로 자세, ROTATION_0 조건을 중심으로 검증했다. 임의 회전은 아직 충분히 검증하지 않았다.

기기 상태 ID:

| ID | 이름 |
| --- | --- |
| 0 | CLOSED |
| 1 | TENT |
| 2 | HALF_OPENED |
| 3 | OPENED |
| 4 | CONCURRENT_INNER_DEFAULT |
| 5 | CONCURRENT_OUTER_DEFAULT |

상태 이름만 믿지 말고 실제 `cmd device_state state`, `dumpsys display` 및 사용자 관찰을 함께 확인한다. 일부 출력에서 상태 4의 `cancel_when_requester_not_on_top=true`가 관찰됐다. 앱 전환 중 요청이 취소되는지 별도 관찰 대상이다.

## 5. 이번 홈 화면 버그의 확정 원인

0.8~0.13.1은 검은 깜빡임을 줄이기 위해 **물리 매핑을 상태 5(외부가 기본 화면)에 고정**했다. 내부 사용 시에는 논리 display 0의 크기만 2448×1848로 바꾸고, 내부 물리 패널에 시스템 미러를 띄웠다.

이때 삼성 홈은 큰 창을 받아도 화면 구분은 외부로 받았다.

| 비교 | 정상 내부 화면 | 0.13.1 경로 재현 |
| --- | --- | --- |
| 홈 Activity bounds | 2448×1848 | 2448×1848 |
| density | 360 | 360 |
| Configuration | `dt/m` | `dt/s` |
| 결과 | 내부 배경·아이콘·위젯 배치 | 외부 배경·배치가 넓은 영역에 표시 |

실기 근거: `measurements/home-layout-v37/normal-inner-home.txt`, `fixed-wide-home.txt` 및 각 PNG.

실제 기기 `services.jar`에서 확인한 내용:

- `FoldDisplayController.isInPrimaryDevice(DisplayInfo)`가 DisplayInfo.address의 물리 ID와 mPrimaryPhysicalDisplayId를 비교한다.
- `DisplayContent.computeScreenConfiguration`과 `onRequestedOverrideConfigurationChanged`가 이 결과로 `Configuration.semDisplayDeviceType`을 0 또는 5로 설정한다.
- `WindowOrganizerController.applyChanges`의 config mask는 `0x20003c00`이고 화면 타입 변경 비트 `0x04000000`은 포함하지 않는다. 임의 WindowContainerTransaction 설정만으로 타입을 바꿀 수 있다고 가정하지 않는다.

시도했지만 채택하지 않은 방법:

- `IActivityTaskManager.updateConfiguration`에 `semDisplayDeviceType=0`만 전달했다.
- 호출 결과는 true였지만 홈 Activity의 실제 CurrentConfiguration은 계속 `dt/s`였다.
- 정상 원복 확인. 자료: `measurements/home-profile-trial/probe-retry.txt`, `home.txt`, `restored.txt`.
- 첫 실행은 `Killed`로 끝나 원인 미확정이며, 자세를 다시 준비한 재시도에서 위 결과를 확인했다. 실패 실행을 성공 근거로 사용하지 않는다.

## 6. 이번에 구현한 0.14.0 / 0.14.1 구조

### DisplayControl.java

- 목표 상태를 항상 5로 두던 코드를 **내부 4 / 외부 5**로 변경했다.
- 첫 요청 전에 StableDisplay의 독립 RecoveryGuard를 준비한다.
- 화면 구성이 실제로 바뀔 때 NativeScene.shield()로 각 패널의 이미지를 유지한다.
- 실제 상태 요청 → StableDisplay.resize(inner)에서 새 물리 패널·네이티브 크기를 확인 → 최소 650ms를 기다린 뒤 NativeScene.finishSwitch(inner).
- **현재 `requestMode` 전체가 synchronized이며 이 650ms 대기 동안 캡처 쪽도 같은 락을 기다린다. 이것이 다음에 개선하려는 핵심 부분이다.**
- captureFrame/routeScene은 `PhysicalPanels.primaryIsInner()`를 기준으로 실제 논리/물리 매핑에 맞춰 패널 레이어를 배치한다.
- CLOSED가 concurrent 요청을 취소하면 기존 observer가 자기 임대가 유효할 때 다시 요청한다.

### StableDisplay.java

- 기존의 `setForcedDisplaySizeDensityWithInfo` 호출, 화면 크기 강제 변경, 외부 투영 고정 스레드, 내부 live mirror를 제거했다.
- 현재는 논리 display 0 캡처용 미러와 독립 복구 감시자 수명을 관리한다.
- 이름이 `resize`지만 이제 강제 크기 설정 함수가 아니다. 현재 display 0의 물리 ID와 `getBaseDisplaySize`가 목표에 맞는지 최대 1800ms 확인한다.
- 성공하면 내부 2448×1848 또는 외부 1248×1972로 캡처한다.
- 캡처 버퍼 크기만으로 앱의 재배치 완료를 보장하지 못한다. captureLayers는 요청 crop 크기로 결과를 만들 수 있다.

### PhysicalPanels.java — 새 파일

- 두 물리 패널 ID, 원본 Rect, SurfaceControl의 물리 layer stack/projection 설정을 한곳에 둔다.
- `primaryIsInner()`는 실제 IDisplayManager.getDisplayInfo(0).uniqueId를 확인한다.
- 모르는 display ID면 오류를 낸다.

### NativeScene.java

- 원본 크기의 내부/외부 GPU 패널 두 개를 유지한다.
- 전환 중에는 임시 private layer stack **2100000000 / 2100000001**에 각각 배치한다.
- 전환 동안 1ms 주기의 짧은 작업으로 두 물리 패널의 출력 stack·projection을 유지한다.
- `finishSwitch`는 패널 레이어 배치와 **두 물리 패널의 출력 설정을 하나의 transaction**으로 시스템 기본 stack 0/1에 돌려보낸다.
- 정상 사용 중에는 기본 화면의 실제 앱이 표시되고 시스템 입력 경로로 동작하도록 설계했다. 예전의 내부 live mirror 경로는 제거했다.
- 이 방식은 시스템 display traversal과 경쟁할 수 있다. **무깜빡임이 증명된 방식으로 취급하지 않는다.**

### FoldService.java

- beginTransfer에서 이전에는 primary 패널만 표시 완료를 기다렸으나, 현재는 **secondary와 primary 모두 whenVisible fence**를 기다린 후 화면 상태 요청을 보낸다.
- 로그 시작 문구는 `native_started:native_profiles`.
- 각도·시각 효과·프레임 퇴역·전환 세대 관리 구조는 유지했다.
- 문제의 기존 코드도 아직 남아 있다:

```java
// 전환 중이 아닐 때 반대 패널에도 현재 화면을 맞춰 그린다.
secondary.frame(latest, !inner, 1, secondary.matches(latest) ? 0 : 1);
```

- 이 결과 내부 패널에 외부용 이미지가 들어가 있고, beginTransfer의 `target.snapshot()`이 그것을 heldTarget으로 잡는다. 새 내부 구성이 준비될 때까지 외부용 이미지가 보이는 원인 후보로 판단했다.
- 마지막 사용자 피드백의 픽셀 노출 타이밍과 이 경로를 다음 실험에서 정확히 대조해야 한다. 구조상 잘못된 비율의 콘텐츠가 대상 패널에 들어가는 것은 소스에서 확인된다.

### RecoveryGuard.java

- 기존: 별도 app_process가 heartbeat EOF 또는 6초 만료 시 자기 PID 소유 요청만 취소·복구.
- 추가: private stack으로 출력 중 프로세스가 사망하면 DMS가 이미 원래 stack을 설정했다고 캐시하고 있을 수 있어, 요청 취소 뒤 600ms 기다리고 다른 override가 없는 경우 물리 출력을 현재 정상 0/1 매핑으로 명시 복구한다.
- **추가한 이 경로를 실제 SIGKILL로 검증하는 작업은 아직 하지 않았다.**

### 0.14.1에만 추가된 변경 — 현재 빌드, 미설치

0.14.0은 CLOSED가 요청을 취소한 뒤 같은 외부 화면 상태 5를 다시 준비할 때도 shield를 켰다. 이때 idle primary의 opacity가 0이면 private stack에 투명 패널만 놓여 검정이 생길 수 있다.

현재 소스는 다음 조건일 때만 shield와 650ms 대기를 수행한다:

```java
boolean shielding = scene != null && PhysicalPanels.primaryIsInner() != inner;
```

**이 수정의 실기 효과는 아직 확인하지 않았다.** 상태 4/5 전환 자체의 모든 검정을 고친 것은 아니다.

## 7. 검증 결과와 검증하지 못한 부분

### 홈 구성 정상화: 확인됨

`tools/NativeProfileProbe.java`에서 새 APK 클래스와 같은 StableDisplay / NativeScene / PanelPower / PhysicalPanels를 이용한 짧은 테스트를 실행했다.

- 자료: `measurements/native-profile-v38/`.
- 내부: `inner-home.txt`의 CurrentConfiguration `dt/m`, 2448×1848.
- 외부: `outer-home.txt`의 CurrentConfiguration `dt/s`, 1248×1972.
- `inner.png`를 직접 확인했고 원래 내부 배경화면과 위젯·아이콘 배치가 돌아왔다.
- 양방향 후 정상 OPENED, size override 없음으로 원복 확인.
- 별도 시험 APK를 `/data/local/tmp/poldy-native-profile.apk`에 올려 실행했으며, 이 파일은 수정된 최종 guard를 넣기 전 0.14.0 빌드일 수 있다. 최신 APK로 착각하지 않는다.

### 실제 설치본 0.14.0: 회귀 있음

- 실제 앱 버전 38 설치·실행 성공.
- 다른 앱/홈에서 사용자의 접힘 로그가 들어왔다.
- 자료: `measurements/native-v38-run/startup-logcat.txt`, `home-inner.mp4`, `home-inner-luma.json`.
- 내부 녹화 575프레임 중 전체 검정 표본 2개:
  - PTS 6.394644s → 다음 PTS까지 약 6.73ms.
  - PTS 6.424756s → 다음 PTS까지 약 7.97ms.
- 녹화 프레임 간격이며 OLED 실제 발광 시간을 측정한 것은 아니다.
- 사용자도 짧은 검정을 확인했다. 따라서 무깜빡임 성공으로 보고하면 안 된다.
- 새 목적지 프레임 준비 로그가 전환 시작 후 약 824~859ms였다. 현재 650ms 락 대기와 관련된 지연 개선이 필요하다.
- 일부 자동 진단 중 사용자도 폰을 움직였다. 그 녹화를 순수한 정지 상태 자동 비교로 취급하지 않는다.
- `home-inner.png`는 전환 중 흐린 held 이미지다. 이 PNG를 내부 홈 완성 상태의 증거로 사용하지 않는다. 정상 배치 증거는 `native-profile-v38/inner.png`.

### private stack 단독 초기 실험

- `tools/PrivateStackTrial.java`, `measurements/private-stack-trial/`.
- 예전 CompositorHoldTrial을 복사해 고정 private stack과 원본 projection으로 실험했다.
- 외부 녹화에 검정 1표본이 남았다. 내부 녹화 파일은 0바이트라 검증 불가.
- 이 실험은 최종 NativeScene의 1ms 루프/복구 구조와 동일한 최종 구현이 아니다. 성공 근거로 과장하지 않는다.

### 테스트 통과의 의미

- 현재 0.14.1: Java 테스트 57개 통과, Lint 오류 0 / 경고 28, 빌드 성공.
- 자료: `measurements/native-v39-build/build.log`, `lint-results.xml`, `status.json`.
- 기존 Java 테스트는 각도·시각 효과·프레임 관리 로직 중심이다. 실제 Samsung display 전환을 유닛 테스트가 검증한 것은 아니다.
- 새 기본 입력 경로의 버튼 진단을 시도했지만 사용자가 앱을 옮겨 후속 UI에 YouTube가 나타났다. **내부 버튼 진단 카운터 증가를 확인하지 못했으므로 통과 처리하지 않는다.**
- 제어 프로세스 사망 시 새 private stack 복구 검증은 계획만 했고 실행하지 않았다.

## 8. 중단 직전에 개발하려던 내용 — 아직 구현하지 않음

### A. 화면 전환 대기와 캡처를 분리

목적: 이미 시스템이 내부 구성을 준비했는데도 650ms를 전부 기다리는 동안 외부 held 이미지가 남는 지연을 줄인다.

검토하던 방향:

1. 현재 `requestMode`가 화면 상태 요청, 목표 물리 ID/크기 확인, 650ms 보호, 출력 복귀를 한 번에 처리하는 것을 분리한다.
2. 목표 구성이 확인되는 즉시 새 논리 화면의 프레임을 받을 수 있게 한다.
3. private shield는 별도 수명/세대로 유지하되 captureFrame과 동일한 제어 락을 장시간 점유하지 않는다.
4. 새 네이티브 프레임이 실제로 준비되고 양 패널 버퍼 표시 완료가 확인되면 출력 복귀를 수행한다.
5. 빠른 반전 시 이전 switch/finish 콜백이 최신 상태를 덮지 않도록 request generation을 반드시 검증한다.
6. guard·임대 만료·잠금·실패 시 원복이 분리된 상태에서도 작동해야 한다.

**650ms를 단순 삭제하거나 무조건 짧게 바꾸는 것만으로 끝내면 안 된다.** 이 시간은 뒤늦은 DMS traversal이 화면 설정을 덮는 문제를 방어하려고 넣은 것이다. 앱 프레임 준비와 물리 출력의 안정 시점을 각각 관찰해야 한다.

### B. 반대 화면용 이미지를 대상 패널에 넣지 않기

목적: 내부에는 내부 비율·내부 구성의 이미지, 외부에는 외부 비율·외부 구성의 이미지만 보여준다.

검토하던 방향:

- NativePanel에 각 패널의 마지막 **실제 네이티브 구성 프레임**을 보존한다.
- 같은 현재 앱/작업에 해당하는 유효한 캐시가 있다면 전환 초기에 그것을 활용한다.
- 현재 앱이 바뀌면 다른 앱의 오래된 캐시를 버린다. 과거 0.8.3에서 반대 패널에 이전 앱이 남는 문제가 있었으므로 단순 영구 캐시로 회귀하면 안 된다.
- 새 앱에 해당하는 네이티브 캐시가 없을 때 반대 화면 이미지를 확대해 보여주는 대신, 콘텐츠가 식별되지 않는 짧은 준비 화면을 쓰는 방안을 검토했다. 구체 디자인은 아직 정하지 않았다. 사용자에게 내부 앱이 즉시 준비된 것처럼 주장하지 않는다.
- 새 대상 프레임은 기존 240ms handoff 및 각도 기반 선명도 복귀와 연결한다. 마지막에 확 바뀌는 효과를 재도입하지 않는다.
- `NativePanel.references`, `FrameRetirement`, snapshot의 bakedBefore/previousBitmap 수명까지 캐시 참조를 포함해야 한다.
- snapshot이 캐시 없는 준비 화면도 캡처할 수 있도록 null 프레임 처리도 검토해야 한다. 현재 snapshot은 bitmap/bakedBefore가 모두 없으면 실패한다.

앱/작업 식별 전달 구상:

- shell 캡처 과정에서 현재 top task/component 식별 값을 읽는다.
- CapturedFrame에 owner/task 식별을 실어 ControlBridge → FoldService에 전달한다.
- owner가 달라지면 패널별 캐시를 무효화한다.
- 추정한 IActivityTaskManager.getTasks 시그니처를 바로 호출하지 말고 현재 펌웨어에서 먼저 확인한다. 창 전환 중 owner와 이미지 시점이 어긋나는 것도 고려한다.
- **CapturedFrame, ControlBridge.FrameResult, NativePanel 캐시, owner 전달은 아직 전혀 수정하지 않았다.**

중단 시점의 마지막 실제 시도:

- `IActivityTaskManager.getTasks`의 실제 메서드 시그니처를 reflection으로 출력하는 작은 `TaskApi.java`를 만들었다.
- javac에서 `--release 17`을 빠뜨려 Java class major version 69가 생성됐고 D8이 거부했다.
- **이 probe는 휴대전화에서 실행되지 않았으며 시그니처 확인 결과가 없다.**
- 파일을 `/tmp/poldy-native-profile/TaskApi.java`에서 `tools/TaskApi.java`로 보존했다.
- 재개 시 필요하면 `javac --release 17`로 다시 컴파일한다. Android Studio 번들 JDK의 기본 class 버전을 그대로 쓰지 않는다.

### C. 짧은 검정 구간의 추가 원인 구분

- 0.14.1의 같은 프로필 재요청 shield 생략을 먼저 설치·검증한다.
- 상태 4↔5 실제 전환 중의 DMS blank와, CLOSED가 override를 취소하고 상태 5를 재준비하는 구간을 분리해서 본다.
- primary opacity 0 상태에서 private 출력으로 옮기는 경우, 양 패널 fence 이전 전환, 늦은 DMS transaction, 요청 세대 역전 여부를 확인한다.
- 1ms 반복 설정만 더 빠르게 하는 것을 확정 해법으로 가정하지 않는다. private stack은 시스템 출력과 경쟁하므로 실제 프레임·사용자 관찰이 필요하다.

## 9. 반드시 유지해야 할 기존 성과와 제약

### 힌지 각도 입력은 이미 해결 경로가 있다

- 공개 TYPE_HINGE_ANGLE은 이 기기에서 0/90/180 중심의 세 단계만 보고했다.
- 한쪽 자이로만 적분해 접힘 각도를 추정하던 방식은 큰 오차와 끝점 몰아 움직임을 만들었다. **그 방식으로 되돌리지 않는다.**
- 현재는 shell 권한으로 `sensors-hal` 로그의 folding_angle / lid_angle_fusion 중간 값을 읽는다.
- HalAngleReader → HalAngleSample → IHingeAngleListener → HingeProgress → AngleSmoother → FoldOptics/FoldPlane 흐름이다.
- 자이로는 FoldAttitude에서 기울기 기반 표현에 사용하며, 접힘 진행도는 HAL 측정값을 따른다.
- AngleSmoother는 sparse sample 사이를 적응적으로 보간한다. 사용자는 0.12.1에서 각도를 잘 따라오고 0.12.2 이후 끊김이 크게 줄었다고 확인했다.
- HAL 로그 경로는 현재 기기·펌웨어에서 검증된 방식이다. 절대각을 물리 각도계로 정밀 보정한 결과는 아니다.

### 프레임과 보안 정책

- 내부 2448×1848, 외부 1248×1972의 원본 GPU 버퍼를 사용한다.
- 캡처 secure/protected 정책은 REDACT(0)로 유지한다. 보호 화면 제한을 우회하지 않는다.
- 승인된 전체 화면 공유 MediaProjection FGS가 캡처 수명을 제한한다.
- 잠금·공유 종료·오류·5분 만료 시 중지한다.
- TransferGate의 generation, 2개 목적지 프레임 확인, 표시 fence, FrameRetirement 참조 수명을 유지한다.
- 반대 비율 이미지를 비균일 확대해 ‘맞춘 것처럼’ 보이게 하지 않는다.
- 전환 중 서로 다른 앱 배치를 양 화면에서 독립 실행하는 기능은 아니다. 하나의 실제 논리 앱 화면과 GPU 효과 패널을 연결하는 구현이다.

### 시각 효과는 이번 버그 수정과 분리해서 유지

- FoldOptics의 blur radiusFraction: `.0027f + .04536f * depth` (0.13.1의 +8%).
- FoldPlane은 사다리꼴 변형을 낮춘 상태, FoldRenderer는 Gaussian 전에 소스 평면 경계를 부드럽게 처리해 색이 경계로 번지도록 했다.
- 0.13 GPU 검증: `measurements/renderer-v36/`, `measurements/renderer-v36-boundary/`, `research/soft-diffusion-0.13.md`.
- 홈 전환 구조를 고치면서 각도 커브·블러를 임의로 다시 조정할 이유는 없다. 회귀 원인 구분이 어려워진다.

## 10. 재개 순서

1. 이 문서와 실제 소스의 versionCode를 확인한다. 현재 39 / 0.14.1, 휴대전화는 마지막 확인 38 / 0.14.0이다.
2. 사용자 USB 연결 및 디버깅 승인 상태를 확인한다. 마지막 PID를 재사용하지 말고 새 PID/현재 state를 읽는다.
3. 현재 FoldService, state override, 크기 override, 잠금 상태를 확인한다. 다른 소유자의 상태 요청을 임의로 초기화하지 않는다.
4. 필요하면 현재 0.14.1을 먼저 설치해 같은 프로필 재요청 수정의 효과를 분리 검증한다. 마지막 사용자 피드백의 두 문제를 고친 최종판으로 소개하지 않는다.
5. 위 8-A/8-B 설계를 소스에 적용한다. 다음 버전은 versionCode와 Shizuku `.version(...)`를 함께 올린다.
6. Java 테스트/Lint/빌드를 실행한다. 새 전환 상태 머신·캐시 소유권·빠른 반전에 대해 의미 있는 테스트를 추가한다. 파라미터 값만 그대로 확인하는 테스트는 불필요하다.
7. 정상 홈 내부/외부 구성이 각각 `dt/m` / `dt/s`인지 확인하고 실제 이미지를 본다.
8. 다른 앱을 연 상태에서 펼침/접힘, 중간 정지/반전, 빠른 연속 동작, 다른 앱으로 이동 후 첫 펼침을 검증한다.
9. 내부/외부 입력, 잠금/중지/시간 만료/제어 프로세스 사망 복구를 확인한다.
10. 사용자 체감과 녹화 측정을 구분해 보고하고 plan.md/연구 기록을 갱신한다.

## 11. 빌드·설치·진단 명령

프로젝트 폴더에서 실행. 다른 Mac이면 SDK/JDK 경로와 serial을 먼저 조정한다.

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest lintDebug assembleDebug

/Users/ldong-yeop/Library/Android/sdk/platform-tools/adb devices -l
/Users/ldong-yeop/Library/Android/sdk/platform-tools/adb -s R5KL8036E1D shell cmd device_state state
/Users/ldong-yeop/Library/Android/sdk/platform-tools/adb -s R5KL8036E1D shell wm size
/Users/ldong-yeop/Library/Android/sdk/platform-tools/adb -s R5KL8036E1D shell wm density
/Users/ldong-yeop/Library/Android/sdk/platform-tools/adb -s R5KL8036E1D shell dumpsys activity services dev.poldy.lab

/Users/ldong-yeop/Library/Android/sdk/platform-tools/adb -s R5KL8036E1D install -r artifacts/poldy-0.14.1-native-home.apk
/Users/ldong-yeop/Library/Android/sdk/platform-tools/adb -s R5KL8036E1D shell am start -W -n dev.poldy.lab/.MainActivity
```

UI helper를 프로젝트 안에 보존했다: `tools/android_ui.py` (Python 3, 현재 Mac의 절대 ADB 경로와 serial이 들어 있다).

```sh
python3 tools/android_ui.py
python3 tools/android_ui.py '1 · 화면 제어 연결'
python3 tools/android_ui.py '3 · 애니메이션 시작 · 5분 시험'
python3 tools/android_ui.py '화면 공유'
```

각 실행은 현재 uiautomator를 새로 읽고 정확히 하나의 텍스트가 있을 때만 탭한다. 사용자가 직접 먼저 동의/화면 이동했으면 ‘text not found’가 정상일 수 있다. 무조건 재시도하지 말고 현재 UI·로그를 확인한다. **사용자가 앱을 옮길 수 있으므로 오래된 좌표를 다른 앱에 사용하지 않는다.**

시험 중지:

```sh
/Users/ldong-yeop/Library/Android/sdk/platform-tools/adb -s R5KL8036E1D shell run-as dev.poldy.lab am stopservice --user 0 -n dev.poldy.lab/.FoldService
```

`Service stopped`인데 exit code 255가 나오는 경우가 있었다. 실제 서비스·state를 읽어 확인한다.

앱 실행 중에만 쓰는 짧은 자동 전환 진단:

```sh
/Users/ldong-yeop/Library/Android/sdk/platform-tools/adb -s R5KL8036E1D shell run-as dev.poldy.lab am startservice --user 0 -n dev.poldy.lab/.FoldService -a dev.poldy.lab.FOLD_DIAGNOSE --el holdMillis 4000
```

이것은 실제 표시 상태를 바꾸므로 사용자가 동시에 폰을 움직이는 실험과 무심코 겹치게 하지 않는다. `--ez reverse true`는 160ms/380ms 반전을 추가하는 진단이다.

필터 로그:

```sh
/Users/ldong-yeop/Library/Android/sdk/platform-tools/adb -s R5KL8036E1D logcat -d -v threadtime -T '09-11 17:21:20.000' -s PoldyFold PoldyControl PoldyCapture PoldyAngle AndroidRuntime
python3 tools/analyze_hal_trial.py measurements/native-v38-run/startup-logcat.txt
```

재개 날짜에 맞춰 `-T` 시간을 바꾼다. `analyze_hal_trial.py`는 첫 인자를 로그 경로로 받으며 `--help` 옵션이 없다. 전체 logcat을 clear하지 않는다.

로컬 녹화 분석:

```sh
swift tools/video_luma.swift measurements/native-v38-run/home-inner.mp4 measurements/native-v38-run/home-inner-luma.json
swift tools/video_difference.swift INPUT.mp4 OUTPUT.json
swift tools/reference_frames.swift INPUT.mp4 OUTPUT_DIRECTORY 0.1 START_SECONDS END_SECONDS
```

screenrecord는 물리 display ID를 선택한다. 두 개를 동시에 실행한 실험 중 하나가 0바이트였으므로 파일 존재만으로 성공 처리하지 말고 decode·프레임 수를 확인한다.

## 12. 파일·백업·이동 시 보관할 자료

### 이번 인계에서 프로젝트 안에 보존한 것

- `plan.md`: 이 문서.
- `artifacts/poldy-0.14.1-native-home.apk` 및 `.sha256`: 최신 빌드, 미설치.
- `artifacts/source-backups/poldy-0.13.1-before-native-home.tar.gz`: 이번 구조 변경 직전의 소스 백업. 원래 `/tmp/poldy-v37-before-home-profile.tar.gz`.
- `artifacts/source-backups/poldy-0.14.1-handoff-source.tar.gz`: 인계 시점 app/src, app/build.gradle.kts, README.md 백업.
- `measurements/native-v39-build/`: 빌드 로그·Lint·버전/검증 요약.
- `tools/android_ui.py`, `tools/video_luma.swift`, `tools/video_difference.swift`, `tools/TaskApi.java`: `/tmp`에만 있던 도구 복사.
- 이미 있던 `artifacts/poldy-0.13.1-fuller-blur.apk`: 고정 매핑 경로의 직전판. 홈 버그가 있으므로 최종 해결책으로 단순 롤백하면 안 된다.

0.14.0 설치 APK 자체는 별도 artifacts 이름으로 보존하지 않았다. `/data/local/tmp/poldy-native-profile.apk`는 실험용 이전 빌드이며 마지막 설치본과 동일하다고 가정하지 않는다. 현재 `app/build/outputs/apk/debug/app-debug.apk`는 0.14.1로 덮어써져 있다.

소스 백업은 원본 폴더에 바로 덮어풀지 말고 임시 별도 디렉터리에 풀어 비교한다. Git 저장소가 아니므로 특히 주의한다.

### 분석 문서

- `research/home-layout-0.13.md`: 홈 버그 원인 비교. 0.14 구현 내용은 아직 이 문서에 충분히 반영하지 않았으므로 본 plan.md와 같이 본다.
- `research/black-flash-investigation.md`: 0.7~0.8의 시스템 blank, 실패 실험, 고정 매핑 선택 근거.
- `research/hal-angle-log-path.md`: 현재 중간 각도 입력 경로.
- `research/hinge-angle-access-2026-09-11.md`: 공식 API·권한·대안 조사.
- `research/soft-diffusion-0.13.md`: 최근 시각 효과와 GPU 검증.
- `research/projected-plane-0.11.md`, `reference-animation-analysis.md`: 영상 분석과 공간 변형.

`tools/HomeLayoutProbe.java`는 0.13.1의 옛 StableDisplay.attachLive API를 호출하는 과거 비교 도구다. 현재 0.14 소스에 그대로 묶어 컴파일하면 호환되지 않는다. 과거 결과를 확인할 때 사용하고, 새 구조 시험은 `NativeProfileProbe.java`를 기준으로 한다. 도구 소스는 Gradle 앱 소스셋에 포함되지 않는다.

### 원본 동영상 — 프로젝트 밖에 있음

- `/Users/ldong-yeop/Downloads/1a0888948391031c.mp4` — 약 11.236초, 1008×860.
- `/Users/ldong-yeop/Downloads/1a088757f121031c.mp4` — 약 19.875초, 1080×1920.
- `/Users/ldong-yeop/Downloads/1a08a212d3350598a.mp4` — 약 26.867초, 720×720.

다른 컴퓨터로 이동한다면 원본 영상도 함께 가져가야 전체 움직임을 다시 분석할 수 있다. 기존 추출 프레임은 `research/reference-frames/`, `research/plane-study/`에 있어 프로젝트와 함께 이동한다.

### `/tmp`의 큰 펌웨어 분석 자료 — 프로젝트에 복사하지 않음

- `/tmp/poldy-services.jar`: 실제 기기 services.jar, 약 28MB.
- `/tmp/poldy-display-code/classes.dex`, `classes.dex.txt`: dexdump 텍스트 약 484MB.
- 필요한 경우 동일 기기 `/system/framework/services.jar`를 다시 받아 SDK `dexdump`로 재생성할 수 있다.
- 일부 dexdump 텍스트에 UTF-8 오류가 있어 Python으로 읽을 때 `errors='replace'`를 사용했다.
- 전 파일을 한 번에 출력하지 말고 `rg`로 대상 메서드를 찾은 뒤 제한된 구간만 읽는다.
- Python 시스템 버전은 3.9였다. `tarfile.extractall(filter=...)` 인자는 지원하지 않는다.

### 외부 참고

공식 제품 페이지 `https://www.apple.com/iphone-duo/`와 독립 재현 프로젝트 `https://github.com/jal-co/iphone-duo`, `https://github.com/lqSky7/iphone-duo-macos-animation`를 이전 시각 효과 작업에서 확인했다. 독립 프로젝트를 Apple의 실제 구현 코드라고 소개하지 않는다. 이번 홈 문제는 웹 추측보다 실제 기기·펌웨어 비교로 확인했다.

## 13. 다음 세션에 전달할 짧은 요청 예시

> 이 프로젝트의 plan.md를 읽고 이어서 개발해 줘. 현재 소스는 0.14.1이고 마지막 휴대전화 설치본은 0.14.0이야. 내부 홈 배치는 정상화됐지만, 짧은 검은 깜빡임과 펼칠 때 외부용 화면이 내부에서 잠깐 보이는 문제가 남았어. plan.md 8장의 전환 대기 분리와 패널별 네이티브 프레임 보존을 검토해서 실제 수정·빌드·설치·실기 검증까지 진행해 줘. 기존 HAL 각도 입력과 블러·부드러운 경계 효과는 유지해 줘.
