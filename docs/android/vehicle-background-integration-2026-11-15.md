# 차량·내비 신호와 Release 백그라운드 연결

2026-11-15. 사용자가 승인한 남은 작업 3번을 구현한다. 범위는 실제 신호 구독, 실제 CarUxRestrictions 반영, release에서도 동작하는 실행 host다. 이벤트 감지·카드 매칭·안내 시점은 4번, 실제 화면·운전자별 이력은 5번, 기기 검증은 6번으로 남긴다.

## 연결 구조

`AppContainer → VehicleMonitoringRuntime → AaosVehicleSignalSource / PleosNavigationSignalSource`로 조립한다. `VehicleBackgroundGuidanceHost`는 Activity가 보일 때 서비스 시작을 요청하고, `VehicleMonitoringService`가 화면과 독립적으로 구독 수명을 소유한다. Compose는 상태를 표시하며 SDK나 음성을 시작하지 않는다.

- AAOS: 실제 UX 제한, 선택 기어, 부호 있는 속도(m/s), 시동 상태, 주차 브레이크를 읽는다. 제어 API를 호출하지 않는다. 차량 shared library는 compileOnly이며 APK에 Android Car 구현을 포함하지 않는다.
- UX는 속도·기어 권한과 독립적으로 읽는다. 현재 입력·상세 화면은 DO 화면이 아니므로 requiresDistractionOptimization 또는 active restriction이 있으면 제한한다. 미지원·연결 유실·초기값 미수신은 Unknown이다. P나 속도 0으로 화면을 열지 않는다.
- NaviHelper 3.0.5: 안내 상태, 경로 상태, TBT, 주의 지점, 카메라 알림, 보고된 제한속도를 수신한다. SDK 객체를 Android 비의존 값으로 변환하며 설명 원문·POI ID·좌표·목적지는 보관하지 않는다. [계약·출처 상세](navigation-signals-evidence-2026-11-15.md).
- 차량 관측 시각과 SDK timestamp를 구분한다. 값 없음·권한 거부·지원 불가·실패·오래됨을 0/false로 대체하지 않는다. 연결/실행 세대가 다른 늦은 callback은 버린다.
- 같은 차량 연결에서 오류·권한 철회로 값을 비워도 마지막 유효 비교 시각은 유지한다. `Drive@20초 → 오류 → 늦은 Park@19초`로 가용 상태가 복구되지 않으며 새로운 정상 관측만 복구한다. 새 연결은 비교 기준을 초기화한다.
- 속도 5초, 내비 관측 10초 만료 및 내비 초기화 15초 제한은 장치 검증 전 보수적인 초기 설정이다. 공식 보장 주기나 제품의 안내 타이밍이 아니다. on-change 기어·시동·UX는 변화가 없다는 이유로 만료하지 않지만 연결 유실·권한 철회 시 무효화한다.
- 변화가 없는 활성 경로를 놓치지 않도록 연결 중 경로 상태를 5초마다 읽는다. 실제 응답이 있어야 관측 시각을 갱신하며 미응답·단절을 정상 활성으로 꾸미지 않는다.
- 도로 제한속도의 SDK 주석은 단위를 확정하기 부족하여 `rawReportedRoadSpeedLimit`로 보관한다. 이를 km/h로 단정하거나 과속 판단에 사용하지 않는다. 카메라 제한속도에는 공식적으로 명시된 km/h를 사용한다.

## 수명과 권한

서비스는 보이는 Activity에서 시작하고 `specialUse` FGS·상시 알림·명시적인 중지 action을 사용한다. 알림 권한 거부가 신호 구독을 막지 않는다. 앱 전환·Activity 재생성은 서비스의 구독을 중복 생성하거나 종료하지 않는다. 서비스 중지 후에도 화면이 보이면 UX 구독은 유지된다.

사용자 중지는 차량 내부 설정에 남아 재진입으로 자동 해제되지 않는다. 중지 명령 직후 백그라운드 구독 소유권을 해제하고 `Stopping → onDestroy → Stopped`로 전이해 빠른 재시작 경합을 막는다. 화면이 보이는 동안 필요한 UX 구독은 유지한다. 자동 idle 종료 중 앱이 복귀하면 해당 서비스 종료를 한 번 기다린 뒤 Idle 상태에서만 다시 시작한다. 실패나 사용자 중지에는 자동 재시도하지 않는다. 신호 감시를 되살리는 sticky 복구·부팅 receiver·alarm·wake lock을 추가하지 않는다. 법령 동기화용 WorkManager가 병합하는 권한·컴포넌트와 이 감시 서비스의 수명은 별개다.

미사용 자원 회수를 위해 화면이 없고 구독 유지 근거가 없는 상태가 30초 지속되면 FGS를 종료한다. 유효한 주행 기어·절댓값 0.1m/s 초과 속도·신선한 활성 경로는 구독 유지의 초기 근거로 사용하며, 유효한 시동 Off/Lock은 이 근거보다 우선한다. 30초/0.1m/s는 자원 관리용 초기값이지 trip 경계나 법령 이벤트 판정이 아니다. 신호가 불명확하면 유예 후 종료한다. 그 뒤 숨은 상태에서 새 출발을 스스로 감지하지 않으며 다음 앱 진입에서 연결을 다시 시작한다.

