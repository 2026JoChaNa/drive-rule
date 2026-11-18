# DRIVE RULE Android

Kotlin·Jetpack Compose 기반의 차량 앱이다. 운전자 등록·권한 안내·샘플 홈, 법령 캐시 동기화, Pleos 음성 입출력에 이어 실제 차량·내비 신호와 백그라운드 실행 host를 연결했다. 현재 실제 우회전(I), 학교 시설 접근(H), 목적지 주변 보호구역 주정차(B)를 검수된 법령 카드 선택·TTS·자동 종료 안내까지 연결했다. 고속도로 주정차(A)는 도로 분류 입력 확보 전 보류한다. Android Studio에서는 이 `android/` 폴더만 연다. 서버와 Gradle 빌드를 공유하지 않는다.

## 코드와 문서 위치

- `android/`: 앱 코드, 빌드 설정, Android 개발 README와 Codex 지침.
- `docs/android/`: 구조 설계, 디자인·SDK 검증 기록, 참고 자료.
- `docs/android/decisions/`: Android 전용 결정 기록.

위 경로는 저장소 루트 기준이다. 이 README를 Android 개발 안내의 진입점으로 유지한다. 공유 루트 README의 통합 작성은 프로젝트 마지막 단계에 진행한다. 기존 공유 결정·ERD는 참고하고, GitHub Actions workflow는 지정 위치인 `.github/workflows/`에 유지한다.

## 현재 구현 범위

**2026-11-15 작업 4 — 우회전 법령 안내:** 첫 `TURN_RIGHT` 접근을 검수 캐시 카드와 연결하고, 원문 TTS·최대 8초 앱 배너/시스템 EVENT 알림을 출력한다. 시점·우선순위·영속 시도 예약·회수/프로필/경로 변경 취소를 처리한다. 후속 작업으로 학교·목적지 입력을 연결했다. 고속도로 이벤트는 도로 분류 입력 부족으로 보류한다. [Figma B·H·I 연결 기록](../docs/android/spatial-events-2026-11-15.md)과 [결정012](../docs/android/decisions/012-school-and-destination-events.md)을 함께 읽는다. 임시 수치·중복 상한과 미검증 사항은 [구현 기록](../docs/android/event-guidance-2026-11-15.md), [결정 011](../docs/android/decisions/011-event-guidance-policy.md)을 따른다. 기기 실행은 하지 않는다.

**2026-11-15 작업 3 — 실제 신호·host 연결:** 기본 Debug와 Release 모두 AAOS의 실제 `CarUxRestrictions`·기어·속도·시동·주차 브레이크와 Pleos NaviHelper 신호를 구독한다. `VehicleMonitoringService`가 화면 밖에서도 구독 수명을 관리하며 사용자 중지·미사용 자원 정리를 처리한다. 신호 수신은 법령 이벤트 감지나 안내 성공을 뜻하지 않는다. 현재 범위와 검증 상태는 [차량·백그라운드 연결](../docs/android/vehicle-background-integration-2026-11-15.md), 구조 결정은 [010](../docs/android/decisions/010-vehicle-signal-host.md)을 따른다. 이번 작업에서는 기기를 실행하지 않는다.

**2026-11-15 작업 2 — 음성·배너:** Pleos 온디바이스 TTS와 오디오 포커스 처리를 연결했다. 명시적 Debug 샘플 모드에서는 합성 이벤트를 음성과 최대 8초 안내 배너로 제공하며, 타 앱 전면에서는 시스템 알림을 사용한다. 이름 등록 중에는 음성을 억제한다. 실제 스피커·팝업 노출과 내비 공존 검증은 별도다. [구현·검증 범위](../docs/android/voice-and-transient-guidance-2026-11-15.md)를 따른다.

