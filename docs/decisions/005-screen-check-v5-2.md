# 005. 화면 대조(v5.2) 요청 반영 — 카드 두 장면·근거 시트·개정 묶음·질문 문장·오류 신고

- 날짜: 2026-11-11
- 상태: 결정 (MVP 범위) — 기획 회신 반영 (2026-11-11)
- 요청: `docs/requests/2026-11-11-erd-screen-check-v5-2.md` (기획, 2026-11-11) 1~5번
- 관련: `docs/db/erd.data.mjs` — `rule_card` · `cached_card` · `pending_question` · `review_request`

## 배경

- 기획이 Figma v5.2 화면을 ERD 와 대조해 컬럼 추가 7건을 요청했다
- 이 기록은 1~5번만 다룬다. 6번(주제당 카드 N장)은 [006](006-topic-primary-card.md), 7번(`favorite_place` 삭제)은 [007](007-remove-favorite-place.md)

## 결정

| # | 화면 | 반영 | 요청과 다른 점 |
|---|---|---|---|
| 1 | 03 출발 전 카드 '하지 않습니다 / 이렇게 합니다' | `rule_card`·`cached_card` + `dont_text`·`do_text`·`scene_key` (모두 NULL 허용, **단문 카드 전용**) | **`action_text` 삭제** — 뜻이 `do_text` 와 같다. 조건 분기형은 두 장면이 없다(아래) |
| 2 | 03-1 근거 시트 처벌·공포 번호 | `rule_card`·`cached_card` + `penalty_text`(NULL 허용), `cached_card` + `law_name`·`law_no`(NULL 허용) | 서버는 `law_revision` 값을 그대로 쓴다. 사본에만 복사 (`promulgated_on` 과 같은 방식). `law_name` 은 근거 시트 "도로교통법 일부개정 (법률 제21728호)" 때문에 추가 |
| 3 | ~~00 홈 개정별 묶음~~ | **반영 안 함** | 기획 회신: 홈은 묶지 않고 카드 제목·시행일·D-day 만 나열한다. `cached_card.revision_id` 는 넣었다가 뺐다 |
| 4 | 07 주차 후 질문 다시 보기 | `pending_question` + `question_text`(NN) | 없음 |
| 5 | 03-1 오류 신고 | `review_request` + `request_type`(ENUM `QUESTION`/`ERROR_REPORT`), `card_id`(FK, NULL 허용), **`card_version`** | `card_version` 추가 — 어느 버전이 틀렸는지 알아야 고친다. `question_text` 는 NULL 허용으로 바꿈 |

**카드 유형별 문장 위치** (기획 회신: 화면 08·08-2, 기획 v4 3번)

| 카드 유형 | 행동 문장 | 장면 |
|---|---|---|
| 단문 | `dont_text` + `do_text` (두 장면) | `scene_key` |
| 조건 분기형 | 분기마다 1개 — `decision_json.branches[].text` (002) | `branches[].scene` |

- 조건 분기형은 카드 칸(`dont_text`·`do_text`·`scene_key`)을 비운다. 토글을 바꾸면 분기의 문장과 그림이 함께 바뀐다
- 유형별 필수 칸은 저장 전 검증기가 확인한다 (DB NN 이 아님 — 유형마다 다르다)

**규칙**

- `dont_text`·`do_text`·`penalty_text` 는 **검수 대상**이다 — 바뀌면 004 규칙대로 `DRAFT` → 재검수 → `version + 1`
- `scene_key` 는 앱에 번들된 그림의 키다. 서버는 그림을 보내지 않는다 (002 의 분기별 `scene` 과 같은 키 공간)
- `request_type = QUESTION` 이면 `question_text` 필수, `ERROR_REPORT` 이면 `card_id`·`card_version` 필수 — 서버 검증으로 보장 (DB CHECK 아님)
- 오류 신고도 질문처럼 **식별자 없이** 보낸다 (프로필·기기 ID 없음)

## 이유

- `action_text` 를 남기면 `do_text` 와 같은 문장이 두 칸에 생기고, 검수자가 둘 중 무엇이 화면에 나가는지 헷갈린다
- 두 장면 칸을 NULL 허용으로 둔 것은 조건 분기형·고정 응답·보호구역 카드에는 두 장면이 없어서다
- 오류 신고에 버전이 없으면, 신고가 들어온 뒤 카드가 수정됐을 때 이미 고친 내용인지 알 수 없다

## 영향

- **차 → 서버 경로가 하나 늘었다**: 자유 질문 문장 + 오류 신고(카드ID·버전). 003 과 ERD 메모를 고쳤다
- `review_request` 를 `rule_card` 와 FK 로 이었다 (`0..1:0..N`)
- 기획서 문구는 고치지 않는다 — `action_text` 삭제는 개발 내부 이름이라 기획 영향 없음

## 함께 정한 것 — 안내 단계(`stage`) 값

- 2차 PT 빌드는 **3개**: 공포 / 시행 1주 전 / 시행일
- **시기별 재알림은 출시 1단계 범위다** (기획 v4 6번: 출시 카드 27장 중 4장, 2027 7~12월 첫 발송). 이전 ERD 메모의 "MVP 밖(2027 하반기 기능)"은 틀린 전제였다
- 재알림은 `stage` 값으로 넣지 않고 **출시 전 별도 일정으로 설계**한다 — 앞의 3단계는 개정일에 따라 정해지고, 재알림은 개정과 상관없이 시기(명절·개학)에 따라 정해진다 (기획 제안)
- 카드 유형 4개(단문 / 조건 분기형 / 보호구역 / 고정 응답)는 그대로

## 열린 질문

1. (개발) `pending_question.question_text` 보관 기간 — 다시 본 뒤 지울지. 차량 DB 안이라 원칙과는 충돌하지 않음
2. (개발) 근거 시트의 "일부개정"(제·개정 구분)을 어디서 가져올지 — `law_name` 에 포함할지, `law_revision` 에 구분 칸을 둘지

## 다시 검토할 때

- 출시 빌드 설계 시 — 시기별 재알림 일정 구조 (003 과 함께 출시 전 과제)
- 오류 신고가 스팸처럼 쌓이면 — 식별자가 없어 기기별 제한을 못 건다. 서버 쪽 요청 수 제한부터 검토
