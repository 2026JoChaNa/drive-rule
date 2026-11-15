# 내비 신호 연결 근거 및 구현 경계

> 후속 변경: 같은 날 B/H 확장에서 getDestinationInfo/onDestinationInfo를 추가했다. 목적지 좌표·남은 거리만 메모리에 보존하고 경로 상태와 함께 5초마다 조회한다. 이 문서의 목적지 미조회는 작업3 당시 범위이며 현재 동작은 [위치 확장 기록](spatial-events-2026-11-15.md)을 따른다.

확인일: 2026-11-15. 이 기록은 3번 작업인 실제 SDK 신호 수신 경로의 근거다. 상황별 카드 매칭·발화 시점·중복·우선순위는 4번 작업이며 여기서 구현하지 않는다.

## 확인한 공식 계약

- [NaviHelper 가져오기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk): `ai.pleos.playground:NaviHelper:3.0.5`, Pleos 공식 Maven 사용. 공식 POM의 공개 데이터 의존성은 `navi-data:3.0.4`다.
- [초기화와 해제](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release): `NaviHelper(applicationContext)`, `initialize(onConnected, onFailed)`, 같은 리스너의 `removeListener` 후 `release`. 재사용은 새 객체로 초기화한다.
- [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions): 이번 읽기 조회에는 `pleos.car.permission.NAVI_ROUTE`만 사용한다. 경로 변경 권한 `NAVI_ROUTE_SEARCH`, 내비 화면 조작 권한 `NAVI_CUSTOM_MAP`는 필요하지 않다. 공식 SDK 소스 `NaviHelper.kt`의 해당 조회 KDoc은 NAVI_ROUTE를 Normal로 표기한다. 기기별 설치·실제 grant는 별도 확인 대상이다.
- [이벤트 리스너](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners): 콜백 전달 여부는 제공 내비 앱의 상태·구현에 달린다. 초기 연결 성공을 모든 신호의 수신 또는 지속 연결 보증으로 해석하지 않는다.
- [릴리스 노트](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note): 현재 NavigationStatus 사용, 구간 단속 enum 분리 등 3.0.5 기준이다. Vehicle SDK의 과거 VehicleSpeedListener는 최신 SDK에서 제거됐으므로 복사해 쓰지 않는다.

공식 API reference의 일부 상세 페이지는 본문을 제공하지 않아, 다음 공식 배포물의 공개 소스와 AAR 공개 서명을 교차 확인했다. 배포물은 검사용 staging에만 두고 저장소에 넣지 않는다.

- [NaviHelper 3.0.5 공개 소스](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/NaviHelper/3.0.5/NaviHelper-3.0.5-sources.jar)
- [navi-data 3.0.4 공개 소스](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/navi-data/3.0.4/navi-data-3.0.4-sources.jar)
- [NaviHelper 3.0.5 POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/NaviHelper/3.0.5/NaviHelper-3.0.5.pom)

| 콜백 / 데이터 | 확인한 값 | 앱 경계 |
| --- | --- | --- |
| `onNavigationStatus(NavigationStatus)` | `INACTIVE`, `INITIALIZING`, `FREE_DRIVE`, `ROUTE_DRIVE` | 별도 앱 enum으로 보존. 운행 여부·법 위반으로 해석하지 않음 |
| `onRouteStateInfo(RouteStateInfo)` | `isRouting: Boolean` | 경로 안내 활성 상태만 보존. 나머지 데이터 제외 |
| `onTBTInfo(List<TBTInfo>)` | `type: TurnType`, `distance: Double` (m) | enum 이름과 거리만 보존. 설명 문자열 제외. 예정 기동이지 실행된 회전이 아님 |
| `onCautionPointInfo(List<CautionPoint>)` | `type`, `remainDistance: Int` (m) | 유형과 거리만 보존. 지점 ID 제외. SCHOOL_ZONE은 시설 반경·경계 진입의 증명이 아님 |
| `onCameraAlert(List<CameraAlert>)` | 유형, 활성 bool, 거리(m), 제한 속도(km/h), 구간 평균(km/h), 공급자 과속 bool | 공급자 원시 보고 의미를 유지. 카메라 ID 제외. 법 위반 판단/알림을 생성하지 않음 |
| `onCurrentRoadSpeedLimit(Int)` | 제한 속도 정수. KDoc은 1초 간격이나 단위를 `(km)`로 표기 | `rawReportedRoadSpeedLimit`로 보존. 단위 확인 전 km/h로 명명·변환하거나 차량 속도와 비교하지 않음 |

## 구현된 수명과 불확실성 처리

`NavigationSignalSource`는 `StateFlow<NavigationSignalSnapshot>`과 `start()` / `stop()`를 제공한다. `PleosNavigationSignalSource(context)`가 실제 SDK를 감싼다. 내부 순서 제어는 main thread에서 수행하며 Context는 applicationContext만 보유한다.

