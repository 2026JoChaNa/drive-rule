# 실제 상황별 법령 안내 — 4번 작업 기록

> 후속 변경: 같은 날 B/H 위치·목적지 입력을 연결했다. 이 문서의 “우회전만/학교·목적지 미구현”은 작업4 당시 범위이며, 현재 상태는 [Figma B·H·I 연결 기록](spatial-events-2026-11-15.md)을 따른다.

작성일: 2026-11-15 · 상태: **구현 반영·JVM/lint/APK 빌드 통과 / 기기 미실행**

이번 작업은 실제 차량·내비 신호에서 우회전 후보를 만들고, 로컬에 동기화한 검수 카드의 문구를 선택해 기존 음성·짧은 시각 안내로 연결하는 범위다. 공식 입력 계약과 최종 코드의 경계를 기록한다. 개발 정책은 [결정 011](decisions/011-event-guidance-policy.md)에 명시하며 기기 동작 성공과 구분한다.

선행 구현은 [법령 캐시·동기화](law-card-sync-2026-11-15.md), [음성·배너 출력](voice-and-transient-guidance-2026-11-15.md), [차량·백그라운드 host](vehicle-background-integration-2026-11-15.md), [내비 신호 계약](navigation-signals-evidence-2026-11-15.md)을 따른다. 서버 계약은 [앱 API](../api/app-api.md), 공유 기획은 [결정 009](../decisions/009-core-features-v6-alignment.md)와 [학교 좌표 결정 010](../decisions/010-school-zone-dataset.md)을 참고한다.

## 1. 이번에 연결하는 범위

```text
실제 Vehicle / Navi 관측
  → 가용성·freshness·실제 UX 확인
  → RIGHT_TURN_CROSSWALK 후보
  → 검수된 로컬 캐시 카드 선택
  → 시점·중복·출력 중 상태 확인
  → 동일 카드 사본의 ttsText + 자동 종료 시각 안내
```

- 실제 감지 대상은 `RIGHT_TURN_CROSSWALK` 하나다. SDK의 예정 우회전 신호를 사용하며, 실제 회전 완료나 횡단보도·보행자·신호등 존재를 감지했다고 표현하지 않는다.
- 앱 전면에서는 기존 앱 내부 배너, 다른 앱 전면에서는 시스템 `CATEGORY_EVENT` 알림 경로를 사용한다. 시스템 알림 게시 성공과 운전자에게 보인 팝업은 서로 다른 검증 결과다. AAOS/Pleos에서 heads-up 표시를 보장하지 않는다.
- 음성은 기존 Pleos 온디바이스 TTS 경계를 사용한다. 이름 입력 STT와의 상호 배제, 오디오 포커스와 취소 수명은 선행 음성 구현을 유지한다.
- 홈·상세·출발 전 목록과 운전자별 안내·확인 이력은 별도 후속 범위다. 음성 종료·자동 닫힘을 사용자의 확인으로 저장하지 않는다.
- 합성 타이머 이벤트를 실제 상황 감지로 취급하지 않는다. 명시적 Debug 샘플 모드는 실제 신호 경로와 구분한다.

## 2. 우회전 입력과 임시 정책

NaviHelper `3.0.5`의 공개 데이터 의존성은 `navi-data:3.0.4`다. `TBTInfo`는 `type: TurnType`과 `distance: Double`을 제공하며 거리는 예정 기동까지의 m 단위다. 이번에는 **`TurnType.TURN_RIGHT`(코드 `2L`)만** 우회전 후보로 사용한다. `TURN_KEEP_R`, `TURN_SHARP_R`, IC 진입·출구 안내를 자동으로 같은 이벤트로 확장하지 않는다. 공식 원문은 [NaviHelper 소스](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/NaviHelper/3.0.5/NaviHelper-3.0.5-sources.jar)와 [navi-data 소스](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/navi-data/3.0.4/navi-data-3.0.4-sources.jar)에서 확인했다.

