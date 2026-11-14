# 현재 UI 배치·Figma·Pleos 디자인 점검

확인일: 2026-11-12 · 범위: 운전자 이름 등록, 최초 권한 안내, 샘플 홈, 운전자 변경 시트의 주간·야간 UI.

이 기록은 최초 MVVM scaffold만 다뤘던 이전 점검을 현재 구현 기준으로 대체한다. 코드와 원본 Figma를 대조하고 색상 대비를 계산했다. **정적 점검 결과와 기기 실행 결과, 실차 검증 대상을 구분한다.**

## 현재 판정

화면 구조, Asta Sans, 주·야간 배경, 원본 아이콘, 카드·입력·시트 구성을 Figma에 맞춰 구현하고 있다. 이번 재점검에서는 시행 예정 배지 색상과 컨트롤 테두리의 토큰 차이, 야간 운전자 아바타 및 음성 상태 막대의 대비 부족을 발견하여 보정했다. 긴 이름에서도 브랜드·테마의 실제 폭을 먼저 확보하도록 헤더를 수정하고 기기에서 확인했다.

최신 사용자 요청, 더미 기능의 실제 상태, 접근성, 가변 창 크기를 반영한 차이는 아래에 명시한다. 실차의 글리프 높이 5mm/3mm, 차량 밝기, 실제 창 전환, `CarUxRestrictions` 연동까지 통과한 상태는 아니다.

## 확인한 Figma 원본

파일: [멋사멋사](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/). 전체 차량 캔버스의 계기판·상태 표시줄·하단 dock은 시스템 UI이며 앱이 다시 그리지 않는다. 앱 창 내부를 비교 대상으로 삼았다.

| 화면 | 야간 | 주간 |
|---|---|---|
| 01-0 운전자 이름 | [앱 창 1844:33055](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1844-33055) | [전체 프레임 1870:30625](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1870-30625) |
| 00 홈 | [앱 창 1844:32262](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1844-32262) | [앱 창 1870:31102](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1870-31102) |
| 01-3 권한 안내 | [앱 창 1844:32191](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1844-32191) | [앱 창 1870:30812](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1870-30812) |
| 09 운전자 변경 | [시트 1844:33168](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1844-33168) | [시트 1870:31149](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1870-31149) |

원본 SVG 24개(홈·이름 공용 18개, 권한 안내 6개)와 Asta Sans 4개 weight를 로컬 번들로 사용한다. SVG의 root width/height/viewBox를 유지하고 `ContentScale.Fit`으로 표시한다. 앱 실행 중 임시 Figma URL이나 원격 폰트를 불러오지 않는다. 에셋 대응, 출처 및 OFL은 [에셋 기록](../figma-assets.md)에 있다.

## 공식 가이드 기준과 현재 근거

