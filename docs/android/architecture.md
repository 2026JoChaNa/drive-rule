# DRIVE RULE Android 구조

2026-11-13 · [결정 A01](decisions/A01-android-guidance-architecture.md) · [결정 A02](decisions/A02-local-profile-and-ui-demo.md) · 단일 `:app` 모듈

앱 코드·빌드·개발 README는 `android/`, 설계·검증·Android 전용 결정은 `docs/android/`에서 관리한다. 공유 루트 README의 통합 작성은 마지막 단계로 남긴다.

화면에는 **MVVM + 단방향 상태 흐름(UDF)**을 사용하고, 주행 안내에는 **화면과 수명을 분리한 실행 조정과 명시적인 상태 전이**를 사용한다. 프로필·법령 캐시는 Repository를 통해 차량 내부에 보관하며, Pleos SDK는 adapter로 격리한다. 이름 등록 같은 단순 기능에는 공통 MVI 프레임워크나 형식적인 UseCase를 추가하지 않는다.

## 적용 상태

| 현재 구현됨 | 아직 기능 구현 전 또는 검증 대기 |
|---|---|
| Compose Screen, ViewModel, immutable UiState/StateFlow | 검수 법령 캐시·프로필별 확인 이력 |
| 이름 최초 설정·건너뛰기·최대 3명 추가/선택, Room 저장 | 이름 수정·삭제와 이력 보존/삭제 정책 |
| 최초 권한 안내 완료 상태 저장, Room v1→v2 migration | 실제 차량 속도/기어·Gleo 연동 |
| Pleos 온디바이스 이름 STT adapter와 화면 수명에 따른 시작·종료 | 실제 차량의 한국어 음성 인식 성공·품질 검증 |
| DataStore 테마 저장, 최초 설정·권한 안내·더미 홈 라이트/다크 화면 | 실제 차량의 디자인·물리 크기·분할창 검증 |
| Repository/UX provider 인터페이스, AppContainer 수동 주입 | 실제 차량 이벤트 판정·법령 정책·프로세스 복구 |
| Unknown/Restricted 입력·상세 제한, debug 시연 환경 분리 | Pleos 신호·TTS·사용자 정의 외부 카드 surface |
| 순수 coordinator·debug FGS 샘플·이벤트 수신/알림 게시 분리 | 실제 내비 앱·절전·실차 이벤트 수신 검증 |
| 단일 app 빌드와 CI 설정, JVM·Room 계측 테스트 작성 | 현재 변경의 검증 결과는 별도 구현 기록에서 관리 |

현재 UI·저장 범위와 검증 결과는 [구현 기록](history/ui-implementation-2026-11-12.md)을 따른다. 아래 주행 안내 흐름도와 상태표는 설계다. 이름이 등장하는 타입을 모두 미리 생성하지 않으며, 현재 연결되지 않은 기능의 실행을 보장하지 않는다.

## 서비스에 맞춘 책임

| 흐름 | 담당 | 핵심 규칙 |
|---|---|---|
| 이름 등록·운전자 선택 | `feature/profile` + profile Repository | 이름만, 최대 3명, 건너뛰면 기본 운전자. 제한 중 입력 차단. 저장 한도는 UI뿐 아니라 저장 트랜잭션에서도 보장. |
| 최초 권한 안내 | `PermissionGuideScreen` + `DriverFlowViewModel` + profile Repository | 최초 등록 후 표시, 완료 상태 영속화, 미완료 재시작 복원. 앱 안내 완료와 OS 권한 부여를 구분. |
| 음성 이름 입력 | `VoiceDriverOnboarding` + `NameSpeechController` → `NameSpeechEngine` | 빈 이름 화면 진입 시 RESUMED·UX 허용 조건에서 한 번 자동 시작. 직접 입력 보호, 중지 후 명시적 재시도. 최종 결과만 입력란에 반영하고 ‘다음’으로 저장. |
| 출발 전 안내 | `feature/predeparture` + 카드/확인 이력 Repository | 캐시의 검수된 게시 사본 사용. 조회 실패와 빈 결과를 구분하고 프로필별 확인 이력 반영. |
| 차량 신호 → 안내 후보 | `core/platform/pleos` → `domain/event` | 단위·관측 시각·지원/권한 상태를 보존. 오래되거나 늦은 후보를 제외. |
| 안내 선택·상태 변경 | `domain/guidance` | 게시/회수, 시점, 중복, 우선순위, 채널별 허용을 명시적으로 판단. |
| 타이머·음성·카드 실행 | `app/guidance` → 출력 port → `core/platform` | 한 실행 조정자가 상태를 변경. UI 재구독으로 실행을 시작하지 않음. |
| 설정 | `feature/settings` + 설정 Repository | 설정을 바꾸면 진행 중 안내에도 정책 적용. 음소거를 지원 상실과 구분. |

