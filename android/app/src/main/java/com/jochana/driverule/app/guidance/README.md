# 안내 실행 조정

안내 coordinator의 위치다. 현재 구현 범위는 아래에 기록하며 다음 항목은 실제 주행 안내까지 확장할 때 지켜야 할 경계다.

- domain의 정책·상태 전이와 출력/저장 port를 조립한다. 구체 SDK adapter는 AppContainer가 주입한다.
- 활성 안내 세션 하나의 상태 변경을 직렬 처리하고, 타이머와 출력 명령을 소유한다. 화면 ViewModel의 scope를 사용하지 않는다.
- 외부 I/O·출력 완료는 비동기 결과 입력으로 받는다. 완료를 기다리느라 취소·회수 처리를 막지 않는다. callback 누락에 대비한 시작·표시·음성 실행/전체 안내 상한은 주입된 정책을 따른다.
- 음성·카드별 시작/종료/실패를 추적한다. 안내 ID·실행 세대가 지난 callback으로 현재 상태를 바꾸지 않으며 취소 후 뒤늦게 열린 출력도 정리한다.
- 시작/중단/자원 해제는 검증된 실행 host의 수명을 따른다. Application 객체나 CoroutineScope만으로 백그라운드 실행을 보장하지 않는다.
- 실행 중단·회수·중복 방지·프로세스 복구 규칙은 저장소의 `docs/android/architecture.md`를 따른다.

## 현재 구현 — 2026-11-13

구현: `GuidanceCoordinator`가 외부 주입 scope/출력/시계를 사용해 단일 카드·중복·만료·취소와 최대 10분 세션을 관리한다. 현재 host는 debug `SampleGuidanceService`이며 실제 차량 listener·TTS는 연결 전이다.
