# 접힘 각도 접근 재검증 · 2026-09-11

> **후속 발견으로 접근 가능성 판단 수정:** 센서 직접 구독과 `dumpsys sensorservice`는 막혀 있지만, 현재 펌웨어의 `sensors-hal` logcat 출력에는 `folding_angle`과 `lid_angle_fusion`의 중간 각도 값이 나온다. ADB로 읽은 기록에서 1–180 사이 68개의 서로 다른 Folding Angle 값을 추출했다. 아래의 ‘연속 각도 입력을 확인하지 못했다’는 최초 조사 시점의 결과이며, 현재는 이 로그 경로의 실시간 지연·갱신 빈도를 검증 중이다. [후속 실험](hal-angle-log-path.md). 사용자 요청에 따라 Samsung 문의는 진행하지 않았으며 발송한 내용도 없다.

사용자는 0.11.2에서도 중간 구간의 효과가 정체되다가 완전 접힘·펼침에서 몰아서 진행된다고 보고했다. 이번에는 시각 효과를 수정하거나 새 APK를 설치하지 않고, 인터넷 공식 자료와 연결된 SM-F971N의 현재 펌웨어를 읽기 전용으로 조사했다.

판정: **연속적인 접힘 각도를 측정하는 방법 자체는 존재한다. 다만 이 기기의 현재 일반 앱·ADB·ADB로 실행한 Shizuku 권한으로 사용할 수 있는 연속 각도 입력은 확인하지 못했다. 한쪽 자이로의 회전을 확대하는 현재 구현은 임의의 잡는 자세에서 정확한 힌지 각도를 복원할 수 없다.** 모든 비공개 경로가 존재하지 않는다는 증명이나, 루팅하면 반드시 해결된다는 판정은 아니다.

## 실제 증상의 근거

현재 설치본 0.11.2의 로그에서 다음 끝점 보정을 확인했다. 실제 각도계로 잰 물리 수치가 아니라, 기기가 보고한 완전 접힘·펼침 상태를 기준으로 한 비교다.

| 휴대전화 시각 | 끝점 신호의 목표 | 보정 직전 추정값 | 표시 진행값 |
| --- | ---: | ---: | ---: |
| 15:51:45.085 | 펼침 180° | 53.59° | 53.89° |
| 15:51:48.285 | 접힘 0° | 157.37° | 155.36° |
| 15:56:11.085 | 펼침 180° | 69.57° | 62.27° |
| 15:56:15.485 | 접힘 0° | 161.08° | 159.71° |

`FoldService.onSensorChanged`는 주 자이로의 Y축 회전을 `MotionAngleEstimator`에 넣는다. `MotionAngleEstimator`는 펼침·접힘 보정 계수를 곱한 회전을 적분한다. 끝점에서 `anchorOptics`가 0° 또는 180°를 다시 목표로 지정한다. 큰 미추적 구간이 남으면 마지막에 그 구간을 따라잡는다. 효과 곡선이나 블러 경계만 바꿔서 해결할 수 없는 입력 문제다.

근거: [새 로그](../measurements/angle-research-2026-09-11/latest-fold-logcat.txt), [기계 판독 요약](../measurements/angle-research-2026-09-11/summary.json).

## 기기에서 재확인한 입력과 권한

