# DRIVE RULE Android

Kotlin·Jetpack Compose 기반의 차량 앱이다. 현재 MVVM/UDF 구조에서 운전자 이름 입력, 최초 권한 안내, 더미 데이터 홈을 연결하고 Pleos 음성 이름 입력 adapter를 추가했다. Android Studio에서는 이 `android/` 폴더만 연다. 서버와 Gradle 빌드를 공유하지 않는다.

## 코드와 문서 위치

- `android/`: 앱 코드, 빌드 설정, Android 개발 README와 Codex 지침.
- `docs/android/`: 구조 설계, 디자인·SDK 검증 기록, 참고 자료.
- `docs/android/decisions/`: Android 전용 결정 기록.

위 경로는 저장소 루트 기준이다. 이 README를 Android 개발 안내의 진입점으로 유지한다. 공유 루트 README의 통합 작성은 프로젝트 마지막 단계에 진행한다. 기존 공유 결정·ERD는 참고하고, GitHub Actions workflow는 지정 위치인 `.github/workflows/`에 유지한다.

## 현재 구현 범위

- 독립 Gradle Wrapper, 버전 카탈로그, 단일 `:app` 모듈
- `MainActivity → DriveRuleApp → DriverFlowViewModel → Repository`와 최초 설정·권한 안내·홈 화면 연결
- `StateFlow` 상태 전달, lifecycle을 따르는 Compose 수집, AppContainer 생성자 주입
- 이름 등록, 건너뛰기 시 ‘운전자 1’, 최대 3명 추가·선택, 재시작 시 선택 프로필 복원
- 최초 등록 후 권한 안내 완료 상태를 Room에 저장하고 미완료 안내는 재시작 후 이어서 표시
- Pleos SpeechToText 3.3.1의 온디바이스 한국어 이름 입력, 빈 이름 화면 진입 시 자동 시작과 중지·다시 말하기
- 홈의 Figma 09 운전자 변경 하단 시트: 현재 운전자 표시, 로컬 프로필 선택·추가, 최대 3명 제한, 라이트·다크 대응
- Room 프로필 저장과 DataStore의 시스템/라이트/다크 테마 선택 저장
- Figma를 기준으로 한 최초 설정·권한 안내·샘플 홈의 라이트/다크 화면, 앱 영역의 창 크기 대응
- 차량 UX가 `Unknown/Restricted`이면 입력·상세 조작 차단

`debug`는 화면 확인을 위해 주차 중 조작 가능 상태를 가정하며, `HomePreviewEntry` 더미 목록을 제공한다. 목록의 문구·시행 상태·시간은 디자인 샘플이며 검수된 법령, 실제 주행 이력 또는 실제 음성 재생 기록이 아니다. 홈의 상세·Gleo·변경 법령 진입은 샘플 또는 준비 중 안내만 제공한다.

`release`에는 샘플 목록이 없으며 차량 상태 `Unknown`으로 입력과 상세를 제한한다. 실제 차량 UX adapter가 연결되기 전에는 정상적인 제품 서비스로 사용할 수 없다. 서버 API, 법령 캐시·프로필별 확인 이력, 차량 신호, 법령 변경 알림, TTS, Gleo, 백그라운드 카드와 프로필 이름 수정·삭제는 이번 구현에 포함하지 않는다. 기존 법령 저장소는 계속 `Unavailable`이다.

## 최초 설정과 음성 이름 입력

처음 등록하거나 이름 입력을 건너뛰면 `이름 등록 → 권한 안내 → 홈`으로 진행한다. 권한 안내의 ‘나중에’와 ‘확인하고 시작’은 모두 안내 완료를 저장하고 홈으로 이동한다. 안내를 끝내기 전에 앱을 종료하면 다음 실행에서 안내를 다시 표시한다. 이후 추가 운전자 등록은 바로 홈으로 돌아간다. Room v1에서 이미 운전자를 등록한 사용자는 v2 migration에서 안내 완료로 처리해 최초 설정을 반복하지 않는다.

