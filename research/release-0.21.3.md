# Foldy 0.21.3 배포 파일 검증

2026-09-14, macOS에서 현재 작업 폴더의 소스를 배포용으로 빌드했다. 애니메이션과 아이콘은 기존 0.21.3과 같고, 별도 개인 키와 비디버그 빌드 설정을 적용했다. 사용자 요청 범위는 배포 파일 생성이다. Git commit·push·스토어 등록은 진행하지 않았다.

## 결과물

- 파일: `dist/foldy-0.21.3/foldy-0.21.3-release.apk`
- applicationId: `dev.poldy.lab`
- versionName / versionCode: `0.21.3` / `80`
- 크기: 5,304,632 bytes
- APK SHA-256: `4c80dd5c6eec217d74aa5e333b943cc86dbc13a589b7e06f5d67112771304234`
- 공개 서명 인증서 SHA-256: `106d60d946941e1d17a69fd88b25812bea01899dbf0a8523f385200e65a2cc04`

`release.json`은 현재 HEAD와 미커밋 변경 여부를 함께 기록한다. 빌드는 미커밋 작업을 포함하므로 HEAD만 체크아웃해서 같은 결과를 복원할 수 있다는 의미가 아니다. `SHA256SUMS`, `verification.txt`, `INSTALL-ko.md`를 APK와 함께 생성했다. 개인 키와 비밀번호는 포함하지 않는다.

## 확인한 항목

1. `:app:testDebugUnitTest`: 112개 통과, 실패·오류·건너뜀 0. 현재 AGP 설정에서는 공유 JVM 테스트가 debug 변형에만 있으며, 별도 `testReleaseUnitTest`가 실행된 것은 아니다.
2. `:app:lintRelease`: 오류 0, 경고 42. 경고가 없는 빌드는 아니다.
3. `:app:assembleRelease`: 성공. 최종 APK의 현대 APK 서명, 개발 인증서 제외, `debuggable`·`testOnly`·백업 비활성, INTERNET 권한 부재를 검사했다.
4. DEX 정의를 검사해 플랫폼 스텁과 시험용 클래스가 포함되지 않은 것을 확인했다. Shizuku·복구 프로세스의 필요한 진입점과 번들 폰트 라이선스는 포함된다. R8와 리소스 축소는 켜지 않았다.
5. 누락된 서명 설정으로 release 빌드를 시도하면 `validateReleaseSigning` 단계에서 실패한다. 이전 개발용 APK를 배포 검증기에 넣으면 개발 인증서 때문에 거부한다.
6. 개인 서명 폴더 권한 700, 키·설정 파일 권한 600. 키는 Git 밖에 있고, 저장소에도 키 파일 패턴을 제외했다. 공개 예시 설정 파일만 추적 가능하다.
7. 독립된 Android API 37.1 ARM64 가상 기기에서 이 APK를 설치했다. `MainActivity` 콜드 실행 결과 `Status: ok`, Foldy 설정 화면과 로고가 정상 표시됐다. 설치 패키지에 DEBUGGABLE 플래그가 없으며 `run-as`는 `package not debuggable`로 거부됐다. 해당 실행에서 AndroidRuntime 오류는 기록되지 않았다.

## 검증 범위와 후속

가상 기기 시험은 패키지 설치·앱 실행·비디버그 설정 확인이다. Samsung의 물리 패널 제어와 Shizuku 접힘 효과를 가상 기기에서 검증한 것은 아니다. 연결된 SM-F971N의 기존 개발용 0.21.3 설치와 실행 상태는 변경하지 않았다. 실제 접힘에 대한 기존 시험은 `research/privacy-closing-0.21.md`에 기록되어 있다.

개발용 앱과 배포용 앱의 서명이 다르므로 기존 앱 위에 바로 설치할 수 없다. 실기 교체 시에는 애니메이션을 정상 종료하고, Foldy 설정 초기화를 동반한 기존 앱 제거가 필요하다. 이번 작업에서는 제거하지 않았다. 이후 배포용 업데이트는 같은 개인 키를 보존하고 versionCode를 증가시킨다.

직접 설치용 APK부터 생성했으며 AAB는 아직 만들지 않았다. `tools/build_release.py --bundle`로 추가할 수 있지만 Play 등록·심사·서명 정책 검토는 별도다. 장시간 사용, 빠른 되접힘과 잠금이 겹치는 경계 사례, 남아 있는 미세 끊김은 이번 배포 포장 검사만으로 해결됐다고 판단하지 않는다.

원본 증거는 Git에서 제외한 `measurements/release/`의 `build-release.log`, `missing-signing.log`, `installed-package.txt`, `run-as.txt`, `runtime-log.txt`, `emulator-release.png`에 있다. 설치 절차와 서명 키 백업은 `RELEASE.md`에 기록했다.

## Git 보관 후속

배포 파일 생성 후 사용자가 commit·push를 요청했다. 위 APK와 공개 검증 자료를 해시 변경 없이 `artifacts/releases/foldy-0.21.3/`에 복사해 소스와 함께 보관한다. `release.json`의 소스 상태는 APK를 처음 빌드한 시점의 기록을 유지한다. 개인 서명 폴더와 개인 측정 원본은 포함하지 않는다.