| 기준 | 공식 근거 | 현재 확인·한계 |
|---|---|---|
| 의미 있는 텍스트·아이콘·이미지의 대비 4.5:1 이상 | [기본 디자인 원칙](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro) | 아래 표의 실제 사용 색상쌍을 계산. 수정 후 라이트·다크 캡처를 확인. 장치의 밝기·눈부심 측정은 아님. |
| 중요 글자 실제 높이 5mm 이상, 보조 3mm 이상 | [기본 디자인 원칙](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro) | sp·Figma px만으로 통과 판정하지 않음. 창 폭에 따라 폰트를 줄이는 구조이므로 실차 1/2 창에서 특히 실측 필요. |
| 중립적인 서체와 정보 위계, 실제 픽셀 높이 18px 이상 권장 | [Typography](https://document.pleos.ai/docs/connect/guide/docs-design/foundations/typography) | 공식 제공 서체인 Asta Sans 400/500/600/700을 사용. nominal sp와 실제 글리프 높이는 다름. 문서의 160dpi 예시를 모든 차량의 변환식으로 사용하지 않음. |
| 짧은 문구, 문장·단락 3줄 이내 | [기본 디자인 원칙](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro) | 현재 샘플 문구와 작은 창 줄바꿈을 검사. 동적 법령 데이터에 대한 길이 계약은 아직 없으며 향후 검수된 짧은 안내와 주차 상세를 분리해야 함. |
| 0.25초를 넘는 처리의 진행 상태 표시 | [기본 디자인 원칙](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro) | 저장 중, 음성 준비·청취·처리 상태 문구가 있음. 모든 장치에서의 응답 지연을 실측했다는 뜻은 아님. |
| 큰 터치 영역, pressed/disabled/loading 상태 | [Basic button](https://document.pleos.ai/docs/connect/guide/docs-design/components/actions/basic-button), [주행 안전 설계](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline) | 앱 주요 버튼은 최소 56dp, 목록은 최소 64dp. 이는 구현 하한이며 공식 공통 최소치로 선언하지 않음. 테마 조작도 56dp/16sp 하한으로 정렬하고 긴 이름 테스트에서 터치 영역을 검증. |
| 가로·세로 앱 창과 시스템 영역 분리 | [Display Layout](https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/layout), [Window Policy](https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/window) | 가용 창 폭과 글자 배율로 header/배지/footer를 재배치하고 내용은 스크롤. Compose 내부 크기 테스트는 실제 시스템의 1/2 window 전환 시험과 구분. |
| 주·야간 대응 및 일관된 상태 표시 | [기본 디자인 원칙](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro) | 시스템/라이트/다크 테마를 제공. 배지·현재 운전자·음성 상태를 색상뿐 아니라 문구로도 전달. 차량 자동 밝기 반응은 미검증. |
| 주행 상태에 따른 입력·상세 제한 | [주행 안전 설계](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline) | 가짜 provider의 Restricted/Unknown 전환 시험과 실제 차량 adapter를 구분. 실제 `CarUxRestrictions` 연동 전이며 release는 Unknown에서 제한을 유지. |

Basic button의 세부 치수 SVG는 이번 웹 조회 도구가 지원하지 않아 본문에 없는 수치를 공식 기준으로 추정하지 않았다. 터치 시 음성·진동 피드백의 실제 제공 여부도 별도 장치 확인이 필요하다. 시각 ripple만으로 이 항목까지 통과했다고 판단하지 않는다.

## 이번에 발견한 차이와 처리

| 항목 | 발견 내용 | 처리 |
|---|---|---|
| 시행 예정 배지 | 이전 코드의 주황 계열은 현재 Figma 원본의 색상과 다름. 실제 variable 정의는 분홍 계열. | `scheduled/scheduledSoft`로 분리: 야간 `#FF7EB6/#3D1A2C`, 주간 `#C2185B/#FFE4EF`. '시행 예정' 문구 유지. |
| 컨트롤 테두리 | 일반 border/surface 토큰을 버튼 테두리와 공유하여 원본과 차이가 남. | `controlBorder`를 분리: 야간 `#44464E`, 주간 `#E0E1E6`. 일반 배경·구분선 토큰과 구분. |
| 선택 아바타 | 야간 흰 글자/파란 원의 대비가 3.73:1. | `onAccent` 토큰을 사용. 야간 `#131417`로 4.94:1, 주간 흰색은 4.92:1. |
| 음성 상태 막대 | 활성색/picker 배경의 대비가 주간 3.77:1, 야간 4.47:1. | `accentText`로 보정하여 주간 4.66:1, 야간 5.46:1. 대기 중 막대도 `textSecondary`로 바꿔 주간 대비를 4.13:1에서 7.21:1로 높임. 상태 문구와 정적 막대를 함께 제공. |
| 긴 이름의 header | 고정 비율 배치만으로는 긴 운전자 이름이 브랜드·테마 영역의 폭을 잠식할 수 있음. | 실제 필요한 폭을 기준으로 header 배치를 결정하는 수정과 720dp·20자, 400×600dp·글자 배율 1.3 검사를 통과. 완료 결과는 아래 실행 검증 절에 기록. |
| 버튼·홈 간격 | outlined 버튼 최소 폭·좌우 여백·아이콘 간격, 홈 카드 상하 및 행 좌우 여백이 원본과 다름. | Figma 기준 218.182 / 39.273 / 15.273, 홈 56 / 24 디자인 단위를 창 배율에 맞춰 적용. |
| 운전자 시트 모서리 | 원본과 최신 사용자 요청이 다름. | 최신 요청에 따라 상단만 둥글게, 하단 좌우는 0dp. 디자인 불일치 버그로 되돌리지 않음. |

코드 위치: `android/app/src/main/java/com/jochana/driverule/ui/theme/Theme.kt`, `ui/components/DriveRuleComponents.kt`, `feature/home/HomeScreen.kt`, `feature/profile/{DriverOnboardingScreen,PermissionGuideScreen,DriverChangeSheet}.kt`.

## 수정된 색상 대비 계산

불투명 sRGB의 상대 휘도로 계산했다. `(밝은 L + 0.05) / (어두운 L + 0.05)`를 사용하며, 투명한 권한 아이콘 타일은 실제 합성 배경과 별도로 확인한다. 아래 수치는 렌더링·물리 화면 측정을 대체하지 않는다.

| 요소 | 주간 전경 / 배경 · 대비 | 야간 전경 / 배경 · 대비 |
|---|---|---|
| 화면 기본 글자 | `#131417/#F7F8FA` · 17.33:1 | `#FFFFFF/#1D1E21` · 16.67:1 |
| 홈 보조 글자 | `#44464E/#FFFFFF` · 9.41:1 | `#C7C9CE/#313236` · 7.73:1 |
| 홈 설명 글자 | `#686A72/#FFFFFF` · 5.39:1 | `#A4A8AE/#313236` · 5.36:1 |
| 설정 배지 | `#0058DE/#E0EDFF` · 5.14:1 | `#3195FF/#0F2740` · 4.96:1 |
| 시행 중 배지 | `#007A3D/#DFF7EA` · 4.84:1 | `#00E074/#0E3322` · 7.86:1 |
| 시행 예정 배지 | `#C2185B/#FFE4EF` · 4.92:1 | `#FF7EB6/#3D1A2C` · 6.45:1 |
| 선택 아바타 문자 | `#FFFFFF/#0064FF` · 4.92:1 | `#131417/#0082FF` · 4.94:1 |
| 현재 운전자 라벨·활성 음성 막대 | `#0058DE/#E0E1E6` · 4.66:1 | `#3195FF/#1D1E21` · 5.46:1 |
| 주요 버튼 | `#FFFFFF/#44464E` · 9.41:1 | `#131417/#E0E1E6` · 14.11:1 |
| 비활성 버튼 글자 | `#44464E/#E0E1E6` · 7.21:1 | `#C7C9CE/#44464E` · 5.68:1 |

브랜드 SVG는 주간 흰색, 야간 `#131417` 글리프를 사용하여 각각 위 선택 아바타와 같은 대비다. 테두리·구분선은 텍스트 대비와 같은 의미 요소로 무조건 간주하지 않으며, 선택 상태에는 명시적인 라벨이 함께 있다.

## 원본과 의도적으로 다른 부분

- 테마 선택과 더미 상태 표시를 추가했다. 샘플 법령 제목·시행 상태·안내 건수를 실제 법령 또는 개인별 수신 이력으로 주장하지 않는다.
- 이름 화면은 최신 요청에 따라 자동으로 음성을 듣기 시작한다. 중지·재시도, 준비·청취·처리·오류 표시를 제공하며 인식 결과는 '다음'으로 확정한다. Figma의 반복 막대 모션을 음량 측정처럼 재현하지 않는다.
- 권한 안내는 현재 제공되는 기능을 설명한다. 알림·차량 연동의 준비 상태를 명시하고 원본의 음성 질문/질문 보관 기능을 구현한 것처럼 표시하지 않는다. '확인하고 시작'은 안내 완료이며 Android 권한 부여를 뜻하지 않는다.
- 작은 창·긴 이름·큰 글자에서는 일부 요소를 세로 배치하고 내용에 스크롤을 제공한다. 디자인 캔버스의 좌표를 실제 dp로 고정하지 않는다.
- 운전자 시트 하단 모서리 0dp는 사용자의 명시적인 수정 요청을 우선한 결과다.

## 이번 수정의 실행 검증

최종 코드에서 `:app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`를 실행해 성공했다. JVM 테스트 45개 통과, lint 오류 0개다. 호스트 메모리를 고려해 기존과 같은 1 worker·Gradle heap 1GB·JVM 테스트 heap 256MB 설정을 이번 명령에만 적용했고 프로젝트의 빌드 설정은 바꾸지 않았다.

Pleos Connect 2.1.0 API 34, emulator-5554, 사용자 10, 디스플레이 2560×1440/160dpi에서 최종 APK의 UI 계측 테스트 **9개를 한 번에 실행해 전부 통과**했다. 테스트 화면의 일반 앱 창 캡처는 1682×1210px이며, 작은 창은 Compose 내부의 가용 크기를 제한했다. UI fixture는 메모리 프로필과 가짜 음성 엔진을 사용한다. 실제 한국어 발화 검사나 실제 시스템 창 전환으로 해석하지 않는다.

| 검사 | 결과 |
|---|---|
| 최초 이름·권한 안내·홈 라이트/다크 | 테마 선택, 표시 상태와 화면 이동, 최종 캡처 확인 |
| 운전자 변경 시트 라이트/다크 | 선택 유지, 상단 곡면·하단 직각, 닫기·행·점선 추가 버튼 확인 |
| 540dp·글자 배율 1.3 | 홈 마지막 행·하단 액션, 시트 선택·추가·닫기, 권한 안내 계속 버튼에 스크롤로 접근 가능 |
| 720dp·20자 한국어 이름 | 브랜드 글자 전체 표시·잘림/말줄임 없음, 세 영역이 겹치지 않음, 테마·운전자 변경 56dp 이상 높이, 조작 성공 |
| 400×600dp·글자 배율 1.3 | 이름·음성·다음 버튼 접근, 최초 등록→권한 안내→홈 성공 |
| 추가 등록·기존 프로필 선택 | 권한 안내를 다시 표시하지 않고 홈으로 이동, 기존 프로필 유지 |
| Restricted/Unknown 전환 | 열린 상세 제거, 복구 시 자동 재개하지 않음; 권한 안내 진행 상태 유지 |

첫 실행에서는 7/9개가 통과했다. 나머지 두 건은 Compose 1.7.6의 텍스트 semantics 재구성에 따른 overflow 오판과 별도 테마 Dialog 창의 부착 시점 문제였다. 브랜드의 실제 글자 경계·전체 문자열·말줄임 여부를 검사하고, 테마 선택은 해당 창의 semantics 부착을 최대 5초 기다리도록 수정했다. 제품 코드를 추가로 바꾸지 않고 전체 9개를 재실행해 통과했다.

최종 캡처 검토에서 다크 시트 한 장이 창 표시 직전 프레임을 담은 것을 발견했다. 캡처 동기화를 보완한 뒤 시트의 주·야간 및 작은 창 검사 2개를 추가로 실행해 통과하고 해당 3장의 캡처를 교체했다. 앱 제품 코드는 동일하다.

색상과 간격을 수정한 최종 APK를 설치했다. 실제 사용자 DB를 초기화하거나 앱을 삭제하지 않았다. 검사 후 `restart-app.ps1`로 MainActivity를 다시 열어 기존 ‘운전자 1’과 라이트 테마가 유지되는 것을 [실제 앱 화면](../screenshots/ui-audit/main-restored.png)에서 확인했다. 수정 전·후 캡처를 섞지 않도록 이번 결과는 `screenshots/ui-audit/`에 별도로 보관했다.

`git diff --check`를 통과했으며 이번 수정 파일은 `android/`와 `docs/android/` 안에 있다.

### 최종 캡처

| 화면 | 라이트 | 다크 |
|---|---|---|
| 이름 등록 | [라이트](../screenshots/ui-audit/onboarding-light.png) | [다크](../screenshots/ui-audit/onboarding-dark.png) |
| 권한 안내 | [라이트](../screenshots/ui-audit/permission-guide-light.png) | [다크](../screenshots/ui-audit/permission-guide-dark.png) |
| 홈 | [라이트](../screenshots/ui-audit/home-light.png) | [다크](../screenshots/ui-audit/home-dark.png) |
| 운전자 변경 | [라이트](../screenshots/ui-audit/driver-sheet-light.png) | [다크](../screenshots/ui-audit/driver-sheet-dark.png) |
| 720dp 긴 이름 | [라이트](../screenshots/ui-audit/home-720dp-long-name.png) | [다크](../screenshots/ui-audit/home-720dp-long-name-dark.png) |

[400×600dp·큰 글자 이름 등록](../screenshots/ui-audit/onboarding-400x600-large-font.png), [540dp 홈](../screenshots/ui-audit/home-compact-large-font.png), [540dp 시트](../screenshots/ui-audit/driver-sheet-compact-large-font.png), [540dp 권한 안내](../screenshots/ui-audit/permission-guide-compact-large-font.png).

## 남아 있는 장치 검증

실차에서 중요/보조 글리프의 5mm/3mm 높이, 밝기·눈부심, 터치 피드백, 시스템 1/2 window 실제 전환을 확인해야 한다. Compose 테스트에서 창 크기를 제한한 결과를 실제 차량 창 전환으로 보고하지 않는다. 실제 `CarUxRestrictions` adapter, 주행 음성·자동 종료 카드, Gleo·백엔드는 이번 UI 배치 검수로 완료되지 않는다. 한국어 이름을 실제로 말했을 때의 인식 정확도도 기존 자동 시작·서비스 연결 검사와 별개다.

빌드 성공, Figma 원본 대조, 대비 계산, 기기 화면 확인, 실차 기준 충족은 각각 다른 증거로 유지한다.
