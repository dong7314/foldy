# 검은 깜빡임 조사 · 2026-09-11

현재 설치본은 **0.8.4-continuity**. 사용자는 0.8.3에서 순간 검정이 보이지 않는 것 같고, 내부 스크롤·버튼과 펼침 시작의 외부 흐림이 정상이라고 확인했다. 검증한 조건의 결과이며 모든 앱·각도·회전에서 완전 동일하다는 보장은 아니다. 아래에 0.7.1 원인 조사와 0.8 전환 경로를 구분해 기록한다.

## 확인된 원인

- `measurements/native-v16-run/inner-test.mp4`의 553프레임을 분석하고 실제 이미지를 확인했다. 검은 프레임의 PTS부터 다음 PTS까지 184.2ms와 132.3ms 구간이 있다. 녹화 영상의 길이이며 물리 OLED의 발광 시간을 직접 측정한 것은 아니다.
- 같은 시각 `display-system.txt`에서 `DisplayDevice`가 물리 패널의 layer stack을 **-1**로 설정한다. `LogicalDisplayMapper`에는 `state OFF, committedState ON`이 같이 나온다. 전원을 유지해도 출력할 화면은 비워진다.
- 실제 services.jar 디컴파일에서 DisplayDeviceInfo.state가 OFF이면 -1을 setDisplayLayerStack에 전달하는 분기를 확인했다 (`DisplayManagerService$$ExternalSyntheticLambda15.accept`, 오프셋 00d2~00f1).
- 0.7.1의 표시 완료 콜백은 정상 작동한다. 기록 16회에서 8~26ms, 중앙값 12.5ms. 대상 프레임 준비는 208~268ms, 중앙값 244ms. 이 성공이 이후 시스템의 출력 비움까지 막지는 못한다.

## 출력 대상 반복 유지 실험: 채택하지 않음

`tools/CompositorHoldTrial.java`는 색상 패턴만 표시한다. 실제 앱 내용을 읽지 않는다. `pin` 모드는 2ms 주기로 물리 출력과 자체 레이어를 같은 transaction에서 다시 연결한다. `tools/run_compositor_trial.sh`가 9초 복구 감시자를 별도로 둔다.

- `measurements/compositor-hold-trial/baseline-4.mp4`: 전환 중 약 86.0ms·41.4ms의 전체 검정 구간.
- `pin-4.mp4`: 해당 전체 검정 구간은 관측되지 않았다. 그러나 **1.105644초 프레임에서 외부용 주황 패턴이 내부 영상 왼쪽에 잘못 나타나고 오른쪽은 검정**이다 (`pin-worst/001.106.png`). 경쟁 상태가 남으므로 앱에 통합하지 않았다.
- 두 영상 약 4.45초 이후의 검정은 실험 종료·기본 상태 복원 구간이다. 전환 개선 결과와 분리해야 한다.
- 정상 실험 후 기본 CLOSED, 전원 유지 토큰 0을 확인했다.

## 비정상 종료 시 원복 주의

초기 standalone 그래픽 실험에서 Typeface 초기화 assertion으로 native 프로세스가 종료됐다. **사망한 시험 PID의 state 5 요청이 남았다.** 이 펌웨어에서 Binder 사망만으로 원복된다는 이전 설명은 보장되지 않는다.

남은 요청이 시험 PID임을 dumpsys로 확인한 뒤, 실제 base state 0을 shell에서 잠시 요청하고 shell 요청을 reset해 제거했다. `cmd device_state state reset`만으로 다른 PID의 잔여 요청이 제거되지는 않았다. 이후 기본 CLOSED와 전원 토큰 0, 시험 레이어 제거를 확인했다.

`run_compositor_trial.sh`는 현재 요청 PID가 자기 시험 PID와 같을 때에만 실제 base state를 잠시 요청하고 reset한다. 초기 실패 로그는 baseline/pin, baseline-2/pin-2에 남아 있다. baseline-3/pin-3은 실행 성공했지만 내부 화면이 꺼져 있을 때 녹화를 시작해 UNASSIGNED_LAYER_STACK으로 영상이 비었다. 분석에 사용하는 정상 녹화는 -4 파일이다.

## 현재 검토 중인 대안

물리 패널 매핑은 concurrent outer state 5에 고정하고, 논리 앱 화면 크기·고해상도 캡처·보조 화면 터치 전달을 바꾸는 방법을 검토 중이다. 0.8 시험판에 구현했다. 정지 상태 자동 전환의 전체 검정 제거, 고해상도 표시, 시스템 입력 복제까지 확인했으나 실제 접힘의 완전 제거는 아직 주장하지 않는다.

실기에서 확인한 API:

- `IWindowManager.mirrorDisplay(0, out SurfaceControl)`: READ_FRAME_BUFFER 필요. 성공.
- 받은 mirror를 표시하지 않는 별도 layer stack에 놓고 `ScreenCaptureInternal.captureLayers(LayerCaptureArgs)`로 캡처하면 **1248×1972 GPU 버퍼**를 얻었다 (`tools/LogicalCaptureProbe.java`). secure/protected policy는 REDACT(0). mirror 생성 직후에는 null이었고, 표시 속성 transaction 후 100ms 대기하면 성공했다. 물리 출력에 mirror를 노출하지 않는다.
- `IWindowManager.setForcedDisplaySizeDensityWithInfo(MultiResolutionChangeRequestInfo)`와 Builder의 setWidth/setHeight/setDensity/**setSaveToSettings(false)**가 존재한다. 실제 firmware 코드에서 WRITE_SECURE_SETTINGS 검사와 save flag 전달 확인. 비저장 크기 변경과 원복, 2448×1848 논리 버퍼를 실기 확인했다.
- `IInputManager.injectInputEvent(InputEvent, int)`가 존재한다. 0.8.0~0.8.2에 임시 전달 코드를 넣었으나 실제 입력은 시스템 미러의 CLONE 경로로 도달하는 것을 확인했다. 0.8.3에서 이벤트 재주입과 입력 Presentation을 제거한 뒤에도 진단 버튼 탭이 정상 도달했다.

다음 검증은 실제 접힘의 남은 검정, 홈 외 앱의 전환, 입력·회전·다른 앱의 재배치 지연이다. 출력 대상 반복 유지 실험에서 발견한 잘림을 재도입하지 않아야 한다.

추가 관찰: 홈 화면에서 자동 진단을 실행한 일부 기록에서는 capture 콜백은 계속 오는데 ValueAnimator의 handoff 종료가 오지 않았다. 0.8은 Handler와 단조 시간에 기반한 FrameTween으로 교체했고 자동 전환 종료를 확인했다. 원인을 특정했다고 주장하지 않는다.

## 0.8 고정 매핑 경로와 증거

- `StableDisplay`: 물리 매핑은 상태 5 그대로 두고 논리 앱 크기만 1248×1972 ↔ 2448×1848로 바꾼다. Samsung MultiResolutionChangeRequestInfo는 save=false. 외부 물리 투영을 원래 크기로 유지하는 2ms 작업은 전환 주변 800ms에 한정한다. 레이어 스택을 반복 변경했던 실패 실험과 다르다.
- `measurements/logical-size-trial/projection`: 368프레임. 0.5~3.8초의 생성 패턴에서 dark_fraction=0, 첫 프레임 대비 평균 밝기 차이 최대 약 0.407/255. 패턴 검증이며 실제 앱/물리 발광 보장이 아니다.
- `live-parent/inner.png`: 논리 앱 화면을 내부 2448×1848로 실시간 표시. IWM.mirrorDisplay가 반환한 MirrorRoot를 단독으로 root에 두면 보이지 않았고, shell이 만든 root SurfaceControl 아래에 reparent하면 정상 표시됐다.
- `NativeScene`의 두 효과 패널은 고정 물리 크기. 별도의 내부 liveParent 아래에 시스템 미러가 있다. 보이지 않는 captureParent 아래의 별도 미러에서 captureLayers로 원본 버퍼를 얻는다. 0.8.1에서 captureParent 크기를 최대 논리 영역을 포함하도록 넓혔다. 이 변경이 이전 검정 이미지의 원인이었다고 단정할 증거는 없다.
- `measurements/native-v18-run`: 정지 상태 자동 왕복 전환, 내부 198프레임·외부 169프레임. 중앙 80% 표본에서 전체 검정 프레임 없음. 최소 평균 휘도 약 37.30 / 35.33. `inner-during.png`에서 실제 앱이 전체 내부 해상도로 표시된다.
- `measurements/native-v17-run`: 사용자의 실제 움직임과 자동 진단이 겹친 녹화. 내부 316·외부 483프레임에 전체 검정은 잡히지 않았지만, 같은 실행의 `inner-during.png`는 모든 픽셀이 0인 검정이다. 녹화가 검정을 빠뜨리거나 동일 프레임을 유지할 가능성을 배제할 수 없으며, 물리 깜빡임 제거를 확정하지 않는다. 사용자 관찰은 ‘많이 개선된 것 같다’이다.
- `measurements/native-v19-run/input-wide.txt`의 InputDispatcher에 내부 displayId=1의 실제 앱 입력 창이 **CLONE**으로 나타난다. 동일 원본 InputChannel 토큰을 사용한다. `input -d 1`의 탭으로 앱 진단 카운터 1회 증가를 확인했고, Poldy의 별도 forward 로그는 발생하지 않았다.
- `measurements/native-v20-run/touch-log.txt`: 입력 재주입 코드와 Presentation을 제거한 뒤에도 13:19:03.498에 진단 탭 도달. 현재 경로는 시스템 입력 복제다. ADB로 생성한 이벤트 검증이며 손가락의 멀티터치·IME·회전은 별도 확인이 필요하다.
- `RecoveryGuard`: Shizuku 제어 프로세스와 다른 app_process에서 실행. 파이프 EOF 또는 6초 heartbeat 만료 시 자기 PID 소유 요청만 기본 상태로 돌리고, 자기 시험 크기만 원복한다. `native-v19-run/guard-result.json`에서 제어 PID를 확인한 후 SIGKILL했을 때 약 0.742초 내 CLOSED, 크기 override 없음. 보호 전원 토큰도 Binder 수명/시간 제한으로 해제된다. 정상 종료 시 복구 감시자 종료도 확인했다.

현재 제약: 완전히 접힌 기본 세로 상태에서 시작한다. 사용자 화면 크기 override가 있으면 중단한다. 회전과 전체 앱의 재배치 특성은 아직 충분히 검증하지 않았다. 서로 다른 두 앱 배치를 동시에 실행하는 기능은 아니다. 0.8.3까지 반대 화면은 이전 비율의 캐시를 보존했으나 앱이 바뀌어도 이전 앱이 남는 문제가 있어, 0.8.4부터 전환이 끝나면 현재 앱으로 갱신한다. 전환 도중에만 직전 프레임을 유지한다. 전환 전 커튼 표시 완료, 최소 280ms 이후 목표 크기 2프레임, 120ms 교체, 160ms 선명도 복귀 순서이며 앱 자체의 완료 신호를 받은 것은 아니다.

## 사용자 확인과 0.8.4 마무리

사용자 0.8.3 답변: 순간 검정이 보이지 않는 것 같음, 내부 스크롤·버튼 정상, 펼침 시작부터 외부 흐림 존재. 다른 앱의 실제 사용에 대한 사용자 관찰이다. 0.8.3 검증 APK는 `artifacts/poldy-0.8.3-verified.apk`에 보존했다.

0.8.4는 이전 앱 캐시를 영구 보존하지 않도록 바꾸고, 방향을 다시 바꿀 때 준비 중인 최신 버퍼 대신 이미 덮어 둔 직전 화면을 보존한다. 외부 화면의 흐림은 180ms에 걸쳐 들어온다. 물리 매핑·전원·크기 전환·시스템 입력 경로는 0.8.3과 같다. Gradle 빌드·14개 Java 테스트·Lint 오류 0을 확인했다.

0.8.3 설정 앱 비교 실행 `measurements/native-v20-settings-run`: 내부 926·외부 712프레임, 전체 검정 표본 없음. 실제 사용자의 움직임이 자동 요청과 겹쳤으므로 정지 상태 전환의 순수 대조 실험으로 취급하지 않는다. dark_fraction이 약 0.60~0.78까지 올라간 프레임은 어두운 테마의 실제 앱 콘텐츠가 있고 전체 검정과 다르다. 원본 일부 프레임을 이미지로 검토했다.

## 원본 영상

총 141개 표본으로 시작 구간을 보강했다. 실제 약 2.073초는 대체로 선명하고 2.140초부터 외부 오른쪽 흐림이 확인된다. 내부 왼쪽과 외부 오른쪽에 다른 공간 마스크가 필요하며, 펼침 끝의 약 0.13~0.20초 선명도 복귀와 닫기 끝을 동일한 커브라고 단정하지 않는다. 자세한 내용은 `reference-animation-analysis.md`.

## 공식 근거

- [AOSP LogicalDisplay](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/display/LogicalDisplay.java): BLANK_LAYER_STACK과 configureDisplayLocked.
- [AOSP LayerStack filter](https://android.googlesource.com/platform/frameworks/native/+/6f3e1c008c/libs/ui/include/ui/LayerStack.h): INVALID_LAYER_STACK의 레이어는 출력에서 제외.
- [SurfaceControl.Transaction](https://developer.android.com/reference/android/view/SurfaceControl.Transaction): completed listener는 자기 transaction의 표시 완료 신호.

0.8.4 자동 왕복 전환 검증: 내부 806프레임·외부 718프레임에서 중앙 표본의 전체 검정 프레임이 없었다. 원본 해상도 정지 캡처로 설정 앱의 전체 내부 화면 표시를 확인했다. 물리 접힘 사용자 확인은 0.8.3 결과를 근거로 구분한다.
