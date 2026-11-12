# Pleos 디자인 가이드 준수 점검

확인일: 2026-11-12 · 대상: 최초 Android MVVM scaffold · 판정: **부분 반영, 최종 화면 준수 미검증**

현재 화면은 기능과 상태 흐름을 연결하기 위한 기본 화면이다. Figma 구현이나 Pleos 디자인 검수를 완료한 화면이 아니다. 코드·고정된 라이브러리 값·기존 테스트 보고서를 조사했으며, 이번 점검에서 에뮬레이터나 실차 화면을 실행하지 않았다. 앱 코드는 변경하지 않았다.

## 항목별 결과

| 항목 | 현재 근거 | 판정 및 후속 조치 |
|---|---|---|
| 주행 제한 | `PreDepartureViewModel`이 Unknown/Restricted에서 카드 목록을 숨긴다. 제한 전환·재구독을 포함한 기존 JVM 테스트 5개 통과 보고서 확인. | 기본 구조 반영. 실제 `CarUxRestrictionsManager`는 미연결이며 현재 provider는 항상 Unknown이다. 실차 제한 준수 완료는 아님. |
| 시선 유도 | 현재 화면에 영상·애니메이션 이미지·자동 스크롤이 없다. | 현재 구현 범위에서 확인. 이후 카드·진행 표시에도 적용. |
| 텍스트 대비 | 현재 제목·상태 문구·카드 제목의 주간/야간 색상 6쌍 계산. 아래 표 참조. | 전부 4.5:1 이상. 실제 화면의 밝기·눈부심·이미지와 향후 추가 색상은 별도 검증. |
| 글자 크기 | Material3 기본 `headlineLarge` 32sp, `bodyLarge` 16sp, `titleLarge` 22sp 사용. 앱 전용 Typography 없음. | 차량 화면에서 중요 정보 5mm/보조 3mm 높이 충족 여부 미검증. sp나 Figma px만으로 통과 판정 금지. |
| 긴 문구 | `PreDepartureScreen`의 `card.title`은 길이·줄 수 제한이 없다. | 3줄 이내 안내를 보장하지 못함. 검수된 짧은 안내 문구와 주차 중 상세를 분리하는 계약 필요. 임의 축약·말줄임으로 법령 의미를 누락하지 않는다. |
| 주·야간 화면 | Compose는 `isSystemInDarkTheme()`와 두 color scheme을 사용한다. | 대응 기반 있음. 차량 모드 전환과 가독성은 기기 검증 필요. |
| 앱 시작 테마 | 주간 XML은 `Theme.Material.Light.NoActionBar`, `values-night`는 `Theme.Material.NoActionBar` 및 `windowLightStatusBar=false`를 사용한다. | 주·야간 XML 대응 기반 있음. 실제 시작 화면의 배경·전환과 Compose 테마의 일관성은 기기 검증 필요. 기존 점검의 야간 XML 부재 표기를 푸시 전 재확인으로 정정함. |
| 창 크기·시스템 영역 | `fillMaxSize`, `fillMaxWidth`, `Scaffold` content inset 반영. 전체 IVI 캔버스를 직접 그리지 않음. | 유연한 크기 대응 기반 있음. 1/2 window 각각의 배치, 큰 글자, 긴 한국어, 창 전환은 미검증. 방향별 설계 미완성. |
| 터치·피드백 | 현재 화면에 클릭 가능한 컨트롤 없음. | 현재 해당 없음. 이름 입력·선택·근거 버튼 구현 시 큰 터치 영역과 피드백 검증. 일반 Android 48dp나 Figma 117px를 Pleos 공식 공통 최소치로 표기하지 않는다. |
| 응답 상태 | Loading/상태 확인 불가/제한/조회 불가/빈 결과 분리. | 상태 문구 있음. 사용자 조작 응답 시간 및 지연 피드백은 기능 구현 후 검증. |
| 알림·백그라운드 카드 | 아직 표시 surface와 TTS 미구현. overlay 권한·강제 Activity 실행 없음. | 준수 판정 대상 구현이 아직 없음. 허용 surface·카테고리·음성 조건을 확인한 뒤 구현. |
| Figma 일치 | v6.0의 A-1은 주차 상세이며 주행 말풍선은 시연 주석. 현재 화면은 이를 구현하지 않음. | 최종 색상·글꼴·간격·카드 구성 미반영. 최신 요청인 출발 전 안내·주행 카드와 원본의 차이를 유지해 설계. |

## 현재 색상 대비 계산

