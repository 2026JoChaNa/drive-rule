# 홈 '곧 바뀌는 규칙' 배너 · 달라지는 교통법규 월별 목록

작성일: 2026-11-14 · 범위: Figma 00-b 배너, 00-1 월별 목록

홈 위쪽에 '곧 바뀌는 규칙' 배너를 넣었습니다. 배너나 '달라지는 교통법규 보기' 버튼을 누르면 이전처럼 샘플 대화상자가 뜨지 않고 월별 목록 화면이 열립니다. **목록의 법령명 · 시행일 · 법률 번호는 Figma 디자인 문구를 옮긴 debug 샘플입니다.** 검수된 데이터가 아니며 화면에 샘플임을 표시합니다. release에는 샘플이 없습니다.

[카드 상세 · 근거 시트](card-detail-evidence-2026-11-14.md) 브랜치 위에 쌓은 작업입니다. 그 PR이 먼저 병합되어야 합니다.

## 디자인 근거

| 화면 | 야간 | 주간 |
|---|---|---|
| 00-b 홈 · 곧 바뀌는 규칙 | [1862:30394](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1862-30394) (배너 `1862:30505`) | [1870:31203](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1870-31203) (배너 `1870:31214`) |
| 00-1 달라지는 교통법규 · 월별 | [1851:29155](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1851-29155) | [1870:31171](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1870-31171) |

Figma에서 00-b는 '후순위 · 2차 PT 1단계 제외'로 표시되어 있지만, 사용자 요청(2026-11-14)으로 이번에 구현했습니다.

색은 Figma 변수의 실제 값으로 확인했습니다. 배너 `bg/surface-2`는 야간 `#3A3B40` / 주간 `#EDEEF2`로, 기존 `border` 토큰과 같습니다. 목록 카드 `bg/surface`는 기존 `surface` 토큰과 같습니다.

## 구현

- `feature/changes/RuleChangePreview.kt` — 표시 모델과 순수 함수
  - `groupRuleChanges`: '시행 예정' · '최근 시행' 두 구역으로 나누고, 구역마다 달을 최신순으로 묶습니다 (Figma 순서 그대로)
  - `upcomingRulesBanner`: 아직 듣지 않은 시행 예정 변경 중 **시행일이 가장 가까운 날짜**의 변경만 모읍니다
- `feature/changes/RuleChangesScreen.kt` — 00-1 화면. 헤더(앱 이름 + 홈으로), 제목 · 설명, 스크롤 목록
- `ui/components/RuleRow.kt` — Figma `Rule row` 컴포넌트. 홈 목록에 있던 줄을 꺼내 홈과 월별 목록이 같이 씁니다
- `feature/home/HomeScreen.kt` — 00-b 배너 추가 (`upcomingBanner`가 없으면 그리지 않음)
- `app/DriveRuleApp.kt` — 화면 이동. 카드 상세가 있는 줄은 카드 상세를, 없는 줄은 기존 샘플 설명을 엽니다. 카드를 닫으면 연 화면(홈 또는 월별 목록)으로 돌아갑니다

### 배너 기준 — 확정 아님

Figma 배너는 '음주운전 조장 금지 · 상습 위반자 가중 과태료 (2027년 6월 3일 시행)'로, 시행일이 더 가까운 '고속도로 정차·주차 무인단속(2027년 4월 14일)'이 빠져 있습니다. 그 항목은 같은 홈의 '주행 중 들은 안내'에 이미 있습니다. 그래서 **이미 들은 규칙은 배너에서 뺀다**고 해석했습니다.

- 지금은 운전자별 이력이 없어 샘플의 `alreadyHeard` 값으로 대신합니다
- 실제 기준(운전자별 확인 이력, 출발 전 1회 표시)은 확인 이력 저장을 만들 때 정해야 합니다

## 에셋

`android/app/src/main/assets/figma/`. 원본을 수정하지 않았습니다. 기존 `camera` · `chevron`은 줄바꿈 문자만 다르고 내용이 같아 다시 넣지 않았습니다.

| 파일 접두어 | 위치 | 크기(px) |
|---|---|---:|
| `banner_chevron` | 00-b 배너 오른쪽 `Icon/Chevron Right` | 43 |
| `home` | 00-1 `Button/홈으로` 아이콘 | 42.667 |
| `drink` · `calendar` · `drug` · `license` | 00-1 Rule row 아이콘 | 32 |

## 검증

2026-11-14, JDK 17, 이 브랜치(카드 상세 PR #21 코드 포함).

| 항목 | 결과 |
|---|---|
| `:app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug` | **성공**. 단위 테스트 81개 모두 통과 (새 테스트 11개: `RuleChangeGroupingTest` 3 · `BackendCardErrorReportRepositoryTest` 4 · `CardErrorReportViewModelTest` 4). lint 오류 0건, 경고 46건은 기존과 같고 새 코드에서 나온 것 없음 |
| release APK에 샘플이 없는지 | **확인**. release dex에 샘플 ID(`change-drink-assist`, `sample-camera`)가 없음. debug에는 있음 |
| 계측 테스트 `CardDetailScreenTest` (5개) | **통과**. Pleos 에뮬레이터, 사용자 10 |
| 계측 테스트 `RuleChangesScreenTest` (2개) | 1개 통과(배너). 월별 목록 테스트는 같은 부제가 두 줄에 있어 단언을 고쳤고, **고친 뒤 다시 돌리지 못함** |
| 전체 계측 테스트 | **끝까지 못 돌림.** PC 메모리가 부족해 실행 도구가 에뮬레이터와 테스트를 중간에 멈춤. 멈추기 전 `DriverFlowScreenTest`에서 12개 통과, 4개 실패 |
| 에뮬레이터 화면 캡처 (주·야간) | **못 함** (같은 이유) |

멈추기 전 `DriverFlowScreenTest` 실패 4개는 이번 변경과 직접 관련 없는 곳에서 났습니다. 다만 원인을 확인하지는 못했습니다.

- `realPleosSpeechStartsAutomaticallyOnNameScreenWithoutVoiceButtonTap` — 실제 Pleos 음성 엔진 45초 대기 초과 (새로 설치한 에뮬레이터의 첫 실행)
- `themePickerWorksInSetupAndHomeAndCapturesBothAppearances` — `PixelCopy` 화면 캡처 실패
- `driverSheetSupportsLightAndDarkWithoutChangingItsSelectedProfile` — 테마 선택 대기 5초 초과
- `speechOnlyFillsFinalNameAndUserMustConfirmBeforeSaving` — 앞 테스트 뒤 Compose 화면을 찾지 못함

메모리가 넉넉할 때 에뮬레이터를 다시 켜고 전체 계측 테스트와 화면 확인을 다시 해야 합니다.

```powershell
adb shell am instrument --user 10 -w -r com.jochana.driverule.test/androidx.test.runner.AndroidJUnitRunner
```

## 하지 않은 것

- 'Gleo에게 묻기' 버튼: 새 화면에는 넣지 않았습니다(사용자 결정). 기존 홈에 있던 버튼은 그대로 둡니다
- 실제 법령 데이터 연결(카드 동기화 · Room 캐시)
- 월별 목록 줄 중 카드 상세가 없는 줄은 기존 샘플 설명 대화상자를 엽니다