최초·추가 등록의 빈 이름 화면에 진입하면 화면이 RESUMED이고 UX 조작이 허용될 때 음성 인식을 한 번 자동으로 시작한다. 이미 직접 입력한 이름은 자동 인식으로 덮어쓰지 않는다. 중간 결과는 상태 영역에 표시하고 최종 결과만 이름 입력란에 반영하며, 저장은 사용자가 이름을 확인한 뒤 ‘다음’을 눌러 확정한다. 취소·직접 입력·화면 이탈·ON_PAUSE/ON_STOP·UX 제한 전환 시 인식을 종료한다. 같은 화면 세션에서 복귀해도 자동 재개하지 않으며, 중지·타임아웃 후에는 ‘다시 말하기’로 명시적으로 재시도할 수 있다. 음성을 사용할 수 없거나 인식에 실패하면 직접 이름을 입력할 수 있다.

음성 adapter는 [SpeechToText 3.3.1의 `ON_DEVICE` 모드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro)와 [`Locale.KOREA`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/set-locale)를 사용한다. 서버 인식으로 전환하지 않으며 SDK가 병합하는 앱의 `INTERNET` 권한을 제거한다. [`pleos.car.permission.STT_SERVICE`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions)는 대상 Pleos 환경의 normal 권한으로 별도 OS 허용 팝업이 없다. 권한 안내는 사용 범위를 설명하는 앱 화면이며 권한 부여 결과를 나타내지 않는다. 알림·차량 속도/기어 연동은 준비 중이고 Gleo 질의는 별도 후속 기능이다.

음성 입력 코드의 연결과 실제 차량에서의 한국어 인식 성공·품질은 구분한다. 이번 변경의 빌드·테스트·장치 검증 결과는 [권한 안내·음성 입력 검증](../docs/android/onboarding-permissions-speech-2026-11-12.md)에서 관리한다.

## 개발 문서

1. [Codex 작업 지침](AGENTS.md)
2. [개발 컨벤션](CODEX_CONVENTIONS.md)
3. [화면·주행 안내·플랫폼의 구조와 실행 수명](../docs/android/architecture.md)
4. [Pleos·Figma·GitHub 출처 확인 기록](../docs/android/source-check-2026-11-12.md)
5. [디자인 가이드 준수 점검과 남은 UI 검증](../docs/android/design-compliance-2026-11-12.md)
6. [결정 008: 화면과 주행 안내의 책임·수명 분리](../docs/android/decisions/008-android-guidance-architecture.md)
7. [결정 009: 로컬 프로필과 화면 시연 범위](../docs/android/decisions/009-local-profile-and-ui-demo.md)
8. [최초 설정·샘플 홈 구현 및 검증 기록](../docs/android/ui-implementation-2026-11-12.md)

이름 등록·출발 전 화면은 MVVM/UDF로 구현한다. 향후 주행 안내는 `domain/event`·`domain/guidance`의 순수 판단, `app/guidance`의 실행 조정, `core/platform`의 SDK adapter로 나눈다. 새 `app/guidance`와 `core/platform/presentation`은 역할 README만 있으며 coordinator·출력 기능 구현 전이다.

현재 화면은 최초 설정·권한 안내와 샘플 홈의 UI 구현이다. 화면 검증 결과는 위 구현 기록에서 관리하며, 빌드·화면 시연을 실제 차량의 물리 글자 크기·UX 제한·Pleos 디자인 준수 완료로 해석하지 않는다.

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
| Room / KSP | 2.7.2 / 2.1.20-1.0.32 |
| Preferences DataStore | 1.1.1 |
| Coil / SVG | 2.7.0 |
| Pleos SpeechToText | 3.3.1 |
| namespace / applicationId | `com.jochana.driverule` |

SDK 34는 설치된 Pleos API 34 개발 환경을 위한 초기 빌드 기준이다. SpeechToText 3.3.1을 추가했으며 최종 제출 targetSdk와 다른 Pleos SDK의 호환성은 해당 연동 단계에서 확인한다. 의존성 추가나 빌드 성공을 실제 음성 인식·차량 연동 성공으로 해석하지 않는다.

