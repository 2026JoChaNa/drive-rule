# Android Codex 컨벤션

작업 시작 시 [CODEX_CONVENTIONS.md](CODEX_CONVENTIONS.md)를 읽는다. SDK·디자인 관련 작업은 [출처 및 검증 기록](../docs/android/source-check-2026-11-12.md)도 확인한다.

## 필수 기준

1. 1차 범위는 운전자 이름, 출발 전 안내, 상황 이벤트에 앞선 음성·자동 종료 카드 안내다. Gleo 대화 기능은 별도 요청 전 구현하지 않는다.
2. Kotlin + Compose + ViewModel/StateFlow를 신규 기본 구조로 사용한다. Pleos·AAOS 접근은 adapter로 분리하고 이벤트 판단은 JVM에서 검증 가능한 순수 Kotlin으로 작성한다.
3. Android Studio에서는 이 폴더만 연다. Gradle Wrapper·버전 카탈로그는 이 폴더가 소유한다. SDK/AGP/Kotlin/JDK 버전은 Pleos 호환성을 확인한 뒤 고정한다.
4. 법령 문구는 검수된 게시 카드에서만 가져온다. AI가 시행일·처벌·법령 문장을 생성하거나 예시를 실제 법령으로 게시하지 않는다.
5. 이름·좌표·VIN·개인별 이력은 차량 밖으로 보내지 않는다. 분석 SDK나 가명 기기 ID를 추가하지 않는다.
6. `RETIRED` 카드는 대기·표시·음성 경로에서도 제외한다. 삭제 대신 회수 상태를 보존한다. 자동 닫힘과 음성 종료를 사용자 확인으로 기록하지 않는다.
7. 기능 제한은 `CarUxRestrictions`를 따른다. 속도 0, 도착 이벤트, 기어 P 하나로 조작 가능 상태를 단정하지 않는다. 상태를 읽지 못하면 제한을 유지한다.
8. 백그라운드 실행, 선제 TTS, 시스템 위 카드 표시는 각각 공식 지원·허용을 확인한다. 임의 overlay 권한이나 background Activity 실행으로 대체하지 않는다.
9. 미지원·권한 거부·오래된 신호는 `Unknown/Unavailable`로 처리한다. 주행 상황이나 법 위반을 추측으로 확정하지 않는다.
10. `docs/decisions/001`~`005`와 현재 ERD를 존중한다. 006·007, 이벤트 매핑, cooldown, 카드 표시 시간, API DTO는 확정 여부를 표시한다.
11. UI 작업은 상세 컨벤션 9절과 [디자인 점검 기록](../docs/android/design-compliance-2026-11-12.md)을 따른다. 기본 Material 화면·빌드 성공을 Pleos 디자인 준수 완료로 보고하지 않는다. 글자 물리 크기, 대비, 주·야간, 1/2 window와 UX 제한 전환을 확인한다.

## 구현 후 검증

`android/`에서 JDK 17과 Android SDK를 설정한 뒤 다음을 실행한다. 버전과 패키지 구조는 README 및 docs/android/architecture.md를 따른다.

```powershell
.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

Flavor나 모듈명을 바꾸면 위 명령과 README도 실제 task에 맞춘다. 에뮬레이터·실기기 시나리오는 상세 컨벤션의 완료 기준을 따른다. 실행하지 못한 검증은 이유를 보고한다.