| 경로 | 확인 결과 | 의미 |
| --- | --- | --- |
| `android.sensor.hinge_angle` · 타입 36 | 권한 불필요. 최근 50개 이벤트의 고유 값은 0, 90, 180뿐 | 현재 관찰 범위에서 연속 각도 입력이 아니다. 90이라는 값도 정확한 90° 통과 측정으로 취급하면 안 된다 |
| `com.samsung.sensor.folding_angle` · 타입 65686 | 센서가 존재하고 시스템 InputManager가 구독 중. `SSENSOR` 필요 | 실제 연속 각도 입력의 유력한 후보이나 정밀도·값의 의미를 직접 검증하지 못함 |
| 보조 가속도계·자이로 · 타입 65687–65690 | 주 센서와 별도로 존재. 모두 `SSENSOR` 필요 | 양쪽 회전 비교에 쓸 후보도 현재 일반 권한으로 접근 불가 |
| `SSENSOR` | `signature|privileged`, 선언 패키지 android. shell에 부여되지 않음 | 일반 권한 동의창이나 ADB 디버깅만으로 얻는 권한이 아님 |
| `dumpsys sensorservice` | 전용 Folding Angle의 50개 이벤트 전부 `[value masked]` | ADB로 센서 덤프를 반복 읽어 연속 각도를 얻는 방법도 현재 출력에서는 불가 |
| `/sys/class/sensors` | shell의 디렉터리 읽기에서 Permission denied | 현재 권한으로 확인한 sysfs 경로 사용 불가. 다른 모든 경로의 부재까지 증명하지 않음 |
| DeviceState·Input·Motion·SemContext·SemInputDeviceManager 진단 | 자세 상태와 설정은 있으나 사용할 연속 각도 값은 찾지 못함 | 화면 상태·회전·전환 임계점과 실제 힌지 각도는 구분해야 함 |

센서의 존재와 시스템의 구독 기록은 확인했지만, 보호된 데이터가 가려져 있어 전용 센서가 몇 도 간격·얼마의 지연·얼마의 오차로 동작하는지는 확인하지 못했다. 이번 조사에서 권한 변경, root, 부트로더 변경, 펌웨어 변경은 하지 않았다.

원본 증거: [센서 덤프](../measurements/angle-research-2026-09-11/sensorservice.txt), [권한 정의](../measurements/angle-research-2026-09-11/permissions.txt), [shell 권한](../measurements/angle-research-2026-09-11/shell-package.txt), [sysfs 결과](../measurements/angle-research-2026-09-11/sysfs-access.txt). 기존 [API 메타데이터 조사](../measurements/angle-revisit/api.txt)도 재검토했다.

## 공식 자료가 보장하는 범위

