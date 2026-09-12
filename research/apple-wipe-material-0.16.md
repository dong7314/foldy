# 0.16.0: iPhone Duo Wipe 재현

2026-09-12에 Apple 한국 iPhone Duo 페이지의 실제 제품 뷰어를 열어 닫힘·중간·펼침
상태를 비교하고, 브라우저가 받은 장면 manifest, glTF, WebGL Wipe 모듈을 다시 분석했다.
페이지에 표시된 3D canvas는 1440×760이다.

## 공식 장면에서 확인한 값

- `Slider`는 2초, 30fps, 61개 authored sample이다. 움직이는 face는 프레임마다
  3도씩 0→180도로 회전하고 23개 screen bone이 함께 변형된다.
- Hinge는 closed 0, landing 0.3333, open 1을 사용한다. spring duration 0.75초,
  bounce 0.15, 양 끝 magnet radius 0.08, strength는 closed 30/open 20이다.
- 내부 landscape Wipe는 `clamp(1.2 * (1 - p), 0, 1)`이다.
- 외부 portrait Wipe는 일반 factor를 덮어쓰고
  `0.5 * clamp(1 - 2 * abs(p - 0.5), 0, 1)`을 사용한다. 따라서 외부 흐림은
  닫힘과 펼침 끝에서 0이며 90도에서만 0.5로 가장 강하다.
- 내부 blur bounds는 `[0.45, 1]`, wipe position은 1이다. 외부 blur bounds는
  `[0, 0.9]`, wipe position은 0이다. 두 화면이 서로 반대 방향으로 퍼진다.
- blur pass는 `distanceToWipe`를 bounds로 remap하고 `wipeAmount * 2.5`를 곱한
  뒤 0~1로 제한하고 0~0.75 구간을 다시 remap한다. 이 값을 mip LOD에 사용한다.
- manifest의 `maxBlur=10` 필드는 이 pass가 읽지 않는다. 실제 fragment shader는
  `maxBlur=8` LOD를 하드코딩하고 bicubic mip sample pass를 두 번 수행한다.
  0.15.1에서 이를 10px Gaussian 반경으로 해석한 것은 잘못이었다.
- 내부 emissive brightness 상태는 closed 0.15, landing 0.25, open 1이지만 최종
  광량은 다시 `smoothstep(0.1, 1, brightness)`를 거친다. landing의 실제 계수는
  약 0.074라 뒤쪽 내부 면이 중간 상태에서 매우 어둡게 보인다.
- shading은 blur와 같은 방향의 spatial wipe, 상하 edge falloff를 함께 사용한다.
  장면 교체용 `FadeThroughBlack` 0.38초는 폴딩 track 자체의 효과가 아니다.

확인한 live asset SHA-256:

```text
scene  0729B842BFE01CDC8BFE478BC50528BE8CAEB963C001D24A558B9BAA58A4A29E
gltf   30CAB0C2102ECF050A5E07BDBE2EF36D1EDFC522779ABFC3434296792843BB4C
bin    B17C153EC94CAE26159308D0908A3C91EB458782890283222AED7D5869981D7A
script F4C91C91B5031FDF5DE1995188987DA364613629EE547119B218773E4F53690B
```

live glTF와 앞서 보관한 glTF/bin은 byte-for-byte 동일했다. 61프레임 회전·crease
수치는 `research/apple-duo-slider-profile.json`에 있다.

## Android 구현

`FoldOptics`는 위 내부·외부 Wipe 함수, blur bounds, shade bounds, 비선형 emissive
곡선을 그대로 계산한다. 이전처럼 내부와 외부에 단조 증가·감소하는 동일한
Gaussian 세기를 적용하지 않는다.

WebGL의 연속 mip LOD를 Android `RenderEffect` 하나로 바꿀 수 없으므로,
`FoldRenderer`는 2×, 8×, 32× 축소한 fine/medium/deep 버퍼를 만들고 공식
`blurArea`의 세 구간으로 합성한다. 큰 Gaussian 하나에서 생기던 균일한 뿌연
테두리 대신, 원본처럼 자유단으로 갈수록 색과 형태가 단계적으로 확산된다.
어두움은 전체 화면 고정 덮개가 아니라 Wipe의 공간 함수와 내부 emissive 함수에
따라 연속적으로 변한다. 보이는 두 끝 상태에서는 블러와 추가 암부가 정확히 0이다.

웹 장면의 카메라 원근과 23-bone 변형은 3D 모델에 필요한 값이다. 실제 갤럭시의
화면은 물리적으로 이미 접히므로 같은 사다리꼴 투영을 bitmap에 다시 적용하면
원근이 두 번 생긴다. 이전 `FoldPlane`의 검은 쐐기와 경계 band를 제거하고,
물리 경첩에는 화면 재질 효과만 적용한다. 각도는 기존 HAL 절대각을 그대로 써서
멈춤과 역전 때 별도 시간 애니메이션이 진행되지 않는다.

## 검증 상태

- Java 단위 테스트 68개, Python 테스트 11개 통과.
- Android Lint 오류 0, 경고 32개.
- debug APK 빌드 성공.
- 실제 Android GPU 표본과 물리 경첩 검증은 USB 디버깅 재승인 후 수행한다.
