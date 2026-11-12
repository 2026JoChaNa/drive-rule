# Android MVVM 기본 구조

2026-11-12. 단일 `:app` 모듈로 시작한다. 화면·상태·순수 로직·외부 연동의 경계를 패키지로 나누며, 현재 기능 구현이 없는 위치는 README로 역할을 보존한다.

## 폴더 구조

```text
android/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/libs.versions.toml
├── gradle/wrapper/ + gradlew / gradlew.bat
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/                       문자열·테마·아이콘·백업 제외 규칙
        │   └── java/com/jochana/driverule/
        │       ├── MainActivity.kt / DriveRuleApplication.kt
        │       ├── app/                   앱 조립과 AppContainer
        │       ├── core/
        │       │   ├── model/             플랫폼과 독립적인 모델
        │       │   ├── data/
        │       │   │   ├── repository/    저장소 구현
        │       │   │   ├── local/         향후 Room/DataStore
        │       │   │   └── remote/        향후 API/DTO/mapper
        │       │   └── platform/
        │       │       ├── vehicle/       차량 UX 상태 adapter
        │       │       ├── pleos/         향후 NaviHelper/Vehicle/FusedLocation
        │       │       └── audio/         향후 TTS/audio focus
        │       ├── domain/
        │       │   ├── repository/        저장소 인터페이스
        │       │   ├── vehicle/           차량 UX 상태 인터페이스
        │       │   ├── event/             향후 이벤트 판정
        │       │   └── guidance/          향후 카드 선택·중복·종료 조정
        │       ├── feature/
        │       │   ├── predeparture/      Route·Screen·ViewModel·UiState
        │       │   ├── profile/           향후 이름 등록·운전자 선택
        │       │   ├── driving/           향후 주행 안내 화면
        │       │   └── settings/          향후 설정
        │       └── ui/theme/              Compose 공통 테마
        ├── test/java/                     JVM ViewModel 테스트
        └── androidTest/                   향후 기기 테스트
```

## MVVM과 의존성 방향

```text
View (Compose Route/Screen)
    ↓ 사용자 동작                  ↑ UiState / StateFlow
ViewModel
    ↓ 인터페이스
domain/repository, domain/vehicle
    ↑ 구현                          ↔ core/model
core/data, core/platform

app/AppContainer → 구체 구현 생성 → ViewModel 생성자 주입
```

- **View:** Route는 ViewModel을 연결하고 lifecycle에 맞춰 상태를 수집한다. Screen은 전달받은 상태와 callback으로 그린다. SDK/DB/API를 직접 호출하지 않는다.
- **ViewModel:** 표시 상태를 조합한다. `Activity`, `Context`, Pleos SDK 객체를 보관하지 않는다. 화면별 UiState를 feature 안에 둔다.
- **Model:** `core/model`은 앱 내부 모델이다. `RuleCardSummary`는 화면에 필요한 최소 요약이며 서버 DTO나 DB 스키마를 확정한 것이 아니다.
- **Repository port:** `domain`은 인터페이스만 소유한다. UI가 Room/API 구현을 알 필요가 없도록 `core/data` 구현을 주입한다.
- **Platform:** SDK의 권한/지원 여부/콜백을 앱 타입으로 변환한다. 이벤트 판단에 `CarPropertyValue` 같은 플랫폼 객체를 넘기지 않는다.
- **AppContainer:** 구체 구현을 선택하는 단일 조립 위치다. 현재는 수동 생성자 주입으로 충분하며 Hilt나 다중 모듈은 실제 필요가 생기면 도입한다.

이벤트 판정·카드 선택이 복잡해지면 `domain/event`·`domain/guidance`에 순수 로직/use case를 추가한다. 단순 Repository 호출을 감싸기 위해 모든 동작에 use case나 BaseViewModel을 만들지 않는다.

## 기본 실행 상태

`UnconfiguredRuleCardRepository`는 `Unavailable`, 차량 UX 공급자는 `Unknown`을 반환한다. ViewModel은 Unknown/Restricted일 때 카드 목록을 노출하지 않는다. `Unrestricted`가 확인된 경우에만 정상 빈 결과와 데이터 이용 불가를 구분한다. 테스트용 fake는 `src/test`에만 있다.

현재 화면은 구조 검증용이며 Figma 완성 UI가 아니다. 앱 백그라운드 실행, 주행 중 팝업, TTS, 자동 종료, 프로필 저장을 구현한 상태로 소개하지 않는다. 상세 기능은 사용자 요청과 기존 컨벤션에 따라 순서대로 추가한다.

## 다음 구현 위치

| 작업 | 위치와 연결 |
|---|---|
| 이름 등록/최대 3명/기본 운전자 | `feature/profile` → repository port → Room 구현 |
| 법령 캐시/회수/동기화 | `core/data/local`, `remote`, `repository`; 검수된 게시본만 제공 |
| SDK 신호/UX 제한 | `core/platform/pleos`, `vehicle` → 앱 모델 |
| 이벤트 기반 사전 안내 | `domain/event` → `domain/guidance` → 화면/음성 adapter |
| 주행 카드/자동 종료 | `feature/driving`와 guidance coordinator, 플랫폼 표시 방식 검증 후 |
| 음성/설정 | `core/platform/audio`, `feature/settings` |

## 버전 선택 근거

- [AGP 8.7](https://developer.android.com/build/releases/agp-8-7-0-release-notes): Gradle 8.9와 JDK 17 기준. 프로젝트 AGP는 8.7.2로 고정했다.
- [Kotlin Gradle 호환성](https://kotlinlang.org/docs/gradle-configure-project.html), [Compose compiler plugin](https://kotlinlang.org/docs/compose-compiler-migration-guide.html): Kotlin/Compose plugin 2.1.20을 같은 버전으로 사용한다.
- [Compose BOM 2024.12.01](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2024.12.01/compose-bom-2024.12.01.pom): Compose 1.7.6, Material 3 1.3.1 조합. 사용하는 AndroidX AAR의 minCompileSdk 34를 확인했다.
- [Gradle 8.9 공식 SHA-256](https://services.gradle.org/distributions/gradle-8.9-bin.zip.sha256): Wrapper 배포 체크섬을 고정했다.

이 조합은 설치된 JDK17/SDK34에서 기본 구조를 빌드하기 위한 기준이다. 앱 마켓 제출 조건과 실제 Pleos SDK 의존성 호환성은 별도 확인한다.
