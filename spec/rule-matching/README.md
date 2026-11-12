# 조건별 답 고르기 명세 (Gleo `ask_rule`)

서버와 차량 앱이 **같은 결과**를 내야 하는 규칙이다. 결정 [009](../../docs/decisions/009-core-features-v6-alignment.md) B-3 · F.

- 앱: Gleo `ask_rule` 에 답할 때 차량 사본(`cached_card.decision_json`)으로 이 규칙을 돌린다 — **실제 사용처**
- 서버: `server/.../card/RuleMatcher.java` — 참조 구현. 이 폴더의 테스트 케이스로 검증한다
- 규칙을 바꾸면 이 문서 · `cases.json` · 두 구현을 한 PR 에서 함께 바꾼다

## 입력

- `decision`: 카드의 결정 테이블 (형식 2 — 소표 `tables[]`, 각 소표에 `conditions` 와 `rows`)
- `given`: Gleo 가 채워 준 조건 `{조건 키: 값}`. 운전자가 '잘 모르겠어요'라고 한 조건은 **넣지 않는다**

조건 키와 값은 공통 조건 목록(`RuleCondition`, 동기화 응답의 `conditions`)에 있는 것만 쓴다.

결정 테이블은 서버 검증기를 통과한 것만 온다 — 소표 **2개 이하**, 소표마다 모든 조합이 정확히 한 행에 걸림.
이 전제 덕분에 아래 4번에서 되물을 조건이 항상 있다 (후보가 3개 이상이면 후보가 둘 이상인 소표가 있고,
그 두 행은 아직 받지 않은 조건에서 반드시 갈린다). 찾지 못하면 표가 잘못된 것이니 오류로 처리한다.

## 규칙

1. `given` 의 키가 하나도 들어 있지 않은 소표는 건너뛴다
2. 남은 소표마다, 행의 `when` 이 `given` 과 어긋나지 않는 행을 모두 후보로 모은다
   - 어긋남 = `when` 과 `given` 에 같은 키가 있는데 값이 다름. `given` 에 없는 조건은 '모름'이라 어긋나지 않는다
   - 같은 `text` 는 하나로 친다 (처음 나온 행을 남김, 소표 순서 → 행 순서)
3. 후보가 **2개 이하** → `ANSWER` (후보 모두)
4. 후보가 3개 이상, 또는 1번에서 남은 소표가 없음 → `ASK`
   - 남은 소표가 없으면: 첫 소표의 첫 조건
   - 아니면: 남은 소표 순서 → 소표의 `conditions` 순서대로 보면서, `given` 에 없고 **그 소표 후보 행의 `when` 값이 둘 이상으로 갈리는**(값이 없는 행도 하나의 값으로 침) 첫 조건

## 출력 → Gleo 응답 (앱)

| 결과 | Gleo callback `status` | `data` |
|---|---|---|
| `ANSWER` | `success` | 후보 행의 `tts` (읽어 주기 문장) |
| `ASK` | `asking` | `{"missingField": "<조건 키>"}` |

카드가 없거나(대표 카드 없음) 보류 행이면 앱은 답변 보류 문장으로 답하고 `POST /api/v1/answer-holds` 로 신고한다.

## 테스트 케이스 (`cases.json`)

```json
{
  "decisions": { "<이름>": <결정 테이블> },
  "cases": [
    { "name": "…", "decision": "<이름>", "given": { … },
      "expect": { "status": "ANSWER", "texts": ["…"] } },
    { "name": "…", "decision": "<이름>", "given": { … },
      "expect": { "status": "ASK", "ask": "<조건 키>" } }
  ]
}
```

- `texts` 는 순서까지 맞아야 한다
- 결정 테이블의 문장은 **테스트용 표지**(`approach.no_red` 등)다. 실제 검수 문장이 아니다
