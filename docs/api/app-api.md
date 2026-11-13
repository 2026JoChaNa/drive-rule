# 차량 앱용 서버 API (v1)

차량 앱(Android)이 서버를 부를 때 보는 문서입니다. 서버 쪽 판단 근거는 결정 기록 [008](../decisions/008-server-api-v1.md) · [009](../decisions/009-core-features-v6-alignment.md) · [010](../decisions/010-school-zone-dataset.md) · [012](../decisions/012-gleo-app-schema-draft.md)에 있습니다. 검수 CMS(관리자) API 는 [admin-api.md](admin-api.md) 입니다.

- 기준: 2026-11-13, `main`
- 실제 스펙: Swagger UI `/swagger-ui.html`, OpenAPI JSON `/v3/api-docs` (코드 생성에 써도 됩니다)
- 이 문서와 서버 동작이 다르면 서버 쪽 버그로 보고 알려 주세요

---

## 0. 먼저 알아 둘 것

### 서버 주소 — HTTP

| 환경 | 주소 |
|---|---|
| 운영 (EC2) | `http://13.124.175.42:8080` |
| 로컬 | `http://localhost:8080` (`server/` 에서 `bootRun`) |

운영은 **HTTP** 입니다 (HTTPS 는 당분간 붙이지 않기로 함, 2026-11-12). Android 9+ 는 평문 HTTP 를 기본으로 막고, 앱 매니페스트도 `android:usesCleartextTraffic="false"` 입니다. 서버를 부르려면 network security config 에서 **이 서버 주소만** 평문을 허용하세요 (`<domain-config cleartextTrafficPermitted="true">`). 앱 전체를 여는 `usesCleartextTraffic="true"` 는 쓰지 마세요.

주소는 빌드 설정(`BuildConfig` 등)으로 빼 두세요. 도메인이 생기면 주소가 바뀝니다.

### 공통 규칙

| 항목 | 규칙 |
|---|---|
| 인증 | 앱용 API는 **인증이 없습니다**. `/api/v1/admin/**` 는 검수 CMS 전용이니 앱에서 부르지 마세요 |
| 보내지 않는 것 | 프로필 ID · 기기 ID · VIN · 위치 · 질문 원문. 앱이 보내는 값은 카드 ID · 버전 · 주제 · 조건뿐입니다 |
| 형식 | JSON, UTF-8. 요청 본문은 `Content-Type: application/json` |
| 압축 | `Accept-Encoding: gzip` 을 보내면 압축해서 줍니다 (OkHttp는 기본으로 보냄) |
| 날짜 | `LocalDate` = `"2027-04-14"`. 시각 = 시차를 붙인 ISO 8601 `"2026-11-12T09:00:00+09:00"` |
| null | 값이 없는 칸은 **빠지지 않고 `null` 로** 옵니다 |
| 모르는 칸 | 응답에 새 칸이 생길 수 있습니다. **모르는 칸은 무시**하세요 (`ignoreUnknownKeys = true`) |
| 모르는 enum 값 | `eventTag` 등에 새 값이 생길 수 있습니다. 파싱에 실패해 동기화 전체가 죽지 않게, 모르는 값은 `null`(이벤트 없음)이나 '모름'으로 받으세요. 카드 유형(`cardType`)이 모르는 값이면 그 카드만 건너뜁니다 |

### 언제 무엇을 부르나

```
시동 · 하루 1회
  ├─ GET /api/v1/cards/sync?syncToken=…      카드 사본 갱신
  └─ GET /api/v1/geo/school-zones            보호구역 좌표 (바뀌었을 때만 파일 받기)

근거 시트 '오류 신고'      → POST /api/v1/cards/{cardId}/error-reports
Gleo 질문에 답할 카드 없음 → POST /api/v1/answer-holds
```

Gleo 질문에는 **서버를 부르지 않고** 차량 사본으로 답합니다 (Gleo 7초 제한, 009 F).

### Gleo function 3개 — 사본으로 답하는 법

App Schema 초안: [`spec/gleo-app-schema/schema.json`](../../spec/gleo-app-schema/schema.json) (앱 `assets/schema.json` 으로 복사, 결정 012). 기획 확인 전이라 topic 목록 · 조건 값이 바뀔 수 있습니다.

