# 카드 상세 · 근거 시트 구현 기록

작성일: 2026-11-14 · 범위: Figma A-1 · B-1 · H-1 · I-1 카드 상세, A-3 2차 안내 표시 방식, A-2 · B-2 · H-2 · I-2 근거 시트

홈 목록에서 안내를 누르면 이전에는 샘플 설명 대화상자가 열렸다. 이제 Figma와 같은 카드 상세 창이 열리고, '근거'를 누르면 근거 시트가 열린다. **보여 주는 법령 문구는 Figma의 디자인 문구를 옮긴 debug 샘플이다.** 검수된 게시 카드가 아니며, 화면 아래에 샘플임을 표시한다. release 빌드에는 샘플이 없다.

## 디자인 근거

| 화면 | 야간 | 주간 |
|---|---|---|
| A-1 고속도로 정차·주차 무인단속 | [1849:27324](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1849-27324) | [1870:31231](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1870-31231) |
| B-1 어린이보호구역 주정차 금지 | [1849:27844](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1849-27844) | [1870:31455](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1870-31455) |
| H-1 신호등 없는 횡단보도 일시정지 | [1849:28391](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1849-28391) | [1870:31790](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1870-31790) |
| I-1 우회전 후 횡단보도 보행자 보호 | [1849:29053](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1849-29053) | [1870:32087](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1870-32087) |
| A-3 시행일 이후 2차 안내 | [1856:30228](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1856-30228) | [1870:32393](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1870-32393) |
| A-2 근거 시트 (B-2 · H-2 · I-2 같은 구조) | [1849:27596](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1849-27596) | [1870:31321](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1870-31321) |

A-3은 A-1과 같은 화면에 배지만 '시행 중'으로 바뀐다. 따로 화면을 만들지 않고 같은 `CardDetailScreen`에 상태 값으로 표현한다.

## 구현

- 코드: `android/app/src/main/java/com/jochana/driverule/feature/card/`
  - `CardDetailScreen` — 헤더(앱 이름 + 근거), 단계 배지 + 이벤트 태그, 두 줄 문구, 장면 그림, 자동 닫힘 막대, 근거 시트
  - `CardDetailPreview` — 표시 모델. 서버 DTO · Room Entity와 분리
  - `CardErrorReportViewModel` — 오류 신고 상태
- 오류 신고: `domain/repository/CardErrorReportRepository` → `core/data/repository/BackendCardErrorReportRepository` → `BackendApiClient.reportCardError` (`POST /api/v1/cards/{cardId}/error-reports`, 본문 `cardVersion`만)
- 샘플: `android/app/src/debug/.../app/AppEnvironment.cardDetails()`. release는 빈 목록
- 화면 폭: Figma는 '창 1칸'(837px) 세로형 창이다. 앱 창이 더 넓으면 그 폭으로 가운데 정렬한다. 글자 배율은 다른 화면과 같은 1687px 기준을 쓴다

### 자동 닫힘

A-1 설명 노드(`1854:30389`)의 "음성 안내 후 8초 뒤 닫힘, '근거'를 누르면 닫힘이 멈춤"을 따른다.

- 8초는 **Figma 디자인 값**이며 확정된 제품 정책이 아니다. 원본 안에서도 타이머 시작점이 다르게 적혀 있다 ([출처 기록](source-check-2026-11-12.md) 5절 3번). 값은 `CARD_AUTO_CLOSE_MILLIS` 한 곳에 둔다
- 지금은 TTS가 연결되지 않았다. 그래서 '음성 후'가 아니라 **카드를 연 때부터** 8초를 센다
- '근거'를 누르면 그 카드에서는 다시 세지 않는다. 시트를 닫아도 사용자가 직접 닫을 때까지 남는다
- 자동 닫힘을 '확인함'으로 기록하지 않는다 (확인 이력 자체가 아직 없음)

### 오류 신고

사용자 결정(2026-11-14)으로 서버 API에 연결했다. 응답은 [앱 API v1](../api/app-api.md) 3절 표를 따른다.