| 항목 | 구현한 개발 기준 | 한계 |
| --- | --- | --- |
| 진입·재무장 | 0 < 거리 ≤150m, ≥200m 또는 확인된 다른 기동에서 재무장 | 위반·법적 구역 경계가 아니며 실차 조정 필요 |
| 내비·차량 | 내비10초, 속도5초; 전진 기어·0 이상 유한 속도 | 속도0은 도착시각 추정 없음, UX 허용 추정 아님 |
| UX·설정 | 실제 UX 알려짐, 활성 프로필·권한 안내 완료, 이름 등록 외 | Restricted에서는 읽기 전용 출력만 사용 |
| 캐시·우선순위 | 성공 동기화24시간 이내, LAW→OPERATION, 현재→예정→날짜없음, verifiedAt 내림차순·ID 오름차순 | 공유 서버 계약이 아니라 개발 선택 정책 |
| 표시·발화 | 표시8초, 후보최대30초/예상도착1초전, 선행최소3초·6자/초 추정 | 실제 한국어 합성 속도·선행시점 미검증, 길면 원문을 자르지 않고 생략 |
| 시도 예약 | 같은 카드/시행단계24시간, 프로필60초 간격·24시간최대2회 | 주행당 제한이나 청취 이력이 아님, 실패·취소도 예약 소비 |
| 취소 | 250ms 재검증, 캐시/신호/프로필 변화 시 추가 검증 | 출력과 DB는 원자적 트랜잭션이 아님 |

150m/200m, 내비 10초/속도 5초는 법령상 기준이나 실차 검증 결과가 아니다. 공개 문서에서 공유 cooldown·우선순위 숫자 계약은 확인되지 않았다. 공유 결정 009의 주행당 안내 수와 짧은 발화에 관한 기획 예시를 구현 완료 기준으로 자동 채택하지 않는다.

`Unknown`, `Unavailable`, `PermissionDenied`, `Failed`, `Stale` 입력에서는 추측으로 후보를 만들지 않는다. 경로 교체·재탐색·취소·종료, 잘못된 거리, 내비 연결 성공 후 미수신 상태를 다룬다. TBT에는 앱에서 사용할 안정적인 기동 ID가 없으므로 단순 콜백 횟수를 새 이벤트로 세면 안 된다. 200m 재무장만으로 재탐색·거리 흔들림·동일 지점 재수신의 중복 문제가 해결됐다고 주장하지 않는다.

## 3. 카드 선택·문구·회수 경계

- 성공적으로 동기화한 로컬 캐시에서 지원되는 카드만 사용한다. 앱 API는 게시 사본을 제공하지만 카드 wire 모델에 별도의 `PUBLISHED` 필드는 없으므로 존재하지 않는 필드에 의존하지 않는다.
- `audience=GENERAL`, 정확한 `eventTag=RIGHT_TURN_CROSSWALK`, 유효한 검수 사본과 `ttsText`를 기준으로 선택한다. `LIMITED`, 이벤트 없는 카드, 지원하지 않는 형식, 회수된 카드를 제외한다.
- 음성과 화면은 같은 카드 ID·version·사본에 근거한다. 출력 직전과 대기·표시·발화 중 캐시 변경 시 회수·교체 여부를 확인하는 것이 검증 기준이다. 선택 후 회수된 카드가 이전 사본으로 계속 안내되지 않아야 한다.
- 선제 음성은 API의 최상위 `ttsText`를 그대로 사용한다. 앱이나 AI가 시행일·처벌·법령 문장을 생성하거나, 짧게 만들기 위해 임의로 잘라 법적 의미를 바꾸지 않는다. 문구가 현재 안내 시간에 맞지 않으면 출력 정책에서 보류해야 한다.
- `decision.tables[].rows[].tts`는 Gleo 질의응답용 조건 분기다. [rule-matching 명세](../../spec/rule-matching/README.md)의 사용자/Gleo 조건을 차량 신호로 임의 채우지 않는다.
- `effectiveOn`이 미래인 카드와 시행 중인 카드를 혼동하지 않는다. `OPERATION` 카드는 법령 개정으로 표시하지 않고 API의 출처 기관·현장 표지판 우선 표시 계약을 유지한다.
- 화면 전환·재구독·같은 신호의 반복 전달만으로 새 음성을 시작하지 않는다. 발화/자동 종료를 사용자 확인이나 영구적인 미확인 해제로 기록하지 않는다.

출력 전 noBackupFilesDir에 시도를 원자 예약하고 DB 캐시·프로필을 재조회한다. 같은 버전의 문구 교정도 사본 전체 비교로 취소한다. 원문이 배너의 2줄 제목·3줄 본문·2줄 맥락/가용 높이에 들어가지 않으면 배너를 생략하며 음성은 독립이다. 시도 기록은 최대128개/24시간이며 손상·시간 역행·저장 실패 시 차단한다.

## 4. 이번에 감지하지 않는 이벤트

