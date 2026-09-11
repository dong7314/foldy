# 연결된 기기 확인 결과

2026-09-11, USB 디버깅으로 직접 읽은 결과입니다. 기준 자료는 `measurements/baseline/`에 로컬 저장했습니다.

- 모델: Samsung SM-F971N, Android 17 / SDK 37, One UI 속성 `90000`.
- 지원 상태: 0 CLOSED, 1 TENT, 2 HALF_OPENED, 3 OPENED, 4 CONCURRENT_INNER_DEFAULT, 5 CONCURRENT_OUTER_DEFAULT.
- 동시 내부 디스플레이 지원 설정: `true`.
- 실제 런타임 디스플레이 레이아웃: 상태 4는 내부 기본+외부 보조 ON, 상태 5는 외부 기본+내부 보조 ON.
- `vendor`의 XML 파일에는 상태 4/5가 없지만 **런타임 덤프에는 존재**합니다. XML만으로 지원 여부를 판단하면 안 됩니다.
- 물리 내부 패널: 2448×1848, 식별자 `local:4630947004648141459`.
- 물리 외부 패널: 1248×1972, 식별자 `local:4630947123231501204`.
- 기본 상태에서 논리 display 0의 물리 패널이 교체됩니다. display 0을 항상 내부 화면으로 취급하면 안 됩니다.

## 기준 측정

사용자가 진단 앱 설치 후 세 번 접었다 펼쳤습니다. 시스템 타임라인은 CLOSED → TENT → OPENED → CLOSED의 반복을 확인했습니다. 진단 앱의 화면 크기 변경과 ON/OFF 전환도 기록했습니다.

표준 `Sensor.TYPE_HINGE_ANGLE`의 실제 이벤트는 **0, 90, 180** 세 값뿐이었습니다. 즉 `90` 표본에서 화면이 전환됐다고 해서 실제 전환 각도가 정확히 90°임을 입증한 것은 아닙니다. 사용자 관찰과 부합하는 전환 과정이지만, 이 센서만으로 정확한 임계각을 측정할 수 없습니다.

Samsung의 별도 센서 `com.samsung.sensor.folding_angle`(65686)이 존재합니다. 이 센서와 보조 가속도계에는 `com.samsung.permission.SSENSOR`가 필요합니다. 실제 permission dump의 보호 수준은 **signature|privileged**입니다. 일반 APK에 선언하거나 통상적인 런타임 권한 승인으로 받는 권한이 아닙니다. `com.android.shell`의 권한 덤프에도 해당 권한 부여는 보이지 않았습니다. `/sys/class/sensors`는 ADB shell에서 접근 거부됐습니다.

따라서 ADB 또는 Shizuku를 쓴다는 이유만으로 세밀한 각도까지 확보된다고 가정하면 안 됩니다. 각도에 정확히 붙는 효과와 세 단계 신호로 진행을 보간한 효과도 구분해야 합니다.

## 블러 기능

이 펌웨어에서 `wm disable-blur` 조회 결과는 `Blur supported on device: false`, `Blur enabled: false`입니다. 일반적인 Android의 window blur 지원 문서와 달리 **이 기기에서는 해당 기능에 의존할 수 없습니다**. 다른 앱 화면을 변형하려면 지원되는 캡처 경로나 별도의 합성 경로를 검토해야 합니다.

## 다음 검증

ADB 임시 실험 결과:

- 상태 5(CONCURRENT_OUTER_DEFAULT): 실제 접힘 상태 TENT에서 두 물리 패널 모두 ON 확인. 29개 표본 중 28개가 둘 다 committed ON. 다만 보조 display 1에 Activity를 직접 띄우는 요청은 거부됐습니다.
- 상태 3(OPENED): 접힌 상태에서 30~45°만 펼친 상태로 요청. 내부 패널 ON, 외부 OFF. **사용자가 내부에 실제 앱 화면이 보인다고 확인했습니다.**
- 상태 1(TENT): 완전히 펼친 뒤 30~45°까지 접은 상태에서 요청. 실제 base state는 OPENED를 유지했지만 외부 패널 ON, 내부 OFF. **사용자가 외부에 실제 앱 화면이 보인다고 확인했습니다.**
- 각 실험은 15초였고 원복 후 mOverrideState=Optional.empty를 확인했습니다.