`decision_json`은 검수된 조건별 문구 선택에만 사용한다. 이벤트 trigger DSL로 쓰지 않는다. 프로필·위치·안내 이력은 서버에 보내지 않으며, 서버 결정 006의 대표 topic 정책과 007의 초안 상태를 구분한다. 서버 wire 계약은 앱 API v1 및 schema 2를 따른다.

## 폴더와 의존성

```text
android/app/src/main/java/com/jochana/driverule/
  app/
    AppContainer.kt           구체 구현 생성과 주입
    DriveRuleApp.kt            화면 연결
    guidance/                 향후 coordinator, 실행 수명·타이머 조정
  feature/
    profile/                  이름 등록·선택, 최초 권한 안내, 화면 수명의 이름 STT controller
    home/                     화면 전용 샘플 홈 모델·화면
    predeparture/             Route / Screen / ViewModel / UiState
    driving/                  안내 상태 표시, 허용된 사용자 동작 전달
    settings/                 설정
  domain/
    repository/               저장소 port
    profile/                  이름 검증·프로필 변경 결과
    vehicle/                  차량 UX port
    speech/                   이름 STT engine 계약·오류
    event/                    순수 이벤트 판정
    guidance/                 순수 정책·상태 전이, 안내 관찰/명령 및 출력 port
  core/
    model/                    실제로 공유하는 앱 모델·단위
    data/
      repository/             저장소 구현
      local/profile/          Room Entity / DAO / Database
      remote/                 향후 API / DTO / mapper
    platform/
      vehicle/                UX 제한 adapter
      pleos/                  차량·내비·위치 신호 adapter
      audio/                  TTS / audio focus adapter
      speech/                 PleosNameSpeechEngine, ON_DEVICE 이름 인식
      presentation/           공식 지원이 확인된 카드 표시 adapter
  ui/theme/                   공통 색상·글꼴·간격·모양
  ui/components/              공통 앱 창·헤더·버튼·asset 표시
```

- `feature` → `domain` 인터페이스/순수 로직과 공유 모델. 화면별 UiState는 해당 feature가 소유한다. 다른 feature의 ViewModel을 참조하지 않는다.
- `domain` → 플랫폼과 무관한 Kotlin 타입과 필요한 Coroutine/Flow 계약. Android·Compose·Pleos·Room·네트워크 DTO·`app`을 참조하지 않는다.
- `core/data`, `core/platform` → `domain` 인터페이스를 구현한다. 저장 형식과 SDK 타입을 UI에 노출하지 않는다.
- `app/guidance` → domain 정책·port에 의존한다. 구체 TTS/카드 adapter, Activity, Screen을 직접 참조하지 않는다.
- `app/AppContainer`는 구체 구현을 선택하는 조립 위치다. feature와 runtime에는 필요한 인터페이스만 주입한다.
- 도메인 모델은 우선 기능 가까이에 둔다. 실제 여러 경계가 공유하는 타입만 `core/model`로 올린다. `RuleCardSummary`는 현재 화면 요약이며 확정된 API DTO가 아니다.
- `feature/home/HomePreviewEntry`는 이번 샘플의 표시 전용 모델이다. 법령 API DTO·게시 카드·실제 주행 이력으로 사용하지 않는다.

`src/debug`와 `src/release`의 `app/AppEnvironment`가 주입 환경을 분리한다. debug만 조작 가능한 주차 상태와 샘플 목록을 제공한다. release는 차량 상태 `Unknown`, 빈 샘플 목록을 제공해 미연동 상태에서 입력·상세를 열지 않는다. 실제 차량 상태를 받는 방식은 후속 adapter 작업이며 debug 가정을 제품 동작으로 옮기지 않는다.

패키지 분리는 컴파일러가 강제하는 Gradle 모듈 경계가 아니다. 변경 리뷰에서 import 방향을 확인한다. 별도 배포·병렬 개발·빌드 병목 등 실질적 필요가 생기면 모듈을 나눈다.

