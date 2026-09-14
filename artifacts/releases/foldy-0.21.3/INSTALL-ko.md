# Foldy 배포 파일

`foldy-0.21.3-release.apk`는 현재 기능을 배포용 개인 키로 서명하고 디버깅을 끈 직접 설치용 빌드다. 스토어 공개나 Git push는 파일 생성과 별도로 진행한다. 현재 애니메이션은 Samsung **SM-F971N / Android 17**에서만 검증·허용한다. 다른 기기에서는 미리보기를 볼 수 있지만 실제 화면 제어는 허용하지 않는다.

이번 배포 APK는 서명·패키징 검사와 가상 기기의 설치·화면 실행 확인을 마쳤다. 실제 접힘 동작에 대한 기존 검증은 같은 소스의 개발용 빌드 기준이며, 휴대전화를 이 배포 서명 APK로 교체한 시험은 아직 진행하지 않았다.

## 설치

1. 지원 기기에 APK를 옮겨 설치한다. APK를 여는 앱에서 요청하는 출처의 설치 권한을 허용한다.
2. [공식 Shizuku 안내](https://shizuku.rikka.app/guide/setup/)에 따라 Shizuku를 설치·시작한다.
3. Foldy에서 화면 제어 연결을 허용하고 애니메이션을 켠다. 재부팅 후에는 Shizuku를 다시 시작해야 할 수 있다.

**기존 개발용 Foldy와 배포용 Foldy는 서명이 다르므로 바로 덮어설치할 수 없다.** 개발용 앱을 배포용 앱으로 교체하려면 먼저 기존 앱에서 애니메이션을 끄고 앱을 제거해야 한다. 제거하면 Foldy의 설정과 Shizuku 접근 연결이 초기화된다. 이 작업은 배포 빌드 스크립트가 자동으로 하지 않는다. 이후 같은 배포 키로 서명한 높은 versionCode의 APK는 배포용 앱을 업데이트할 수 있다. Android의 [앱 서명과 업데이트 설명](https://developer.android.com/studio/publish/app-signing)을 참고한다.

## 실행 중 보호와 제한

잠금·보안 창에서는 효과를 중단하고 보관 화면을 폐기한다. HIDE_OVERLAY_WINDOWS 권한이 있는 앱은 앱 전체에서 효과가 쉬어갈 수 있다. 보호 신호를 제공하지 않는 앱의 민감한 내용을 자동으로 분류하는 것은 아니다. 일반 화면은 애니메이션을 위해 GPU 메모리에서 처리하며, 제품 코드에는 이미지 파일 저장·전송 기능이 없다. 앱 자체에는 INTERNET 권한이 없지만 Shizuku 제어 프로세스는 더 넓은 shell 권한을 사용한다.

기기·펌웨어 내부 API에 의존한다. 미세 끊김과 잠금·빠른 되접힘이 겹치는 상황의 장시간 검증은 남아 있으며, 배포 빌드라는 이름이 모든 기기의 동작이나 무결함을 보증하지 않는다.

## 다음 빌드 만들기

JDK와 Android SDK 36이 필요하다. 기본 서명 설정 경로는 `~/.config/foldy/signing/release.properties`다. 다른 서명 파일을 사용할 때는 `FOLDY_SIGNING_PROPERTIES`에 파일의 절대 경로를 지정한다. 형식은 저장소의 `release-signing.properties.example`을 참고한다. 비밀번호를 명령행 인수나 Git 파일에 넣지 않는다.

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
python3 tools/build_release.py
```

AAB도 필요하면 `python3 tools/build_release.py --bundle`을 실행한다. AAB 생성과 Play Console 등록·앱 심사 통과는 별개다. Play App Signing을 설정할 때 직접 배포 APK와의 서명 호환 여부를 결정해야 한다.

결과는 `dist/foldy-0.21.3/`에 생성된다. `SHA256SUMS`로 파일 해시, `release.json`으로 서명 인증서 지문과 소스 상태, `verification.txt`로 APK 서명과 최종 manifest 검증 결과를 확인한다. 업데이트 전에는 `app/build.gradle.kts`의 versionCode를 올리고, 제어 코드가 바뀐 경우 Shizuku UserService 버전도 올린다.

서명이 누락되면 release 빌드를 실패시키며 개발 키로 대체하지 않는다. Shizuku와 복구 프로세스의 진입점·리플렉션 호환성을 유지하기 위해 이번 배포 빌드는 코드 난독화와 축소를 켜지 않는다. `debuggable=false`는 별도로 적용·검사한다.

## 서명 키 보관

최초 배포 키는 Mac의 `~/.config/foldy/signing/`에 보관하며 폴더 권한은 700, 키와 설정 파일 권한은 600이다. 저장소와 배포 폴더에 포함하지 않는다. **이 개인 폴더는 안전한 별도 장소에 백업해야 한다.** 직접 배포 APK는 같은 키가 있어야 다음 업데이트를 서명할 수 있다. `tools/init_release_signing.py`는 최초 설정용이며 이미 키가 있으면 새로 만들지 않는다. 사용자에게 공유할 파일은 APK와 공개 검증 자료이고, 서명 키·비밀번호는 공유하지 않는다.