| function | 받는 값 | 사본에서 고르는 것 | callback |
|---|---|---|---|
| `LIST_RULE_CHANGES` | `since`(YYYY-MM) 또는 `period`(`this_month` / `upcoming`) — 둘 다 선택 | `audience = GENERAL` 이고 시행일이 그 뒤인 카드, 시행일 순 | 둘 다 비면 `asking` + `missingField: "since"` |
| `ASK_RULE` | `topic`(필수) + 공통 조건 키 4개(선택, 1장 '공통 조건') | `topic` 이 같고 회수되지 않은 카드 1장 → 조건 분기면 [매칭 명세](../../spec/rule-matching/README.md) | 카드 없음 · 보류 행 → 답변 보류 문장 + 4장 신고. `topic = other` 는 항상 보류 |
| `CONFIRM_RULE` | `action`(`confirm` / `repeat`) — 카드 ID 는 받지 않음 | 이 대화에서 **방금 읽어 준 카드** (앱이 기억) | `confirm` → 프로필별 확인 이력 저장, `repeat` → 같은 `tts` 다시 |

- 조건 파라미터가 비어 오면 '모름'입니다. 별도 '모름' 값은 없습니다
- Gleo 는 앱 응답으로 문장을 새로 만듭니다. `silent` 로 두고 앱 TTS 가 검수 문장을 그대로 읽을 수 있는지는 11/18 검증 항목입니다 (009 F)

---

## 1. 카드 동기화 — `GET /api/v1/cards/sync`

### 요청

| 쿼리 | 필수 | 설명 |
|---|---|---|
| `syncToken` | 아니오 | 지난 응답의 `syncToken`. **해석하지 말고 그대로** 돌려주세요. 없으면 전체를 받습니다 |

### 응답 200

```json
{
  "syncToken": "MjoxNzk0ODk5MjAwMDAw",
  "fullSync": false,
  "cards": [ { "cardId": 5, "version": 2, "...": "아래 표" } ],
  "retiredCardIds": [3],
  "conditions": [
    { "key": "crosswalk_light", "question": "돌아선 뒤 횡단보도 보행 신호는 무슨 색이었나요?",
      "options": [ { "key": "green", "label": "초록불이었어요" }, { "key": "green_flashing", "label": "초록불이 깜빡였어요" },
                   { "key": "red", "label": "빨간불이었어요" } ] }
  ]
}
```

| 칸 | 설명 |
|---|---|
| `syncToken` | 다음 호출에 넘길 값. **이번 응답을 저장하는 트랜잭션 안에서 함께 저장**하세요. 저장에 실패하면 옛 토큰을 그대로 둡니다 |
| `fullSync` | `true` = 토큰 없이 부른 응답 (게시 카드 전부 + 회수 ID 전부) |
| `cards` | 그사이 게시된(또는 다시 게시된) 카드. 내용 전체가 옵니다 |
| `retiredCardIds` | 그사이 회수된 카드 ID. 차에 간 적 없는 카드 ID는 오지 않습니다 |
| `conditions` | 공통 조건 목록 — 되묻기 화면의 질문 · 선택지 문구. 매번 전체가 옵니다 |

### 앱이 지킬 규칙

1. **받은 카드는 `cardId` 로 덮어씁니다.** 같은 카드가 두 번 올 수 있습니다(서버가 경계 시각을 5분 겹쳐 보냄). 덮어쓰기라 문제없습니다
2. **카드를 내리는 신호는 `retiredCardIds` 뿐입니다.** 그 카드는 `is_retired = 1`. 모르는 ID면 무시합니다
3. **목록에 없다고 지우지 마세요. `fullSync = true` 여도 마찬가지입니다.** 게시 뒤 수정 · 검수 중인 카드는 목록에서 빠지지만, 차는 마지막 게시본을 계속 써야 합니다
4. **한 응답은 한 트랜잭션으로** 반영합니다 (카드 · 회수 · 토큰 · 조건 목록)
5. **주제(`topic`)로 카드를 찾을 때는 회수 카드를 빼세요.** 서버는 회수할 때 주제를 비우지만, 회수는 ID만 오므로 차 사본에는 옛 주제가 남아 있습니다. 같은 주제를 새 카드가 이어받으면 차에 같은 주제가 두 장 생깁니다. Room에서 `topic` 에 UNIQUE를 걸지 말고 `WHERE topic = ? AND is_retired = 0` 으로 찾으세요
6. **410 `SYNC_TOKEN_EXPIRED`** → 토큰을 버리고 토큰 없이 다시 부릅니다. 규칙 3 그대로 덮어쓰기만 합니다 (서버가 카드 형식을 바꾸면 모든 차가 이렇게 전체를 다시 받습니다)

