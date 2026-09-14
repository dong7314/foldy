# Foldy 작업 인계

2026-09-14 현재 작업 폴더는 `/Users/ldong-yeop/Desktop/private/foldy`다. 예전 `/poldy` 경로는 존재하지 않는다. 제품 이름은 Foldy이며 업데이트 호환을 위해 applicationId `dev.poldy.lab`은 유지한다.

## 사용자 요청과 범위

이전 보안 감사에서 실행 중 보완할 부분과 접을 때 매끄럽지 않은 현상을 수정한 뒤, 사용자가 별도 배포 버전 파일 생성을 요청했다. 0.21.3 배포용 서명 APK를 생성했고, 이후 현재 작업을 commit·push해 달라는 요청을 받았다. 대상은 기존 `origin/main`이며, 소스·검증 도구·문서와 공개 배포 파일을 포함한다. 스토어 등록과 회전 정책 재설계는 이번 범위가 아니다.

## 현재 상태

0.21.3 / versionCode 80을 연결된 SM-F971N에 설치하고 설치 APK 해시 일치를 확인했다. 보안 창 감지, 효과 상위 레이어 숨김, 캐시 폐기, 일반 화면 복귀 시 재개, privileged Binder UID 검사, 대기 중 캡처 감소를 추가했다. 대상 화면 준비 중에는 효과가 구워진 이미지 대신 원본 이미지에 현재 각도 효과를 매 프레임 적용한다. 준비·일시 정지 중에도 앱에서 끌 수 있다.

112개 단위 테스트, lint 오류 0, APK 빌드, 실제 GPU 합성 검사와 보안 플래그 전환 시험을 마쳤다. 별도 테스트 앱은 제거했다. 0.21.1에서 사용자는 효과가 한꺼번에 움직이는 현상이 많이 줄었다고 확인했다. 0.21.2는 같은 렌더러를 유지하고 잠금 해제 직후 첫 프레임 전 접힘 요청을 보류하는 안정성 보완을 추가했다. 상세 결과와 한계는 `research/privacy-closing-0.21.md`에 있다. 완전한 무깜빡임·무끊김이나 보안 위험 0으로 보고하지 않는다.

## 이어서 할 일

1. 0.21.1에서 접힘 체감 개선을 확인받았다. `measurements/privacy-closing-0.21/after-folds-0.21.1.log`를 후속 비교 기준으로 쓴다. 0.21.0은 ColorFade 오감지로 정지한 버전이므로 정상 기준으로 삼지 않는다.
2. 내부 패널 GPU 합성의 p95가 13.8ms였으므로, 실제 제품 프레임 간격과 캡처 부하를 함께 확인한 뒤 추가 최적화를 결정한다. 이 값은 개별 합성 벤치마크이며 제품 FPS가 아니다.
3. 잠금·해제와 빠른 되접힘이 겹치는 회귀, 보호 앱에서 펼친 상태로 복귀하는 회귀를 넓힌다. 현재 시스템 보호 신호가 없는 앱의 민감한 내용을 자동 분류하지는 않으므로 사용자 제외 목록은 후속이다.
4. 전용 서명·비디버그 APK 생성과 검증은 완료했다. 실기 개발용 앱은 서명이 다르므로 자동 교체하지 않았다. 사용자가 교체를 요청하면 애니메이션을 정상 종료하고, 제거에 따른 Foldy 설정 초기화를 알린 뒤 배포용 설치를 진행한다. Git push는 추가 요청 범위에 포함됐고, 스토어 등록은 별도 요청에 따른다.

## 작업 환경

- ADB: `/Users/ldong-yeop/Library/Android/sdk/platform-tools/adb`, serial `R5KL8036E1D`.
- SDK 36, Java 경로 `/Applications/Android Studio.app/Contents/jbr/Contents/Home`.
- `JAVA_HOME`을 위 JDK 경로로 지정하고 `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --console=plain`으로 확인한다.
- 사용자의 density 360과 회전 선호를 임의 초기화하지 않는다.
- main에 이전 세션의 미커밋 변경이 많이 있다. 이번 변경 외의 작업을 되돌리지 않는다.
- 상세 구현: `FoldService`, `NativePanel`, `NativeScene`, `DisplayControl`, `WindowPrivacyObserver`, `WindowPrivacyPolicy`, `ControlCaller`.
- `app/src/platformStubs`는 compileOnly다. Android 클래스 대체물을 APK에 넣지 않는다.

## 아이콘 수정 (0.21.3)

사용자 요청에 따라 바깥쪽 네 모서리만 약하게 둥글게 수정했다. `ic_poldy.xml`의 실행 아이콘·스플래시와 `SetupIcon.BRAND`의 설정 화면 로고에 적용했다. 기존 색·비율·가운데 모서리는 유지한다. 아이콘 시각 확인과 `:app:assembleDebug`를 통과했다. 이전 112개 단위 테스트 기록은 0.21.2의 동작 코드 기준이며, 이번 아이콘 수정에는 별도 테스트를 추가하지 않았다. APK는 `artifacts/foldy-0.21.3-rounded-icon.apk`, 미리보기는 `measurements/icon-rounded/foldy-icon.svg.png`다. commit·push는 하지 않았다.

## 배포 파일 생성 (0.21.3)

- `dist/foldy-0.21.3/foldy-0.21.3-release.apk`와 공개 서명 검증 결과·SHA256SUMS·설치 안내를 생성했다. 사용자 회신 없이 우선 직접 설치용 APK로 진행했으며 AAB는 생성하지 않았다. 필요하면 `python3 tools/build_release.py --bundle`을 사용한다.
- 개인 키와 비밀번호 설정은 Git 밖의 `/Users/ldong-yeop/.config/foldy/signing/`에만 있다. 폴더 700, 파일 600이다. 내용을 로그나 문서에 복사하지 않는다. 다른 Mac으로 이동할 때 소스와 별개로 안전하게 이전·백업해야 한다. 기존 키가 있는데 새 키를 생성하지 않는다.
- `tools/build_release.py`가 JVM 테스트·release lint·서명 빌드 후 최종 APK의 비디버그 설정, 서명, 시험 클래스와 플랫폼 스텁 제외, 제어 진입점·폰트 라이선스를 검사한다. 서명 누락 실패와 개발용 APK 거부도 확인했다. Shizuku/복구 진입점 보존을 위해 R8는 이번 빌드에서 켜지 않았다.
- 112개 JVM 테스트 통과, release lint 오류 0·기존 경고 42. 독립된 가상 기기에서 배포 APK 설치·실행 및 run-as 차단을 확인했다. 실제 휴대전화의 설치본은 계속 개발용 0.21.3 / code 80이다. 이번 release 서명 APK로 실기 애니메이션을 시험한 것은 아니다.
- 자세한 안내는 `RELEASE.md`, 증거와 한계는 `research/release-0.21.3.md`에 기록한다. 다음 업데이트는 같은 키를 보존하고 versionCode를 증가시킨다.

## Git 반영 범위

사용자의 commit·push 요청에 따라 0.18.5 이후 누적된 앱·시험·문서 변경과 0.21.3 배포 설정을 한 변경 묶음으로 정리한다. 검증된 배포 APK와 공개 체크섬·서명 검사·설치 안내는 `artifacts/releases/foldy-0.21.3/`에 보관했다. 자동 생성 폴더 `dist/`, 개인 측정 폴더 `measurements/`, 개인 서명 파일은 계속 제외한다. 이 APK의 `release.json`에는 커밋 전 빌드 당시의 HEAD와 미커밋 여부가 기록되어 있다.