글꼴은 공식 Asta Sans 배포본을 번들로 사용한다. SIL Open Font License 1.1 원문을 `app/src/main/assets/licenses/ASTA_SANS_OFL.txt`에 포함했다. SVG 이미지는 번들 asset을 읽으며 이미지 로딩을 위해 원격 서버에 연결하지 않는다.

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

### Run 뒤 아이콘을 눌러도 앱 창이 보이지 않을 때

Pleos에서 앱 프로세스는 실행됐지만 기존 task가 정상 앱 slot에 표시되지 않는 현상을 확인했다. 앱만 완전히 종료·재실행하면 복구됐으며 저장된 운전자·테마는 유지됐다. Android 터미널에서 실행한다.

```powershell
.\scripts\restart-app.ps1
# 장치가 여러 개면 -Serial emulator-5554 등 실제 serial 지정
```

스크립트는 현재 Android 사용자를 조회한다. 앱 데이터 삭제나 에뮬레이터 초기화는 하지 않는다. 원인 관측·아이콘 출처·실행 검증은 [런처 점검 기록](../docs/android/launcher-verification-2026-11-12.md)을 참고한다.

Manifest는 Automotive 기기를 대상으로 하며 이름 음성 입력용 `STT_SERVICE`를 선언한다. 아직 주행 중 조작 가능 앱으로 선언하지 않았고 차량·위치·overlay·foreground service 권한을 요청하지 않는다. 앱이 직접 마이크를 녹음하는 `RECORD_AUDIO` 경로는 사용하지 않는다.

## 검증과 CI

JVM 테스트는 기존 출발 전 상태 처리에 더해 이름 검증, 프로필 흐름, 중복 저장 방지, UX 제한, 저장소 재시도와 테마 오류 복구를 대상으로 한다. Room 계측 테스트에는 동시 생성 상한·선택 보존·DB 재오픈 시나리오가 있다. 보고서는 `app/build/reports/tests/testDebugUnitTest/`와 `app/build/reports/lint-results-debug.html`에 생성된다. 새 기능의 실행 결과와 화면 검증은 [구현 기록](../docs/android/ui-implementation-2026-11-12.md)을 따른다.

[Android CI](../.github/workflows/android-ci.yml)는 Android 변경에서 lint, JVM 테스트, debug 빌드를 실행하도록 설정되어 있다. 원격 CI 실행·실제 차량 동작은 로컬 빌드와 별도로 검증한다. 연결된 기기의 계측 테스트는 `:app:connectedDebugAndroidTest`로 실행한다.

서명 release APK는 제출 단계에서 루트 `apk/조차나_DRIVERULE_release.apk`에 준비한다. debug APK를 제출물로 복사하지 않으며 서명키는 저장소 밖에서 관리한다.

## 2026-11-12 최초 scaffold 검증 — 과거 기록

아래는 프로필·샘플 홈을 추가하기 전 결과이며 현재 변경의 통과 증거가 아니다. 최신 결과는 [최초 설정·샘플 홈 구현 기록](../docs/android/ui-implementation-2026-11-12.md)에 별도로 기록한다.

- JDK 17에서 `:app:lintDebug :app:testDebugUnitTest :app:assembleDebug` 성공.
- ViewModel 테스트 5개 통과, 실패·오류·건너뛰기 0개.
- lint 오류 0개. 버전 업데이트 권고 21건(7개 대상이 분석 변형별로 반복)만 남아 있다. 경고를 숨기지 않았으며 초기 SDK34 호환 조합을 유지한다.
- debug APK 생성 확인. 에뮬레이터 설치·화면 실행·실차 및 Pleos SDK 동작은 아직 검증하지 않았다.
- 초기 로컬 검증 당시에는 GitHub 원격 CI 실행·커밋·푸시를 수행하지 않았다. 이후 원격 연결 재확인은 [출처 확인 기록](../docs/android/source-check-2026-11-12.md)의 추가 점검을 참고한다.