### 카드 (`cards[]`) — 앱 `cached_card` 1행

| 칸 | 타입 | null | 설명 |
|---|---|---|---|
| `cardId` | number | | 카드 ID |
| `version` | number | | 카드 버전. 오류 신고에 그대로 씁니다 |
| `cardType` | enum | | `SHORT` 단문 · `CONDITIONAL` 조건 분기(`decision` 있음) · `FIXED_RESPONSE` 고정 응답 |
| `changeKind` | enum | | `LAW` 법령 개정 · `OPERATION` 운영 변경(법령 아님 — 화면에 "법령 개정 아님 · 출처 기관" + "현장 표지판 우선") |
| `audience` | enum | | `GENERAL` 일반 운전자 · `LIMITED` 일부 운전자(바뀐 규칙 목록 · 이벤트에서 빼고, 물으면 답함) |
| `eventTag` | enum | ✓ | 미리 알려 줄 이벤트. `null` = Gleo 답변 전용 (아래 표) |
| `sourceOrg` | string | ✓ | 운영 변경 카드의 출처 기관 (`OPERATION` 이면 항상 있음) |
| `topic` | string | ✓ | Gleo `ask_rule` 의 주제. 이 주제의 대표 카드일 때만 있습니다 |
| `title` | string | | 제목 (≤100자) |
| `dontText` · `doText` | string | ✓ | 두 장면 문장 (`SHORT`, 둘 다 있거나 둘 다 없음) |
| `sceneKey` | string | ✓ | 장면 그림 키 (`CONDITIONAL` 이면 없음 — 행마다 `scene`) |
| `ttsText` | string | | 이벤트 안내 때 읽는 문장 (≤500자) |
| `legalBasis` | string | | 근거 조문 (예: `도로교통법 제27조 제1항`) |
| `lawExcerpt` | string | | 조문 원문 (근거 시트) |
| `penaltyText` | string | ✓ | 처분 문장 |
| `lawName` · `lawNo` · `promulgatedOn` | string · string · date | ✓ | 법령 개정과 이어진 카드만. 예: `도로교통법 일부개정` · `제22067호` · `2026-10-13` |
| `effectiveOn` | date | ✓ | 시행일. 안내 단계(시행 예정 / 시행 중)를 이 날짜로 정합니다 |
| `exceptions` | string | ✓ | 예외 |
| `decision` | object | ✓ | 조건별 답 결정 테이블 (`CONDITIONAL` 만). 아래 |
| `verifiedAt` | datetime | | 2인 검수를 마친 시각. 앱 `verified_at`(epoch millis)로 바꿔 저장하세요 |

`eventTag` 값 (기획 v6 1-3 — 판정은 차가 합니다):

| 값 | 이벤트 |
|---|---|
| `SCHOOL_ZONE_ENTRY` | ① 어린이보호구역 진입 (보호구역 좌표 약 370m 안) |
| `PARKING_DESTINATION` | ② 주정차 · 목적지가 보호구역 300m 안 (남은 거리 약 500m) |
| `PARKING_HIGHWAY` | ② 주정차 · 고속도로에서 기어 P |
| `RIGHT_TURN_CROSSWALK` | ③ 우회전 지점 약 150m 전 |

### 결정 테이블 (`decision`, 형식 2)

```json
{ "schema": 2,
  "tables": [
    { "key": "approach", "title": "우회전하기 전",
      "conditions": ["right_turn_traffic_light", "front_traffic_light"],
      "rows": [ { "when": { "right_turn_traffic_light": "yes" }, "text": "…", "tts": "…", "scene": "…" } ] }
  ] }
```

- Gleo `ask_rule` 에 답할 때 이 표에서 답을 고르는 규칙은 서버 · 앱 **공용 명세**입니다: [`spec/rule-matching/README.md`](../../spec/rule-matching/README.md)
- 앱 구현은 [`spec/rule-matching/cases.json`](../../spec/rule-matching/cases.json) 의 케이스를 테스트로 돌려 서버와 같은 결과를 내는지 확인하세요
- `schema` 가 2가 아니면 그 카드는 쓰지 말고 건너뜁니다 (형식이 바뀌면 서버가 토큰을 무효로 만들어 전체를 다시 보냅니다)

### 공통 조건 (`conditions[]`)

| `key` | 선택지 `key` |
|---|---|
| `right_turn_traffic_light` | `yes` · `no` |
| `front_traffic_light` | `red` · `yellow` · `green` |
| `crosswalk_light` | `green` · `green_flashing` · `red` |
| `pedestrian_present` | `yes` · `no` |

