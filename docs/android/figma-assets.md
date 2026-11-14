# 최초 설정·권한 안내·샘플 홈 Figma 구현 자료

확인일: 2026-11-12. Android 앱 창만 구현하며, Figma 전체 캔버스의 계기판·시스템 상태 표시줄·하단 dock은 앱에서 그리지 않는다.

## 디자인 근거

- [멋사멋사 — 최초 설정 원본](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1844-33051): 앱 창 `1844:33055`.
- [야간 홈 앱 창](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1844-32262).
- [주간 홈 앱 창](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1870-31102).
- [주간 최초 설정](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1870-30625).
- [야간 권한 안내](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1844-32187): 앱 창 `1844:32191`.
- [주간 권한 안내](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1870-30808): 앱 창 `1870:30812`.

Figma의 고해상도 디자인 컨텍스트와 화면 렌더를 확인했다. React/Tailwind 산출물은 레이아웃 참고로만 읽고 Kotlin/Compose의 상태 없는 화면으로 옮겼다. CSS fallback 값 대신 Figma 노드에 실제 적용된 채움색을 사용한다. 관련 코드는 `android/app/src/main/java/com/jochana/driverule/{ui,feature/profile,feature/home}`이다.

## 에셋 대응표

파일은 `android/app/src/main/assets/figma/`에 보관한다. 각 이름에 `_dark.svg`, `_light.svg`가 있으며 원본 파일을 수정하지 않았다. Figma가 제공한 다운로드 명령(`curl -f -L`)으로 모두 받아 길이가 0보다 큼을 확인했다. 임시 Figma asset URL은 런타임에서 사용하지 않는다.

`FigmaIcon`은 local asset URI와 Coil SVG decoder를 사용하며 색상 필터·path 추출·VectorDrawable 재작성·다른 아이콘 대체를 하지 않는다. SVG 원본의 width/height/viewBox를 보존하고 Compose가 화면의 동일 비율 slot에 `ContentScale.Fit`으로 표시한다.

| 파일 접두어 | 원본 내 위치·야간 node | 원본 크기(px) | 코드 호출 위치·화면 표시 |
|---|---|---:|---|
| `brand` | 앱 이름 앞, `I1844:32266;1674:1578` 및 최초 설정의 같은 컴포넌트 | 41.143 × 41.143 | `DriveRuleHeader`, 파란 72 × 72 tile 중앙 |
| `profile` | 운전자 변경 버튼, `I1844:32269;1673:1578` | 34.909 × 34.909 | `HomeScreen` → `DriveRuleButton(icon="profile")` |
| `parking` | 첫 번째 안내, `I1851:29052;1676:1712` | 32 × 32 | `GuidancePreviewRow`, 64 × 64 accentSoft tile 중앙 |
| `crosswalk` | 두 번째 안내, `I1851:29070;1676:1712` | 32 × 32 | `GuidancePreviewRow`, 횡단보도 원본 아이콘 |
| `turn` | 세 번째 안내, `I1851:29089;1676:1712` | 32 × 32 | `GuidancePreviewRow`, 우회전 원본 아이콘 |
| `camera` | 네 번째 안내, `I1851:29035;1676:1712` | 32 × 32 | `GuidancePreviewRow`, 단속 카메라 원본 아이콘 |
| `chevron` | 각 안내의 오른쪽, `I1851:29052;1676:1723` 외 3개 | 32 × 32 | `GuidancePreviewRow`, 상태 배지 다음 |
| `microphone` | Gleo 버튼, `I1844:32301;1673:1572` | 42.667 × 42.667 | `HomeActions` → `DriveRuleButton(icon="microphone")` |
| `arrow` | 달라지는 교통법규 버튼, `I1851:29148;1673:1572` | 42.667 × 42.667 | `HomeActions` → `DriveRuleButton(icon="arrow")` |

홈의 주·야간 에셋 각각 9개, 총 18개에 권한 안내용 6개를 추가했다. 최초 설정·권한 안내는 홈과 정확히 같은 brand export(`5f621.svg` / `52050.svg`)를 재사용한다. 계기판과 dock 이미지들은 앱 표시 영역 밖이므로 패키징하지 않았다. 입력 화면의 음성 막대는 Figma Rectangle 노드이며 SVG 에셋이 아니다. 권한 안내의 아이콘 대응표는 아래 01-3 절을 따른다.

## 폰트