## 화면 상태 관리

Route/앱 조립 화면은 ViewModel 생성·연결과 lifecycle에 맞춘 상태 수집을 맡는다. Screen은 표시 상태와 callback만 받아 그린다. `DriverFlowViewModel`은 `changeName`, `saveName`, `skip`, `selectProfile`, `completePermissionGuide` 등 명시적인 함수로 입력을 처리하고 Repository 결과를 UiState로 변환한다. 복잡한 화면만 Action/reducer 도입을 검토한다.

저장이 중요한 상태는 Room/DataStore가 소유한다. 프로필·현재 선택·최초 권한 안내 완료는 Room, 시스템/라이트/다크 선택은 DataStore에 저장한다. 입력 중인 이름은 ViewModel의 임시 상태이므로 프로세스 종료 후 자동 복원하지 않는다. 한 번 표시할 메시지도 처리 결과를 상태로 표현해 화면이 없는 동안 유실되는 일회성 신호에 의존하지 않는다.

`RoomDriverProfileRepository`는 이름을 trim하고 20 Unicode code point 이내인지 검사한다. 이 숫자는 화면 구현용 임시 한도이며 서버 계약이 아니다. 생성·선택은 트랜잭션으로 처리하며 최대 3명 한도를 저장 시점에도 확인한다. `driver_profile(id, name, last_selected_at)`은 기존 ERD 필드를 따르고, 현재 선택은 Android 전용 `local_profile_selection` singleton/FK에 기록해 시스템 시계 역행에 영향을 받지 않는다. 공유 ERD나 백엔드 스키마를 변경하지 않는다. DB는 v1부터 schema를 내보내며 현재 v2에는 `local_onboarding_state(singleton_id, permission_guide_completed)`를 추가했다. 읽기 오류를 빈 프로필로 바꾸지 않으므로 DB 실패가 신규 등록 화면으로 오인되지 않는다.

최초 등록·건너뛰기는 `이름 등록 → 권한 안내 → 홈`으로 연결된다. 첫 프로필 생성과 안내 미완료 상태를 같은 트랜잭션에 기록하고, ‘나중에’와 ‘확인하고 시작’은 모두 안내 완료 저장 후 홈으로 이동한다. 미완료 상태는 앱 재시작 후에도 복원하고 완료 저장 실패 시 안내 화면에 남는다. 이후 추가 등록은 안내를 반복하지 않고 홈으로 돌아간다. `MIGRATION_1_2`는 v1의 기존 등록자가 있으면 안내를 완료로 설정하고, 프로필이 없으면 첫 등록 후 안내를 진행하도록 한다. 이 값은 앱 내 안내 확인 여부이며 Android 권한 상태가 아니다.

주행 안내를 `LaunchedEffect(uiState)`나 ViewModel의 collector 시작 시 재생하지 않는다. UI는 coordinator가 제공한 읽기 전용 안내 상태를 관찰하고, 허용된 닫기·음소거 같은 명령만 전달한다. 현재 `VehicleUxState`는 출발 전 화면의 보수적인 요약이다. 실제 주행 연동 시에는 기능별 UX 제한과 음성/표시 허용 여부를 별도로 보존하며 `Unrestricted` 하나로 전체 실행 권한을 판정하지 않는다.

## 음성 이름 입력의 책임과 수명

`AppContainer`는 `NameSpeechEngine` factory를 제공하고 `core/platform/speech/PleosNameSpeechEngine`이 Pleos SDK 타입을 감춘다. `VoiceDriverOnboarding`은 화면 수명의 `NameSpeechController`를 만들고, controller는 연결·수음·최종 결과 대기·오류 상태와 취소를 관리한다. 음성 이름 입력은 아래의 장기 주행 안내 coordinator와 별개인 짧은 화면 작업이다.

