# 005. 화면 대조(v5.2) 요청 반영 — 카드 두 장면·근거 시트·개정 묶음·질문 문장·오류 신고

- 날짜: 2026-11-11
- 상태: 결정 (MVP 범위) — 기획 확인 대기 2건(아래 '열린 질문')
- 요청: `docs/ERD 보완 요청 · 화면 대조 결과 (v5.2 기준).md` (기획, 2026-11-11) 1~5번
- 관련: `docs/db/erd.data.mjs` — `rule_card` · `cached_card` · `pending_question` · `review_request`

## 배경

- 기획이 Figma v5.2 화면을 ERD 와 대조해 컬럼 추가 7건을 요청했다
- 이 기록은 1~5번만 다룬다. 6번(주제당 카드 N장)은 [006](006-topic-primary-card.md), 7번(`favorite_place` 삭제)은 [007](007-remove-favorite-place.md)

## 결정

| # | 화면 | 반영 | 요청과 다른 점 |
|---|---|---|---|
| 1 | 03 출발 전 카드 '하지 않습니다 / 이렇게 합니다' | `rule_card`·`cached_card` + `dont_text`(NULL 허용), `do_text`(NN), `scene_key`(NULL 허용) | **`action_text` 삭제** — 뜻이 `do_text` 와 같다 |
| 2 | 03-1 근거 시트 처벌·공포 번호 | `rule_card`·`cached_card` + `penalty_text`(NULL 허용), `cached_card` + `law_no`(NULL 허용) | 서버는 `law_revision.law_no` 를 그대로 쓴다. 사본에만 복사 (`promulgated_on` 과 같은 방식) |
| 3 | 00 홈 개정별 묶음 | `cached_card` + `revision_id`(NULL 허용, FK 아님) | 앱에는 개정 테이블이 없어 FK 를 두지 않는다 |
| 4 | 07 주차 후 질문 다시 보기 | `pending_question` + `question_text`(NN) | 없음 |
| 5 | 03-1 오류 신고 | `review_request` + `request_type`(ENUM `QUESTION`/`ERROR_REPORT`), `card_id`(FK, NULL 허용), **`card_version`** | `card_version` 추가 — 어느 버전이 틀렸는지 알아야 고친다. `question_text` 는 NULL 허용으로 바꿈 |

**규칙**

- `dont_text`·`do_text`·`penalty_text` 는 **검수 대상**이다 — 바뀌면 004 규칙대로 `DRAFT` → 재검수 → `version + 1`
- `scene_key` 는 앱에 번들된 그림의 키다. 서버는 그림을 보내지 않는다 (002 의 분기별 `scene` 과 같은 키 공간)
- `request_type = QUESTION` 이면 `question_text` 필수, `ERROR_REPORT` 이면 `card_id`·`card_version` 필수 — 서버 검증으로 보장 (DB CHECK 아님)
- 오류 신고도 질문처럼 **식별자 없이** 보낸다 (프로필·기기 ID 없음)

## 이유

- `action_text` 를 남기면 `do_text` 와 같은 문장이 두 칸에 생기고, 검수자가 둘 중 무엇이 화면에 나가는지 헷갈린다
- `dont_text` 를 NULL 허용으로 둔 것은 고정 응답·보호구역 카드처럼 '하지 않을 행동'이 없는 카드가 있을 수 있어서다
- 오류 신고에 버전이 없으면, 신고가 들어온 뒤 카드가 수정됐을 때 이미 고친 내용인지 알 수 없다

## 영향

- **차 → 서버 경로가 하나 늘었다**: 자유 질문 문장 + 오류 신고(카드ID·버전). 003 과 ERD 메모를 고쳤다
- `review_request` 를 `rule_card` 와 FK 로 이었다 (`0..1:0..N`)
- 기획서 문구는 고치지 않는다 — `action_text` 삭제는 개발 내부 이름이라 기획 영향 없음

## 열린 질문 (기획 확인 필요)

1. **조건 분기형 카드의 두 장면**: '하지 않습니다 / 이렇게 합니다'를 분기마다 따로 두는지
   - 지금은 카드 단위다. 분기마다 다르다면 `dont_text`·`do_text` 를 002 의 `branches[]` 안으로 옮긴다
2. **홈 묶음 제목**: 묶음에 법령명을 보여 주는지
   - 그렇다면 `cached_card` 에 `law_name` 을 더 둔다 (지금은 없음)
3. (개발) `pending_question.question_text` 보관 기간 — 다시 본 뒤 지울지. 차량 DB 안이라 원칙과는 충돌하지 않음

## 다시 검토할 때

- 위 열린 질문 1·2 회신이 오면
- 오류 신고가 스팸처럼 쌓이면 — 식별자가 없어 기기별 제한을 못 건다. 서버 쪽 요청 수 제한부터 검토