- `start()` 중복 호출은 SDK를 중복 생성하지 않는다. `stop()`은 타이머·리스너·SDK를 해제하고 다시 `start()`할 수 있다. 세션 generation이 바뀐 뒤 온 초기화·데이터 콜백은 무시한다.
- 최초 연결 성공 후 리스너를 등록하고 `getRouteStateInfo()`와 `getTBTInfo()`를 조회한다. 경로 상태는 on-change이므로 연결 중 5초마다 `getRouteStateInfo()`를 다시 읽는다. 응답을 받은 경우에만 routeActive의 관측 시각을 갱신하며 요청 자체로 fresh 처리하지 않는다. 위치·목적지·검색·경로 변경 API는 호출하지 않는다.
- 관측은 `Unknown`, `Fresh(value, observedElapsedMs)`, `Stale(lastObservedElapsedMs)`, `Unavailable(reason)`로 나눈다. 관측 시각은 앱 수신 시점의 elapsed realtime이며 SDK가 생성한 시각이나 GPS 시각은 아니다.
- **신호 만료 10초, 초기화 제한 15초, 만료 확인 1초, 경로 재조회 5초**는 어댑터 한 곳의 보수적 기본값이다. 공식 전달 주기를 입증한 값이나 실차 검증 결과가 아니다. 재조회에 응답하지 않는 route나 미갱신 status는 Stale이 되며 false로 바뀌지 않는다. 일부 변화 기반 콜백은 정상 연결에서도 Stale일 수 있다.
- `Connected`는 최초 초기화 성공을 뜻한다. SDK 공개 API에는 모든 binder 단절의 통지가 없으므로 상태 하나로 지속 수신 가능성을 판단하면 안 된다. 소비자는 관측 freshness도 확인해야 한다.
- 권한 없음/회수, 초기화 실패·시간 초과, SDK 부재를 명시적으로 보존하고 값을 버린다. 자동 재시도 루프를 두지 않는다. 명시적 stop/start가 새 연결 시도다.
- 경로 교체·재탐색·종료/취소·탐색 오류를 받으면 기존 회전 정보는 즉시 Unknown으로 지운다. 수신되지 않은 자료를 빈 목록이나 false로 만들지 않는다. 잘못된 음수·비유한 거리는 InvalidData로 처리한다.
- 앱은 원문, 좌표, 목적지, 설명, 지점 ID를 저장하거나 로그로 출력하지 않는다. 신호 snapshot은 메모리에만 있다. SDK 내부 로그의 기기별 실제 내용까지 검증한 것은 아니다.

## 차량·서비스 연결 시 지켜야 할 경계

[CarPropertyValue](https://developer.android.com/reference/android/car/hardware/CarPropertyValue)는 AVAILABLE일 때만 값을 사용한다. timestamp는 elapsedRealtimeNanos와 같은 시간 기준이다. [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)의 속도는 signed m/s와 CAR_SPEED(dangerous), 기어·시동·주차 브레이크는 CAR_POWERTRAIN(normal)이다. 권한과 별개로 실제 장치의 property 지원·가용 상태 확인이 필요하다.

[Pleos 주행 제한 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)는 CarUxRestrictions를 기준으로 요구한다. 속도 0/P 기어로 UI 조작 허용을 대신 판단하지 않는다. [CarUxRestrictionsManager](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager)의 현재값 조회와 변경 listener를 함께 사용해야 한다.

[Pleos BG→CCH 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/bg-to-cch-transition-guide)는 사용자에게 제공 중인 기능이 끝난 뒤 불필요한 FGS 상주를 금지한다. 앱 전환 자체만으로 기능이 끝난 것은 아니다. [Android FGS 유형](https://developer.android.com/develop/background-work/services/fgs/service-types)의 specialUse는 다른 적합 유형이 없는 유효한 사용례와 구체적 subtype 설명이 필요하며, 선언만으로 Pleos 배포 승인을 받은 것이 아니다. 단순 Car binder 접근을 connectedDevice로, 대기 중인 감지를 mediaPlayback으로 가장하지 않는다.

공유 [앱 API](../api/app-api.md)의 이벤트 태그는 SCHOOL_ZONE_ENTRY/PARKING_DESTINATION/PARKING_HIGHWAY/RIGHT_TURN_CROSSWALK다. SDK enum을 이 태그로 자동 치환하지 않는다. 학교 시설 좌표 파일·로컬 복사본, 목적지 반경, 발화 거리·중복 정책은 다음 단계의 별도 계약이다. 현재 API에 OVERSPEED 카드 태그는 없다.

## 검증 상태

- 완료: 공식 웹 문서, 공식 POM/SDK 소스, AAR 공개 서명 확인. main-thread 순서 제어·늦은 콜백·초기화 취소·권한·독립 만료·경로 무효화·변화 없는 경로의 조회 갱신/미응답에 대한 JVM 테스트 8개 작성.
- 통합 빌드·JVM 테스트 결과는 [차량·백그라운드 통합 검증](vehicle-background-integration-2026-11-15.md)에 기록한다. 이번 작업에서 에뮬레이터·실차 검증은 실행하지 않는다.
- 미확인: 실차 NAVI_ROUTE grant, 내비 앱별 신호 공급/갱신 주기, 무경로/다른 내비 앱 전환/서비스 재연결, 거리 기준점·초기 안내 거리, 도로 제한 속도 단위, 실제 CarUxRestrictions 전환, release FGS 허용/종료 동작. 이러한 검증 없이 SDK 연결 완료나 상황 감지 완료로 보고하지 않는다.
