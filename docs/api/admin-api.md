# 검수 CMS API 사용 설명서 (v1)

검수자와 운영자가 **법령 개정 → 카드 작성 → 2인 교차 검수 → 게시 → 회수**를 하는 방법입니다. CMS 화면은 아직 없어서 이 API 를 직접 부릅니다.

- 기준: 2026-11-13, `main`
- 판단 근거: [004 카드 수명주기](../decisions/004-card-lifecycle.md) · [006 대표 주제](../decisions/006-topic-primary-card.md) · [009](../decisions/009-core-features-v6-alignment.md) · [011 법령 수집](../decisions/011-law-revision-collector.md)
- 실제 스펙: Swagger UI `/swagger-ui.html` (엔드포인트 · 필드 목록). 이 문서는 **순서와 규칙**을 설명합니다
- 이 문서의 예시 요청은 통합 테스트 `AdminManualTests` 가 같은 순서로 돌려 확인합니다. 문서를 고치면 테스트도 함께 고쳐 주세요
- 차량 앱이 부르는 API 는 [app-api.md](app-api.md)

---

## 0. 먼저 알아 둘 것

### 주소와 키

| 환경 | 주소 | 키 |
|---|---|---|
| 운영 (EC2) | `http://13.124.175.42:8080` | EC2 `~/drive-rule/.env` 의 `ADMIN_API_KEY` |
| 로컬 | `http://localhost:8080` | `bootRun` 전에 정한 `ADMIN_API_KEY` (예: `local`) |

모든 요청에 헤더 `X-Admin-Key: <키>` 를 붙입니다. 틀리거나 없으면 `401 ADMIN_KEY_REQUIRED` 입니다. 서버에 키가 설정되지 않았으면 CMS API 전체가 401 입니다.

- **키를 채팅 · 이슈 · 커밋에 붙이지 마세요.** 아래 예시처럼 셸 변수에 넣어 쓰세요
- 운영은 HTTP 라 키가 평문으로 오갑니다 (HTTPS 는 보류, 2026-11-12). 공용 Wi-Fi 에서는 부르지 마세요

예시는 Git Bash · macOS · Linux 셸 기준입니다.

```bash
BASE=http://localhost:8080/api/v1/admin
KEY=local                       # 운영: KEY=$(ssh drive-rule 'grep "^ADMIN_API_KEY=" ~/drive-rule/.env | cut -d= -f2')
H=(-H "X-Admin-Key: $KEY" -H "Content-Type: application/json")
```

### 형식

