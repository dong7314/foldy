# 0.15.0: 물리 패널 전원 고정과 Apple 61프레임 곡선

2026-09-12, SM-F971N / Android 17에서 조사·구현. versionCode 41, Shizuku service version 41.

## 남은 검은 깜빡임의 원인과 수정

0.14.2 실기 시험에서 내부에 외부용 화면이 잠깐 보이는 문제는 사라졌지만, 사용자는 접기와 펼치기 모두에서 검은 깜빡임이 남았다고 확인했다. 화면 녹화 725프레임에는 전체 검정 프레임이 없었다. 반면 시스템 로그에서는 논리 display ID가 두 물리 패널 사이에서 교환될 때 `LogicalDisplayMapper`가 양쪽을 transition 상태로 만들고 임시 OFF를 요청했다. 기존 `IDisplayManager.setDisplayStateOverrideWithDisplayId(..., ON, ...)` 임대가 있어도 `SurfaceFlinger.setDisplayPowerMode`의 실제 OFF/ON 호출은 한 번에 약 140~184ms 걸렸다. 이는 앱 합성 프레임보다 아래에서 생긴 물리 출력 공백이므로 화면 녹화에 잡히지 않는 사용자 관찰과 일치한다.

`tools/SurfacePowerApiProbe.java`로 이 펌웨어의 runtime API를 확인했다. 각 물리 display token을 얻는 `SurfaceControl.getPhysicalDisplayToken(long)`과 전원을 직접 지정하는 `SurfaceControl.setDisplayPowerMode(IBinder, int)`를 shell 프로세스에서 호출할 수 있었다. `tools/SurfacePowerPinTrial.java`는 두 물리 패널에 `POWER_MODE_NORMAL`을 1ms 간격으로 재지정하면서 concurrent 상태 5→4→5를 요청했다.

- 5→4 전환: transition 시작 00:55:16.488, 새 layout 적용 00:55:16.503, 15ms.
- 4→5 전환: transition 시작 00:55:18.285, 새 layout 적용 00:55:18.303, 18ms.
- 두 전환 모두 mapper의 pending OFF 기록은 있었지만, 기존의 물리 `OFF -> ON` 및 140~184ms `setDisplayPowerMode` 호출이 발생하지 않았다.
- 시험 종료 뒤 CLOSED, 1248×1972, size override 없음, density override 360으로 복구했다.

앱의 `NativeScene.shield()`는 이제 불투명한 양쪽 버퍼와 물리 layer stack을 고정하는 동안 두 전원 thread도 함께 유지한다. 두 패널은 서로의 전원 호출에 막히지 않는다. `finishSwitch()`에서 반복 작업을 취소하고 정상 stack으로 원자적으로 복귀한다. `close()`는 전원 worker 종료를 최대 500ms 확인한 뒤 `PanelPower` 임대를 해제할 수 있게 해, 늦은 ON 호출이 종료 후 복구와 경쟁하지 않도록 했다.

앱에 통합한 0.15.0도 설치 후 6초 자동 왕복을 실행했다. 외부→내부는 mapper transition 29ms, 내부→외부는 39ms였고 `physical_power_pin_failed`, 물리 `OFF -> ON`, `SF.setDisplayPowerMode took` 기록은 0개였다. 두 generation 모두 native profile 준비와 output release를 완료했고 외부 기본 concurrent 상태로 돌아왔다. 로그는 `measurements/native-v41-auto-logcat.txt`에 보관했다. 이 결과는 긴 물리 정전 호출을 제거했다는 시스템 로그 증거다. 실제 경첩을 움직일 때 사용자가 보던 검은 깜빡임까지 사라졌는지는 육안으로 다시 확인한다.

## Apple 공식 장면의 프레임 추출

Apple iPhone Duo 제품 페이지의 “Take a closer look” 접기 조절 장면은 동영상이 아니라 Three.js glTF 애니메이션이다. 공개 장면의 `Slider` track은 2초, 30fps, 61개 표본이며 27개 회전 channel을 가진다. `tools/apple_duo_profile.py`가 glTF accessor와 binary buffer를 직접 읽어 다음 값을 프레임마다 보관한다.

- 움직이는 face의 0→180° 회전: 프레임마다 정확히 3°.
- hinge guide의 0→90° 회전: 프레임마다 1.5°.
- 접힘부 23개 deform bone의 open 자세 대비 quaternion 각거리와 RMS.
- 닫힘 RMS를 1, 펼침 RMS를 0으로 정규화한 crease와 release.
- 장면 상태의 inner screen brightness: closed 0.15, landing 0.25, open 1.

추출한 전체 프레임은 `research/apple-duo-slider-profile.json`에 기록했다. 원본 glTF SHA-256은 `30cab0c2102ecf050a5e07bdbbe2ef36d1edfc522779abfc3434296792843bb4c`, 추출 JSON은 `f40adb32f97ab28be788780361f652ed6af8d4a6b8a66055c8911a01ab9dc4d3`이다. 첫 4프레임에는 crease가 닫힘 방향으로 최대 1.4995% 더 움직이는 authored bounce가 있으며, 이후 연속적으로 풀린다.

`AppleDuoMotion`은 61개 release 표본을 그대로 보관하고 실제 힌지 각도 3°마다 한 프레임으로 대응시킨다. 중간 각도만 인접 프레임 사이에서 선형 보간한다. `FoldOptics`의 기존 임의 cubic 시간 곡선을 제거하고 이 값으로 외부 효과의 증가와 내부 효과의 감소를 결정한다. rigid face의 공간 투영은 공식 face 회전과 같은 실제 각도를 사용하며, inner brightness도 세 공식 상태 사이에서 각도에 따라 적용한다. 입력은 여전히 실측 HAL 각도이므로 멈춤과 역전에서 별도 timeline을 재시작하지 않는다.

장면의 `FadeThroughBlack` 0.38초 값은 gallery scene 교체용이고 Slider 접기 track 자체가 아니다. 이를 패널 전환에 적용하면 해결하려는 검은 공백을 다시 만들기 때문에 사용하지 않는다. 웹 3D 모델의 23개 실제 mesh bone을 휴대전화의 물리 경첩 위 2D overlay로 복제할 수는 없으므로, 앱은 그 본들의 프레임별 집계 곡선·face 회전·화면 밝기를 재현하고 기존 원본 화면 diffusion과 투영에 연결한다.

## 검증 자료

- 직접 전원 시험: `build/surface-power-pin-dex/result.txt`, `build/surface-power-pin-dex/system-log.txt`.
- API 목록: `tools/SurfacePowerApiProbe.java`.
- 재현 가능한 프레임 추출기: `tools/apple_duo_profile.py`.
- 전체 61프레임 수치: `research/apple-duo-slider-profile.json`.
- Java 단위 테스트 73개, Python 테스트 7개, Lint 오류 0 / 경고 29, debug APK 빌드 통과.
- 실제 Android GPU에서 내부·외부 각 0/1/2/3/6/12° 이탈 표본 12개 렌더링 성공. 결과는 `measurements/native-v41-renderer/`.
- APK: `artifacts/poldy-0.15.0-power-pin-apple-motion.apk`, SHA-256 `98dba5247262b74883ec78e19783dca2c0e75f37bd304f8213f5cbf7cf1cb7cf`.