최초·추가 등록의 빈 이름 화면은 `RESUMED`와 UX 허용 조건을 만족할 때 화면 세션당 한 번 인식을 자동 시작한다. 직접 입력한 이름은 자동 인식으로 덮어쓰지 않는다. 중간 텍스트는 상태 영역에 표시하고 최종 결과만 `changeName`에 전달한다. 인식 결과가 곧 프로필 저장을 뜻하지 않으며 사용자가 ‘다음’을 눌러 확정한다. 취소·직접 입력·이름 화면 이탈·앱 `ON_PAUSE`/`ON_STOP`·UX 제한 전환에서 세션을 종료하고, 세대가 지난 callback은 입력값을 바꾸지 못한다. 같은 화면 세션에서 복귀해도 자동 재개하지 않고 중지·타임아웃 뒤에는 ‘다시 말하기’로 명시적으로 재시도한다. 미지원·권한 실패·무음·타임아웃은 오류 상태로 제공하며 직접 입력을 유지한다. 로컬 엔진 준비는 최대 45초, 첫 ready 이후 청취는 최대 20초로 분리하고 중복 ready는 시간을 연장하지 않는다. 에뮬레이터 냉시작의 약 26초 준비 관측과 검증 범위는 [권한 안내·음성 입력 검증](history/onboarding-permissions-speech-2026-11-12.md)에 기록했다.

adapter는 [SpeechToText 3.3.1의 `Mode.ON_DEVICE`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro)와 [`Locale.KOREA`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/set-locale)를 사용한다. SDK 기본값인 HYBRID 또는 서버 인식으로 전환하지 않는다. [`request()`/`stop()`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/start-and-stop), [최종 결과 구분](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/result-listeners), [listener 제거·release](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/initialization-and-release)를 adapter 내부에서 처리한다.

[`pleos.car.permission.STT_SERVICE`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions)는 대상 Pleos 환경의 normal 권한이며 별도 OS 허용 팝업이 없다. 앱의 권한 안내는 기능 범위 설명만 담당한다. 앱이 직접 녹음하지 않고 원본 오디오·인식 결과를 앱 로그나 질문 이력으로 저장하지 않는다. 백엔드 API 준비로 앱 `INTERNET` 권한은 선언했지만 음성 adapter는 계속 `Mode.ON_DEVICE`를 사용한다. 실제 장치에서의 서비스 가용성·한국어 인식 성공·품질은 통합 검증 기록에서 별도로 확인한다.

## API 경계 — 2026-11-13

`AppContainer.backendApi` → `core/data/remote/BackendApiClient` → Retrofit/OkHttp 순서로 통신한다. 클라이언트 생성만으로 요청하지 않으며 ViewModel이나 `RoomDriverProfileRepository`에는 연결하지 않았다. 2026-11-13 사용자 확인에 따라 등록은 차량 내부 Room 저장으로 유지한다. 현행 서버에는 사용자 등록 API가 없으며 이름·프로필 ID를 전송하지 않는다. 프로필 DB 스키마·migration·입력/권한 안내 흐름은 유지한다.

DTO는 wire 형식이며 domain 모델이 아니다. JSON 추가 필드는 무시하고 enum 문자열 및 decision JSON을 보존하지만, 알 수 없는 카드 유형·schema를 실제 안내에 사용할 수 있다는 뜻은 아니다. 후속 mapper와 원자적 캐시 갱신을 구현하기 전 `ruleCardRepository`는 계속 Unavailable이며 홈은 더미다. 자세한 API 목록·전송 정책·검증 결과는 [연동 준비 기록](history/backend-api-preparation-2026-11-13.md)을 따른다.

## 주행 안내 흐름과 실행 수명

아래 화살표는 런타임 데이터 흐름이며 import 의존성과 다르다.

```mermaid
flowchart LR
    Signals["Pleos 신호 adapter"] --> Rules["이벤트 판정"]
    Rules --> Runtime["안내 coordinator"]
    Cache["법령 캐시 · 프로필 · 설정"] --> Runtime
    Policy["순수 안내 정책 · 상태 전이"] <--> Runtime
    Runtime --> Voice["음성 adapter"]
    Runtime --> Card["허용된 카드 표시 adapter"]
    Voice -->|시작·완료·실패| Runtime
    Card -->|표시·닫힘·실패| Runtime
    Runtime -->|읽기 전용 상태| UI["ViewModel · Compose"]
    UI -->|허용된 명령| Runtime
```

`app/guidance`의 coordinator는 활성 안내 세션마다 하나만 존재한다. 앱의 서로 다른 화면/진입점에서 시작하더라도 같은 세션을 중복 생성하지 않는다. 모든 입력과 완료 callback은 한 순서로 처리하여 상태를 변경한다. reducer는 현재 상태·입력·시간값을 받아 다음 상태와 실행 명령을 계산하고, 직접 SDK/DB/타이머를 호출하지 않는다. reducer와 coordinator 클래스는 주행 기능을 구현할 때 추가한다.

