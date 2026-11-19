# A03. 실제 신호 구독과 안내 판단의 분리

- 날짜: 2026-11-15
- 상태: 구현 결정. 기기·플랫폼 배포 검증 대기.
- 근거: 사용자 작업 목록 3번(차량·내비·UX·release 실행) 요청. 상황별 판단은 4번으로 분리.

AAOS 차량/UX와 Pleos NaviHelper의 읽기 전용 어댑터를 `core/platform`에 두고, 값·가용성·관측 시각을 `domain/vehicle`의 Android 비의존 계약으로 전달한다. 앱 process의 runtime은 UI와 FGS 소유권을 조정하고, 실제 서비스는 보이는 Activity에서 시작한다. Compose 수집은 구독 시작이나 음성 재생 명령을 발생시키지 않는다.

기본 Debug와 Release는 실제 UX를 사용한다. 주차를 가정한 UI·TTS 샘플은 Debug 전용 명시 옵션으로 유지한다. 이를 실제 차량 데이터와 자동으로 섞지 않는다. 임의 overlay·주행 중 Activity·위치 추적·차량 제어를 추가하지 않는다.

차량·내비 신호 수신은 이벤트 검출이나 법령 안내 성공이 아니다. 검수 캐시 선택·시점·중복·회수 취소는 다음 단계가 맡는다. 프로필/법령 DB와 공유 서버 계약은 변경하지 않는다. 자원 회수 정책과 지원 범위·검증 결과는 [구현 기록](../history/vehicle-background-integration-2026-11-15.md)을 따른다.
