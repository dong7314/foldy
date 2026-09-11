# 내부 화면에 외부 홈 구성이 표시되는 원인 · 2026-09-11

0.13.1 사용 중 사용자가 내부 화면의 홈이 외부 화면 배치를 좌우로 넓힌 모습이라고 보고했다. 정상 OPENED 상태와 현재 앱의 고정 outer-primary 경로를 같은 물리 자세에서 비교해 재현했다. 앱 본체 수정이나 업데이트 설치는 아직 하지 않았다.

## 실기 비교

자료: `measurements/home-layout-v37/`. 화면 캡처는 로컬 진단 자료이며 사용자 개인 배경화면을 포함한다.

| 항목 | 정상 OPENED | 현재 경로 재현 |
| --- | --- | --- |
| 물리 접힘 상태 | OPENED (3) | OPENED (3) |
| 시스템 요청 상태 | override 없음 | CONCURRENT_OUTER_DEFAULT (5) |
| 홈 Activity bounds | 2448×1848 | 2448×1848 |
| 홈 density | 360 dpi | 360 dpi |
| 홈 configuration | `dt/m` | `dt/s` |
| 홈 표시 결과 | 내부용 배경·배치 | 외부용 배경·배치가 넓은 영역에 배치됨 |

`normal-inner-home.txt`와 `fixed-wide-home.txt`의 `CurrentConfiguration`에서 확인했다. 해상도나 밀도 차이가 없어도 화면 구분이 달라진다. 따라서 전체 이미지에 비균일 확대를 적용해서 생기는 픽셀 왜곡과 구분해야 한다. 홈이 외부용 구성을 선택한 뒤 넓은 창에 그리는 문제가 재현된 것이다.

`tools/HomeLayoutProbe.java`는 설치된 APK의 StableDisplay, NativeScene, PanelPower를 재사용하여 약 10초간 비교한 후 요청·크기를 원복한다. 시작 전 정상 OPENED, 기존 크기 override 없음, 잠금 해제를 검사한다. 별도 RecoveryGuard와 15초 프로세스 종료 제한이 있다. 삼성 홈의 데이터나 설정은 수정하지 않았다. `probe.txt`의 RESTORED 및 이후 `restored-state.txt`, `restored-home.txt`로 원복을 확인한다.

## 실제 펌웨어 코드

실기에서 이전에 확보한 `/system/framework/services.jar`의 dexdump를 읽었다.

- `FoldDisplayController.isInPrimaryDevice(DisplayInfo)`는 DisplayInfo.address의 물리 display ID와 mPrimaryPhysicalDisplayId를 비교한다.
- `DisplayContent.computeScreenConfiguration`은 위 결과에 따라 `Configuration.semDisplayDeviceType`에 0 또는 5를 기록한다.
- `DisplayContent.onRequestedOverrideConfigurationChanged`도 기본 display에 대해 같은 물리 주소 판정을 다시 수행한다.

현재 StableDisplay는 물리 패널 매핑을 외부로 유지하고 논리 크기만 변경한다. 그래서 넓은 크기가 전달돼도 삼성의 화면 구분은 외부로 남는다. 단순 해상도 재설정이나 일회성 Configuration 값 변경만으로 해결됐다고 볼 수 없다.

## 수정 방향과 현재 한계

내부용 홈 및 배경화면을 정상적으로 쓰려면 삼성 시스템의 내부 화면 구성이 활성화되도록 전환 경로를 보완해야 한다. 물리 매핑 변경은 과거 검은 깜빡임의 원인이었으므로 state 4/5 교환을 무조건 되살리는 것은 수정으로 채택하지 않았다. 실제 내부 구성 활성화와 양 패널 전환의 연속성을 함께 검증해야 한다. 이번 작업은 원인 확인까지이며 해결 완료로 취급하지 않는다.
