# ERD 보완 요청 · 화면 대조 결과 (v5.2 기준)

Nov 11, 2026 · @차정은

ERD 변경 7건과 기술 확인 2건을 요청드립니다. 운전 상황 제외(결정 001)는 기획 v6·Figma v5.2에 반영했습니다.

## 운전 맥락 필터링 회신 (결정 001)

- 기획도 빼는 것으로 확정했습니다. 다시 넣을 계획은 없습니다.
- '나에게 해당하는지'는 게시 전 검수 단계에서 일반 운전자 해당분만 골라 처리합니다. 게시된 카드는 모든 운전자에게 보여 줍니다.
- 프로필은 이름만 받습니다. `driver_profile.driving_context`, `rule_card.audience`는 삭제 그대로 갑니다.
- 나중에 걸러야 한다면 운전자 입력이 아니라 차량 속성(전기차·화물차 등, 발전 계획 2단계)으로 합니다. 그때 값 목록과 규칙을 먼저 드리겠습니다.

## ERD 변경 7건

| # | 테이블 | 변경 | 이유 (화면) |
| --- | --- | --- | --- |
| 1 | `rule_card`, `cached_card` | + `dont_text`, `do_text`, `scene_key` | 03 출발 전 카드의 '하지 않습니다 / 이렇게 합니다' 두 장면 |
| 2 | `rule_card`, `cached_card` | + `penalty_text`, `cached_card`에 `law_no` | 03-1 근거 시트의 처벌·공포 번호 |
| 3 | `cached_card` | + `revision_id` | 00 홈: 같은 개정의 변경을 묶어 표시 |
| 4 | `pending_question` | + `question_text` | 07 주차 후 주행 중 질문 문장을 다시 표시 |
| 5 | `review_request` | + `request_type`(question / error\_report), `card_id`(NULL 허용) | 03-1 '오류 신고' |
| 6 | `rule_card` | `topic` UQ 해제, + `is_primary` | 한 주제에 카드 N장. Gleo는 대표 카드 1장으로 답함 |
| 7 | `favorite_place` | 테이블 삭제 | 내비 즐겨찾기·최근 목적지로 대체 |

컬럼 이름은 제안이며 최종 이름·타입은 개발팀이 정합니다. 1·2의 문구는 검수 대상입니다.

## 동작 결정 (ERD 변경 없음)

- **목적지**: 출발 전 판정 때 NaviHelper `getDestinationInfo`로 조회 + 목적지 이벤트. 내비 즐겨찾기·최근 목적지(`getBookmarkInfo`, `getRecentDestinationInfo`)는 카드 동기화 때 보호구역과 미리 대조. 차 밖으로 보내지 않음.
- **답변 보류(07-1)**: 검수 요청 등록까지만. 검수 후 개별 알림 없음.
- **01-0 건너뛰기**: '운전자 1' 프로필 자동 생성, 이름은 09에서 변경.
- **프로필 최대 3명**: 앱 로직으로 제한.

## 확인 요청 (11/18 기술 검증과 함께)

- NaviHelper `getDestinationInfo`·`getBookmarkInfo`·`getRecentDestinationInfo`의 필요 권한과 시동 직후 응답 여부.
- ENUM 값이 기획과 맞는지: `stage` = 공포 / 시행 1주 전 / 시행일 / 시기별 재알림, `card_type` = 단문 / 조건 분기형 / 보호구역 / 고정 응답.

근거: [NaviHelper API 목록](https://pleos.ai/playground/resources/en/api-reference/connect-sdk/NaviHelper/intro)
