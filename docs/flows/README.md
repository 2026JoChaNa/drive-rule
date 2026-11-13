# 도메인별 흐름도

[`flows.drawio`](flows.drawio) 한 파일에 페이지 8장. drawio(데스크톱 · VS Code 확장 · app.diagrams.net)에서 열고 아래 탭으로 넘긴다.

| 페이지 | 어디서 | 내용 |
|---|---|---|
| 0 전체 지도 | 서버 · 차량 | 기능 사이 데이터 이동, 차 밖으로 나가는 것 |
| ① 법령 수집·분류 | 서버 | 매일 개정 조회 → 분류 대기 → 일반 운전자 해당 분류 |
| ② 카드 검수·게시 | 서버 | 초안 → 검사 → 2인 교차 검수 → 게시 → 수정 · 회수 |
| ③ 사본 동기화 | 차량 ↔ 서버 | 카드 변경분 · 회수, 보호구역 파일 |
| ④ 이벤트 전 안내 | 차량 | 프로필 · 단계별로 한 번만 안내하는 판정 |
| ⑤ Gleo 규칙 묻기 | 차량 | 사본으로 답하기, 되묻기, 답변 보류 |
| ⑥ 바뀐 규칙·확인 | 차량 | 바뀐 규칙 목록, 확인 · 다시 듣기 |
| ⑦ 신고 처리 | 차량 → 서버 | 오류 신고 · 답변 보류 → 검수 대기열 |

- 빨간 점선 상자 = 차 밖으로 나가는 데이터. 프로필 ID · 위치 · 질문 원문은 어디에도 없다
- 각 페이지 오른쪽 메모의 **정해지지 않은 것**은 아직 기획 · 결정 기록에 규칙이 없는 부분이다

## 고치는 법

`flows.drawio` 를 손으로 고치지 않는다. [`gen-flows.mjs`](gen-flows.mjs) 의 `pages` 데이터(노드 칸 · 엣지)를 고치고 다시 만든다.

```bash
node docs/flows/gen-flows.mjs
```

좌표는 격자(열 280 · 행 120)로 계산한다. 노드 · 메모 겹침, 엣지가 노드를 지나감, 서로 다른 엣지 포개짐, 대각선, 격자 밖 좌표가 있으면 실패하고 파일을 쓰지 않는다.

## 근거

- 서버: [결정 004](../decisions/004-card-lifecycle.md) · [011](../decisions/011-law-revision-collector.md) · [admin-api.md](../api/admin-api.md)
- 차량: [결정 009](../decisions/009-core-features-v6-alignment.md) · [app-api.md](../api/app-api.md) · [매칭 명세](../../spec/rule-matching/README.md) · [ERD](../db/erd.data.mjs) (`notice_log` · `card_confirmation`)
- ④ 의 '주행 1회 2건'은 기획 v6 1-1. 차량 앱 구현은 아직 없다 (흐름은 설계 기준)