Gleo App Schema `ASK_RULE` 파라미터와 1:1입니다 (스키마 초안: [`spec/gleo-app-schema/schema.json`](../../spec/gleo-app-schema/schema.json), 결정 012). 2026-11-13 확정했습니다 — 노란불(`yellow`) · 녹색 점멸(`green_flashing`)을 더했습니다. 화면 문구는 응답의 `question` · `label` 을 쓰고, 키는 하드코딩하지 말고 응답을 기준으로 삼으세요.

---

## 2. 보호구역 좌표 — `GET /api/v1/geo/school-zones`

카드 `SCHOOL_ZONE_ENTRY` · `PARKING_DESTINATION` 판정에 씁니다. 전국 12,152곳, 반기마다 갱신 (결정 010).

### manifest 응답 200

```json
{ "dataset": "school-zones", "schema": 1, "referenceDate": "2026-10-27", "count": 12152,
  "sha256": "55c727292dcc0d984fab8f6856eabfae6fd9f20e3dbeb46916a4ee61676f9c48",
  "bytes": 313438,
  "url": "/api/v1/geo/school-zones/55c727292dcc0d984fab8f6856eabfae6fd9f20e3dbeb46916a4ee61676f9c48.json" }
```

헤더: `ETag`, `Cache-Control: no-cache`. 다음에 `If-None-Match: <저장한 ETag>` 를 보내면 바뀌지 않았을 때 **304**(본문 없음).

### 파일 — `GET {url}`

```json
{ "schema": 1, "dataset": "school-zones", "source": "공공데이터포털 …", "referenceDate": "2026-10-27",
  "count": 12152, "zones": [[33.16917, 126.26988], [33.22053, 126.25703]] }
```

- `zones` = `[위도, 경도]` (소수 5자리). 이름 · 주소 · ID는 없습니다
- 전송은 gzip 약 77KB, 풀면 313,438바이트

### 앱이 지킬 규칙

1. `sha256` 이 저장한 값과 같으면 아무것도 하지 않습니다
2. 다르면 `url` 을 **manifest 주소 기준으로 풀어서** 받습니다 (지금은 상대 경로지만 나중에 CDN 절대 URL이 될 수 있음 — `HttpUrl.resolve()` 그대로 쓰면 됨)
3. 받은 바이트(압축 푼 뒤)의 **SHA-256 을 직접 계산**해 `sha256` 과 비교합니다. 다르면 버리고 옛 사본을 씁니다
4. 맞으면 `local_school_zone` 을 **한 트랜잭션에서 통째로 교체**합니다 (원본에 ID가 없어 행 단위 갱신은 안 됩니다)
5. 파일 URL이 404 `DATASET_VERSION_NOT_FOUND` 면 그사이 갱신된 것입니다 → manifest부터 다시 받습니다
6. 사본이 아직 없으면 이벤트 ① · ②-목적지는 쉽니다 (틀린 안내보다 안내 없음)
7. 근접 알림은 가까운 N곳만 등록하고, 움직이면 다시 고릅니다. 12,152곳을 다 등록할 수 없습니다 (Android 100개 한도, Pleos 한도는 11/18 검증)

---

## 3. 오류 신고 — `POST /api/v1/cards/{cardId}/error-reports`

근거 시트의 '오류 신고' 버튼.

```json
{ "cardVersion": 2 }
```

| 응답 | 뜻 | 앱 처리 |
|---|---|---|
| **202** | 접수됨 (본문 없음) | "신고했어요" |
| 404 `CARD_NOT_FOUND` | 서버에 없는 카드 | 재시도하지 않음 |
| 422 `CARD_VERSION_UNKNOWN` | 서버보다 높은 버전 | 재시도하지 않음 (앱 버그) |
| 400 `INVALID_REQUEST` | `cardVersion` 없음 · 0 이하 | 재시도하지 않음 (앱 버그) |

`cardVersion` 은 **앱 사본의 버전**입니다. 서버에서 이미 고쳐 버전이 올라갔어도 옛 버전 신고는 받습니다. 카드가 수정 · 검수 중이어도 받습니다.

---

## 4. 답변 보류 신고 — `POST /api/v1/answer-holds`

Gleo 질문에 답할 카드가 차 사본에 없을 때(대표 카드 없음 · 보류 행) 보냅니다. 운전자에게는 답변 보류 문장으로 답합니다.