`Theme.kt`의 기본 scheme에서 사용하는 Material3 **1.3.1**의 `ColorLightTokens`, `ColorDarkTokens`, `PaletteTokens`, `FilledCardTokens` 값을 로컬 AAR 바이트코드로 확인했다. 현재 Card의 채움색은 `surfaceContainerHighest`, 글자는 `onSurface`이다. 동적 색상·사용자 정의 투명도는 사용하지 않는다.

| 모드·요소 | 전경 | 배경 | 대비 |
|---|---|---|---|
| 주간 화면 제목 | `#1D1B20` | `#FEF7FF` | 16.23:1 |
| 주간 상태 문구 | `#49454F` | `#FEF7FF` | 8.88:1 |
| 주간 카드 제목 | `#1D1B20` | `#E6E0E9` | 13.17:1 |
| 야간 화면 제목 | `#E6E0E9` | `#141218` | 14.35:1 |
| 야간 상태 문구 | `#CAC4D0` | `#141218` | 10.91:1 |
| 야간 카드 제목 | `#E6E0E9` | `#36343B` | 9.47:1 |

계산은 불투명 sRGB의 상대 휘도로 `(밝은 L + 0.05) / (어두운 L + 0.05)`를 사용했다. 채널값 c를 0~1로 정규화하고 c≤0.04045이면 c/12.92, 그 외에는 `((c+0.055)/1.055)^2.4`로 선형화한다. `L=0.2126R+0.7152G+0.0722B`. 이것은 색상값 계산이며 화면 실측이나 Figma token 일치 검증이 아니다. 테마·라이브러리·알파·상태색을 바꾸면 다시 계산한다.

## 첫 실제 UI 구현의 완료 기준

1. `ui/theme`에서 색상·Typography·간격·모양을 관리하고 각 화면의 Figma frame과 차이를 기록한다. 시스템 시작 화면도 주·야간에 맞춘다.
2. 대상 앱 창의 px/dp 크기, density, 글자 배율, 실제 글리프 높이를 확인한다. 중요 정보 5mm·보조 3mm 기준을 장치에서 검증하며 글꼴 nominal sp를 실제 글리프 높이로 간주하지 않는다.
3. 출발 전/주차 상세/주행 안내별 문구 계약을 나누고, 핵심 안내는 3줄 이내로 읽을 수 있게 구성한다. 긴 원문은 허용된 주차 상세에서 제공한다.
4. 주간·야간 × 1/2 window × 기본/큰 글자에서 긴 한국어와 모든 오류·빈 상태를 확인한다. 시스템 Driving view, dock 및 inset을 침범하지 않는다.
5. 이름 입력·근거 열람 중 UX 제한 전환, 앱 복귀, 권한·신호 상실을 검증한다. 키보드·검색 등 실제 제한 플래그는 adapter에서 보존한다.
6. 주행 카드와 TTS는 공식 허용 표면·실행 조건을 확인하고 검증한다. 알림 디자인 문서만으로 임의 overlay를 허용된 것으로 해석하지 않는다.

빌드·lint·JVM 테스트 성공과 디자인 검수 완료를 구분한다. 현재 보고서는 1~6이 완료되었다는 증거가 아니다.

## 공식 근거와 프로젝트 자료

- [Pleos 기본 디자인 원칙](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro): 대비 4.5:1, 중요/보조 정보 5/3mm, 간결한 문구, 주·야간, 지연 시 상태 표시.
- [Typography](https://document.pleos.ai/docs/connect/guide/docs-design/foundations/typography): sp/px 설명 및 폰트. 160dpi 예시를 모든 차량의 물리 크기 변환으로 사용하지 않는다.
- [Colors](https://document.pleos.ai/docs/connect/guide/docs-design/foundations/colors): scale/system 및 주·야간 색상 체계.
- [Display Layout](https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/layout), [Window Policy](https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/window): 앱 영역과 창 배치.
- [앱 구조 및 동작 모델](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model): Driving view 보호, portrait/landscape, 가변 창과 생명주기.
- [주행 규제 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide): `CarUxRestrictions` 및 기능별 active restrictions.
- [Basic button](https://document.pleos.ai/docs/connect/guide/docs-design/components/actions/basic-button), [주행 안전 설계](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline): 버튼·조작 설계. 이번 조사에서 버튼 SVG의 세부 치수는 확인하지 않았음.
- [System Notification](https://document.pleos.ai/docs/connect/guide/docs-design/components/notification/system-notification): 시스템 알림 디자인.
- [Figma v6.0](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1844-31772), [이전 원본 확인 기록](source-check-2026-11-12.md), [현재 구조](architecture.md).
