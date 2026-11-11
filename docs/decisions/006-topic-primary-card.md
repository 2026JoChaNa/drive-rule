# 006. 한 주제에 카드 N장 — Gleo 대표 카드 지정 방식 (초안)

- 날짜: 2026-11-11
- 상태: **초안** — 기획 확인 대기, ERD 미반영
- 요청: `docs/ERD 보완 요청 · 화면 대조 결과 (v5.2 기준).md` 6번
- 관련: `rule_card.topic`(UQ) · `cached_card.topic` · [004](004-card-lifecycle.md)

## 배경

- 기획 요청: `rule_card.topic` UQ 를 풀고 `is_primary` 를 더한다. 한 주제에 카드 N장, Gleo 는 대표 카드 1장으로 답한다
- 004 는 "한 주제에 답이 둘이 되지 않게" `topic` 을 UQ 로 정했다 — 이 요청은 그 결정을 바꾼다
- 요청에는 `cached_card` 가 빠져 있지만 Gleo 는 **차량 사본으로** 답한다. 앱 쪽에도 같은 정보가 필요하다

## 선택지

| | A. `is_primary` 추가 (요청안) | B. `topic` 은 대표 카드에만, 묶음 키 따로 ✅ 잠정 |
|---|---|---|
| 형태 | `topic` UQ 해제 + `is_primary` | `topic`(Gleo 키, UQ) 유지 + `topic_group` 추가 |
| 대표 1장 보장 | 생성 컬럼 `IF(is_primary, topic, NULL)` + UNIQUE 필요 | 기존 UQ 그대로 |
| 앱 변경 | `cached_card` + `is_primary` | `cached_card` + `topic_group` |
| Gleo 조회 | `topic = ? AND is_primary` | `topic = ?` (지금과 같음) |
| 뜻 | `topic` 하나가 'Gleo 키'와 '묶음' 두 가지를 겸함 | 칸마다 뜻이 하나 |

## 잠정 결정 — B

- Gleo 가 대표 카드 1장으로만 답한다면, 나머지 카드에 `topic` 이 붙어 있을 이유는 **화면 묶음**뿐이다
- 묶음과 Gleo 키를 나누면 004 의 UQ 를 그대로 지킬 수 있고, 플래그 + 생성 컬럼 같은 장치가 필요 없다
- 단, "N장이 한 주제로 묶여 보이는 화면"이 없다면 `topic_group` 도 필요 없다 → 기획 회신으로 확정

## 두 안 모두 정해야 할 것

- **대표 카드가 `DRAFT`/`IN_REVIEW` 일 때**: 004 대로 앱은 마지막 사본으로 답한다 — 변경 없음
- **대표 카드가 `RETIRED` 일 때**: 회수 시 그 카드의 `topic` 을 비운다(B) / `is_primary` 를 내린다(A). 다른 카드를 대표로 지정하기 전까지 그 주제는 Gleo 보류 문장으로 답한다
- 대표 지정·변경도 검수 대상인지 — 잠정: 대상 아님 (문장이 바뀌지 않음), 기획 확인

## 기획에 물을 것

1. 한 주제에 카드 N장이 묶여 보이는 화면이 어디인지 (없다면 Gleo 키만 있으면 됨)
2. 대표 카드가 회수됐을 때 Gleo 가 보류 문장으로 답해도 되는지

## 영향 (확정 시)

- B: `rule_card`·`cached_card` + `topic_group`. 004 '규칙' 문장 유지
- A: `rule_card` UQ 해제 + `is_primary` + 생성 컬럼, `cached_card` + `is_primary`. 004 '규칙'의 `topic` UQ 문장을 고친다
