# 007. `favorite_place` 삭제 — 내비 즐겨찾기·최근 목적지로 대체 (초안)

- 날짜: 2026-11-11
- 상태: **초안** — 11/18 기술 검증 결과 대기, ERD 미반영 (`favorite_place` 아직 있음)
- 요청: `docs/ERD 보완 요청 · 화면 대조 결과 (v5.2 기준).md` 7번 · '동작 결정 > 목적지'
- 관련: `favorite_place` · `destination_briefing` · `local_school_zone`

## 배경

- 기획 요청: 앱이 '자주 가는 장소'를 따로 받지 않고, 내비 데이터를 쓴다
  - 출발 전 판정: NaviHelper `getDestinationInfo` + 목적지 이벤트
  - 카드 동기화 때: `getBookmarkInfo`·`getRecentDestinationInfo` 를 보호구역과 미리 대조
  - 위치는 차 밖으로 보내지 않음
- 근거 API: [NaviHelper API 목록](https://pleos.ai/playground/resources/en/api-reference/connect-sdk/NaviHelper/intro)

## 잠정 결정

- 11/18 검증에서 아래가 **모두 확인되면** `favorite_place` 를 삭제한다
  1. 세 API 를 앱 권한으로 호출할 수 있다
  2. 시동 직후(출발 전 판정 시점)에 응답이 온다
  3. 좌표(위도·경도)를 돌려준다 — 보호구역 대조에 필요
- 하나라도 안 되면 `favorite_place` 를 유지하고 사용자 입력을 받는다

## 미리 대조한 결과의 저장

- 잠정: **테이블을 두지 않는다**. 즐겨찾기·최근 목적지 수가 적고, 보호구역 사본은 이미 차량 DB 에 있어 판정 때 다시 계산해도 된다
- 시동 직후 응답이 느리면(검증 2번이 '늦게 옴') 대조 결과를 저장하는 테이블을 검토한다 — 저장한다면 좌표를 담으므로 차량 DB

## 기획 회신 (2026-11-11)

- 즐겨찾기·최근 목적지가 없는 운전자는 **생략**한다. 빈 상태 화면도 만들지 않는다
- 브리핑의 기본 경로는 현재 목적지(`getDestinationInfo`)다. 즐겨찾기·최근 목적지는 미리 대조해 두는 보조 경로 (기획 v4 3번, 결정 B3)
- 목적지를 정하면 04·04-W 브리핑은 그대로 나온다
- 11/18 검증 뒤 확정하는 데 동의

## 영향 (확정 시)

- 앱 DB 에서 `favorite_place` 삭제 — 다른 테이블이 참조하지 않아 관계 변경 없음
- 앱 권한 목록에 NaviHelper 권한 추가
