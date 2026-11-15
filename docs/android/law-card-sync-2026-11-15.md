# 법령 카드 동기화·차량 내부 캐시

2026-11-15. 남은 작업 중 1번인 법령 데이터 연결을 구현한다. 화면·운전자별 안내 이력 연결(5번)과 기기 통합 검증(6번)은 사용자 요청대로 다음 환경 준비 후 진행한다.

## 실행과 저장

`MainActivity → LawCardSyncScheduler → LawCardSyncWorker → CachedRuleCardRepository → BackendApiClient / RoomLawCardCache`로 연결한다. 화면의 Flow 수집은 로컬 데이터만 읽으며 네트워크 호출을 일으키지 않는다. 기존 `UnconfiguredRuleCardRepository`를 제거하고 실제 캐시 저장소를 AppContainer에 연결한다.

- 앱 진입 시 프로세스당 한 번 고유 작업을 예약한다. 실제 차량 시동 신호는 아직 연결하지 않았다.
- 네트워크 연결 조건으로 최초 동기화와 24시간 주기 동기화를 예약한다. 최초 주기 작업은 24시간 뒤이며 기기 제약에 따라 실행이 늦어질 수 있다. 앱 진입과 주기 작업이 겹치면 저장소의 Mutex가 요청부터 저장까지 직렬화한다.
- 일시적인 통신 오류·클라이언트 타임아웃, HTTP 5xx, 저장 오류는 30초부터 지수 backoff로 최대 5회 재시도한다. API 계약에 따라 410 복구 이외의 4xx(408/429 포함)와 잘못된 응답은 해당 실행을 실패로 종료한다. 다음 앱 실행·주기 작업 또는 명시적인 `synchronize()` 호출에서 다시 시도할 수 있다.
- WorkManager 2.9.1을 사용한다. 공식 AAR의 minCompileSdk는 34이며 기존 SDK 버전을 올리지 않는다. 일반 백그라운드 작업만 사용하고 WorkManager의 선택적 foreground service 선언은 제거한다. 기존 debug 샘플 이벤트 서비스와는 별개다.

법령 캐시는 새 `drive-rule-law-cache.db`에 저장한다. 기존 `drive-rule-profiles.db`와 프로필 migration은 변경하지 않는다. `cached_law_card`에는 카드 ID/버전/주제, DTO 직렬화 JSON, 검수 epoch millis, 회수 여부와 지원 여부를 보관한다. `law_cache_state`에는 조건 전체 목록, 불투명 토큰, 마지막 성공 시각을 보관한다. 두 테이블은 한 트랜잭션으로 갱신·조회한다.

공유 ERD의 법령 사본 개념을 구현하되 이번 단계에서는 필드 전체를 별도 SQL 열로 중복하지 않고 DTO 직렬화 JSON과 조회·제외에 필요한 열을 사용한다. DTO 파싱에서 무시한 추가 필드까지 원본 응답 전체를 보존하는 방식은 아니다. 외부로 제공하는 `CachedLawCard`는 DTO와 분리된 검증 모델이다. 날짜는 LocalDate, 검수 시각은 epoch millis이며 조건 테이블 순서를 보존한다. 이후 화면·이력 구현 시 필요해지는 인덱스나 FK는 별도 migration으로 다룬다. 지금은 프로필과 캐시 사이에 DB 간 FK를 만들지 않는다.

## 서버 계약 반영

기준은 [앱 API v1](../api/app-api.md)와 저장소의 서버 구현이다.

1. 토큰을 해석하지 않고 다음 요청에 그대로 전달한다. 카드·회수·조건·토큰·성공 시각을 모두 저장한 뒤에만 새 토큰이 유효하다.
2. 전체 동기화도 병합이다. 응답에 없는 기존 게시 사본은 유지하고, 명시적인 `retiredCardIds`만 회수 처리한다. 회수 행은 삭제하지 않고 조회에서 제외한다.
3. 같은 버전도 갱신한다. 서버의 대표 주제 변경은 카드 버전을 올리지 않을 수 있다. 낮은 버전은 무시한다.
4. 서버에서 회수는 종료 상태다. 기존 회수 사본을 뒤늦은 게시 응답으로 되살리지 않고, 한 응답에 게시·회수가 겹치면 회수를 우선한다. 모르는 회수 ID는 무시한다.
5. HTTP 410과 `SYNC_TOKEN_EXPIRED`가 함께 오면 토큰 없이 한 번 재요청한다. 이 재요청이 실패해도 기존 캐시·토큰을 별도로 지우지 않는다. 토큰 없는 응답은 `fullSync=true`여야 한다.
6. 공통 조건은 매번 전체 교체한다. 조건 키나 옵션을 하드코딩하지 않는다. 기존 조건형 사본도 새 조건 목록에 맞춰 다시 검증한다.
7. 미지원 카드 유형·schema 또는 유효하지 않은 카드 한 장은 안내 대상에서 제외한다. 높은 버전이 지원 불가라면 옛 버전을 계속 안내하지 않도록 지원 불가 상태를 보존한다. 이는 명시적인 회수와 별개의 앱 정책이다. 응답에서 아예 누락된 사본의 보존 규칙과도 구분한다.
8. 모르는 eventTag는 이벤트 없음으로 처리한다. `LIMITED` 카드는 캐시에 남기되 일반 출발 전 catalog에는 포함하지 않는다. 새 `observeCache()`는 향후 용도별 선별을 위해 전체 지원 사본을 제공한다.
9. schema 2의 구조·참조·조건 조합의 유일한 포괄을 검증한다. `when` 누락/null은 wildcard다. Gleo 응답 선택 로직은 이번 작업에 추가하지 않는다.