**2026-11-15 법령 동기화 추가:** 앱 진입 및 24시간 주기에 앱 API를 호출하여 법령 카드·조건·토큰을 별도 Room 캐시에 저장한다. 변경·회수·410 토큰 만료·오프라인 보존을 처리한다. 화면은 아직 아래의 샘플을 사용하며, 캐시를 화면·안내 이력에 연결하는 단계는 후속 작업이다. [구현 범위·검증 기록](../docs/android/law-card-sync-2026-11-15.md)을 기준으로 한다.

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
- 실제 UX·차량 property·내비 신호 수신, 권한·미지원·오래된 값 구분, Debug/Release의 서비스 수명 관리

기본 `debug`도 실제 UX와 실제 host를 사용한다. `HomePreviewEntry` 더미 목록은 표시용으로 남아 있으며 문구·시행 상태·시간은 검수된 법령, 실제 주행 이력 또는 실제 음성 재생 기록이 아니다. 홈의 상세·Gleo·변경 법령 진입은 샘플 또는 준비 중 안내만 제공한다.

`release`에는 샘플 목록·샘플 발생기가 없다. 두 variant 모두 차량 shared library·UX 신호를 사용할 수 없으면 `Unknown`으로 입력과 상세 제한을 유지한다. 주차 UI와 기존 합성 이벤트·TTS·배너 시연이 필요할 때만 Debug 빌드에 `-PdriveRuleSampleMode=true`를 명시한다. Release에는 이 옵션이 적용되지 않으며 실제 연결 검증은 옵션 없이 진행한다.

4번의 공통 안내 경로와 우회전·학교·목적지 감지를 연결했다. 고속도로 판단 근거 확보, 5번 실제 화면·프로필별 이력, 6번 설정·기기 검증은 남아 있다. Gleo와 프로필 이름 수정·삭제도 후속 범위다. 최초 동기화 전 법령 catalog는 `Unavailable`이며 성공한 빈 사본과 조회 실패를 구분한다. 신호·검수 사본·시점·로컬 중복 제한을 모두 통과한 B/H/I 접근만 출력한다. 24시간 최대2회 시도라는 기존 개발 제한은 세 이벤트가 공유하며, 위치 권한·학교 사본이 없으면 H/B 입력 조건에 따라 보류한다. 출력 시도 기록은 들은 안내나 사용자 확인 이력이 아니다.

## 백엔드 API 연결 준비

2026-11-13 전달된 [앱 API v1](../docs/api/app-api.md)과 운영 OpenAPI를 기준으로 `core/data/remote/BackendApiClient`를 준비했다. 카드 동기화·보호구역 manifest 조회·오류 신고·답변 보류 신고의 DTO와 오류 처리를 제공한다. 2026-11-15부터 앱 진입 시 별도 WorkManager 작업이 카드 동기화를 요청한다. 프로필 등록 및 Compose 화면 수집에서 직접 호출하지 않는다. API 클라이언트는 지연 생성되며 운영 서버에 시험 데이터를 쓰지 않았다.

**운전자 등록은 차량 내부에 저장한다(2026-11-13 사용자 확인).** 현행 서버에는 등록 API가 없으며 최초 등록·추가 등록은 기존 `RoomDriverProfileRepository`를 사용하며 이름·프로필 ID를 전송하지 않는다. 별도 등록 API가 추가되면 요청/응답·인증·중복 처리 계약을 확인한 뒤 연결한다. 현재 공통 통신 준비를 사용자 등록 서버 연동 완료로 보지 않는다.

기본 주소는 `http://13.124.175.42:8080/`이며 `BuildConfig.API_BASE_URL`로 관리한다. `-PdriveRuleApiBaseUrl=http://10.0.2.2:8080/`로 에뮬레이터에서 개발 PC의 서버를 선택할 수 있다. 주소는 인증 정보·query·하위 경로가 없는 origin이어야 한다. 평문 허용은 운영 IP 하나에 한정하고 debug에만 `10.0.2.2`, `127.0.0.1`, `localhost`를 추가했다. 새 HTTP host를 사용하려면 해당 variant의 `network_security_config.xml`에도 명시한다. 앱 전체 평문 허용이나 인증서 검증 해제는 사용하지 않는다.