| 이벤트 | 현재 부족한 입력 | 확보한 근거와 후속 작업 |
| --- | --- | --- |
| `SCHOOL_ZONE_ENTRY` | 검증된 학교 좌표 로컬 사본과 실제 차량 위치/근접 신호 | Navi `SCHOOL_ZONE` caution은 학교 좌표 반경 진입을 대신하지 않음. API는 사본이 없으면 이벤트 생략을 요구. 좌표 downloader·검증·원자 교체 및 위치 어댑터 필요 |
| `PARKING_DESTINATION` | 학교 좌표 사본, 앱 모델에 연결된 목적지 좌표와 남은 거리 | SDK `DestinationInfo.latitude/longitude/remainDistance`와 `getDestinationInfo()/onDestinationInfo`는 존재. 목적지 변경·취소·미갱신을 처리하는 어댑터 확장 필요 |
| `PARKING_HIGHWAY` | 현재 도로가 고속도로임을 나타내는 검증 가능한 입력 | 기어 P만으로 판단 불가. 공개 Navi 모델에 현재 도로 분류 필드를 확인하지 못함. `RouteOption.HIGHWAY`는 경로 옵션이며 IC TBT는 예정 안내. 현재 학교 좌표 파일도 고속도로 데이터를 제공하지 않음 |

학교 파일은 시설 좌표 목록이며 실제 보호구역 경계 polygon이 아니다. 시설점에서 약 370m, 목적지 주변 약 300m와 남은 거리 약 500m는 앱 API의 기획 조건이지 해당 지점의 법적 구역을 확정하는 근거가 아니다. 과속·좌회전·안전거리도 현재 지원 이벤트에 추가하지 않는다. 특히 앱 API에는 `OVERSPEED` 태그가 없다.

## 5. 위치 후속 구현에 확인한 공식 근거

이번 단계에는 FusedLocation이나 학교 좌표 캐시를 새로 연결하지 않는다. 후속 설계를 위해 다음까지만 확인했다.

- [FusedLocation 가져오기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/project-setup): 공식 의존성은 `ai.pleos.playground:FusedLocation:1.1.3`이다. [위치 수신 가이드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/location-data-access)는 단건 조회·연속 콜백·동일 리스너 해제를 구분한다.
- [공식 소스](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/FusedLocation/1.1.3/FusedLocation-1.1.3-sources.jar)의 `LocationData`는 위도·경도·고도·heading·confidence·timestampMillis를 제공한다. confidence를 오차 반경(m)으로 변환하거나 timestamp를 특정 clock으로 가정할 공개 계약은 확인하지 못했다.
- 같은 소스의 `ProximityAlertRequest`는 좌표·양수 radiusMeters·ENTER/EXIT 집합을 받고, `ProximityAlertListener`는 등록 ID·전이·오류를 전달한다. 등록 가능한 개수와 실차 백그라운드 전달은 확인되지 않았다. Google의 제한 숫자를 Pleos 보장으로 가져오지 않는다.
- 1.1.3 연속 수신 내부 stub은 null 위치를 콜백에 전달하지 않고 로그만 남긴다. 단건 조회는 null을 전달한다. 따라서 null 처리와 함께 무응답 stale 처리가 필요하다.
- [위치 권한 가이드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions)는 `pleos.car.permission.FUSED_LOCATION`을 dangerous로 정의한다. 일반 Android 위치 권한과 포그라운드·백그라운드 접근 정책을 이 SDK의 동작 보장으로 해석하지 말라고 명시한다. privacy 차단 안내 API는 접근 허용 API가 아니다.

현재 `BackendApiClient.schoolZoneManifest()`와 DTO만 존재한다. 실제 좌표 사본에는 manifest ETag/304, URL resolve, 압축 해제 후 SHA-256·크기·schema/dataset/count·좌표 검증, 성공 후 원자 교체가 필요하다. 실패 시 기존 사본을 보존하고 사본이 전혀 없으면 두 학교 관련 이벤트를 생략한다. 목적지·차량 위치는 차량 안에서만 비교한다.

## 6. 권한·백그라운드·개인정보