직렬화 대상은 상태 변경과 명령 발행이다. DB/I/O·음성/카드의 완료를 기다리느라 입력 처리를 막지 않는다. 외부 작업 결과는 안내 ID·실행 세대를 가진 입력으로 되돌리고, 대기 중에도 취소·회수·권한 변화를 처리한다. 실행 전 로컬 예약이 필요한 명령은 예약 성공 결과와 최신 허용 상태를 확인한 뒤 발행한다.

coordinator는 실행 host가 제공하는 명시적인 scope와 시작/종료 수명을 따른다. 화면 ViewModel의 scope를 쓰지 않는다. `Application`의 객체 보관이나 별도 CoroutineScope가 OS의 백그라운드 실행 허가를 뜻하지 않는다. 실제 service/host·권한·표시 surface는 Pleos 지원을 확인한 뒤 선택한다. 종료 시 listener·타이머를 해제하고 자기 안내의 음성·카드를 정리한다.

| 소유자 | 소유하는 상태 | 종료·복구 책임 |
|---|---|---|
| 화면 ViewModel | 표시 상태, 아직 저장하지 않은 입력 | 화면 수명에 맞춰 수집. 필요시 초안 복원. 주행 안내 음성 실행 수명을 소유하지 않음. |
| 활성 안내 coordinator | 안내 ID, 채널 상태, 타이머, 실행 중 명령 | 명시적인 세션 시작/중단. 앱 화면 복귀만으로 이전 안내 재생 금지. |
| 로컬 Repository | 프로필, 검수 카드 사본/회수, 확인·안내 기록 | 트랜잭션·migration, 프로세스 재생성 후 재조회. 개인정보 외부 전송 없음. |
| 플랫폼 adapter | SDK 연결/listener와 실제 출력 핸들 | 연결/권한 실패를 보고하고 중단 시 정리. 정책을 독자적으로 재시도하지 않음. |

## 안내 상태 전이 규칙

개념 상태는 `Idle → Eligible → Starting → Presenting → Closed`다. 시작 전에는 `Suppressed`, 시작 후 중단에는 `Cancelled` 또는 `Failed`와 사유를 기록한다. 음성·카드는 따로 `NotRequested/Starting/Active/Completed/Failed/Cancelled`를 갖는다. 구체 Kotlin 타입과 저장 스키마는 첫 구현에서 확정한다.

| 입력/상황 | 처리 규칙 |
|---|---|
| 신선한 이벤트 후보 | 현재 게시 사본, 프로필, 시점, 중복, 우선순위, UX/권한 및 출력 지원을 평가. 통과한 후보만 Eligible로 전환. |
| 실행 시작 | `guidanceId`와 세션/실행 세대값을 부여. 실행 직전 최신 조건 재확인. 한 번에 활성 음성 1개·카드 1개, 기존 자원 종료 전 다음 안내 실행 금지. |
| 출력 요청 수락 | 요청 수락과 실제 음성 시작/카드 표시 성공을 구분. 성공 callback을 받은 채널만 Active로 전환. 지원 API가 실제 시작을 알려주지 않으면 추정 상태와 한계를 명시. |
| 음성/카드 완료 | 각 채널을 따로 종료. 같은 안내의 다른 채널 완료로 간주하지 않음. 요청한 모든 채널이 종료돼야 안내 종료. |
| 자동 닫힘 | coordinator만 타이머를 소유. 표시 시작 기준과 음성 종료 기준을 정책으로 구분. 시작 실패/음소거/완료 callback 누락에도 무한 대기하지 않도록 시작·표시·음성 실행 상한 및 전체 안내 deadline을 정책에 둠. 시간값은 미정. |
| 회수·만료·관련 권한 상실 | 관련 후보/명령을 무효화하고 해당 안내 출력 취소. 실행 중 회수된 문구를 재시도하지 않음. UX/포커스/음소거 변화는 채널별 허용에 따라 중단. |
| 경로·프로필 변경 | 안내의 관련 맥락을 무효화하는 경로 변경이면 후보/활성 안내를 취소하고 재평가. 단순 ETA 갱신을 새 사건이나 자동 취소로 취급하지 않음. 프로필 전환 시 이전 안내를 종료해 운전자별 이력이 섞이지 않게 함. |
| 늦은 callback/타이머 | 안내 ID와 실행 세대가 맞지 않으면 현재 상태를 변경하지 않음. 취소 후 실제로 열린 출력은 adapter에서 정리하도록 보장하며, 단순히 callback만 무시하고 남겨 두지 않음. |
| 새 후보와 취소 동시 도착 | 상태 변경을 직렬화. 취소/회수/권한 이벤트는 신호 샘플 병합 과정에서 버리지 않음. 새 안내 시작 직전 다시 유효성 확인. |
| 실패/통신 단절 | 유효한 로컬 사본이 있을 때만 안내. 음성 실패 시 카드만 유지할지 등 부분 실패 정책은 허용된 채널 조합에 따라 명시. 재연결로 지난 이벤트를 뒤늦게 재생하지 않음. |