통신 조합은 OkHttp 4.12.0 / Retrofit 2.11.0 / kotlinx.serialization 1.8.1이다. 알 수 없는 JSON 필드는 무시하고 wire enum은 문자열로 받는다. 캐시 mapper에서 지원 카드 유형/schema 2를 검증하며, 카드·회수·조건·토큰을 원자적으로 저장한다. WorkManager 2.9.1이 네트워크 조건과 재시도를 맡는다. 과거 [연동 준비 기록](../docs/android/backend-api-preparation-2026-11-13.md)과 현재 [법령 캐시 기록](../docs/android/law-card-sync-2026-11-15.md)을 구분한다.

## 최초 설정과 음성 이름 입력

처음 등록하거나 이름 입력을 건너뛰면 `이름 등록 → 권한 안내 → 홈`으로 진행한다. ‘확인하고 시작’은 현재 기기에서 요청 가능한 dangerous 권한인 차량 속도와 API 33+ 알림 권한을 요청하고, 거부·취소해도 안내를 완료할 수 있다. ‘나중에’는 OS 요청 없이 진행한다. 안내 완료와 OS 권한 허용은 별개이며 홈의 권한 버튼으로 다시 요청할 수 있다. 제한 전환이나 프로필·화면 변경 후 도착한 이전 요청의 callback은 안내를 완료시키지 않는다. 안내를 끝내기 전에 앱을 종료하면 다음 실행에서 다시 표시한다. 이후 추가 등록은 바로 홈으로 돌아간다. Room v1 기존 등록자는 v2 migration에서 안내 완료로 처리한다.

최초·추가 등록의 빈 이름 화면에 진입하면 화면이 RESUMED이고 UX 조작이 허용될 때 음성 인식을 한 번 자동으로 시작한다. 이미 직접 입력한 이름은 자동 인식으로 덮어쓰지 않는다. 중간 결과는 상태 영역에 표시하고 최종 결과만 이름 입력란에 반영하며, 저장은 사용자가 이름을 확인한 뒤 ‘다음’을 눌러 확정한다. 취소·직접 입력·화면 이탈·ON_PAUSE/ON_STOP·UX 제한 전환 시 인식을 종료한다. 같은 화면 세션에서 복귀해도 자동 재개하지 않으며, 중지·타임아웃 후에는 ‘다시 말하기’로 명시적으로 재시도할 수 있다. 음성을 사용할 수 없거나 인식에 실패하면 직접 이름을 입력할 수 있다.

음성 adapter는 [SpeechToText 3.3.1의 `ON_DEVICE` 모드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro)와 [`Locale.KOREA`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/set-locale)를 사용한다. 서버 인식으로 전환하지 않는다. 앱은 백엔드 API 준비를 위해 `INTERNET` 권한을 선언하지만 음성 adapter는 계속 `Mode.ON_DEVICE`만 사용한다. [`pleos.car.permission.STT_SERVICE`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions)는 대상 Pleos 환경의 normal 권한으로 별도 OS 허용 팝업이 없다. 차량 속도·알림의 런타임 요청과 차량/내비 normal 권한의 실제 허용 상태는 별도로 관리한다. 알림 거부는 차량 신호 구독을 막지 않는다. Gleo 질의는 후속 기능이다.

음성 입력 코드의 연결과 실제 차량에서의 한국어 인식 성공·품질은 구분한다. 최초 입력 검증은 [2026-11-12 기록](../docs/android/onboarding-permissions-speech-2026-11-12.md), 현재 권한·UX·host 변경의 결과와 기기 미검증 범위는 [2026-11-15 연결 기록](../docs/android/vehicle-background-integration-2026-11-15.md)에서 관리한다.

## 개발 문서

