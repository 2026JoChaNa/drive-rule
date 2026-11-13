# Gleo App Schema

[`schema.json`](schema.json) 은 차량 앱이 `assets/schema.json` 으로 넣어 Pleos 심사를 받는 파일이다. 결정 [012](../../docs/decisions/012-gleo-app-schema-draft.md).

- 상태: 확정 (2026-11-13, 012 확인표 6개). 앱에 `assets/schema.json` 으로 복사를 요청한다
- 고칠 때: 서버 `GleoAppSchemaTest` 가 가이드 형식 · 공통 조건 목록(`RuleCondition`) 일치 · 시드 topic 포함을 검사한다. 조건을 바꾸면 `RuleCondition` · [`../rule-matching/cases.json`](../rule-matching/cases.json) 도 한 PR 에서 함께 바꾼다
- 작성 규칙: [Pleos App Schema 작성 가이드](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/app-capability) — function 이름 대문자 · 밑줄, description 영어 1~2문장 · 예시 금지, 파라미터 type 하나 · enum 은 문자열

## function 과 앱 응답

| function | 파라미터 | 앱이 하는 일 |
|---|---|---|
| `LIST_RULE_CHANGES` | `since`(YYYY-MM) 또는 `period`(`this_month` / `upcoming`), 둘 다 선택 | 차량 사본에서 일반 운전자 해당 카드를 시행일 순으로. 둘 다 비면 `asking` + `missingField: "since"` |
| `ASK_RULE` | `topic`(필수) + 공통 조건 4개(선택) | topic 의 대표 카드를 [매칭 명세](../rule-matching/README.md)로. 카드가 없으면 답변 보류 + `POST /api/v1/answer-holds` |
| `CONFIRM_RULE` | `action`(`confirm` / `repeat`) | 방금 읽어 준 카드를 확인 처리하거나 다시 읽음 (카드 ID 는 받지 않음 — 012 결정 4) |

조건 파라미터를 Gleo 가 비워 보내면 '모름'이다. 별도 '모름' 값은 없다.
