# Poldy

Poldy는 폴더블 휴대전화가 접히거나 펼쳐질 때 외부 화면과 내부 화면 사이의 전환을 자연스럽게 이어 주는 Android 앱입니다. 화면 구성이 바뀌는 짧은 시간 동안 두 패널을 미리 준비하고, 현재 화면을 각 패널의 원본 해상도로 유지하면서 실제 경첩 각도에 맞춘 흐림과 명암 효과를 적용합니다.

현재 버전은 **0.16.1 (versionCode 44)**이며 Samsung SM-F971N과 Android 17에서 개발하고 실기 검증했습니다. 사용자는 이 버전의 실제 접기·펼치기에서 검은 깜빡임과 잘못된 외부 화면 표시가 사라진 것을 확인했습니다.

## 주요 기능

- 외부 1248×1972, 내부 2448×1848 패널을 각각 원본 해상도로 준비합니다.
- 화면 전환 전에 현재 패널의 프레임을 유지하고, 대상 앱이 새 화면 크기로 배치된 뒤 시스템 출력으로 복귀합니다.
- Samsung HAL에서 읽은 1–179° 경첩 각도를 보간해 효과가 손의 움직임을 따라가도록 합니다.
- 두 물리 패널의 전원과 밝기를 전환 중 유지해 논리 디스플레이 교체에서 발생하던 검은 깜빡임을 막습니다.
- Apple iPhone Duo 제품 뷰어의 Wipe 재질을 분석한 내부·외부 방향별 확산, 명암, 화면 밝기 곡선을 적용합니다.
- 화면 비율이 다를 때 내용을 자르거나 늘이지 않고 패널 안에 맞춥니다.
- 화면 잠금, 화면 공유 종료, 앱의 중지 동작에서 화면 상태와 임시 크기를 원래 값으로 복구합니다.

## 동작 방식

Poldy는 Android 화면 공유 동의를 실행 권한으로 사용합니다. 실제 프레임은 Shizuku 사용자 서비스를 통해 시스템 화면에서 GPU 버퍼로 받고, Poldy 자체 레이어는 다음 캡처에서 제외합니다. 화면 전환 동안 `SurfaceControl` 레이어로 양쪽 패널의 마지막 정상 프레임을 유지한 뒤 대상 해상도의 새 프레임이 준비되면 실제 앱 화면을 다시 드러냅니다.

렌더러는 웹용 3D 기기의 원근을 화면에 다시 투영하지 않습니다. 물리 패널 자체가 이미 접히므로 소프트웨어 원근을 중복 적용하면 외부 화면이 찌그러지고 검은 쐐기가 생깁니다. 현재 구현은 실제 패널 위에 Wipe 재질의 확산과 명암만 합성합니다.

핵심 흐름은 다음과 같습니다.

```text
HAL 경첩 각도
  → 각도 보간
  → 양쪽 패널 프레임 유지
  → 대상 디스플레이 프로필 전환
  → 대상 원본 해상도 프레임 확인
  → Apple Wipe 기반 GPU 합성
  → 시스템 출력 복귀
```

## 요구 사항

- Samsung SM-F971N
- Android 17 기반의 현재 검증 펌웨어
- Shizuku 13.6 이상
- 다른 앱 위에 표시 권한
- 전체 화면 공유 동의
- 빌드 시 Android SDK 36과 JDK 17

루팅, 부트로더 해제, 펌웨어 수정은 필요하지 않습니다. 시스템 내부 API와 기기별 디스플레이 구성을 사용하므로 다른 모델이나 펌웨어에서는 자동 제어를 활성화하지 않습니다.

## 사용 방법

1. Shizuku를 실행합니다.
2. Poldy에서 **화면 제어 연결**을 누르고 Shizuku 접근을 허용합니다.
3. **다른 앱 위에 표시 허용**을 누르고 Poldy를 허용합니다.
4. 휴대전화를 완전히 접은 상태에서 **애니메이션 시작**을 누릅니다.
5. Android 화면 공유 창에서 **전체 화면 공유**를 선택합니다.
6. 다른 앱을 사용하면서 휴대전화를 접거나 펼칩니다.
7. Poldy 화면이나 알림에서 **중지**를 누르면 원래 화면 상태로 복구됩니다.

Shizuku는 일반적으로 휴대전화를 재부팅한 뒤 다시 시작해야 합니다. 설정 방법은 [Shizuku 공식 안내](https://shizuku.rikka.app/guide/setup/)를 참고하세요.

## 빌드와 검증

Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python -m unittest discover -s tools -p 'test_*.py'
```

macOS 또는 Linux:

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python3 -m unittest discover -s tools -p 'test_*.py'
```

빌드 결과는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다. `src/test`는 상태 전환, 프레임 소유권, 각도와 광학 곡선을 검증하며 `src/androidTest`는 실제 Android GPU에서 렌더 경계와 성능을 검사합니다. 이 검증 코드는 배포 APK에 포함되지 않습니다.

## 프로젝트 구조

- `app/src/main`: 앱 UI, 화면 공유 서비스, Shizuku 디스플레이 제어, GPU 렌더러
- `app/src/test`: JVM 단위 테스트
- `app/src/androidTest`: Android GPU 계측 테스트
- `research`: 기기 동작, 깜빡임 원인, 참고 애니메이션 분석 자료
- `tools`: ADB 수집, 영상 분석, 기기별 재현과 검증 도구
- `artifacts`: 실기 검증에 사용한 버전별 APK와 체크섬

구현 근거는 [검은 깜빡임 조사](research/black-flash-investigation.md), [네이티브 화면 인계](research/native-handoff-0.14.2.md), [전원·밝기 유지와 공식 모션](research/power-pin-apple-motion-0.15.md), [Apple Wipe 재질 분석](research/apple-wipe-material-0.16.md)에 기록했습니다. 공식 제품 뷰어에서 추출한 61개 각도 표본은 [apple-duo-slider-profile.json](research/apple-duo-slider-profile.json)에 보관합니다.

## 개인정보와 제한 사항

화면 프레임은 기기 메모리에서만 처리하며 저장하거나 전송하지 않습니다. 앱에는 네트워크 권한이 없습니다. 보호 콘텐츠와 보안 콘텐츠는 Android의 캡처 정책에 따라 가려집니다.

현재 구현은 한 앱의 화면을 내부·외부 패널에 이어 보여 줍니다. 두 패널에서 서로 다른 앱을 동시에 실행하는 기능은 제공하지 않습니다. 화면 회전, 멀티 윈도우, 펌웨어 변경에 따른 시스템 내부 API 차이는 추가 검증이 필요합니다.