음성 시작·카드 표시·종료는 실제 확인된 채널 상태만 기록한다. 자동 종료나 음성 완료는 명시적 사용자 확인으로 변환하지 않는다. 포커스 복구 시 중단된 음성을 자동 재개하는 정책도 첫 구현 전 별도로 정한다. 대기 후보에는 용량·만료 정책을 두고 무제한 큐를 만들지 않는다. 취소·회수 같은 제어 입력은 후보 큐의 포화로 유실되지 않게 한다.

한 안내의 음성·카드는 같은 카드 ID/버전의 검수된 내용 스냅샷을 사용한다. 안내 도중 캐시가 갱신됐다고 새 제목과 이전 음성 문장을 섞지 않는다. 카드 ID에 대한 회수는 진행 중 사본에도 적용한다. 취소·출력 정리 자체가 실패하면 상태를 정상 종료로 꾸미지 않고 후속 실행을 보류하며 장치 복구 규칙을 적용한다.

## 중복과 복구

- 이벤트 발생 식별자/구간과 `profileId + cardId + version`을 구분해 중복 키를 만든다. callback마다 새 발생 ID를 만들거나 경로 재계산을 무조건 새 사건으로 취급하지 않는다. 안내 ID, trip ID, 서버 카드 ID는 서로 다른 목적이다. OS 프로세스가 재시작됐다는 이유로 새 trip으로 판단하지 않는다.
- DB 기록과 SDK 출력은 하나의 원자적 트랜잭션이 아니다. 정확히 한 번 재생을 보장한다고 주장하지 않는다. 실행 전 최소 로컬 예약/중복 기록을 남기고, 종료가 불명확한 시도는 복구 시 자동 재생하지 않는 방향으로 구현한다. 이 선택은 장애 시 안내가 누락될 수 있으나 불확실한 중복 재생을 줄인다.
- 복구 시 캐시·회수 상태·프로필·설정을 다시 읽고 신선한 차량 신호를 확보한다. 이전 in-flight 작업·타이머·TTS를 복원해 즉시 재생하지 않는다. 미완료 예약과 trip의 만료/보존 정책은 저장 구현 전에 확정한다.
- 같은 실행 안의 만료/타이머에는 단조 시간을 사용한다. 재부팅 전 단조 시각을 새 부팅과 비교하지 않는다. 법령 시행일은 한국 기준 날짜로 별도 처리한다.
- 현재 `notice_log`와 `card_confirmation`에 실시간 안내 상태를 임의로 끼워 넣지 않는다. 필요한 로컬 기록 스키마·migration·보존 기간은 결정 문서로 추가한다. 분석 서버나 이벤트 소싱 전체 로그는 도입하지 않는다.

## 구현 순서와 완료 기준

