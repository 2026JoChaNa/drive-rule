# 002. 조건 분기 카드는 JSON 결정 테이블(UNIQUE)로 저장

- 날짜: 2026-11-11
- 상태: 결정 (MVP 범위)
- 관련: `docs/db/erd.data.mjs` — `rule_card.decision_json` (MySQL `JSON`), `cached_card.decision_json` (Room `TEXT`)

## 배경

- 기획 기능 ⑤: 상황마다 답이 다른 규칙(예: 우회전)은 정해진 조건만 되묻고, 조건별 **검수된 문장**으로 답한다 — AI 가 답을 만들지 않음
- 기획 기능 ⑥: 주차 중 조건 토글을 바꾸면 상황 그림과 문장이 바뀐다
- 규모: 조건 분기 카드 PT 1장(우회전) · 출시 8장

## 결정

**1. 테이블 4개 대신 카드 안 JSON 한 칸**

| | 테이블 (조건·선택지·문장·매핑) | JSON 한 칸 ✅ |
|---|---|---|
| 무결성 | FK 로 DB 가 보장 | 저장 전 검증기가 보장 |
| 검수 단위 | 카드가 여러 테이블에 흩어짐 | 카드 버전과 분기가 한 덩어리로 고정 |
| 앱 동기화 | 조인 → 조립 필요 | 그대로 전송 (앱도 JSON 사용) |
| 검색·통계·조건 재사용 | 쉬움 | 어려움 — MVP 에 필요 없음 |
| 구현량 | 엔티티 4개 + 조립 코드 | 컬럼 1개 + 검증기 1개 |

**2. 형식 = DMN 결정 테이블, hit policy `UNIQUE`**

```json
{
  "schema": 1,
  "conditions": [
    { "key": "turn_light", "question": "우회전 신호등이 있었나요?",
      "options": [{ "key": "yes", "label": "있음" }, { "key": "no", "label": "없음" }] },
    { "key": "signal", "question": "전방 차량 신호가 빨간불이었나요?",
      "options": [{ "key": "red", "label": "빨간불" }, { "key": "green", "label": "초록불" }] }
  ],
  "branches": [
    { "when": { "turn_light": "yes" }, "text": "…", "tts": "…", "scene": "right_turn_light" },
    { "when": { "turn_light": "no", "signal": "red" }, "text": "…", "tts": "…" },
    { "when": { "turn_light": "no", "signal": "green" }, "text": "…", "tts": "…" }
  ]
}
```

- `when` 에 없는 조건 = 상관없음
- **행 순서는 의미 없음** — 한 상황에 맞는 행은 정확히 1개
- `text` 화면 문장 · `tts` 읽어 주기 문장 · `scene` 앱 번들 그림 키(선택)
- `schema` = JSON 형식 버전 (형식이 바뀌어도 예전 카드를 구분해 읽기 위함)

**3. 규칙**

- 조건 값은 **정해진 선택지(enum)만** — 수식·비교 연산자 없음
- 카드당 조건 **3개 이하** (조합 최대 8가지 — 검수 부담 상한)
- 되묻기 순서는 **저장하지 않고 계산**: `conditions` 순서대로 묻다가, 남은 후보 행이 1개가 되면 멈춤
- 팀이 답할 수 없는 조합은 보류 문장("확인된 안내가 없습니다…")을 **그 조합의 행으로 명시** — 별도 기본 응답 없음

**4. 저장 전 검증 (서버 CMS, 테스트 필수)**

- 형식: 키 중복 없음, `when` 이 없는 조건·선택지를 참조하지 않음
- **완전성**: 모든 선택지 조합이 어떤 행에 걸림
- **겹침 없음**: 어떤 조합도 두 행에 동시에 걸리지 않음
- 하나라도 실패하면 저장·게시 불가

## 이유

- **`UNIQUE` (vs `FIRST`)**: `FIRST` 는 행 순서가 결과를 바꿔, 검수자가 행 하나만 보고 "이 문장이 언제 나가는지" 알 수 없다. `UNIQUE` 는 행마다 적용 조건이 독립적이라 2인 교차 검수와 맞는다
- **enum 만**: 표현식 언어를 넣으면 검수 대상이 '문장'에서 '로직'으로 넓어지고 파서·검증이 커진다
- **순서 계산**: 질문 표시 조건을 따로 저장하면 결정 테이블과 어긋날 수 있다 (SurveyJS 도 없는 질문을 참조하는 조건이 그대로 로드된다고 경고)
- **룰 엔진 라이브러리 안 씀**: 카드 8장 × 조건 3개 규모는 검증기 + 매칭 함수로 충분

## 다시 검토할 때

- 여러 카드가 같은 조건(예: "전방 신호")을 공유해야 할 때 → 조건 테이블 분리
- 조건 단위 검색·통계가 필요할 때, 카드가 수십 장 이상으로 늘 때 → 정규화 테이블
- 수치 조건(속도, 시간대 등)이 필요할 때 → 구간을 enum 으로 나눌 수 있는지 먼저 검토

## 참고

- [DMN Hit Policy — Camunda](https://docs.camunda.org/manual/7.22/reference/dmn/decision-table/hit-policy)
- [Semantics and Analysis of DMN Decision Tables — 완전성·겹침 검사 (Calvanese et al., 2016)](https://arxiv.org/pdf/1603.07466.pdf)
- [docassemble Interview logic — 필요한 질문만 묻기](https://docassemble.org/docs/logic.html)
- [SurveyJS Survey JSON Validation — 조건 참조 오류 경고](https://surveyjs.io/form-library/documentation/survey-json-validation)