1. [Codex 작업 지침](AGENTS.md)
2. [개발 컨벤션](CODEX_CONVENTIONS.md)
3. [화면·주행 안내·플랫폼의 구조와 실행 수명](../docs/android/architecture.md)
4. [Pleos·Figma·GitHub 출처 확인 기록](../docs/android/source-check-2026-11-12.md)
5. [디자인 가이드 준수 점검과 남은 UI 검증](../docs/android/design-compliance-2026-11-12.md)
6. [결정 008: 화면과 주행 안내의 책임·수명 분리](../docs/android/decisions/008-android-guidance-architecture.md)
7. [결정 009: 로컬 프로필과 화면 시연 범위](../docs/android/decisions/009-local-profile-and-ui-demo.md)
8. [최초 설정·샘플 홈 구현 및 검증 기록](../docs/android/ui-implementation-2026-11-12.md)
9. [결정 010: 실제 신호 구독과 안내 판단 분리](../docs/android/decisions/010-vehicle-signal-host.md)
10. [실제 차량·내비·UX·Release host 연결](../docs/android/vehicle-background-integration-2026-11-15.md)

이름 등록·출발 전 화면은 MVVM/UDF를 사용한다. `domain/vehicle`은 실제 신호 계약, `domain/event`·`domain/guidance`는 순수 판단, `app/guidance`는 실행 조정, `core/platform`은 SDK·서비스 adapter를 담당한다. 실제 차량·내비·위치 신호 host에서 B/H/I 법령 판단과 출력 coordinator까지 연결했다. 타 앱 위 사용자 정의 카드 지원은 여전히 확인되지 않았고 시스템 알림 게시가 팝업 노출을 보장하지 않는다.

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
| Pleos TextToSpeech / NaviHelper | 3.3.1 / 3.0.5 |
| Pleos FusedLocation | 1.1.3 |
| namespace / applicationId | `com.jochana.driverule` |

SDK 34는 설치된 Pleos API 34 개발 환경을 위한 초기 빌드 기준이다. SpeechToText·TextToSpeech 3.3.1과 NaviHelper 3.0.5를 사용하며 최종 제출 targetSdk와 기기별 지원은 별도 확인한다. 의존성 추가나 빌드 성공을 실제 음성 인식·차량 연동 성공으로 해석하지 않는다.

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

### 시연 영상 녹화용 빌드 (시연 모드)

시연 영상을 찍을 때만 쓰는 debug 빌드다. release에는 들어가지 않는다.

```powershell
.\gradlew.bat :app:installDebug -PdriveRuleDemoMode=true
.\scripts\restart-app.ps1
.\scripts\demo-event.ps1 -Event turn   # school · parking · turn · highway
```

- 샘플 모드와 같이 주차 상태를 가정하고 샘플 화면 데이터를 쓴다
- 홈의 개발용 테스트 패널과 30초 타이머 이벤트는 없다
- 화면 곳곳의 '샘플' 표시를 숨긴다. 대신 영상 자막에 시연용 샘플 데이터라고 밝힌다
- `demo-event.ps1`을 실행하면 앱이 앞으로 나오고, 해당 이벤트의 Figma 카드(H · B · I · A)가 열리며 카드 문장을 음성으로 읽는다
  - 앱이 스스로 다른 앱 위로 나오는 것이 아니라 adb 명령이 앱을 띄운다. 실제 차량에서의 표시 방식이 아니다
  - 카드와 음성 문장은 디자인 확인용 샘플이며 검수된 법령이 아니다. 들은 안내 이력으로 기록하지 않는다

Manifest는 Automotive 기기를 대상으로 하며 STT/TTS, 차량 읽기 `CAR_SPEED`·`CAR_POWERTRAIN`, 내비 읽기 `NAVI_ROUTE`, 실제 host의 `specialUse` foreground service와 선택적 알림 권한을 선언한다. 기본 Debug와 Release 모두 실제 host를 사용한다. 주행 중 조작 가능한 DO 앱으로 선언하지 않았으며 실제 UX 제한을 따른다. 위치·overlay 권한과 직접 녹음용 `RECORD_AUDIO` 경로는 사용하지 않는다. 서비스 용도 선언은 Pleos 배포 승인이나 영구 실행 보장이 아니다.

