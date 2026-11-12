# DRIVE RULE Android

Kotlin·Jetpack Compose 기반의 차량 앱 개발용 MVVM 기본 구조다. Android Studio에서는 이 `android/` 폴더만 연다. 서버와 Gradle 빌드를 공유하지 않는다.

## 현재 구현 범위

- 독립 Gradle Wrapper, 버전 카탈로그, 단일 `:app` 모듈
- `MainActivity → DriveRuleApp → PreDepartureRoute/Screen → ViewModel → Repository` 연결
- `StateFlow` 상태 전달, lifecycle을 따르는 Compose 수집, AppContainer 생성자 주입
- 차량 UX가 `Unknown/Restricted`이면 상세를 숨기는 기본 처리
- 프로필·주행 안내·설정·이벤트·데이터·Pleos/TTS 후속 구현 위치

현재 공급자는 차량 상태 `Unknown`, 카드 저장소 `Unavailable`을 반환한다. 앱 시작 시 차량 상태 확인 불가 화면이 나오는 것이 정상이다. 법령·운전자 데이터를 생성하지 않으며 이름 등록, Room, 서버 API, 실제 차량 신호, TTS, 백그라운드 카드 기능은 아직 연결하지 않았다.

## 개발 문서

1. [Codex 작업 지침](AGENTS.md)
2. [개발 컨벤션](CODEX_CONVENTIONS.md)
3. [MVVM 구조와 확장 위치](../docs/android/architecture.md)
4. [Pleos·Figma·GitHub 출처 확인 기록](../docs/android/source-check-2026-11-12.md)
5. [디자인 가이드 준수 점검과 남은 UI 검증](../docs/android/design-compliance-2026-11-12.md)

현재 화면은 MVVM 상태 연결용 기본 UI다. 일부 안전·테마 기반은 반영했지만 Figma 구현과 Pleos 디자인 검수를 완료한 상태는 아니다.

## 도구 버전

| 항목 | 버전 |
|---|---|
| JDK / JVM target | 17 |
| Gradle / Android Gradle Plugin | 8.9 / 8.7.2 |
| Kotlin / Compose compiler plugin | 2.1.20 |
| min / compile / target SDK | 28 / 34 / 34 |
| Build Tools | 35.0.0 |
| Compose BOM | 2024.12.01 |
| Activity / Lifecycle | 1.9.3 / 2.8.7 |
| Coroutines | 1.9.0 |
| namespace / applicationId | `com.jochana.driverule` |

SDK 34는 설치된 Pleos API 34 개발 환경을 위한 초기 빌드 기준이다. 최종 제출 targetSdk와 실제 Pleos SDK 버전 호환성은 연동 단계에서 확인한다. SDK를 추가하지 않은 기본 구조의 빌드 성공을 Pleos 연동 성공으로 해석하지 않는다.

## 열기 및 빌드

1. Android Studio에서 이 폴더를 열고 Gradle JDK를 17로 설정한다.
2. SDK Manager에서 Android SDK Platform 34와 Build Tools 35.0.0을 준비한다.
3. `ANDROID_HOME`을 설정하거나 Android Studio가 생성하는 `local.properties`에 `sdk.dir`을 둔다. 로컬 경로는 커밋하지 않는다.

PowerShell, `android/`에서:

```powershell
.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

macOS/Linux에서는 같은 위치에서:

```bash
bash ./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Pleos 에뮬레이터를 실행한 뒤 Android Studio에서 `app`을 Run하거나:

```powershell
.\gradlew.bat :app:installDebug
```

Manifest는 Automotive 기기를 대상으로 한다. 아직 주행 중 조작 가능 앱으로 선언하지 않았고 차량·위치·overlay·foreground service 권한을 요청하지 않는다.

## 검증과 CI

JVM 테스트는 미확인/제한 상태에서 카드가 숨겨지는지, 미연동과 정상 빈 결과가 구분되는지, 차량 상태 변경 후 기존 카드가 사라지는지를 검증한다. 보고서는 `app/build/reports/tests/testDebugUnitTest/`와 `app/build/reports/lint-results-debug.html`에 생성된다.

[Android CI](../.github/workflows/android-ci.yml)는 Android 변경에서 lint, JVM 테스트, debug 빌드를 실행하도록 설정되어 있다. 원격 CI 실행·실제 차량 동작은 로컬 빌드와 별도로 검증한다. 기기 테스트는 아직 추가하지 않았다.

서명 release APK는 제출 단계에서 루트 `apk/조차나_DRIVERULE_release.apk`에 준비한다. debug APK를 제출물로 복사하지 않으며 서명키는 저장소 밖에서 관리한다.

## 2026-11-12 로컬 검증 결과

- JDK 17에서 `:app:lintDebug :app:testDebugUnitTest :app:assembleDebug` 성공.
- ViewModel 테스트 5개 통과, 실패·오류·건너뛰기 0개.
- lint 오류 0개. 버전 업데이트 권고 21건(7개 대상이 분석 변형별로 반복)만 남아 있다. 경고를 숨기지 않았으며 초기 SDK34 호환 조합을 유지한다.
- debug APK 생성 확인. 에뮬레이터 설치·화면 실행·실차 및 Pleos SDK 동작은 아직 검증하지 않았다.
- 초기 로컬 검증 당시에는 GitHub 원격 CI 실행·커밋·푸시를 수행하지 않았다. 이후 원격 연결 재확인은 [출처 확인 기록](../docs/android/source-check-2026-11-12.md)의 추가 점검을 참고한다.
