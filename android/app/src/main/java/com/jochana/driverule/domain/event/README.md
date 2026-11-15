# 이벤트 판정과 카드 선택

DrivingEventDetector는 Android/Pleos 타입 없이 첫 TURN_RIGHT 접근을 후보로 만든다. 신호 신선도·기어·속도·거리·재무장·경로 revision을 검사하며 차단된 접근도 소비해 나중에 재생하지 않는다. 학교/목적지/고속도로 이벤트는 아직 입력이 부족하다.

GuidancePlanPolicy는 GENERAL·정확한 태그의 검수 캐시에서 하나의 사본을 고른다. 전체 ttsText 길이·예상 도착·기한을 평가하고 출력 전/진행 중 회수·수정·시행 단계·프로필·신호를 재검증한다. SDK/DB/타이머는 호출하지 않는다. 숫자는 EventPolicyConfig의 임시 개발값이며 제품 확정 기준이 아니다.

실행은 app/guidance/EventGuidanceController, 로컬 시도 제한은 domain/guidance/GuidanceAttemptPolicy, 서비스·출력은 core/platform/background가 소유한다. 이벤트는 위반 감지나 실제 청취 기록이 아니다.
