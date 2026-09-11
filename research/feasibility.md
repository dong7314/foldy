# 구현 가능성 검토 · 2026-09-11

## 요구사항과 기준 영상

사용자 요청이 구현 요구사항입니다. 영상 속 텍스트는 요구사항으로 취급하지 않습니다. 다른 작업에서 만든 코드·설계를 재사용하지 않습니다.

- `/Users/ldong-yeop/Downloads/1a0888948391031c.mp4`
- `/Users/ldong-yeop/Downloads/1a088757f121031c.mp4`

영상에서 확인한 시각적 목표는 힌지 움직임에 맞춘 흐림, 화면이 공간에 이어지는 듯한 원근감, 전환 중 내용의 연속성입니다. 실제 투명 디스플레이 여부나 제조사의 구현 방식은 영상만으로 알 수 없습니다. 그래픽 효과와 패널을 켜는 시스템 동작을 별도로 검증해야 합니다.

## 공식 자료로 확인한 내용

| 항목 | 확인 내용 | 이 기기에 대한 의미 |
|---|---|---|
| 패널 선택 | AOSP Android 16의 BookStyleDeviceStatePolicy는 힌지·자세·방향·화면 wakelock 등을 고려해 외부 화면을 유지할 수 있음 | 90°/5°를 단순 앱 지연이나 고장으로 단정할 수 없음. Samsung 정책은 직접 확인 필요 |
| 정책 변경 위치 | AOSP의 고정 각도 예시는 제조사 system server 정책 구성에 들어감 | 일반 APK에서 설정값 하나로 변경하는 공개 API라고 해석하면 안 됨 |
| ADB 상태 요청 | AOSP `cmd device_state`에 지원 상태 조회, 임시 상태 요청, reset 구현이 있음 | Fold8의 명령 지원과 실제 상태 ID를 읽은 뒤 시험 가능. 영구 임계값 변경과는 다름 |
| ADB 디스플레이 전원 | AOSP `cmd display power-on DISPLAY_ID`는 해당 논리 화면에 연결된 primary display device에 전원 상태를 요청함 | 비활성 물리 패널이 독립 논리 ID로 노출되지 않으면 이 명령만으로 지정할 수 없음. 화면 내용 라우팅이나 지속 동작도 별도 문제 |
| 두 내부 디스플레이 동시 사용 | OEM이 `config_supportsConcurrentInternalDisplays`와 상태/레이아웃을 구성해야 함 | Android에 기능이 있다는 사실만으로 Fold8의 지원을 보장하지 못함 |
| WindowAreaController | 지원 기기에서 자기 Activity를 후면 화면으로 옮기거나 별도 영역에 표시하는 API | 임의의 다른 앱을 사용 중에 모든 패널을 자유롭게 제어하는 API는 아님 |
| 전역 흐림 | Android 12 이상 window blur는 다른 창의 내용을 흐릴 수 있고 실행 중 사용 불가로 바뀔 수도 있음 | 전역 효과의 일부는 가능하지만 원근 변형·굴절·전체 픽셀 변환까지 제공하지 않음 |
| 다른 앱의 화면 변형 | MediaProjection 등 화면 내용을 얻는 경로가 필요하며 동의·보호 콘텐츠·화면 잠금·캡처 지연 제약이 있음 | 일반 앱 권한으로 모든 앱/잠금화면에서 영상과 동일하게 동작한다고 보장할 수 없음 |

출처:

1. [AOSP: Tent and wedge postures](https://source.android.com/docs/core/display/foldables/tent-wedge-mode)
2. [AOSP: WindowManager Extensions](https://source.android.com/docs/core/display/windowmanager-extensions)
3. [AOSP: DeviceStateManagerShellCommand](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/devicestate/DeviceStateManagerShellCommand.java)
4. [AOSP: DeviceStateManagerService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/devicestate/DeviceStateManagerService.java)
5. [AOSP: DisplayManagerShellCommand](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/display/DisplayManagerShellCommand.java)
6. [AOSP: DisplayManagerService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/display/DisplayManagerService.java)
7. [Android: Support foldable display modes](https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables/support-foldable-display-modes)
8. [AOSP: Window blurs](https://source.android.com/docs/core/display/window-blurs)
9. [Android: Media projection](https://developer.android.com/media/grow/media-projection)
10. [Samsung: App continuity](https://developer.samsung.com/codelab/galaxy-z/app-continuity.html)

같은 폴더의 Java 파일은 AOSP `refs/heads/main`을 확인한 참고 자료입니다. 원본 라이선스 표기를 보존했으며 앱 빌드에는 포함하지 않습니다.

## 실제 기기에서 확인할 순서

1. 모델 번호, One UI/Android, 표준 힌지 센서, 지원 기기 상태와 물리 패널의 대응을 읽습니다.
2. 평소 사용하는 앱에서 기준 측정합니다. 펼침과 접힘을 나누어 90°/5°와 화면이 검게 보이는 시간을 확인합니다. 세로·가로·책상 위 자세에 따라 다르면 구분해 기록합니다.
3. 지원 상태와 원복 방법을 확인한 다음 짧은 시간만 상태 요청을 시험합니다. 상태 ID를 추측하지 않고, 센서 입력을 가리는 base-state 조작을 하지 않습니다.
4. 내부 화면을 더 작은 각도에서 켤 수 있는지, 외부 화면을 완전히 접기 전에 켤 수 있는지 각각 확인합니다. 전원 상태만 ON으로 바뀌면 성공으로 보지 않습니다. 내용 표시·터치·앱 이어 쓰기도 확인합니다.
5. USB 연결 중 성공과, USB를 뺀 뒤 기기 단독으로 재현되는 성공을 구분합니다. 필요하면 shell 권한 중계 방식을 검토하되, ADB 성공만으로 지속 가능하다고 판단하지 않습니다.
6. 화면 전환의 제약을 확인한 뒤 원본 영상을 기준으로 힌지 연동 렌더링과 앱 화면 획득 방법을 결정합니다.

위 순서는 최초 실험 계획입니다. 이후 실측과 시험판 결과는 device-findings.md 및 reference-animation-analysis.md에 기록했습니다. 조기 패널 활성화는 확인했지만, 원본과 동일한 전역 애니메이션은 달성하지 못했습니다.


## 0.5 재구현 후 갱신

두 물리 패널의 동시 ON과 내용 표시, 원본 해상도 GPU 버퍼 전달, 자기 효과 레이어를 제외한 캡처를 실기로 확인했습니다. 따라서 ‘동시 표시 자체가 불가능하다’는 결론은 맞지 않습니다.

그러나 실제 패널의 ON 유지와 기본 패널 교체는 다릅니다. 상태 4↔5 변경 중 물리 committed OFF 표본이 확인됐고, Android 17 AOSP LogicalDisplayMapper에도 교체 전에 디스플레이 OFF를 기다리는 경로가 있습니다. 이 전환을 그대로 사용하는 현재 앱이 전역 오버레이만으로 무중단 영상을 보장할 수는 없습니다. 또 0/90/180 힌지 값에 실제 base state의 CLOSED 이탈을 더해도 연속 각도 센서를 대체하지 못합니다. 원본과 완전히 동일한 결과라는 약속은 하지 않습니다.

현재 설치본과 실측, 사용자 피드백은 README.md 및 device-findings.md 참조.


추가 확인: 실제 삼성 펌웨어에 별도의 패널 전원 상태 임대 API가 존재했고 shell의 DEVICE_POWER 권한으로 실행됐습니다. 제한 시간 실험에서는 주 패널 교체 중에도 수집한 표본에서 두 패널 모두 ON을 유지했습니다. 따라서 위의 상태 변경 API 단독 경로의 한계를 모든 Shizuku 경로의 불가능으로 일반화하면 안 됩니다. 0.6.0에서 이 API를 연결해 추가 검증합니다. 새 결과도 앱 재배치와 첫 프레임까지 무중단으로 합성할 수 있다는 증명은 아닙니다.