1. **이름 등록:** 최초 설정·권한 안내·Room 저장·운전자 선택과 Pleos 이름 STT adapter를 연결한 상태다. 안내 미완료 재시작·migration·음성 취소/종료·건너뛰기/빈 이름/최대 3명/동시 저장/UX 제한 전환의 실행 결과는 구현 기록에서 확인한다.
2. **출발 전 안내:** 로컬 게시 카드와 프로필별 확인 이력 연결. 회수/오프라인/빈 결과/큰 글자·분할창 검증. 실제 법령 검수본과 개발 fixture 구분.
3. **플랫폼 확인:** 위 UI 개발과 병행해 UX 신호·TTS·카드 surface·실행 host를 각각 작은 연동 예제로 확인. 미지원이면 adapter 계약과 제공 범위를 조정.
4. **주행 안내 규칙:** 가짜 신호/시계/출력으로 상태 전이를 구현. 중복 후보, 우선순위 충돌, 회수, 프로필/경로 변경, callback 역전·누락, 취소 후 늦은 표시, 재구독/프로세스 복구를 검증.
5. **실제 연결:** 검증된 host/adapter만 연결하고 내비 동시 사용·권한 철회·주야간·백그라운드·실제 선행 시점을 기기에서 확인.
6. **서버 계약:** 앱 API v1 공통 client/DTO를 준비했다. 캐시의 카드·회수·조건·토큰 원자적 갱신과 홈 연결은 다음 단계이며 Gleo 기능 2는 후속 범위로 유지한다.

단순 Repository 전달에는 UseCase를 강제하지 않는다. 복잡하거나 공유되는 정책만 분리한다. BaseViewModel/범용 event bus/전역 mutable store는 만들지 않으며, Hilt·MVI 라이브러리·다중 모듈은 실제 필요가 생길 때 도입한다.

## 참고와 빌드 기준

- [Android 아키텍처 권장 사항](https://developer.android.com/topic/architecture/recommendations), [UI/UDF](https://developer.android.com/topic/architecture/ui-layer), [선택적 domain layer](https://developer.android.com/topic/architecture/domain-layer). 이 프로젝트의 순수 domain과 실행 coordinator 구분은 프로젝트 설계이며 공식 UseCase 수명 모델과 동일하다고 주장하지 않는다.
- [개발 컨벤션](../../android/CODEX_CONVENTIONS.md), [디자인 점검](history/design-compliance-2026-11-12.md), [Pleos 근거 및 지원 확인 기록](history/source-check-2026-11-12.md).
- [AGP 8.7](https://developer.android.com/build/releases/agp-8-7-0-release-notes): Gradle 8.9와 JDK 17 기준. 현재 AGP 8.7.2.
- [Kotlin 호환성](https://kotlinlang.org/docs/gradle-configure-project.html), [Compose compiler](https://kotlinlang.org/docs/compose-compiler-migration-guide.html): Kotlin/Compose plugin 2.1.20.
- [Compose BOM 2024.12.01](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2024.12.01/compose-bom-2024.12.01.pom): Compose 1.7.6/Material 3 1.3.1, AndroidX minCompileSdk 34 확인.
- [Gradle 8.9 SHA-256](https://services.gradle.org/distributions/gradle-8.9-bin.zip.sha256): Wrapper 체크섬 고정.
- 로컬 저장/UI 의존성: Room 2.7.2, KSP 2.1.20-1.0.32, Preferences DataStore 1.1.1, Coil/SVG 2.7.0. Asta Sans와 OFL 원문을 앱에 번들로 포함한다. 실제 버전은 `android/gradle/libs.versions.toml`을 따른다.

현재 조합은 JDK17/SDK34 개발 환경 기준이며 Pleos SpeechToText 3.3.1을 추가했다. 프로필·최초 권한 안내·음성 이름 입력 adapter·샘플 홈이 현재 구현 범위다. 주행 안내 엔진과 차량 신호·TTS·카드 SDK 연동은 설계 단계이며, 제출 조건과 실제 음성 인식 결과는 별도로 검증한다.

## 백그라운드 이벤트 실행 준비 — 2026-11-13

최초 구현은 debug 전용 `SampleGuidanceService`가 `GuidanceCoordinator`와 타이머 출처를 소유한다. MainActivity.onStart의 host가 한 번 시작하며 Activity.onStop이나 Compose 재구독은 세션을 끝내거나 새로 만들지 않는다. 읽기 전용 runtime은 전체 수신·앱 화면 부재 중 수신·알림 게시를 구분한다. 알림 권한 거부가 이벤트 처리를 중단시키지 않는다.

서비스는 10분 샘플 세션 또는 명시 중지로 종료한다. 실제 차량 신호·TTS·운전 상황 정책·법령 캐시는 아직 연결하지 않았다. 과거 문서의 전체 주행 엔진·플랫폼 출력 구상과 이번 작은 구현을 구분한다. [상세 실행·검증](history/background-events-2026-11-13.md).