- JSON, UTF-8. 날짜는 `2026-10-13`, 시각은 `2026-11-13T09:00:00` (서울 시각, 시간대 표기 없음)
- 오류는 [app-api.md 5장](app-api.md#5-오류-응답-형식)과 같은 모양입니다: `{ "status", "title", "detail", "code", "errors": [] }`. **`code` 로 판단**하고, `errors` 에 칸별 이유가 들어 있습니다

### 한눈에 보기

```
법령 개정 ─(분류)─▶ APPLICABLE ─┐
                                ▼
            카드 작성 ──▶ DRAFT ──검수 요청──▶ IN_REVIEW ──서로 다른 검수자 승인 2건──▶ PUBLISHED ──▶ 앱 동기화에 나옴
                           ▲                    │ 반려 1건                              │ 수정
                           └────────────────────┴───────────────────────────────────────┘
            (RETIRED 가 아니면 어느 상태에서든) ──회수──▶ RETIRED (끝, 앱에서 내려감)
```

| 하는 사람 | 하는 일 | 장 |
|---|---|---|
| 운영자 | 검수자 등록 · 법령 개정 등록 / 분류 · 수집 | 1 · 2 |
| 작성자 | 카드 작성 · 수정 · 검수 요청 · 대표 주제 지정 | 3 |
| 검수자 2명 | 승인 / 반려 | 3-5 |
| 운영자 | 회수 · 신고 처리 | 3-8 · 4 |

---

## 1. 검수자

```bash
curl -s "${H[@]}" -X POST $BASE/reviewers -d '{"name":"검수자1"}'
# 201 {"id":1,"name":"검수자1"}
curl -s "${H[@]}" $BASE/reviewers
# 200 [{"id":1,"name":"검수자1"}, …]
```

- 이름은 1~50자. 같은 이름도 등록됩니다 — 검수는 **id** 로 구분합니다
- 서버에는 로그인이 없습니다. 검수할 때 `reviewerId` 를 직접 넣으므로, **각자 자기 id 만 쓰는 것은 사람이 지키는 규칙**입니다
- 서버는 카드 작성자를 기록하지 않습니다. **작성한 사람은 그 카드를 검수하지 않는다**도 사람이 지키는 규칙입니다

---

## 2. 법령 개정

카드의 출처가 되는 개정 1건 = 1행입니다. 분류(`triageStatus`)로 "일반 운전자에게 해당하는가"를 정합니다 (기획 기능 ⑦).

### 2-1. 자동 수집 · 지금 수집

매일 06:30 에 서버가 도로교통법 · 시행령 · 시행규칙의 최근 공포분을 받아 **없는 공포 번호만** `PENDING` 으로 넣습니다 (011). 바로 돌리려면:

```bash
curl -s "${H[@]}" -X POST $BASE/law-revisions/sync
```

```json
{ "from": "2026-10-01", "to": "2026-11-13",
  "laws": [
    { "lawId": "001638", "outcome": "OK", "lawName": "도로교통법", "created": ["제22100호"], "existing": 1, "error": null },
    { "lawId": "007079", "outcome": "FAILED", "lawName": null, "created": [], "existing": 0, "error": "Open API 거절: …" }
  ] }
```

- 법령마다 따로 성공 · 실패합니다. `FAILED` 의 `error` 를 보고 다시 돌리면 됩니다 (이미 넣은 것은 `existing` 으로 셉니다)
- 인증값(`LAW_API_OC`)이 없으면 `503 LAW_API_NOT_CONFIGURED`. **2026-11-13 운영은 인증값이 없어 꺼져 있습니다**

### 2-2. 손으로 등록

```bash
curl -s "${H[@]}" -X POST $BASE/law-revisions -d '{
  "lawName": "도로교통법 일부개정", "lawNo": "제22067호",
  "sourceKey": "MANUAL-22067", "promulgatedOn": "2026-10-13" }'
# 201 {"id":1, …, "triageStatus":"PENDING", "sourceUrl":null}
```

| 칸 | 규칙 |
|---|---|
| `lawNo` | 공포 번호. **이미 있으면 `409 LAW_NO_DUPLICATED`** — 수집기가 넣은 것과도 겹치지 않습니다 |
| `sourceKey` | 어디서 왔는지. 손 등록은 `MANUAL-<번호>`. 수집기는 `LAWGO-<법령일련번호>` 를 쓰고, 그 행에만 원문 링크 `sourceUrl` 이 붙습니다 |
| `lawName` | "{법령명} {제개정구분}" (예: `도로교통법 시행령 일부개정`) |

### 2-3. 목록 · 분류

```bash
curl -s "${H[@]}" "$BASE/law-revisions?triageStatus=PENDING"     # 분류 대기만 (공포일 최신순)
curl -s "${H[@]}" -X PATCH $BASE/law-revisions/1/triage -d '{"triageStatus":"APPLICABLE"}'
```

| 값 | 뜻 | 카드 |
|---|---|---|
| `PENDING` | 분류 전 | 이을 수 있음 |
| `APPLICABLE` | 일반 운전자 해당 — 카드를 쓴다 | 이을 수 있음 |
| `NOT_APPLICABLE` | 해당 없음 (서식 · 면허 시험 · 기관 내부 절차 등) | **이을 수 없음** — 카드 작성 · 수정 때 `409 LAW_REVISION_NOT_APPLICABLE` |

분류는 언제든 다시 바꿀 수 있습니다. 이미 이어진 카드는 그대로 둡니다.

---

## 3. 카드

### 3-1. 작성

```bash
curl -s "${H[@]}" -X POST $BASE/cards -d '{
  "revisionId": 1,
  "cardType": "SHORT", "changeKind": "LAW", "audience": "GENERAL", "eventTag": "SCHOOL_ZONE_ENTRY",
  "title": "보호구역 신호등 없는 횡단보도 일시정지",
  "ttsText": "어린이보호구역입니다. 신호등 없는 횡단보도 앞에서는 사람이 없어도 먼저 멈춥니다.",
  "legalBasis": "도로교통법 제27조제7항",
  "lawExcerpt": "제27조(보행자의 보호) ⑦ …",
  "penaltyText": "승용차 범칙금 6만 원 · 벌점 10점",
  "effectiveOn": "2022-07-12" }'
# 201 {"id":7, …, "status":"DRAFT", "version":1}
```

카드 1장 예시는 [`server/seed/pt-cards.json`](../../server/seed/pt-cards.json) 이 가장 정확합니다 (시연 카드 5장). 시드를 한꺼번에 넣는 방법은 [README](../../README.md) '시연 카드 넣기'.

**칸**

| 칸 | 필수 | 최대 | 뜻 |
|---|---|---|---|
| `cardType` | ✓ | | `SHORT` 단문 · `CONDITIONAL` 조건 분기 · `FIXED_RESPONSE` 고정 응답 |
| `changeKind` | ✓ | | `LAW` 법령 개정 · `OPERATION` 운영 변경(법령 아님) |
| `audience` | ✓ | | `GENERAL` 일반 운전자 · `LIMITED` 일부 운전자 |
| `eventTag` | | | 미리 알려 줄 이벤트: `SCHOOL_ZONE_ENTRY` · `PARKING_DESTINATION` · `PARKING_HIGHWAY` · `RIGHT_TURN_CROSSWALK`. 비우면 Gleo 답변 전용 |
| `revisionId` | | | 2장의 법령 개정 id |
| `sourceOrg` | | 100 | 운영 변경 카드의 출처 기관 (예: `경찰청 · 성남시`) |
| `topic` | | 40 | Gleo 대표 주제 — 3-7 참고 |
| `title` | ✓ | 100 | 화면 제목 |
| `ttsText` | ✓ | 500 | 읽어 주는 한 문장 (5초 이내 목표) |
| `legalBasis` | ✓ | 200 | 근거 조문 |
| `lawExcerpt` | ✓ | 20000 | 법령 원문 발췌 |
| `penaltyText` | | 300 | 처분 (범칙금 · 과태료 · 벌점) |
| `effectiveOn` | | | 시행일 |
| `exceptions` | | 20000 | 예외 |
| `dontText` · `doText` | | 300 | 단문의 두 장면 (하지 말 것 / 할 것) |
| `sceneKey` | | 40 | 장면 그림 키 |
| `decision` | | | 조건 분기 결정 테이블 — 3-2 |

빈 문자열은 '없음'으로 저장합니다.

**칸 사이 규칙** — 어기면 `422 CARD_INVALID`, `errors` 에 칸별 이유

| 경우 | 규칙 |
|---|---|
| 단문 (`SHORT`) | `dontText` · `doText` 는 둘 다 쓰거나 둘 다 비움. `decision` 없음 |
| 조건 분기 (`CONDITIONAL`) | `decision` 필수. `dontText` · `doText` · `sceneKey` 는 비움 |
| 고정 응답 (`FIXED_RESPONSE`) | `decision` 없음 |
| 운영 변경 (`OPERATION`) | `sourceOrg` 필수, `revisionId` 없음 |
| 법령 개정 (`LAW`) | `sourceOrg` 없음 |
| 일부 운전자 (`LIMITED`) | `eventTag` 없음 (이벤트로 먼저 알리지 않고, 물으면 답함) |

### 3-2. 조건 분기 결정 테이블 (`decision`)

```json
{ "schema": 2,
  "tables": [
    { "key": "approach", "title": "교차로 진입 전",
      "conditions": ["right_turn_traffic_light", "front_traffic_light"],
      "rows": [
        { "when": { "right_turn_traffic_light": "yes" }, "text": "화면 문장", "tts": "읽어 주는 문장" },
        { "when": { "right_turn_traffic_light": "no", "front_traffic_light": "red" }, "text": "…", "tts": "…" },
        { "when": { "right_turn_traffic_light": "no", "front_traffic_light": "green" }, "text": "…", "tts": "…" } ] } ] }
```

- 조건은 **공통 조건 목록**에 있는 것만: `right_turn_traffic_light`(yes/no) · `front_traffic_light`(red/green) · `crosswalk_light`(green/red) · `pedestrian_present`(yes/no). 목록은 Gleo App Schema 와 1:1 이라 카드에서 새로 만들 수 없습니다 ([012](../decisions/012-gleo-app-schema-draft.md))
- 소표 1~2개, 소표당 조건 1~3개, 선택지 조합 8가지 이하
- **소표마다 모든 조합이 정확히 한 행에 걸려야** 합니다. 빠진 조합 · 두 행에 걸리는 조합이 있으면 422 와 함께 그 조합을 알려 줍니다 (예: `decision.tables[1]: 어느 행에도 걸리지 않는 조합이 있습니다 {crosswalk_light=red, pedestrian_present=yes}`)
- 행의 `when` 에서 빠진 조건은 '어느 값이든'입니다
- 앱이 이 표로 답을 고르는 규칙: [`spec/rule-matching/README.md`](../../spec/rule-matching/README.md)

### 3-3. 목록 · 상세

```bash
curl -s "${H[@]}" "$BASE/cards?status=IN_REVIEW"     # 상태: DRAFT · IN_REVIEW · PUBLISHED · RETIRED (생략하면 전부, id 순)
curl -s "${H[@]}" $BASE/cards/7
```

### 3-4. 수정

```bash
curl -s "${H[@]}" -X PUT $BASE/cards/7 -d '{ …3-1 과 같은 내용 전체… }'
```

- **내용 전체**를 보냅니다 (빠진 칸은 비워집니다). 칸 규칙은 3-1 과 같습니다
- 어느 상태에서 고치든 **DRAFT 로 돌아갑니다.** 다시 검수 요청 → 2인 승인을 거쳐야 합니다
- 게시 중이던 카드를 고쳐도 **앱에는 이전 게시본이 남습니다.** 새 내용은 다시 게시될 때 나갑니다. 다만 그사이 앱을 새로 설치한 차는 이 카드를 받지 못합니다 (동기화는 게시 중인 카드만 보냄) — 게시 중인 카드는 고친 뒤 바로 검수를 끝내 주세요
- 회수된 카드는 고칠 수 없습니다 (`409 INVALID_CARD_STATE`)
- 한 번이라도 게시된 카드는 수정으로 `topic` 을 바꿀 수 없습니다 (`409 TOPIC_CHANGE_NOT_SYNCED`) — 3-7

### 3-5. 검수 요청 · 검수

```bash
curl -s "${H[@]}" -X POST $BASE/cards/7/submit                                       # DRAFT → IN_REVIEW
curl -s "${H[@]}" -X POST $BASE/cards/7/reviews -d '{"reviewerId":1,"decision":"APPROVE"}'  # 아직 IN_REVIEW
curl -s "${H[@]}" -X POST $BASE/cards/7/reviews -d '{"reviewerId":2,"decision":"APPROVE"}'  # → PUBLISHED
curl -s "${H[@]}" $BASE/cards/7/reviews                                              # 검수 기록
```

| 상황 | 결과 |
|---|---|
| 서로 다른 검수자 승인 2건 | `PUBLISHED`, `verifiedAt` 이 찍힘 → 다음 앱 동기화에 나감 |
| 반려 1건 | 바로 `DRAFT`. 고친 뒤 다시 검수 요청 |
| 같은 검수자가 같은 버전을 두 번 | `409 ALREADY_REVIEWED` |
| 없는 검수자 | `404 REVIEWER_NOT_FOUND` |
| `IN_REVIEW` 가 아닌 카드 | `409 INVALID_CARD_STATE` |
| `DRAFT` 가 아닌 카드를 검수 요청 | `409 INVALID_CARD_STATE` |

**버전:** 검수 기록이 있는 버전을 다시 검수 요청하면 `version` 이 1 오릅니다. 반려한 검수자도 고친 내용을 새로 검수할 수 있고, 앞 버전의 승인은 새 버전에 세지 않습니다. 앱은 오류 신고에 이 `version` 을 함께 보냅니다.

### 3-6. 게시됐는지 확인

```bash
curl -s "http://localhost:8080/api/v1/cards/sync"     # 앱이 부르는 동기화 (키 없음)
```

`cards[]` 에 그 카드가 보이면 앱에 나간 것입니다. 앱은 하루 1회와 시동 때 동기화합니다.

### 3-7. Gleo 대표 주제 (`topic`)

Gleo `ASK_RULE` 이 주제(topic)로 물을 때 답하는 카드입니다. **한 주제에 대표 카드는 1장**입니다 (006).

```bash
curl -s "${H[@]}" -X PUT $BASE/cards/7/topic -d '{"topic":"school_zone_crosswalk"}'   # 지정
curl -s "${H[@]}" -X PUT $BASE/cards/7/topic -d '{"topic":null}'                      # 해제
```

- 주제 이름은 **App Schema 의 topic 목록**에 있는 것만 의미가 있습니다 — 목록 밖 이름은 Gleo 가 보내지 않으므로 아무도 묻지 않습니다. 목록: [`spec/gleo-app-schema/schema.json`](../../spec/gleo-app-schema/schema.json) (`ASK_RULE.topic.enum`)
- 다른 카드가 이미 대표면 `409 TOPIC_TAKEN` (`detail` 에 그 카드 id). 그 카드를 해제하거나 회수한 뒤 지정합니다
- 문장이 바뀌지 않으므로 **재검수 없이** 바뀝니다
- 한 번이라도 게시된 카드는 **지금 게시 중일 때만** 바꿀 수 있습니다 (`409 TOPIC_CHANGE_NOT_SYNCED`). 수정 · 검수 중인 카드는 동기화되지 않아, 바꾼 주제가 차에 가지 않기 때문입니다
- 작성 · 수정 본문의 `topic` 으로도 지정할 수 있습니다 (게시된 적 없는 카드만)

### 3-8. 회수

```bash
curl -s "${H[@]}" -X POST $BASE/cards/7/retire
```

- `RETIRED` 는 끝 상태입니다. 되돌릴 수 없고, 고칠 수도 없습니다. 같은 내용이 다시 필요하면 새 카드를 만듭니다
- 대표 주제가 비워집니다 → 다른 카드를 그 주제의 대표로 지정할 수 있습니다
- 다음 앱 동기화에서 차의 사본이 내려갑니다. 게시된 적 없는 카드는 앱에 아무것도 가지 않습니다

---

## 4. 검수 요청 대기열 (앱 신고)

앱이 보낸 신고 두 종류가 쌓입니다. 신고에는 운전자 식별자가 없어 **신고한 사람에게 따로 알릴 수 없습니다.**

| `requestType` | 언제 | 들어 있는 것 |
|---|---|---|
| `ERROR_REPORT` | 운전자가 카드 화면에서 '오류 신고' | `cardId` · `cardVersion` |
| `ANSWER_HOLD` | Gleo 질문에 답할 카드가 없거나 보류 행에 걸림 | `topic` · `conditions` (질문 원문은 없음) |

```bash
curl -s "${H[@]}" "$BASE/review-requests?status=OPEN&requestType=ANSWER_HOLD"   # 최신순
curl -s "${H[@]}" -X PATCH $BASE/review-requests/3 -d '{"status":"RESOLVED"}'
```

| `status` | 뜻 |
|---|---|
| `OPEN` | 처리 전 |
| `RESOLVED` | 카드를 쓰거나 고쳐서 처리함 |
| `DISMISSED` | 처리하지 않기로 함 (중복 · 범위 밖 등) |

- `ANSWER_HOLD` 를 `topic` 별로 세면 어떤 카드를 먼저 써야 하는지 보입니다 (012 topic 목록 근거)
- 같은 신고가 여러 번 올 수 있습니다 (앱 재시도). 하나를 처리하고 나머지는 `DISMISSED` 로 둡니다

---

## 5. 오류 코드

| HTTP | `code` | 언제 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 필수 칸 없음 · 길이 초과 · 형식 오류 · 모르는 enum 값 (`errors` 에 칸 이름) |
| 401 | `ADMIN_KEY_REQUIRED` | 키 없음 · 틀림 |
| 404 | `CARD_NOT_FOUND` · `REVIEWER_NOT_FOUND` · `LAW_REVISION_NOT_FOUND` · `REVIEW_REQUEST_NOT_FOUND` | 없는 id |
| 409 | `INVALID_CARD_STATE` | 지금 상태에서 할 수 없는 동작 (`detail` 끝에 현재 상태) |
| 409 | `ALREADY_REVIEWED` | 같은 검수자 · 같은 버전 두 번째 검수 |
| 409 | `TOPIC_TAKEN` · `TOPIC_CHANGE_NOT_SYNCED` | 3-7 |
| 409 | `LAW_NO_DUPLICATED` · `LAW_REVISION_NOT_APPLICABLE` | 2-2 · 2-3 |
| 409 | `CONFLICT` | 동시에 같은 값을 넣어 DB 중복 제약에 걸림 — 목록을 다시 보고 판단 |
| 422 | `CARD_INVALID` | 3-1 칸 사이 규칙 · 3-2 결정 테이블 |
| 422 | `DATA_REJECTED` | DB 가 거절한 값 (서버 버그일 수 있으니 알려 주세요) |
| 503 | `LAW_API_NOT_CONFIGURED` | 2-1, 법령 Open API 인증값 없음 |

---

## 6. 시연 카드를 게시하는 순서 (2026-11-13 운영 상태 기준)

운영 서버에는 **시연 카드 5장(id 1~5)이 모두 DRAFT** 이고, 검수자는 0명, 법령 개정은 제22067호 1건(`PENDING`)입니다.

1. 검수자 2명 등록 (1장) — 실제로 검수할 팀원 이름으로
2. 제22067호 분류: `APPLICABLE` (2-3) — 카드 A 의 출처
3. 카드마다 내용 확인 → 필요하면 수정 (3-4). 기획 확인 대기 항목이 남은 카드:
   - 우회전(id 5): '돌아선 뒤 횡단보도' 소표의 보행 신호 적색 행이 `(검수 문장 필요)` (009 기획 확인 4번)
   - 근거 대조: [`docs/content/pt-cards-legal-check.md`](../content/pt-cards-legal-check.md)
4. 검수 요청 (3-5) → 검수자 2명이 각자 승인
5. 우회전 카드에 대표 주제가 붙어 있는지 확인 (`topic: right_turn`)
6. 동기화로 5장이 나오는지 확인 (3-6)

검수 순서와 판단(무엇을 보고 승인하는가)은 사람이 정합니다. 서버는 2명 · 서로 다름 · 버전만 지킵니다.