- 읽기 내비 신호에는 `pleos.car.permission.NAVI_ROUTE`를 사용한다. SDK 조회 API는 Normal 권한으로 문서화돼 있다. 경로 검색·변경 권한을 추가할 이유가 없다. [NaviHelper 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)
- 차량 속도는 `CAR_SPEED`와 기기 property 가용성, 기어 등은 `CAR_POWERTRAIN` 계약을 따른다. 권한 거부·철회와 unsupported를 구분한다. 실제 UI 제약은 CarUxRestrictions로 처리한다. [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds), [Pleos 주행 제한](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
- 기존 FGS host가 제공 중인 기능과 종료 조건을 유지한다. `specialUse` 선언만으로 Pleos 배포 승인이나 모든 백그라운드 신호 수신이 보장되는 것은 아니다. 불필요한 상주, 임의 overlay, 백그라운드 Activity 실행, 거짓 CALL/MESSAGE/NAVIGATION 알림 분류로 팝업을 만들지 않는다. [Pleos BG→CCH](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/bg-to-cch-transition-guide), [Android FGS 유형](https://developer.android.com/develop/background-work/services/fgs/service-types)
- 운전자 이름·차량 좌표·목적지·VIN·개인별 이력·식별자를 외부로 보내지 않는다. 이벤트 후보와 SDK 관측은 필요한 값만 메모리에서 사용하고 원문 신호를 로그로 저장하지 않는다. SDK 내부 로그까지 검증한 것은 아니다.
- 안내 권한·오디오 포커스·TTS 지원·시각 표시 허용 여부는 각각 확인한다. 신호 수신 성공을 음성 출력이나 팝업 표시 성공으로 보고하지 않는다.

## 7. 검증 상태와 다음 기기 확인

| 검증 | 상태 |
| --- | --- |
| 서버 이벤트·카드·학교 좌표 계약, 공식 SDK 소스 확인 | 확인. 위 출처와 선행 근거 문서 참조 |
| 최종 코드와 이 문서의 정책 수치·동작 대조 | 확인. 위 표 및 결정011의 개발값과 일치 |
| 이벤트 경계·재무장·중복·회수·순서·만료 JVM 테스트 | 전체297개 통과(실패/오류/skip 0). 이번 정책32·예약15·실행조정14·출력라우터6개 추가 |
| lint·Debug/Release 빌드 및 회귀 테스트 | lint 오류0/기존 경고49. Debug·Release(R8)·Debug AndroidTest APK 컴파일 모두 성공 |
| 에뮬레이터·실차 실행 | **이번 작업에서 실행하지 않음. 2026-11-16 검증 예정** |

실제 저장소 android/에서 JDK17·SDK34로 아래 명령을 실행했다(2026-11-15, 6분30초, 종료코드0).

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --max-workers=2 --console=plain
```

lint 경고는 GradleDependency45, AndroidGradlePluginVersion3, OldTargetApi1로 선행 작업과 같다. Release merged manifest의 실제 VehicleMonitoringService 포함/샘플 서비스 제외를 확인했다. 새 배너 UI 테스트6개는 APK 컴파일까지만 확인했고 기기에서 실행하지 않았다. 빌드된 Release는 서명 전 검증 산출물이다.

다음 기기 검증에서는 합성 샘플과 실제 내비 입력을 구분하고 아래 결과를 따로 남긴다.

1. 실제 경로의 TURN_RIGHT·거리 갱신, 최초 수신 위치, 150m 부근 안내 시점과 음성 종료까지의 여유를 확인한다. 경로 교체·재탐색·취소·반복 우회전·200m 경계 흔들림에서 오발화와 중복을 확인한다.
2. 속도·내비 신호 중단/지연, 권한 거부·철회, 내비 앱 교체, UX Unknown/제한 전환에서 후보가 보류·취소되는지 확인한다. P나 속도 0만으로 UX 제한을 푸는 경로가 없는지 확인한다.
3. 게시 캐시 없음·미지원·LIMITED·회수·교체 상태와 미래 시행일을 확인한다. 안내 선택 후 회수된 사본이 대기·음성·화면에 남지 않는지 확인한다.
4. 다른 내비 앱이 전면인 동안 서비스 수명, 실제 TTS, 오디오 포커스 손실·복귀, 이름 STT와의 겹침, 중지·재시작을 확인한다. EVENT 알림 게시와 실제 화면 노출을 구분하고 HUN 미노출을 감추지 않는다.
5. 앱 전면 배너의 자동 종료·주야간·창 크기·CarUxRestrictions 적합성을 확인한다. 자동 닫힘과 음성 종료가 사용자 확인 이력으로 바뀌지 않는지 확인한다.

실차 정책·선제 TTS 허용·물리적 표시 적합성·기기별 signal 제공과 배포 승인은 아직 검증 완료로 표시하지 않는다. 구현은 android/와 docs/android/에만 반영한다. 기기 실행·Git commit/push는 이번 요청에 포함하지 않는다.
