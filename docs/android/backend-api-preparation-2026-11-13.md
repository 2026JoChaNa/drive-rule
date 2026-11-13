# Android 백엔드 API 연동 준비

확인일: 2026-11-13 · 대상: 차량 앱용 API v1, 최초 사용자/운전자 등록 요청과 현행 서버 계약의 관계.

## 결론과 현재 범위

**첨부 문서, 운영 OpenAPI, 저장소의 서버 컨트롤러 어디에도 사용자·운전자 등록 API가 없다.** 사용자가 2026-11-13에 차량 내부 저장 구조로 진행하도록 확정했다. 현행 운전자 등록은 기존 Room 프로필 생성·선택·권한 안내 흐름을 유지하며, 이름이나 로컬 프로필 ID를 서버로 보내지 않는다.

이번 Android 작업은 호스트를 한정한 HTTP 연결 설정과 `BackendApiClient`·통신 DTO의 준비 범위다. 클라이언트에는 동기화·manifest GET과 오류·답변 보류 POST의 **4개 연산**만 넣었으며, 좌표 파일 downloader는 아직 없다. 샘플 홈을 실제 서버 데이터로 교체하거나, 카드·조건·토큰을 Room에 동기화하거나, 사용자 등록을 서버에 수행한 상태가 아니다. 빌드·JVM 테스트와 Pleos 에뮬레이터의 공개 GET·로컬 등록 회귀 검증을 완료했다. 아래 결과와 미구현 범위를 함께 따른다.

## 확인한 자료