| 응답 | 상태 | 화면 |
|---|---|---|
| 202 | `Sent` | '신고했어요', 버튼 비활성 |
| 400 · 404 · 422 | `Rejected` | 다시 보내지 않음 |
| 5xx · 네트워크 실패 · 그 밖의 응답 | `Failed` | '다시 신고' |
| 신고 대상 없음 (샘플) | `Unavailable` | 버튼 비활성, "샘플 카드는 오류 신고를 보내지 않습니다" |

**샘플 카드에는 서버 카드 ID가 없어 신고 버튼이 비활성이다.** 운영 서버에 시험 신고가 쌓이지 않게 하려는 것이다. 실제로 보내는 경로는 loopback MockWebServer 테스트로만 확인했다. 운영 서버로 보낸 적은 없다. 실제 카드 동기화가 붙으면 `CardEvidencePreview.reportTarget`에 카드 ID와 사본 버전을 넣는다.

'법령 원문 보기'는 링크가 있는 카드에서만 켜진다. 앱이 링크를 추측해 만들지 않는다. 샘플에는 링크가 없다.

## 에셋

`android/app/src/main/assets/figma/`. 원본을 수정하지 않았다.

| 파일 접두어 | 야간 / 주간 node | 크기(px) | 쓰는 곳 |
|---|---|---:|---|
| `scene_highway` | `1849:27547` / `1870:31249` | 759 × 677 | A-1 · A-3 장면 |
| `scene_destination` | `1849:27918` / `1870:31531` | 759 × 499 | B-1 장면 (비율 고정 + 범례) |
| `scene_crosswalk` | `1849:28797` / `1870:31808` | 759 × 677 | H-1 장면 |
| `scene_right_turn` | `1849:29461` / `1870:32105` | 759 × 677 | I-1 장면 |
| `tag_parking` · `tag_child_zone` · `tag_turn_right` | 이벤트 태그 아이콘 | 42.667 | 이벤트 태그 |
| `evidence` | `Button/근거` 아이콘 | 34.909 | 카드 헤더 |
| `law_source` · `report` | 근거 시트 버튼 아이콘 | 42.667 | 근거 시트 |
| `legend_zone.png` | `1849:27950` (Figma 래스터 채움) | 48 × 48 | B-1 범례 점, 주·야간 공용 |

장면 SVG는 Figma가 `Scene (crop)` 크기로 잘라 낸 export다. 맨 앞에 상위 프레임의 배경 path가 함께 들어 있지만 장면이 불투명하게 덮는다. 화면에서는 위쪽 기준으로 잘라(`ContentScale.Crop`, `TopCenter`) Figma의 crop과 같게 보인다.

**Figma와 다른 점**

- ~~A-1의 자동차는 Figma에 움직임(motion)이 있다~~ — 잘못 적었다. A-1에는 키프레임이 없다. Figma에서 실제로 움직이는 장면은 H-1 · I-1이며, [화면 움직임](motion-2026-11-14.md)에서 구현했다
- 장면 그림은 Figma export 그대로이며 앱 토큰으로 다시 칠하지 않았다

## 검증

**2026-11-14 기준 미검증.** 코드 작성 중 세션이 끝나 아래를 하나도 실행하지 못했다.

- [ ] `.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:lintDebug :app:testDebugUnitTest`
- [ ] 단위 테스트: `BackendCardErrorReportRepositoryTest`(loopback MockWebServer), `CardErrorReportViewModelTest`
- [ ] 계측 테스트: `CardDetailScreenTest` (Pleos 에뮬레이터, 사용자 10)
- [ ] 에뮬레이터 화면 확인: 4장면 × 주·야간, 장면 SVG의 상위 배경 path가 보이지 않는지, 작은 창
- [ ] release APK에 샘플 카드가 없는지

## 이어서 할 일

- ② 홈 '곧 바뀌는 규칙' 배너(00-b `1862:30394`), '달라지는 교통법규' 월별 화면(00-1 `1851:29155`) — Figma 구조만 확인함
- ① 시동 직후 '누가 운전하나요'(02-0 `1844:33114`) · '출발 전 알림'(02 `1844:31778`) — 앱 안에서만 표시하기로 결정함
- 'Gleo에게 묻기' 버튼은 새 화면에 넣지 않기로 결정함