Android는 API 30부터 `TYPE_HINGE_ANGLE`을 제공한다. AOSP는 이를 기기 두 부분 사이의 각도를 도 단위로 나타내는 센서로 정의한다. 따라서 Android 전체에서 접힘 각도 측정이 불가능하다는 주장은 틀리다. 다만 Android 17 CDD 7.3.12의 최소 요구는 0–360° 범위의 서로 다른 값 최소 두 개다. 해당 타입이 존재한다는 사실만으로 1° 간격 등 연속 보고를 보장하지 않는다. [Android Sensor API](https://developer.android.com/reference/android/hardware/Sensor#TYPE_HINGE_ANGLE), [AOSP 센서 정의](https://source.android.com/docs/core/interaction/sensors/sensor-types#hinge_angle), [Android 17 CDD](https://source.android.com/docs/compatibility/17/android-17-cdd#7_3_12_hinge_angle_sensor).

Android의 `FoldingFeature`는 힌지 각도를 API로 노출하지 않는다고 공식 안내한다. Samsung의 Flex mode 개발 문서 역시 Jetpack WindowManager의 자세 상태와 접힘 영역을 안내한다. 따라서 WindowManager나 Flex mode 라이브러리로 바꾸는 것만으로 이번 기기의 연속 각도가 새로 제공되지는 않는다. [Android fold-aware 안내](https://developer.android.com/develop/adaptive-apps/guides/foldables/make-your-app-fold-aware), [Samsung Flex mode](https://developer.samsung.com/galaxy-z/flex-mode.html).

ADB로 실행한 Shizuku는 ADB 권한 범위를 사용하며, 공식 안내도 ADB 권한의 제한을 명시한다. 현재 shell에 없는 Samsung 전용 권한이 자동으로 생기는 것은 아니다. 시스템 서명·특권 권한은 일반 런타임 권한과 다르며, 특권 앱은 시스템 이미지와 허용 목록 조건도 따른다. [Shizuku 소개](https://shizuku.rikka.app/introduction/), [Shizuku 안내](https://shizuku.rikka.app/guide/setup/), [Android 권한 정의](https://developer.android.com/guide/topics/manifest/permission-element), [특권 권한 허용 목록](https://source.android.com/docs/core/permissions/perms-allowlist).

웹의 오래된 Screen Fold API 초안에서 각도를 다룬 예시가 검색되지만, 2026-05-20 W3C Device Posture API 초안의 공개 값은 `continuous`와 `folded` 두 자세다. 여기서 continuous는 연속 각도 숫자 스트림이라는 뜻이 아니다. 브라우저/WebView를 넣어 정밀 각도를 얻는 대안으로 볼 수 없다. [W3C Device Posture API](https://www.w3.org/TR/2026/CRD-device-posture-20260520/).

## 자이로 보정만으로 해결하지 못하는 이유

자이로는 센서가 붙은 부분의 회전을 측정한다. 힌지 각도는 두 부분 사이의 상대 회전이다. 한쪽을 같은 방향으로 고정한 채 반대쪽만 움직이는 여러 접힘 각도는, 고정된 쪽의 자이로만으로 구별되지 않는다. 기기 전체를 돌리는 움직임도 접힘 회전과 섞인다. 가속도계·중력·회전 벡터를 같은 한쪽에서 더 읽어 필터링해도, 반대쪽의 관측이 새로 생기지는 않는다. 이는 센서의 측정 정의에서 도출한 기하학적 한계다. [Android 운동 센서](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion).

현재 계수를 다시 보정하면 특정 잡는 방식의 오차를 줄일 여지는 있다. 3축 처리, 바이어스 보정, 신뢰도 계산도 유용하다. 그러나 이런 개선을 임의의 자세에서 정확한 실제 힌지 측정과 동일하게 설명하면 안 된다. 양쪽 센서의 상대 자세를 융합하는 방법은 가능성이 있지만, 양쪽 접근·축 정렬·동기화·드리프트 보정이 선행돼야 한다. 두 가속도계만 사용하는 단순 중력 비교도 힌지 축이 중력과 평행한 자세에서는 충분하지 않다.

## 다음 구현 판단 기준

1. 가장 직접적인 후보는 Samsung 전용 Folding Angle 이벤트다. 제조사가 허용하는 API 또는 시스템 서명/특권 통합 경로가 확보되면, 실제 접기·펴기·멈춤·반전에서 값의 연속성과 지연부터 검증해야 한다. 공개 개발 문서에서 일반 앱용 접근 경로는 찾지 못했다. 제조사에 접근 방법을 확인하는 일은 의미가 있지만, 문의를 보낸 상태는 아니다.
2. root·펌웨어 수정은 별도 시스템 개발의 검토 영역이다. 이 기기에서 가능한지 조사·실행하지 않았고 성공을 보장하지 않는다. root 자체만으로 정확한 데이터와 적절한 지연이 보장되는 것도 아니다.
3. 외부 각도 센서 또는 반대쪽의 별도 관측을 추가하면 연구용 측정은 설계할 수 있다. 현재 휴대전화만 사용하는 일반 앱의 해결책으로 검증된 것은 아니다.
4. 현재 권한을 유지하면 가능한 것은 오차가 있는 추정 효과다. 입력이 없을 때 시간으로 효과를 진행시키는 연출은 멈춤·반전을 정확하게 따라가는 요구와 다르다. 끝점의 갑작스러운 보정은 완화할 수 있어도, 그 사실을 정밀 각도 문제 해결로 처리하면 안 된다.

이번 결과는 0.11.2의 각도 추적 요구 미충족을 확인한 것이다. GPU 픽셀 검증·단위 테스트 통과는 각도 정확도의 검증이 아니며, 전체 애니메이션 개발 목표는 아직 달성되지 않았다.