```json
{ "topic": "right_turn", "conditions": { "crosswalk_light": "red", "pedestrian_present": "no" } }
```

| 칸 | 필수 | 설명 |
|---|---|---|
| `topic` | 예 | Gleo가 고른 주제 (≤40자) |
| `conditions` | 아니오 | 운전자가 답한 조건. **'잘 모르겠어요'라고 한 조건은 넣지 않습니다**. 없으면 생략 |

| 응답 | 뜻 |
|---|---|
| **202** | 접수됨 (본문 없음) |
| 422 `CONDITION_UNKNOWN` | 공통 조건 목록에 없는 키나 값 — `errors` 에 어느 칸인지 나옵니다 |
| 400 `INVALID_REQUEST` | `topic` 없음 · 40자 초과 |

질문 원문은 보내지 않습니다. 조건은 공통 조건 목록에 있는 키 · 값만 받습니다.

---

## 5. 오류 응답 형식

모든 오류는 [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) Problem Details + `code` · `errors` 입니다. `Content-Type: application/problem+json`.

```json
{ "title": "Unprocessable Content", "status": 422,
  "detail": "조건이 공통 조건 목록에 맞지 않습니다", "instance": "/api/v1/answer-holds",
  "code": "CONDITION_UNKNOWN",
  "errors": ["conditions.weather: 공통 조건 목록에 없는 조건입니다"] }
```

- 분기는 **`code` 로** 하세요. `detail` · `errors` 문구는 사람이 읽는 용도라 바뀔 수 있습니다
- 앱용 API에서 나오는 `code`: `INVALID_REQUEST`(400) · `CARD_NOT_FOUND`(404) · `DATASET_VERSION_NOT_FOUND`(404) · `SYNC_TOKEN_EXPIRED`(410) · `CARD_VERSION_UNKNOWN`(422) · `CONDITION_UNKNOWN`(422)

## 6. 재시도

| 상황 | 처리 |
|---|---|
| 네트워크 오류 · 5xx · 타임아웃 | 지수 백오프로 재시도. 동기화는 다음 시동 · 다음 날에 다시 해도 됩니다 (사본으로 계속 동작) |
| 410 | 토큰 없이 한 번 다시 부름 (1장 규칙 6) |
| 그 밖의 4xx | 재시도하지 않음 |

신고 두 개(3 · 4장)는 같은 요청을 두 번 보내면 두 건으로 쌓입니다. 응답을 받지 못해 다시 보내는 경우는 괜찮지만, 같은 신고를 반복해서 보내지 않게 해 주세요.

## 7. 지금 서버 상태 (2026-11-13)

- 게시된 카드가 **0장**입니다. 시연 카드 5장(A · B · H · I · 우회전 조건 분기)이 DRAFT로 들어가 있고, 검수자 2명이 승인하면 동기화에 나옵니다 (순서: [admin-api.md 6장](admin-api.md#6-시연-카드를-게시하는-순서-2026-11-13-운영-상태-기준)). 그전까지 `cards` 는 빈 배열입니다
- 동기화 응답 모양을 미리 보려면 로컬 서버에 시드를 넣고 CMS로 게시하세요 (README '시연 카드 넣기')
- 공통 조건 키 이름이 2026-11-13 에 바뀌었습니다 (`turn_light` → `right_turn_traffic_light` 등, 값은 같음, 012). 동기화 응답의 `conditions` 를 기준으로 쓰셨다면 고칠 것이 없습니다

## 8. 앱 문서와 다른 점 (확인 요청)

`docs/android/` 는 결정 테이블을 **형식 1** 로 가정하고 Gleo를 2차 범위로 두었습니다. 서버는 **형식 2**(소표 `tables[]`)이고, Gleo 질문은 차량 사본으로 답하는 구조입니다 (009). 앱 설계를 정할 때 이 문서와 009를 기준으로 맞춰 주세요.

## 9. 서버 쪽 남은 일

| 항목 | 영향 |
|---|---|
| App Schema 기획 확인 (012 확인표) | topic 목록 · 노란불 값이 바뀌면 `schema.json` · 1장 공통 조건 표가 바뀜. 제출 전(11/16 주) 확정 |
| 고속도로 노선 · 횡단보도 좌표 | 카드 A · I 판정용. 2장과 같은 모양(`/api/v1/geo/{dataset}`)으로 추가 예정 (11/18 검증 후) |
| 익명 API 요청 제한 | 신고 두 개는 인증이 없어 반복 요청에 열려 있음 (보안 작업은 당분간 보류) |