이 host는 실제 신호 연결 기반이며 아직 법령 안내 판단을 수행하지 않는다. 차량 D 상태나 내비 경로만으로 플랫폼이 승인한 사용자 기능을 증명하는 것은 아니다. 실제 안내 기능을 연결한 뒤 [Pleos BG→CCH 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/bg-to-cch-transition-guide)에 따라 사용 중/미사용 전환을 기기에서 검증하고, `specialUse` 용도와 상황형 안내의 배포 승인을 확인해야 한다. 이번 구현·빌드를 마켓/차량 운영 승인이나 영구 실행 보장으로 보고하지 않는다.

권한은 다음과 같이 구분한다.

| 권한 | 처리 |
|---|---|
| `android.car.permission.CAR_SPEED` | dangerous. 최초 ‘확인하고 시작’ 또는 홈 권한 버튼으로 런타임 요청 |
| `android.car.permission.CAR_POWERTRAIN` | 표준 AAOS normal. 선언하고 실제 기기 보호 수준·허용 여부 확인 |
| `pleos.car.permission.NAVI_ROUTE` | NaviHelper normal 읽기 권한. 경로 생성·변경 권한은 추가하지 않음 |
| `POST_NOTIFICATIONS` | API 33+ 선택 요청. 거부해도 신호 구독 가능 |

권한 안내 완료는 OS 권한 허용과 별개다. 거부·취소 후에도 안내를 완료할 수 있으며 ‘나중에’는 OS 요청 없이 진행한다. 주행 제한이 켜지거나 프로필·화면이 바뀐 뒤 도착한 완료 callback은 이전 안내를 완료하지 않는다. 권한 변경은 process 단위 fingerprint로 재조회하여 Activity 교체에도 반영하고, 재허용 시 새 구독으로 초기값을 받는다. 사용자의 클릭 없이 설정 화면을 열지 않는다.

## Debug와 Release

기본 Debug와 Release는 실제 차량 상태·실제 host를 사용한다. Debug의 홈 법령 목록은 여전히 표시용 fixture이며 실제 안내 기록이 아니다. Release에는 샘플 목록·샘플 발생기가 없다. 공유 라이브러리나 차량 UX가 없는 환경에서는 Unknown 화면 제한을 유지한다.

기존 2번의 합성 이벤트·TTS·배너 시연과 주차 UI 미리보기는 명시적으로 다음 옵션을 사용한다.

```powershell
.\gradlew.bat :app:assembleDebug -PdriveRuleSampleMode=true
```

이 옵션은 Debug에만 적용된다. Release는 항상 실제 모드다. 실제 연결 검증은 옵션을 빼고 다시 빌드한다. 샘플 서비스 계측 검증도 이 옵션과 `backgroundSmoke=true`를 모두 요구한다. 기존 프로필·테마·법령 DB·AVD를 지우지 않는다.

## 검증

실제 저장소 `android/`에서 JDK 17 / Android SDK 34로 다음 명령을 실행했다. 최종 결과는 `BUILD SUCCESSFUL`이다.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --console=plain
```

- JVM 230개 통과, 실패·오류·건너뛰기 0개. 차량 신호 정규화·시각 역전·오류 후 늦은 값·권한 철회·연결 교체, 내비 초기화·만료·경로 재조회·중지 정리, UI/FGS 구독 소유권과 idle 자원 회수를 검증했다.
- lint 오류 0개, 경고 49개(의존성 업데이트 45, AGP 업데이트 3, target API 권고 1). 버전 상향은 이번 연동과 분리했다. SDK deprecated API 등의 컴파일 경고는 남아 있다.
- Debug APK, R8를 거친 unsigned Release APK, Android 계측 테스트 APK 빌드 성공. 새 권한 안내 테스트 4개는 컴파일했으며 기기에서 실행하지 않았다.
- Release 병합 manifest에 실제 `VehicleMonitoringService`·specialUse와 optional `android.car`가 포함되는 것을 확인했다. 샘플 서비스·CarProbe·WorkManager `SystemForegroundService`, overlay·full-screen intent·직접 녹음 권한은 포함하지 않는다. Release와 기본 Debug의 `SAMPLE_GUIDANCE=false`를 확인했다.
- `git diff --check` 통과. 이번 단계는 `android/`·`docs/android/`의 37개 파일로 한정했다. 이전 작업 기준에서 이번에 수정하지 않은 291개 파일은 해시가 동일하며, 프로필·법령 캐시 구현을 덮어쓰거나 DB/AVD를 초기화하지 않았다.

에뮬레이터·실차 설치/실행은 하지 않았다. 빌드 통과는 실제 센서 수신·한국어 음성 출력·백그라운드 유지·플랫폼 배포 승인의 증거가 아니다.

다음 기기 확인은 (1) 주차 중 UX 초기값·이름 등록·권한 거부/허용, (2) 제한 전환 때 입력·상세·STT 중단, (3) 내비 전면에서 실제 신호 갱신과 서비스 유지, (4) 화면 재생성·사용자 중지·idle 자원 정리, (5) 권한 철회·재허용·SDK 단절 및 프로세스 재시작을 포함한다. 속도/기어의 실제 관측과 상황 이벤트 감지는 별도로 판정한다. 신호 수신을 실제 안내·청취·법령 확인 이력으로 저장하지 않는다.

## 공식 근거

- [Pleos 주행 규제](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide), [CarUxRestrictionsManager](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager)
- [CarPropertyManager](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager), [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds), [Car 권한](https://developer.android.com/reference/android/car/Car), [timestamp](https://developer.android.com/reference/android/car/hardware/CarPropertyValue)
- [FGS 유형·specialUse](https://developer.android.com/develop/background-work/services/fgs/service-types), [보이는 Activity에서 시작](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [권한 거부 처리](https://developer.android.com/training/permissions/requesting#handle-denial)
