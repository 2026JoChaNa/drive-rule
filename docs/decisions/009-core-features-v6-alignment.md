# 009. 핵심 기능 두 개에 맞춘 재정렬 (기획 v6) — 이벤트 태그 · 조건 소표 · 앱 사본 우선

- 날짜: 2026-11-12
- 상태: **초안** — B·F 와 '기획 확인' 표시 항목은 기획 회신 대기. **B-2 공통 조건 목록은 Gleo App Schema 제출(11/16 주) 전에 확정해야 한다**
- 기준: Notion 「🚦 기획 v6」(2026-11-11) · 팀 핵심 기능 정의(아래 '배경')
- 관련: [001](001-remove-driving-context.md) · [002](002-condition-branch-format.md) · [003](003-no-telemetry-in-mvp.md) · [004](004-card-lifecycle.md) · [005](005-screen-check-v5-2.md) · [008](008-server-api-v1.md)

## 배경

팀이 처음 정한 핵심 기능은 두 개다.

1. **이벤트가 생기기 전에 알려 주기** — 특정 주행 이벤트가 일어나기 직전에, 그에 맞는 법령 정보를 미리 전달한다
2. **물으면 답하기 (Gleo)** — 운전자가 Gleo 에 물으면 Gleo 가 대화로 조건을 모아 넘기고, 우리는 검수된 법령 정보만 돌려준다

v6 는 이를 구체화했다: 이벤트 3개(어린이보호구역 진입 · 주정차 · 우회전 후 횡단보도), 안내 2단계(공포 시 · 시행 후), 카드 종류(법령 개정 / 운영 변경), Gleo function 3개(`list_rule_changes` · `ask_rule` · `confirm_rule`).

008 은 v5 시절 결정 기록과 Figma(섹션 `현대차`)를 기준으로 만들어 두 기능 어느 쪽에도 직접 답하지 못한다. 이 기록이 그 차이를 정리한다.

## 조사에서 확인한 제약 (Pleos 문서, 2026-11-12)

| 사실 | 출처 | 영향 |
|---|---|---|
| Gleo 는 앱 응답을 **7초** 안에 받지 못하면 취소한다 | [Callback 가이드](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/action_callback) | F |
| 앱 응답은 `status`(success / asking / fail) · `message` · `data` 이고, **Gleo 가 이를 바탕으로 최종 문장을 새로 만든다** | 같은 문서 · [핵심 개념](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts) | F (검수 문장이 바뀌어 말해질 위험) |
| `asking` 으로 빠진 값을 되물을 수 있다 (`data` 에 `missingField`) | Callback 가이드 | B-3 |
| 파라미터 타입은 string · number · boolean · array 만. **중첩 객체 없음**. enum 은 문자열만. `required` 를 Gleo 가 다 채운다는 보장은 없음 | [App Schema 가이드](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/app-capability) | B-2 |
| 스키마는 앱의 `assets/schema.json` 으로 들어가 Pleos 심사를 받고, Gleo 가 **부팅 때 한 번** 읽는다. 제출 후 수정 절차는 문서에 없다 | [Schema 전달](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/deliver-schema) | B-2 (조건 이름·값이 사실상 고정) |

## 결정

### A. 이벤트 태그 — 카드당 하나 (`rule_card.event_tag`, NULL 허용)

| 값 | 이벤트 (v6 1-3) | 시연 카드 |
|---|---|---|
| `SCHOOL_ZONE_ENTRY` | ① 어린이보호구역 진입 | H, G(P1) |
| `PARKING_DESTINATION` | ② 주정차 · 목적지 | B |
| `PARKING_HIGHWAY` | ② 주정차 · 고속도로 | A |
| `RIGHT_TURN_CROSSWALK` | ③ 우회전 후 횡단보도 | I |
| NULL | 이벤트 없음 — Gleo 답변 전용 | 우회전 조건별 답변 등 |