따라서 관찰된 전환 시점을 앞당길 수 있음은 양방향으로 입증됐습니다. 원하는 임계각을 영구 수정한 것은 아닙니다. 자동화와 애니메이션의 완성도는 별도 검증 중입니다. 부트로더 해제·루팅·펌웨어 수정은 수행하지 않았습니다.

## 앱 내 제어 구현 중

Shizuku v13.6.0을 공식 RikkaApps 릴리스에서 설치하고 USB ADB로 시작했습니다. 앱은 Shizuku UserService 안에서 DeviceStateManager의 Binder 요청을 사용합니다. CONTROL_DEVICE_STATE는 이 기기에서 signature 권한이므로 일반 앱에 pm grant로 부여할 수 없습니다. UserService는 shell 권한으로 동작합니다.

요청은 제어 프로세스에 귀속됩니다. 정상 종료 시 해제하고, 앱의 갱신이 끊기면 4초 임대 만료로 해제합니다. 상태 5 요청 중 앱을 강제 종료했을 때 기본 상태 3으로 돌아온 것도 확인했습니다.

참고: [Shizuku API 공식 문서](https://github.com/RikkaApps/Shizuku-API), [공식 릴리스](https://github.com/RikkaApps/Shizuku/releases/tag/v13.6.0).


## 두 화면 시험판에서 발견한 회귀

0.3.0은 DISPLAY_CATEGORY_PRESENTATION으로 표시 대상을 골랐는데, 이 기기의 해당 목록에는 MediaProjection의 가상 화면도 들어갑니다. 실제 로그에서 companion_started:display=7이 찍혔고, dumpsys로 display 7이 Poldy local frames 가상 화면임을 확인했습니다. 그곳에 내용을 그리면 자동 미러링을 교체해 캡처 피드백을 유발합니다.

0.3.1은 모든 표시 화면을 조회한 뒤, 실기로 검증한 두 물리 패널의 native mode와 하드웨어 제품 정보로 대상을 한정합니다. display 1은 실제 내부 또는 외부 패널로 매핑되며, 캡처 화면을 대상에서 제외합니다. 효과 도중 반대 움직임이 시작되면 현재 전환 프레임을 복사해 다음 효과에 사용합니다.

## 0.4.0 화면 비율·끝점 복귀 수정

사용자는 전환 직전 화면의 비율로 흐려졌다가 새 패널 비율로 깜빡이는 현상과, 중간에서 접는 방향을 바꾸면 잘못된 패널이 남는 현상을 보고했습니다.

- 전환 직전 프레임은 보조 패널용으로만 남깁니다. 주 패널 효과에는 전환 중 새로 받은 프레임을 사용하며, 실제 주 패널의 native mode와 현재 전체 화면 비율을 대조합니다. 대상 패널이 잠시 안정된 뒤 시작하고, 프레임 준비 시간 1.6초를 넘기면 효과를 생략합니다.
- 효과 도중 주 화면 비율이 바뀌면 기존 효과를 제거합니다. 시작한 쪽 끝점으로 돌아오면 대기 중인 효과도 취소합니다.
- 끝점 신호가 오면 이전 요청 방향 대신 실제 끝점(완전 펼침/완전 접힘)에 맞는 동시 표시 상태로 전환하고, 삼성 기본 상태가 따라오면 임시 요청을 해제합니다. 중간 구간에서 방향이 바뀌는 순간을 알아내는 기능은 아닙니다.
- MediaProjection 프레임의 비율이 일치해도 다른 앱 내부의 재배치 완료까지 증명할 수는 없습니다. 모든 앱에서 재배치 깜빡임이 사라진다고 단정하지 않습니다.

수정본 빌드 후 USB 연결이 끊겼으나, 사용자의 재연결 후 0.4.0 설치에 성공했습니다. Shizuku 서버가 꺼져 있어 다시 시작하고 Poldy 앱을 재실행해 연결했습니다. 휴대전화 로그 09:34:06에 capture_size=506x800 및 started를 확인했습니다. 기록은 measurements/layout-v6-run에 수집합니다. 실제 보조 패널 내용 표시와 USB 분리 상태의 실행은 아직 미완료입니다.

이후 09:36~09:37의 12회 전환에서 실제 보조 패널 display 1에 대한 Presentation 시작 로그가 기록됐습니다. 사용자 피드백은 내부 화면 깜빡임, 저화질 재표시, 원본과 다른 연결성입니다. 효과 지연 중앙값 577ms와 약 3.06배 이미지 확대가 코드·로그에 의해 확인됐습니다. 앱은 자동 시험 종료 후 다시 force-stop했고, cmd device_state state에서 기본 CLOSED와 override 없음 상태를 확인했습니다. 상세 내용은 reference-animation-analysis.md에 정리했습니다.


## 0.5 원본 해상도 재구현과 실측 (2026-09-11)

- 완전 CLOSED 상태에서도 상태 5를 요청해 8초 동안 시험했습니다. 16개 표본 중 15개가 두 물리 패널 committed ON이었고 자동 원복됐습니다. `measurements/prewarm-closed` 참조.
- Android 17의 `ScreenCaptureInternal.captureDisplay`를 Shizuku UserService에서 실행해 내부 2448×1848·외부 1248×1972 GPU 버퍼를 전달했습니다. 프레임은 파일에 저장하거나 외부로 전송하지 않습니다. secure/protected policy는 모두 REDACT(0)입니다.
- 별도 SurfaceView 효과 레이어를 제외할 수 있음을 실제 픽셀로 확인했습니다. 마젠타 레이어 포함 시 중앙 픽셀 `ffd013d2`, 제외 시 원래 배경 `ff111a20`이었습니다. 원본 해상도 캡처와 양쪽 물리 화면의 Poldy 화면 출력을 확인했습니다.
- 0.5.0의 매 프레임 RenderEffect/BitmapShader 생성 경로에서 EGL 메모리 약 1.29GB를 관찰했습니다. 0.5.1은 이미지 셰이더를 효과에 보관하지 않고, 재사용하는 RenderNode·Gaussian blur·알파 마스크로 변경했습니다. 실제 반복 접기 61초의 13개 표본에서 EGL 68~277MiB, 마지막 157MiB였습니다. 장시간 무누수를 입증하는 결과는 아닙니다.
- **0.5.1 사용자 결과: 흐림은 보이나 깜빡임 지속, 펼치기 시작할 때 흐림 없음, 외부 패널에 내부 화면의 오른쪽 부분만 보임.** 성공으로 판정하지 않습니다.
- `measurements/native-v10-run/physical-states.json`의 218개 표본 중 두 패널의 committedState가 동시에 OFF인 표본이 2개 있습니다. 상태 4↔5 전환 중 패널 전원이 끊기는 구간입니다. 반면 두 패널이 ON인 구간도 있습니다. 동시 점등 지원과 무중단 기본 패널 교체는 다른 조건입니다.
- Android 17 AOSP `LogicalDisplayMapper.setDeviceStateLocked`는 새 레이아웃 전에 전환 중 디스플레이를 OFF로 만들고, `areAllTransitioningDisplaysOffLocked` 확인 후 매핑을 바꿉니다. 삼성의 전체 구현이 AOSP와 동일하다고 단정할 수 없지만 실측과 맞는 동작입니다. 앱 오버레이로 꺼진 물리 패널을 표시할 수 없습니다.
- 완전 접힘에서 시스템이 상태 요청을 해제해 다음 열기 전에 내부 패널이 꺼지는 현상도 확인했습니다. 0.5.3은 유효한 시험 임대가 남아 있을 때 CLOSED 상태 콜백에서 cover-primary 동시 표시를 재요청합니다. 이를 통해 전원 전환 순간까지 제거했다고 주장하지 않습니다.
- 0.5.3은 실제 `DeviceStateInfo.baseState`를 프레임과 함께 읽습니다. CLOSED→다른 실제 상태 변화를 기존 0/90/180 힌지 신호와 합치고 중복 시작을 차단합니다. 기기 상태가 바뀌기 전의 첫 미세한 움직임은 감지하지 못합니다. base OPENED를 완전 180°로 간주하지 않습니다.
- 오른쪽 정렬로 일부가 잘리던 처리는 전체 프레임을 비율 유지해 맞추는 방식으로 수정했습니다. 비율이 다른 미리보기에는 여백이 생깁니다. 전환 중 떠나는 쪽에는 그 패널의 전환 직전 프레임을 유지합니다. 임의 앱을 양쪽 비율로 동시에 실행하는 기능은 아닙니다.

공식 참고: [Android 17 ScreenCaptureInternal](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/core/java/android/window/ScreenCaptureInternal.java), [Android 17 LogicalDisplayMapper](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/core/java/com/android/server/display/LogicalDisplayMapper.java).

### 0.5.3 사용자 확인

사용자가 ‘아까 말한 잘려보이는 이슈는 아예 없어진 것 같다’고 확인했습니다. 깜빡임과 애니메이션 완성도는 여전히 부족하다고 평가했습니다. 완료로 처리하지 않습니다.

`measurements/native-v12-run`에는 실제 base_signal:1 → OPEN으로 펼침을 시작한 기록, CLOSED 이후 동시 표시 복원, 양쪽 원본 해상도 캡처가 있습니다. 228개 물리 상태 표본에서도 주 패널 교체 중 두 패널 committed OFF가 확인됐습니다. 이 결과는 깜빡임을 단순한 캡처 이미지 화질 문제로만 볼 수 없음을 보여 줍니다.


## 0.6.0: 별도 패널 전원 임대 발견

단순 기기 상태 변경의 한계를 확인한 뒤, 실제 SM-F971N `IDisplayManager`에 Samsung 전용 `setDisplayStateOverrideWithDisplayId(IBinder, int state, int displayId, int maxTimeoutMs)`가 있음을 reflection으로 확인했습니다. 기기의 `/system/framework/services.jar`를 Mac의 임시 폴더에 읽기 전용으로 가져와 DEX를 확인했습니다. BinderService는 DEVICE_POWER를 검사하며 shell에 해당 권한이 실제 부여돼 있습니다. 인수 순서·STATE_UNKNOWN(0) 해제·시간 제한·Binder death 해제를 기기 코드로 확인한 뒤 실험했습니다.

`tools/PanelPowerTrial.java`는 검증 기종만 허용하고 기존 기기 상태 요청이 있으면 거부합니다. 7초 강제 종료 타이머와 5.5초 시스템 전원 임대를 사용하며 finally에서 원복합니다. 상태 5→4→5 전환 중 전원 임대 2개가 존재하는 **7개 표본 모두 두 패널 state/committedState ON**이었습니다. 종료 후 전원 임대 0개 및 기본 CLOSED 복귀를 확인했습니다. 표본 사이의 짧은 꺼짐이나 화면 콘텐츠 깜빡임까지 배제하는 증거는 아닙니다. 기록: `measurements/power-hold-trial`.

0.6.0은 이 경로를 PanelPower로 앱에 연결했습니다. 시험 중 두 논리 디스플레이에 별도 Binder 토큰을 사용해 3초 제한으로 ON을 요청하고 1초마다 갱신합니다. 동일 토큰을 두 화면에 공유하면 timeout 제거가 섞일 수 있어 토큰을 분리했습니다. 정상 종료 시 명시적으로 해제하며 시스템 시간 제한과 프로세스 사망으로도 해제됩니다. 매핑 변경 전에 전원 임대를 걸어 물리 OFF를 막는 목적이며, 앱 재배치의 깜빡임까지 해결됐는지는 사용자 확인 대상입니다.


### 0.6.0 앱 내 반복 시험 수치

`measurements/native-v13-run/physical-live.jsonl`의 약 40초·153개 표본에서 두 물리 패널 committed ON은 151개였습니다. 완전 접힘 상태를 제외하고 동시 표시 요청이 있는 111개 표본은 모두 두 패널 committed ON이었습니다. 완전 접힘에서 요청이 자동 취소·복원되는 동안 내부 패널만 committed OFF인 표본 2개가 남았습니다. 주 화면 교체와 끝점 취소를 구분해야 합니다.

당시 수집한 13회 destination_ready 지연은 229~595ms, 중앙값 333ms였습니다. 0.5.1의 9회 수집 중앙값 609ms보다 작지만 동일 앱·속도로 통제한 벤치마크는 아닙니다. 패널 ON 표본과 빠른 준비 시간이 무깜빡임 육안 판정을 대체하지 않습니다. 현재 0.6.0에 대한 사용자 시각적 피드백은 대기 중입니다. APK 빌드·Java 14개 테스트·Lint(오류 0개) 통과했습니다.


## 0.7.0: 사용자가 확인한 검은 화면 공백 대응

사용자는 0.6.0에서도 단순한 배치 변화가 아니라 ‘화면이 순간 검게 꺼졌다 다시 보임’이라고 확인했습니다. 앞선 ON 표본 결과만으로 패널 또는 콘텐츠의 검은 구간을 모두 제거했다고 주장하지 않습니다.

코드에는 불투명도 0.8로 아래 재배치가 비칠 수 있는 경로, 보조 Presentation/SurfaceView의 교체, 두 캡처 프레임 수신 직후 흐림 범위 즉시 변경, 마지막 캡처 즉시 제거가 있었습니다. 각각 시각적 불연속을 만들 수 있지만 이들만으로 사용자의 모든 검은 구간을 설명한다고 확정하지 않습니다.

0.6.1의 교체 보간 수정은 로컬 빌드만 했고 휴대전화에는 설치하지 않았습니다. 이어 0.7.0에서 FoldService의 표시 계층을 NativeScene/NativePanel로 교체했습니다. NativeScene은 shell 권한으로 두 개의 SurfaceControl 루트 레이어를 만들며, 실제 패널별 원본 해상도 Surface 버퍼는 유지합니다. 기본 패널 변경 시 한 Transaction으로 두 레이어의 layer stack을 바꾸고, 주 패널의 평상시 불투명도는 0이지만 그 버퍼 자체는 최신 프레임으로 계속 채웁니다. 전환 중에는 이전 프레임을 불투명하게 유지하고 새 프레임과 120ms 교차 표시, 끝점에서 실제 앱으로 160ms 페이드를 적용합니다. 마지막에 화면 버퍼를 비우는 경로를 제거했습니다.

앱 창과 다른 앱의 생명주기에 종속되지 않는 표시 계층을 확보할 수 있는지는 `tools/NativeLayerProbe.java`로 확인했습니다. 실기 결과: `VALID=true, SURFACE=true, GPU_DRAW=OK, invisible=true`. 이 실험은 alpha 0의 64×64 임시 레이어만 만들었으며 표시 상태는 변경하지 않고 종료 시 해제했습니다. shell의 ACCESS_SURFACE_FLINGER 및 INTERNAL_SYSTEM_WINDOW 부여도 덤프로 확인했습니다. 이것은 앱 프로세스에 전송한 전체 화면 레이어의 시각적 품질까지 검증한 것은 아닙니다.

0.7.0 빌드·Java 테스트 14개·Lint는 통과했습니다. 설치 명령은 `adb: no devices/emulators found`로 실패했습니다. 기기 설치본은 아직 0.6.0이며 재연결 후 새 루트 레이어 출력, 캡처 제외, 정상 종료·강제 종료 시 레이어 해제, 접기·펴기 중 검은 프레임 유무를 확인해야 합니다. 기존 백업은 Mac 임시 폴더에 보존했습니다.


## 2026-09-11 후속: 0.8 고정 매핑

현재 설치본은 0.8.4-continuity이다. 물리 출력 교체 중 -1 연결을 확인하고, 물리 매핑 고정 + 논리 크기 변경 + 내부 시스템 미러 방식으로 변경했다. 사용자에게서 깜빡임이 많이 개선됐다는 피드백을 받았다. 전체 검정 없는 정지 상태 자동 전환, 입력 CLONE, 프로세스 사망 복구 시험의 범위와 미검증 항목은 [검은 깜빡임 조사](black-flash-investigation.md)를 따른다. 앞선 버전의 불가능 판정·미구현 설명과 현재 결과를 혼동하지 않는다.

0.8.3 사용자 확인: 순간 검정이 안 보이는 것 같음, 내부 스크롤과 버튼 정상, 펼침 시작의 외부 흐림 확인. 이 관찰 범위를 넘어 모든 조건의 무깜빡임을 보장하지 않는다.