Figma의 Asta Sans를 [42dot 공식 저장소의 TTF](https://github.com/42dot/Asta-Sans/tree/main/fonts/ttf)에서 내려받아 `res/font/asta_sans_{regular,medium,semibold,bold}.ttf`로 포함했다. 원본 폰트를 변형하거나 서브셋화하지 않았다. `AstaSans` FontFamily는 각 실제 weight 400/500/600/700을 연결한다.

[공식 OFL 1.1](https://github.com/42dot/Asta-Sans/blob/main/OFL.txt) 사본은 `android/app/src/main/assets/licenses/ASTA_SANS_OFL.txt`에 앱과 함께 포함한다. 앱 실행 중 폰트를 내려받지 않는다.

## 의도적인 차이와 미구현 기능

- 더미데이터 요청에 따라 `샘플 · 주차 상태 가정`을 표시하고, 홈에는 실제 주행 이력이나 법령 안내가 아니라는 문구를 넣었다. Figma의 예시를 법적 근거로 승인하지 않는다.
- 테마 선택을 추가해 시스템/라이트/다크로 확인할 수 있게 했다. 데이터 보관·상태 제어·UX 제한 판정은 화면 밖에서 담당한다.
- 원본의 이름값은 placeholder로 고정하지 않고 실제 입력한다. 빈 이름의 다음 버튼은 비활성화하며 저장 중·오류 상태를 표시한다.
- 이름 입력에는 Pleos SpeechToText 3.3.1의 ON_DEVICE·한국어 adapter를 연결했다. 최초·추가 등록의 빈 이름 화면은 RESUMED·UX 허용 상태에서 한 번 자동으로 듣기를 시작하며, 이미 직접 입력한 이름을 덮어쓰지 않는다. 버튼은 진행 중 ‘음성 입력 중지’, 중지 후 ‘다시 말하기’로 표시한다. 화면 이탈·ON_PAUSE/ON_STOP·UX 제한 시 종료하고 같은 화면 세션 복귀 때 자동 재개하지 않는다. 중간 결과는 상태 영역에 표시하고 최종 결과만 이름 입력란에 반영한다. 자동 저장하지 않으며 ‘다음’으로 확정한다. Gleo 질의 버튼은 여전히 기능 준비 안내만 제공한다.
- `get_motion_context(1844:33055, recursive=true)`에서 음성 막대 10개가 3초 무한 반복으로 scaleY 0.45/0.75/1을 오가는 ease-in-out track임을 확인했다. 현재 보이는 5개 막대는 정적 도형이며 상태색·문구로 연결/수음/처리/오류를 구분한다. 실제 음량 시각화로 표시하지 않고, 숨겨진 footer Voice group도 별도 기능으로 구현하지 않는다.
- 1687px 앱 창을 기준으로 배치를 조정하되 실제 Android dp와 Figma px를 동일한 물리 크기로 단정하지 않는다. 창 너비를 사용자 글자 배율로 나눈 값이 620 미만이면 header, 상태 배지, footer를 재배치하며 목록·입력 내용은 스크롤한다. 조작 버튼은 최소 56dp, 보조 본문은 최소 16sp로 설정했다. 이 값은 제품의 임시 하한이며 Pleos 공식 물리 크기 인증을 뜻하지 않는다.
- 주간 최초 설정의 picker panel은 `#E0E1E6`, 야간은 `#1D1E21`이다. 원본의 둥근 panel 및 상단 입력 여백을 유지한다.

## 대비 확인

설정 배지와 현재 운전자 라벨은 `accentText`로 대비를 보정했다. 이후 UI 재점검에서 선택된 운전자 아바타와 음성 상태 막대도 보정했으며, 현재 전체 검토 결과는 [디자인 점검](history/design-compliance-2026-11-12.md)을 따른다. 로고 배경과 입력 테두리는 원본 accent 값을 유지한다. 불투명 sRGB 상대 휘도 계산 결과이며 장치 밝기·물리 글자 높이 검증과 구분한다.

| 요소 | 주간 전경/배경·대비 | 야간 전경/배경·대비 |
|---|---|---|
| 기본 글자 | `#131417 / #F7F8FA` · 17.33:1 | `#FFFFFF / #1D1E21` · 16.67:1 |
| 보조 글자 | `#44464E / #FFFFFF` · 9.41:1 | `#C7C9CE / #313236` · 7.73:1 |
| 설명 글자 | `#686A72 / #FFFFFF` · 5.39:1 | `#A4A8AE / #313236` · 5.36:1 |
| 설정 배지(보정) | `#0058DE / #E0EDFF` · 5.14:1 | `#3195FF / #0F2740` · 4.96:1 |
| 시행 중 배지 | `#007A3D / #DFF7EA` · 4.84:1 | `#00E074 / #0E3322` · 7.86:1 |
| 시행 예정 배지 | `#C2185B / #FFE4EF` · 4.92:1 | `#FF7EB6 / #3D1A2C` · 6.45:1 |

원본 설정 배지의 `accent / accentSoft` 대비는 주간 4.15:1, 야간 4.07:1이었다. 실제 기기에서 1/2 window, 큰 글자, 상태 전환, 원본 SVG slot 및 렌더 비율의 최종 검증 결과는 이번 구현의 별도 검증 기록에 남긴다.

### 구현 검증 기록

주·야간 화면과 작은 창/큰 글자의 확인 결과는 [UI 구현 검증](history/ui-implementation-2026-11-12.md)에 있다. Material 다이얼로그의 야간 액션 글자는 `#60A5FF / #313236`(5.07:1)로 보정하고, 다이얼로그 글자·최소 터치 영역도 앱 창 크기에 맞췄다. 비활성 버튼과 이름 placeholder에는 secondary 글자색을 적용했다.

## 01-3 권한 안내 화면

2026-11-12에 [야간 원본](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1844-32187)(앱 창 `1844:32191`)과 [주간 원본](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1870-30808)(앱 창 `1870:30812`)의 디자인 컨텍스트·렌더·실제 채움색을 확인했다. `PermissionGuideScreen`은 앱 창만 구현하며 기존 `DriveRuleWindow`, `DriveRuleHeader`, `DriveRuleButton`과 테마를 재사용한다.

주·야간의 base, border, secondaryButton, textPrimary, textSecondary, filledButton, filledButtonText는 기존 토큰과 일치했다. 권한 행 아이콘 타일은 두 테마 모두 원본의 흰색 6%와 radius 28px를 사용한다. 1687px 기준 가로 여백 64px, 행 간격 56px, 아이콘·설명 간격 40px, 설명 내부 간격 8px를 앱 창 배율에 맞춰 적용했다.

### 추가 원본 SVG

`android/app/src/main/assets/figma/`의 아래 파일 각각에 `_dark.svg`, `_light.svg`를 추가했다. Figma 다운로드 URL을 `curl -f -L`로 내려받았으며 파일은 수정하지 않았다. 모두 root width/height `51.2`, viewBox `0 0 51.2 51.2`이며 `PermissionRow` → `FigmaIcon`에서 같은 비율의 51.2px slot을 사용한다. 임시 URL은 런타임에서 사용하지 않는다.

| 파일 접두어 | 야간 아이콘 node / export | 주간 아이콘 node / export |
|---|---|---|
| `permission_bell` | `1844:32230` / `d4572.svg` | `1870:30851` / `f2efd.svg` |
| `permission_road` | `1844:32236` / `cdb40.svg` | `1870:30857` / `cc960.svg` |
| `permission_microphone` | `1844:32242` / `80510.svg` | `1870:30863` / `f2523.svg` |

다운로드 출처 접두어: 야간 `https://www.figma.com/api/mcp/asset/32fa62ed-ef58-4d2d-a575-36a0e8a718ea`, 주간 `https://www.figma.com/api/mcp/asset/b5a4ed9f-5682-44b2-95be-eae35269631b`. 브랜드 아이콘은 기존의 동일한 export를 재사용한다.

### 의도적인 차이

- 알림과 차량 속도·기어는 현재 연동 준비 중임을 표시한다. 원본의 실제 법령 변경 알림·차량 신호 수신이 동작한다는 문장은 사용하지 않는다.
- 이번 음성 입력은 이름 입력 화면에서만 사용하는 운전자 이름 입력용이다. 이름 화면 진입 시 자동 시작하며 중지와 재시도를 지원한다. 원본의 음성 질문·질문 보관·개인정보 가림 주장을 복사하지 않는다. adapter는 [공식 SpeechToText 모드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro)에 따라 `ON_DEVICE`로 고정하고 [`Locale.KOREA`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/set-locale)를 설정한다. 앱이 원본 녹음이나 질문 이력을 저장하지 않는다.
- 앱 내 권한 안내 확인과 Android 시스템의 권한 부여를 구분하기 위해 주요 버튼을 ‘확인하고 시작’으로 표시한다. 나중에/확인 버튼은 화면 외부 콜백으로 안내 완료를 저장하고 홈으로 이동한다. 화면 자체에서 음성 인식을 시작하거나 권한을 요청하지 않는다. [`STT_SERVICE`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions)는 대상 Pleos 환경의 normal 권한으로 별도 OS 팝업이 없다.
- 기존 테마 선택을 유지한다. 좁은 창과 큰 글자에서는 배지·설명을 세로로 배치하고 하단 버튼을 전체 너비로 표시한다. 본문과 하단 액션은 함께 스크롤되어 높이가 부족해도 접근할 수 있다.
- `get_motion_context`에서 주·야간 모두 숨겨진 Voice level 그룹 안의 5개 막대에 3초 반복 모션을 확인했다. 해당 그룹은 실제 `visible=false`이므로 표시하거나 애니메이션을 실행하지 않는다.

구현 파일·문자열 XML 및 SVG 원본 크기·비어 있지 않음을 확인했다. 통합 후 권한 안내의 라이트·다크·작은 창 캡처와 흐름 검사, 실제 Pleos 서비스 검사 범위는 [권한 안내·음성 입력 검증](history/onboarding-permissions-speech-2026-11-12.md)에 기록했다. 서비스 연결 검사를 실제 한국어 이름 인식 정확도 검증으로 해석하지 않는다.

### 현재 Figma 변수 재대조

홈 주·야간 `get_variable_defs`에서 시행 예정 배지의 `signal/pink`와 `signal/pink-soft`를 확인해 `scheduled/scheduledSoft`로 적용했다. 버튼의 `border/subtle`은 야간 `#44464E`, 주간 `#E0E1E6`이며, 앱 외곽·구분선의 `bg/surface-2`와 분리했다. outlined 버튼의 최소 폭 218.182px·가로 padding 39.273px·아이콘 간격 15.273px, 홈 카드 상하 padding 56px·행 좌우 padding 24px를 앱 창 배율에 맞춰 적용했다. 원본 SVG는 변경하지 않았다. 최신 캡처는 [현재 UI 점검](history/design-compliance-2026-11-12.md)에 있다.