- 사용자 첨부 `1-app-api.md`: 차량 앱용 API v1, 기준일 2026-11-12.
- 저장소 [차량 앱용 API](../api/app-api.md): 기준일 2026-11-13. 첨부와 앱 API 경로·인증·프로필 비전송 원칙은 같으며, Gleo function 설명과 현재 서버 상태가 보강되어 있다.
- 운영 [OpenAPI JSON](http://13.124.175.42:8080/v3/api-docs): 2026-11-13에 인증 헤더 없는 GET으로 HTTP 200 확인. OpenAPI 3.1.0, `DRIVE RULE API`, v1.
- 공유 [서버 결정 008](../decisions/008-server-api-v1.md): 처음 설정은 차량 DB, 앱은 식별자 없이 호출하므로 인증하지 않는다고 명시한다. 과거 `questions` API·`since` 동기화 설명은 현재 컨트롤러와 다른 이전 결정이다.
- 공유 [서버 결정 009](../decisions/009-core-features-v6-alignment.md): 자유 질문 API 삭제·앱 사본 기반 답변·`syncToken` 방향을 설명한다. 문서 자체의 초안·기획 확인 대기 표시는 유지한다.
- Android [결정 008](decisions/008-android-guidance-architecture.md), [결정 009](decisions/009-local-profile-and-ui-demo.md): 화면/도메인/adapter 경계와 로컬 프로필 보관 원칙. 공유 결정 008/009와 번호가 같지만 별개의 문서다.
- [Android Network Security Configuration](https://developer.android.com/privacy-and-security/security-config): manifest의 설정 파일 연결과 `domain-config`의 호스트별 평문 허용 근거. debug의 로컬 HTTP 예외는 debug 소스 세트의 별도 XML로 적용한다.
- [kotlinx.serialization 1.8.1 공식 릴리스](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.8.1): 해당 릴리스가 Kotlin 2.1.20으로 갱신되었음을 확인했다. 현재 Kotlin 버전과의 선택 근거이며 Android 통합 빌드 성공을 대신하지 않는다.

운영 명세 원문은 작업용 staging의 `app-api-openapi-2026-11-13.json`에 저장했다. 공개 GET의 응답도 staging에 보관했으며 이름·기기 ID·인증정보·질문 원문을 요청에 넣지 않았다. 서버의 POST·관리자 API는 실행하지 않았다.

## 실제 존재하는 앱 API

운영 OpenAPI와 `CardSyncController`, `GeoDatasetController`, `AppRequestController`를 대조했다. 앱용 operation은 아래 5개이며 모두 인증이 없다.

| 메서드·경로 | 요청 | 응답·역할 |
|---|---|---|
| `GET /api/v1/cards/sync` | 선택 쿼리 `syncToken` | 게시 카드·회수 ID·조건 목록·다음 토큰 |
| `GET /api/v1/geo/school-zones` | 필요 시 `If-None-Match` | 보호구역 manifest, 변경 없으면 304 |
| `GET /api/v1/geo/school-zones/{sha256}.json` | manifest가 제공한 URL | 보호구역 좌표 파일 |
| `POST /api/v1/cards/{cardId}/error-reports` | `{ "cardVersion": 양의 정수 }` | 202, 카드 오류 신고 |
| `POST /api/v1/answer-holds` | `topic`, 선택 `conditions` | 202, 답변 보류 신고 |

`/api/v1/admin/**`는 검수 CMS 전용이며 `X-Admin-Key`가 필요하다. 앱 클라이언트에 관리자 키·CMS 경로를 넣지 않는다. 서버 코드의 `AdminApiKeyInterceptor`는 이 관리자 패턴에만 적용된다. OpenAPI의 전역 security와 앱 operation security는 없고, `adminKey`는 관리자 operation에만 지정되어 있다.

`users`, `profile`, `driver`, `register`, `signup`, `auth`, `login`에 해당하는 등록·로그인 경로는 운영 OpenAPI에 없으며 저장소의 컨트롤러에도 없다. CMS 검수자 생성 API는 운전자 등록 API가 아니다.

## 실제 HTTP 읽기 검증

아래는 개발 PC에서 공개 GET을 실행한 결과다. **Android 앱이나 에뮬레이터에서 연결에 성공했다는 결과가 아니다.** 법령 내용의 정확성·검수 상태를 검증하거나 관리자 데이터를 조회하지 않았다.

| 호출 | 실제 결과 |
|---|---|
| `GET /v3/api-docs` | HTTP 200, `application/json`, 19,398바이트. OpenAPI 3.1.0, 서버 URL `http://13.124.175.42:8080`. |
| `GET /api/v1/cards/sync` | HTTP 200, `application/json`. 필드: `syncToken`, `fullSync`, `cards`, `retiredCardIds`, `conditions`. `fullSync=true`, 카드 0개, 회수 ID 0개, 공통 조건 4개. |
| `GET /api/v1/geo/school-zones` | HTTP 200, `application/json`. 필드: `dataset`, `schema`, `referenceDate`, `count`, `sha256`, `bytes`, `url`. `dataset=school-zones`, `schema=1`, `count=12152`, `referenceDate=2026-10-27`, `bytes=313438`. ETag 헤더 있음. |

manifest는 SHA-256 `55c727292dcc0d984fab8f6856eabfae6fd9f20e3dbeb46916a4ee61676f9c48`와 그 해시를 포함한 상대 파일 URL을 반환했다. PC 확인에서는 좌표 파일 다운로드·해시 검증·304 재요청을 실행하지 않았다. 이후 Android 검사에서는 manifest의 ETag 재요청 304까지 확인했다.

게시 카드가 0개이므로 실제 네트워크 응답으로 카드 DTO의 모든 필드·enum·결정 테이블을 검증할 수는 없었다. 그 부분은 계약 기반 fixture와 후속 게시 카드 응답으로 검증해야 한다. 빈 배열은 통신 실패가 아니며 화면에 법령을 만들어 채우지 않는다.

## Android 연결 준비 기준

### 이번 구현에 포함한 설정

| 항목 | 현재 구현 |
|---|---|
| 라이브러리 | OkHttp 4.12.0, Retrofit 2.11.0, kotlinx.serialization 1.8.1. Kotlin 2.1.20 유지. |
| 주소 | `BuildConfig.API_BASE_URL`. Gradle `-PdriveRuleApiBaseUrl=<origin>`으로 변경하며 scheme·host·port의 서버 origin을 지정한다. |
| 평문 범위 | main은 `13.124.175.42`만 허용. debug는 여기에 `10.0.2.2`, `localhost`, `127.0.0.1`을 추가. 나머지 호스트는 기본 평문 금지. |
| 연산 | 카드 sync GET, 보호구역 manifest GET, 오류 신고 POST, 답변 보류 POST. 좌표 파일 다운로드·DB 적용 없음. |
| 자동 동작 | 개인정보를 담은 자동 헤더 없음. 앱 수준 재시도 loop 없음. redirect 비활성화. POST body는 `oneShot`으로 HTTP 계층의 재전송을 방지. |
| timeout | connect 10초, read 15초, write 10초, call 전체 25초. |

이 표는 추가한 코드·설정의 범위이며 실행 검증 수치는 아래 검증 결과 절에서 별도로 관리한다. `oneShot`은 사용자가 별도로 같은 신고를 다시 호출하는 것까지 중복 제거하는 서버 멱등성 계약이 아니다.

### HTTP·인증·데이터 경계

- 운영 서버의 현재 주소는 HTTP이며 HTTPS 제공을 확인한 상태가 아니다. 기본 주소를 빌드 설정으로 관리하고, 주소를 바꿨다는 이유만으로 평문 허용 목록을 자동 확장하지 않는다.
- main network security config는 기본 평문 금지와 함께 **`13.124.175.42` 호스트만** 평문을 허용한다. debug의 로컬 개발용 예외 3개는 main/release에 적용하지 않는다. 앱 전체를 여는 `usesCleartextTraffic=true`를 사용하지 않는다. 도메인/CDN이 달라지면 URL과 허용 대상을 다시 검토한다.
- 네트워크 보안 설정의 예외는 호스트 단위다. `/api/v1` 경로나 특정 포트로 자동 제한되는 기능으로 설명하지 않는다. 앱이 사용할 API 경로는 별도 클라이언트 계약으로 제한한다.
- 공개 API를 위해 `INTERNET` 권한을 선언하더라도 Pleos 음성 인식을 서버 모드로 바꾸지 않는다. 기존 SpeechToText는 `ON_DEVICE`·한국어를 유지한다.
- 이름·로컬 프로필 ID·기기 ID·VIN·위치·질문 원문을 DTO나 자동 헤더에 추가하지 않는다. 등록 API가 없다는 이유로 신고 API에 이 정보를 담아 보내지 않는다.

### `BackendApiClient`와 DTO의 역할

클라이언트는 현행 공개 API 요청·응답과 오류를 표현하는 통신 경계다. 화면이나 로컬 프로필 저장소에 바로 결합하지 않는다. DTO의 nullable 필드와 모르는 JSON 필드를 허용하고, opaque `syncToken`은 해석하지 않고 보관할 수 있도록 한다.

enum은 raw 문자열로 받는 준비 단계다. 문자열로 파싱됐다는 것만으로 카드 사용이 허용된 것은 아니다. **후속 DTO→도메인 mapper에서** 알려지지 않은 `cardType`과 지원하지 않는 결정 `schema`는 해당 카드만 제외하고, 알려지지 않은 `eventTag`는 이벤트 연결 없음/Unknown으로 처리해야 한다. `changeKind`·`audience` 역시 알 수 없는 값을 허용된 법령·일반 대상이라고 단정하지 않는다. 날짜·검수 시각·필수 값 검증도 이 경계에 남아 있다.

오류는 `code`로 분기하고 `detail/errors` 문구를 고정 계약으로 사용하지 않는다. 202 무본문과 304 무본문을 일반 JSON 성공 응답으로 파싱하지 않는다. OpenAPI가 성공 응답만 기술한 부분은 앱 API 문서의 400/404/410/422 오류 계약도 함께 확인한다.

## 아직 연결하지 않는 기능

- 사용자/운전자 서버 등록: 이번 범위에 넣지 않는다. 사용자 결정에 따라 로컬 Room 이름 등록·최대 3명·선택 보존을 유지하며 이름·로컬 프로필 ID를 전송하지 않는다.
- 카드 Room 동기화: 카드·회수·조건·토큰을 한 트랜잭션으로 반영하고 성공 후 토큰을 갱신해야 한다. `fullSync=true`에서도 목록에 없다는 이유로 기존 사본을 삭제하지 않는다. 회수는 `retiredCardIds`로만 반영한다. 서버 `rule_card.topic`의 UNIQUE와 달리 앱 `cached_card.topic`에는 UNIQUE를 두지 않고 `topic = ? AND is_retired = 0`으로 조회한다.
- 동기화 410 복구: 토큰 없이 한 번 재요청하고, 그 밖의 4xx는 무조건 재시도하지 않는다.
- 보호구역 저장: manifest 상대 URL 해석, 다운로드한 비압축 바이트의 SHA-256 검증, 이전 사본 유지, 원자적 교체와 ETag 저장은 별도 구현이다.
- UI 실데이터 전환: 현재 더미 홈과 실제 서버의 빈 카드 응답을 혼합하지 않는다. 샘플 상태 표시를 지우고 실제 법령을 표시하는 전환은 별도 작업이다.
- 신고 전송·Gleo·주행 이벤트: 실제 POST를 보내거나 Gleo 후속 범위를 이번 준비 작업으로 구현하지 않는다. 중복 신고 방지·재시도 정책과 카드 매칭은 각각 검증해야 한다.

## Android 통합·검증 결과

2026-11-13, JDK 17과 Pleos Connect v2.1.0 / Android 14(API 34) 에뮬레이터에서 확인했다. 아래 서버 GET은 새 debug APK에서 실행한 결과다.

| 항목 | 실제 결과 |
|---|---|
| 빌드 | `:app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` 성공. |
| JVM | 총 61개 통과, 실패·오류·건너뛰기 0. 이 중 새 API MockWebServer 검사 16개. |
| lint | 오류 0, 경고 46건(AGP·의존성 업데이트 권고 45, 기존 target SDK 권고 1). 경고를 숨기거나 baseline으로 제외하지 않았다. |
| 네트워크 설정 | merged manifest와 기기에서 INTERNET 허용·RECORD_AUDIO 미부여 확인. 기본 평문 금지, 운영 IP 허용, 다른 공개 호스트·인접 IP·하위 도메인 금지 확인. |
| 실제 서버 GET | 카드 sync 200, 보호구역 manifest 200, 같은 ETag 재요청 304 통과. 실제 서버 POST는 호출하지 않았다. |
| 최초·추가 등록 UI | 최초 이름 등록 → 권한 안내 → 홈, 추가 등록 → 바로 홈 및 기존 운전자 재선택의 2개 검사 통과. |
| Room 저장 복원 | 선택한 프로필·시계 역행 처리, 최초 등록·미완료 권한 안내의 DB 재오픈 검사 2개 통과. 앱 실제 DB는 삭제하지 않았다. |
| Pleos 음성 | INTERNET 권한을 추가한 APK에서도 `ON_DEVICE` 엔진의 ready 및 해제 검사 통과. 실제 한국어 발화의 인식 정확도 검사는 아니다. |

기기 검사는 네트워크 정책 1개 + 실제 서버 GET 1개 + 등록 UI 2개 + Room 복원 2개 + 음성 ready 1개, **총 7개 통과**, 건너뛰기 0이다. UI 검사는 테스트용 프로필 저장소, Room 복원 검사는 독립 테스트 DB를 사용한다. 기존 앱 데이터 삭제·재설치 초기화·migration 변경은 수행하지 않았다.

MockWebServer의 16개 검사는 요청 경로·opaque 토큰 인코딩·신고 body 제한·모르는 필드/enum·null·202/204/304·Problem code·잘못된 JSON·연결 실패·취소·redirect 미추적·POST 503 재전송 방지를 다룬다. 신고 요청은 PC 내부 loopback 서버에서만 실행했다. 최종 응답 처리도 취소 시 본문을 닫고 취소를 결과로 바꾸지 않도록 검토했다.

공개 서버 검사는 `BackendPublicApiSmokeTest`에 `backendSmoke=true`를 명시한 경우에만 실행된다. 기본 계측 검사에서는 이 1개를 skip하여 자동으로 운영 서버에 접근하지 않는다. 연결된 기기에서 공개 GET만 따로 다시 확인할 때는 설치된 debug·androidTest APK에 대해 다음처럼 실행한다(장치와 현재 사용자 번호는 실제 환경에 맞춘다).

```powershell
adb -s emulator-5554 shell am instrument --user 10 -w -r -e backendSmoke true -e class com.jochana.driverule.core.data.remote.BackendPublicApiSmokeTest com.jochana.driverule.test/androidx.test.runner.AndroidJUnitRunner
```

로컬 빌드는 메모리 사용을 줄이기 위해 worker 1개, Gradle heap 1 GiB, Kotlin in-process와 임시 JVM test heap 256 MiB 설정을 사용했다. 저장소의 일반 빌드 명령·CI 설정을 이 장치용 값으로 바꾸지는 않았다. 서명 release APK와 실차 검증은 이번 결과에 포함하지 않는다.

등록 방식은 사용자 확인을 마쳤으며 차량 내부 Room 저장을 유지한다. 서버에 이름을 등록하거나 법령 캐시·홈 실데이터 연결을 완료했다는 뜻은 아니다.

파일 변경 범위는 `android/`와 `docs/android/`다. 공유 서버 컨트롤러·결정·DB 계약은 변경하지 않았다. `git diff --check`와 변경 Markdown의 로컬 링크 검사를 통과했다.