## 검증과 CI

JVM 테스트는 이름·프로필·UX·저장소와 실제 신호의 가용성·만료·늦은 callback·구독 수명을 대상으로 한다. Room 계측 테스트에는 동시 생성 상한·선택 보존·DB 재오픈 시나리오가 있다. 보고서는 `app/build/reports/tests/testDebugUnitTest/`와 `app/build/reports/lint-results-debug.html`에 생성된다. 과거 화면 결과는 [UI 구현 기록](../docs/android/ui-implementation-2026-11-12.md), 현재 변경의 실행 결과·기기 미검증 범위는 [차량·host 연결 기록](../docs/android/vehicle-background-integration-2026-11-15.md)을 따른다.

[Android CI](../.github/workflows/android-ci.yml)는 Android 변경에서 lint, JVM 테스트, debug 빌드를 실행하도록 설정되어 있다. 원격 CI 실행·실제 차량 동작은 로컬 빌드와 별도로 검증한다. 연결된 기기의 계측 테스트는 `:app:connectedDebugAndroidTest`로 실행한다.

서명 release APK는 제출 단계에서 루트 `apk/조차나_드라이브룰_release.apk`에 준비한다. debug APK를 제출물로 복사하지 않으며 서명키는 저장소 밖에서 관리한다.

## 2026-11-12 최초 scaffold 검증 — 과거 기록

아래는 프로필·샘플 홈을 추가하기 전 결과이며 현재 변경의 통과 증거가 아니다. 이후 UI 결과는 [최초 설정·샘플 홈 구현 기록](../docs/android/ui-implementation-2026-11-12.md), 최신 차량·host 변경은 [2026-11-15 연결 기록](../docs/android/vehicle-background-integration-2026-11-15.md)을 따른다.

- JDK 17에서 `:app:lintDebug :app:testDebugUnitTest :app:assembleDebug` 성공.
- ViewModel 테스트 5개 통과, 실패·오류·건너뛰기 0개.
- lint 오류 0개. 버전 업데이트 권고 21건(7개 대상이 분석 변형별로 반복)만 남아 있다. 경고를 숨기지 않았으며 초기 SDK34 호환 조합을 유지한다.
- debug APK 생성 확인. 에뮬레이터 설치·화면 실행·실차 및 Pleos SDK 동작은 아직 검증하지 않았다.
- 초기 로컬 검증 당시에는 GitHub 원격 CI 실행·커밋·푸시를 수행하지 않았다. 이후 원격 연결 재확인은 [출처 확인 기록](../docs/android/source-check-2026-11-12.md)의 추가 점검을 참고한다.

## 샘플 시연과 실제 연결 검증의 구분

`-PdriveRuleSampleMode=true`로 만든 Debug에서만 기존 10분 샘플 세션을 사용한다. 첫 합성 이벤트는 10초 뒤, 이후 30초마다 발생하며 2번에서 추가한 TTS·자동 종료 배너를 시연한다. 샘플 계측 테스트는 이 빌드 옵션과 `backgroundSmoke=true`를 모두 요구한다. [2026-11-13 샘플 기록](../docs/android/background-events-2026-11-13.md)은 당시 동작의 과거 기록이며 현재 기본 실행 모드를 뜻하지 않는다.

옵션 없는 Debug와 Release는 실제 신호를 받지만 법령 이벤트를 생성하지 않는다. 사용자 중지는 재진입으로 자동 해제되지 않으며 백그라운드에서 유효한 구독 유지 근거가 없으면 유예 후 종료한다. 숨은 상태의 자동 재출발 감지나 부팅 복구를 보장하지 않는다. 이번에는 기기를 실행하지 않았고 다음 기기 확인 항목은 [현재 연결 기록](../docs/android/vehicle-background-integration-2026-11-15.md)에 남긴다.
