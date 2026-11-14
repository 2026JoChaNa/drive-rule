# 기기 테스트

`java/`에 Compose 화면 흐름, Room 저장소 및 v1→v2 migration, Pleos 음성 서비스 준비 상태 검사를 둔다. UI 검사는 메모리 프로필·가짜 UX/음성 엔진으로 사용자 데이터를 보존한다. 실제 SDK 검사는 지원되는 Pleos 환경에서 별도로 실행한다.

Android 프로젝트 루트에서 `./gradlew.bat :app:connectedDebugAndroidTest`로 실행할 수 있다. 특정 검사는 AndroidJUnitRunner의 class/method 필터로 실행한다. 현재 UI 검사에는 주·야간, 540dp·큰 글자, 720dp·긴 이름, 400×600dp·큰 글자, UX 제한 전환과 자동 음성 시작이 포함된다. 내부 크기 제한을 실제 시스템 창 전환으로 간주하지 않는다.

최신 결과와 캡처는 [디자인 점검](../../../../docs/android/history/design-compliance-2026-11-12.md), 프로필·권한·음성 범위는 [통합 검증](../../../../docs/android/history/onboarding-permissions-speech-2026-11-12.md)에 기록한다. 서비스 ready 검사는 실제 발화 인식 정확도 검사를 대체하지 않는다.