- 카드 문장은 이벤트 상황에 맞춰 쓰인다(예: B "목적지 근처는 …"). 같은 법령을 두 이벤트에서 알려야 하면 **문장이 다른 카드 두 장**을 만들어 각각 검수한다
- 사례: DATEX II 는 상황 기록 하나에 원인 하나([DATEX II](https://docs.datex2.eu/v3.3/situation)). TPEG2-TEC 는 여러 원인 허용 — 우리는 문장이 상황에 묶이므로 DATEX II 쪽을 따른다
- 한 이벤트에 여러 카드(H · G) → 차가 주행 1회 최대 2건 규칙(v6 1-1)으로 고른다. 이벤트 안 우선순위가 필요해지면 그때 칸을 더한다

### B. 조건 분기 — 카드 하나에 소표 여러 개 (002 개정)

**B-1. 소표 안은 UNIQUE, 소표끼리는 모아서(COLLECT) 답한다**

v6 2-4 우회전 표 5행은 판단 두 개가 섞여 있다: 1~3행 = 교차로 진입 전(우회전 신호등 · 전방 신호), 4~5행 = 돌아선 뒤 횡단보도(보행 신호 · 보행자). 그래서 조건이 4개가 되고 행이 겹친다.

- 3행(전방 녹색)과 4·5행(보행 녹색)은 **함께 해당하는 것이 맞다** → 함께 답한다
- 1행(우회전 신호등 있음)과 3행(전방 녹색)은 **답이 부딪힌다** → 3행에 `turn_light = no` 가 빠진 것이다 **(기획 확인)**

DMN 은 겹치지 않는 표에 UNIQUE, 해당 결과를 모두 모을 때 COLLECT 를 쓴다([Camunda hit policy](https://docs.camunda.org/manual/7.22/reference/dmn/decision-table/hit-policy), [선택 기준](https://camunda.com/best-practices/choosing-the-dmn-hit-policy/)). 소표로 나누면 소표마다 002 의 규칙(UNIQUE · 완전성 · 조건 3개 이하)을 그대로 지켜, 검수자가 행 하나만 보고 "이 문장이 언제 나가는지" 알 수 있다.

`decision_json` 형식 2 (예시 — 문장은 v6 2-4 초안):

```json
{
  "schema": 2,
  "tables": [
    { "key": "approach", "title": "교차로 진입 전",
      "conditions": ["turn_light", "front_signal"],
      "rows": [
        { "when": { "turn_light": "yes" }, "text": "녹색 화살표가 켜졌을 때만 우회전합니다", "tts": "…" },
        { "when": { "turn_light": "no", "front_signal": "red" }, "text": "보행자가 없어도 정지선 앞에서 먼저 멈춘 뒤 천천히 우회전합니다", "tts": "…" },
        { "when": { "turn_light": "no", "front_signal": "green" }, "text": "천천히 우회전할 수 있습니다. 돌아서 만나는 횡단보도에 사람이 있으면 멈춥니다", "tts": "…" }
      ] },
    { "key": "crosswalk", "title": "돌아선 뒤 횡단보도",
      "conditions": ["ped_signal", "pedestrian"],
      "rows": [
        { "when": { "ped_signal": "green", "pedestrian": "yes" }, "text": "횡단보도 앞에서 멈춥니다", "tts": "…" },
        { "when": { "ped_signal": "green", "pedestrian": "no" }, "text": "주위를 살피고 천천히 지나갈 수 있습니다", "tts": "…" },
        { "when": { "ped_signal": "red" }, "text": "(검수 문장 필요)", "tts": "…" }
      ] }
  ]
}
```

- 조건 정의(질문 · 선택지)는 카드에 두지 않고 **공통 조건 목록**(B-2)을 참조한다
- 완전성 검사가 v6 표의 빈칸을 찾아냈다: **보행 신호 적색일 때의 행이 없다 (기획 확인 — 검수 문장 필요)**. 보행 신호가 적색이어도 건너는 사람이 있으면 제27조 일시정지 의무는 남는다
- 소표 수 상한 **2** · 소표당 조합 8 가지(002 와 같은 검수 부담 상한)
  - 소표 수 상한은 B-3 의 '한 번에 답하는 문장 2개'와 같아야 한다. 소표 3개가 각각 한 행으로 좁혀지면 되물을 조건 없이 문장이 3개가 된다 (코드 리뷰, 2026-11-12)

**B-2. 조건 이름·값은 공통 목록으로 고정한다 (App Schema 제출 전 확정)**

Gleo 파라미터는 중첩 객체를 받지 못하므로 v6 의 `conditions` 는 **조건마다 최상위 파라미터 하나**(문자열 enum)가 된다. 스키마는 앱에 들어가 심사를 받으니, 조건 이름·값은 카드마다 정하지 않고 코드의 공통 목록으로 고정한다. 검증기는 목록에 없는 조건을 거부한다.

제안 (v6 2-4 에서 뽑음):

| 파라미터 | 뜻 | 값 |
|---|---|---|
| `turn_light` | 우회전 신호등이 있나 | `yes` / `no` |
| `front_signal` | 전방 차량 신호 | `red` / `green` |
| `ped_signal` | 돌아선 뒤 횡단보도 보행 신호 | `green` / `red` |
| `pedestrian` | 보행자가 건너거나 건너려 하나 | `yes` / `no` |

- 노란불 · 점멸 신호 같은 값은 v6 에 없어 넣지 않았다 **(기획 확인)**
- 새 조건 = 앱 업데이트 + Pleos 심사. 출시 카드 27장 기준으로 다시 본다
- `topic` 도 enum 으로 고정하는 것을 제안한다 — Gleo 가 고르기 쉽고, 카드가 정해진 MVP 범위에 맞다. **topic 목록은 기획이 준다**

**B-3. 조건이 비었을 때 — 해당 소표의 후보를 함께 답하고, 많으면 되묻는다**

1. 받은 조건이 하나도 걸리지 않는 소표는 건너뛴다
2. 남은 소표에서 받은 값과 어긋나지 않는 행을 모두 후보로 모은다 (같은 문장은 하나로)
3. 후보 ≤ 2 → `success` 로 모두 답한다 (v6 2-4 대화 예시: 보행 신호 녹색만 → 4·5행)
4. 후보 ≥ 3, 또는 걸린 소표가 없음 → `asking`. 되물을 조건은 소표 순서 · 조건 순서대로, 후보를 가르는 첫 조건 (002 '순서는 저장하지 않고 계산' 유지)

- 후보 2개 상한은 음성 안내 5초 이내(v6 1-1)를 고려한 값이다 **[추론]**
- Figma 6-A '잘 모르겠어요' = 그 조건을 비워 둔 것과 같다 → 별도 선택지 없이 위 규칙으로 처리된다 (008 의 열린 질문 해소)

### C. 일부 운전자 대상 — 카드 단위 `audience` (`GENERAL` / `LIMITED`)

- v6 2-3: 일부 운전자 대상 항목은 기본 목록에서 빼고, 물으면 답한다
- 제22067호 하나에 일반 대상(고속도로 무인단속)과 일부 대상(재범자 조건부 면허)이 함께 있다 → 법령 개정 단위(`triage_status`)로는 나눌 수 없어 **카드 단위**로 둔다
- `LIMITED` 카드는 바뀐 규칙 목록 · 이벤트 안내에서 빠지고 `ask_rule` 로만 답한다
- 001 과 충돌하지 않는다 — 001 은 운전자 입력(프로필)으로 거르기를 뺀 것이고, 이 칸은 운전자 입력 없이 '목록에 보이나'만 정한다

### D. 다시 알림 기준 — `version` 과 분리

- 법령 개정 카드: v6 대로 시행일 0시에 확인 카운트 초기화 → `effective_on` 으로 계산, 칸 불필요
- 운영 변경 카드: "운영 시간·속도가 바뀔 때"만 다시 알린다. `version` 은 오타 수정에도 오르므로 기준으로 쓰면 같은 안내가 반복된다
- 사례: 개인정보 처리방침도 '중요한 변경'만 알리고, 중요성은 운영자가 정한다([Termly](https://termly.io/faq/how-much-notice-do-i-need-to-give-for-a-change-in-privacy-policy/))
- 결정: CMS 수정 화면의 '운전자에게 다시 알림' 체크로만 오르는 번호를 둔다. **운영 변경 카드(G, P1)를 만들 때 추가** — 지금은 이 원칙만 남긴다
- 안내 단계 2개(공포 · 시행 후)는 v6 대로. 차량 DB `notice_log.stage` 값을 고친다 (005 '함께 정한 것'의 3단계를 대체)

### E. 주차 중 자유 질문 매칭 — 삭제

- 핵심 기능 ② 에서 질문 이해는 Gleo 가 하고 앱은 `topic` 을 받는다. 서버 문장 매칭(008 결정 4, `POST /api/v1/questions`, `BigramQuestionMatcher`)을 뺀다
- '답변 보류' 기록은 남긴다 (대표 카드가 없거나 보류 행에 걸린 경우). **질문 원문은 보내지 않고 `topic` · 조건만** — Gleo 발화에 위치가 섞일 수 있다(v6 "위치는 보내지 않습니다")
- `review_request.question_text` 는 이 경로에서 쓰지 않게 된다 → ERD 갱신 때 함께 정리

### F. Gleo 답변은 앱 사본으로 — 서버 조회 API 를 만들지 않는다 **(기획 확인)**

v6 2-1 은 "앱이 서버 API 를 호출하고, 닿지 못하면 사본으로"다. 위 제약과 함께 보면:

- 7초 안에 Gleo 처리 + 차량 통신 + 서버 왕복을 끝내야 한다. 터널 등에서 실패한다
- 실패 시 사본으로 답해야 하므로 **앱에는 매칭 로직이 반드시 있다.** 서버에도 두면 같은 로직이 두 벌이 된다
- 두 벌이 어긋나면 **통신 상태에 따라 같은 질문에 다른 답**이 나간다 — 검수 원칙에 가장 나쁜 실패

결정:

- `list_rule_changes` · `ask_rule` 는 **앱이 차량 사본(`cached_card`)으로 답한다.** 질문할 때 서버로 아무것도 보내지 않는다 (v6 보다 개인정보 노출이 적다)
- 서버 역할 = 검수 CMS · 동기화 · 신고 접수(오류 신고 · 답변 보류)
- 사본 신선도: 동기화를 하루 1회 + **시동 시**. 회수된 카드가 차에 남는 시간은 길어야 주행 1회
- 일관성: 매칭 명세(B-3)와 **공용 테스트 케이스 JSON**(입력 · 기대 출력)을 저장소에 두고, 서버 검증기 테스트와 앱 테스트가 같은 파일을 돌린다. DMN 진영이 여러 구현을 같은 테스트로 맞추는 방식이다([DMN TCK](https://social-biz.org/2017/01/18/dmn-technology-compatibility-kit-tck/))
- 사례: 로컬 우선 설계는 기기에 있는 데이터로 기다림 없이 동작하는 것을 첫 원칙으로 둔다([Ink & Switch, Local-first software](https://inkandswitch.com/local-first/))

운전자가 보는 동작은 v6 와 같다. 바뀌는 것은 v6 2-1 의 3·4번 문구(호출 대상)다.

**함께 제안 — 11/18 기술 검증 항목 추가**: Gleo 는 앱 응답으로 문장을 새로 만든다. 응답 `mode` 를 `silent` 로 두고 **우리 TTS 가 검수 문장(`tts`)을 그대로 읽을 수 있는지** 확인한다. 안 되면 v6 6장 '녹음 대조' 로 바꿔 말하기 정도를 잰다.

### G. 동기화 — 시각 대신 불투명 토큰

- 사례: Google Calendar 는 `syncToken`(속을 알 수 없는 문자열)을 주고, 토큰이 무효면 410 으로 전체 동기화를 시킨다. 삭제 항목도 증분 응답에 담는다([Google 동기화 가이드](https://developers.google.com/calendar/api/guides/sync))
- 회수를 ID 로 보내는 것(004)은 이미 같은 방식이다
- 결정: `nextSince`(시각) → `syncToken`(문자열). 서버가 '전체 다시 받기'를 지시할 수 있게 한다 — 서버 구현을 바꾸거나 카드 형식이 바뀔 때(B 의 `schema: 2`) 앱을 고치지 않아도 된다

### H. 카드 종류 · 출처

- `rule_card.change_kind` = `LAW` / `OPERATION` (v6 '카드 종류': 법령 개정 / 운영 변경)
- 운영 변경 카드는 `law_revision` 과 잇지 않는다 → `source_org`(출처 기관, 예: "경찰청 · 성남시") 추가. 화면에 "법령 개정 아님 · 출처 기관" 표시 (v6 1-2)
- 운영 구간 좌표는 보호구역 목록처럼 CDN 파일 — P1 이라 나중에
- `card_type` 은 **문장 형식만** 나타내도록 정리: `SHORT` / `CONDITIONAL` / `FIXED_RESPONSE`. `SCHOOL_ZONE` 은 이벤트 태그(A)와 뜻이 겹쳐 뺀다
- 단문(`SHORT`)의 두 장면(`dont_text` · `do_text`)은 **둘 다 있거나 둘 다 없음**으로 완화한다 (005 는 둘 다 필수). v6 이벤트 카드 A · B · H · I 는 음성 한 문장 + 근거뿐이고 두 장면이 없다

## 기획 확인 요청

| # | 항목 | 마감 |
|---|---|---|
| 1 | B-2 공통 조건 목록 (4개 조건과 값, 노란불 등 추가 여부) | **App Schema 제출 전 (11/16 주)** |
| 2 | B-2 `topic` 목록 | **App Schema 제출 전 (11/16 주)** |
| 3 | B-1 우회전 3행에 '우회전 신호등 없음' 조건 추가 | 카드 검수 전 |
| 4 | B-1 보행 신호 적색 행의 검수 문장 | 카드 검수 전 |
| 5 | F Gleo 답변을 앱 사본으로 (v6 2-1 3·4번 문구) | 앱 Gleo 구현 전 |
| 6 | F 11/18 검증에 'silent + 우리 TTS' 추가 | 11/18 |

## 영향

- **ERD**: `rule_card` + `event_tag` · `audience` · `change_kind` · `source_org`, `card_type` 값 정리, `decision_json` 형식 2. `cached_card` 같은 칸. `notice_log.stage` 값 2개. `review_request.question_text` 정리
- **서버 (008 대체 부분)**: `POST /api/v1/questions` · 매칭기 삭제 → 답변 보류 신고 API. 결정 테이블 검증기 형식 2 + 공통 조건 목록. 동기화 `syncToken`. 시연 카드 A · B · H · I 시드
- **앱**: B-3 매칭과 바뀐 규칙 목록을 사본으로 구현. 공용 테스트 케이스를 앱 테스트에서도 돌림
- 002 의 '조건 정의는 카드 안', '카드당 조건 3개' 는 이 기록 B 로 대체된다 (확정 시 002 에 표시)

## 구현 (2026-11-12, 브랜치 `feat/server-api-v1`)

기획 확인이 필요 없는 부분을 먼저 반영했다. B 는 **초안 값**으로 넣었다 — 기획 회신에 따라 `RuleCondition` 과 시드만 고치면 된다.

| 결정 | 반영 |
|---|---|
| A · C · H | `rule_card` + `change_kind` · `audience` · `event_tag` · `source_org`, `card_type` 값 정리. 칸 사이 규칙은 `CardValidator` (운영 변경 → 출처 기관 필수 · 일부 대상 → 이벤트 없음) |
| B | `decision_json` 형식 2 + `DecisionTableValidator`(소표별 UNIQUE · 완전성). 공통 조건 목록 `RuleCondition`(초안 4개) — 동기화 응답 `conditions` 로 앱 되묻기 문구까지 보낸다 |
| B-3 · F | 명세 [`spec/rule-matching/README.md`](../../spec/rule-matching/README.md) + 공용 테스트 케이스 `cases.json`. 서버 참조 구현 `RuleMatcher` 가 `RuleMatcherSpecTest` 로 이 파일을 돌린다. 앱도 같은 파일을 돌린다 |
| E | `POST /api/v1/questions` · 매칭기 삭제 → `POST /api/v1/answer-holds` (topic + 공통 조건 키·값만, 목록 밖 값은 422). `review_request.question_text` → `topic` · `conditions_json` |
| G | `GET /api/v1/cards/sync?syncToken=` — 불투명 토큰, 무효면 410 `SYNC_TOKEN_EXPIRED`, `fullSync` 표시. 토큰 형식 번호 2 (형식 2 카드라 예전 토큰은 전체 동기화) |
| 시연 | `server/seed/pt-cards.json`(A · B · H · I + 우회전) + `seed.mjs` — CMS API 로 **DRAFT** 로 넣는다. v6 에 없는 값(법령 원문 · 조문 번호)은 '확인 필요'로 비워 두었다. `SeedCardsTest` 가 시드를 카드 규칙으로 검사한다 |
| D | 원칙만 기록 (운영 변경 카드 G 를 만들 때 칸 추가) |

## 다시 검토할 때

- 한 문장이 두 이벤트에 그대로 맞는 카드가 많아질 때 → 이벤트 태그를 별도 테이블로 (A)
- 출시 카드 27장에서 공통 조건이 늘어 스키마 재심사가 잦아질 때 → 조건 파라미터를 일반화(예: `condition_key` + `condition_value` 배열)할지 검토 (B-2)
- Gleo 응답 시간 · 통신 품질이 충분하고 서버 쪽 최신성이 중요해질 때 → 서버 조회 API (F). 이때도 공용 테스트 케이스로 두 구현을 묶는다