## 실패·오프라인 상태

아직 동기화한 적이 없는 캐시와 정상적으로 받은 카드 0건을 구분한다. 네트워크 실패나 저장 실패가 발생하면 마지막 성공 사본을 계속 읽을 수 있다. 캐시 내용과 최근 동기화 실행 상태를 분리하여 `observeCache()`와 `syncState`로 제공한다. 성공 시각은 DB에 남지만 실행 중/실패 상태는 프로세스 안의 상태이며 예약 작업 결과는 WorkManager가 별도로 관리한다.

마지막 성공 시각은 법령의 현재 효력을 보장하지 않는다. 법령 데이터 유효 기간·오래된 캐시의 실제 안내 허용 기준은 후속 안내 정책에서 확정한다. 캐시 구현만으로 오프라인 중 새 회수를 즉시 알 수 있다고 주장하지 않는다.

개인 이름·프로필 ID·좌표·VIN·안내 이력을 요청이나 WorkRequest에 넣지 않는다. HTTP 앱 API에는 동기화 토큰만 전달한다. 앱의 기존 host별 평문 예외 및 백업 제외 규칙을 유지한다.

## 이번 단계 밖

- 홈의 ‘들은 안내’, 변경 법규, 상세·근거 화면은 아직 기존 샘플을 사용한다. 서버 사본을 내려받은 것을 실제 들은 이력으로 기록하지 않는다.
- 카드 오류 신고에는 이후 실제 카드 ID와 버전을 연결한다.
- 보호구역 좌표 파일 다운로드·SHA-256 검증은 차량 이벤트 연동 단계의 별도 작업이다. 카드 동기화가 좌표 데이터까지 저장한다는 뜻은 아니다.
- TTS, 실제 차량 신호, 주행 중 안내 판단, 실제 시동에 의한 동기화는 이번 단계에 포함하지 않는다.

## 검증

운영 API `GET /api/v1/cards/sync`를 읽기 전용 호출하여 HTTP 200, fullSync=true, 게시 카드 0건, 회수 ID 0건, 공통 조건 4개를 확인했다. 서버를 변경하거나 데이터를 게시하지 않았다. 실제 카드가 포함된 운영 응답 검증은 게시 데이터가 준비된 뒤 수행해야 한다.

JDK 17 / SDK 34에서 다음 검증을 완료했다.

- `:app:testDebugUnitTest`: 전체 136개 통과, 실패·오류·건너뜀 0개. 이번에 추가한 45개는 카드·schema 2 검증 22개, 병합 정책 10개, 저장소 동기화 13개다. HTTP 410 복구, 실패 시 캐시 보존, 동시 요청 직렬화, 취소 전파, 개인정보 없는 요청도 포함한다.
- `:app:lintDebug`: 오류 0개, 경고 49개. 경고는 고정한 target SDK·AGP·라이브러리의 업데이트 권고이며, 이번 작업에서 호환성 기준 버전을 올리지 않았다.
- `:app:assembleDebug`, `:app:assembleRelease`, `:app:assembleDebugAndroidTest`: 성공.
- 법령 캐시 Room v1 schema JSON 생성 확인. 기존 프로필 코드·schema 해시가 변경 전과 같음을 확인했다. 병합된 release manifest에는 foreground service 권한·WorkManager foreground service가 없으며 debug 샘플 서비스는 유지된다.

Room 계측 테스트 6개는 작성·컴파일했으며 기기에서는 실행하지 않았다. 디스크 재열기, 트랜잭션 실패 rollback, 회수 상태, 조건 갱신 실패, 일관된 Flow 관측은 이 계측 테스트의 검증 대상이다. 기기 실행과 재부팅/네트워크 전환 확인은 사용자 테스트 환경 준비 후 진행한다. 기존 앱 데이터는 초기화하지 않았다.

구현 근거: [고유 WorkManager 작업](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work), [제약·주기·재시도](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work), [WorkManager 2.9.1](https://developer.android.com/jetpack/androidx/releases/work#2.9.1).
