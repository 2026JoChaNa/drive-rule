# 백그라운드 이벤트 실행 준비 — 2026-11-13

## 이번 범위

사용자 요청에 따라 카드 동기화보다 **다른 앱이 전면인 동안에도 이벤트를 수신·처리하는 실행 수명**을 먼저 검증한다. 향후 주된 출력은 음성이며, 현재 알림 카드는 실행 확인을 위한 선택 기능이다. 실제 차량 이벤트·TTS·법령 API/캐시 연동은 이번 구현에 포함하지 않는다.

Debug 앱을 열면 MainActivity가 서비스 시작을 요청한다. 첫 샘플은 10초 뒤, 이후 30초 간격으로 발생한다. 세션은 최대 10분이며 홈의 실행 중지/샘플 실행으로 제어한다. 명시 중지는 로컬 설정으로 유지되어 화면 재진입이나 프로세스 재시작만으로 풀리지 않는다. 자연 만료 후에는 같은 프로세스에서 직접 다시 시작한다. 강제 종료·재부팅·OS 프로세스 종료 후 무인 자동 부활은 제공하지 않는다.

이벤트는 `timer_sample` 출처의 시뮬레이션이다. 에뮬레이터에서 설정 앱 전환이 성공해도 실제 내비게이션 이벤트, 화면 꺼짐/차량 절전, 실차 지속 수신을 입증하지 않는다. 센서·위치·마이크·wake lock·서버 호출은 추가하지 않는다.

## 구조와 관찰

- `MainActivity` → 변형별 `AppEnvironment.createBackgroundHost()` → debug `SampleBackgroundGuidanceHost` → `SampleGuidanceService`.
- Service가 scope, 샘플 발생기, `GuidanceCoordinator`와 출력 adapter를 소유한다. Activity.onStop은 서비스 중지로 연결하지 않는다. Compose는 읽기 전용 상태만 관찰한다.
- `domain/guidance`의 이벤트 정책은 만료·최근 64개 ID 중복을 차단한다. Coordinator는 단일 카드 교체, 세대별 취소, 8초 닫힘과 세션 만료를 관리한다. 자동 닫힘은 확인/청취 이력이 아니다.
- 홈 테스트 패널: 전체 수신 수, Activity가 안 보일 때 수신한 수, 최근 샘플 제목, 알림 게시 수를 각각 표시한다. 상태는 프로세스 메모리에만 보관하며 운전자별 이력에 저장하지 않는다.
- `adb logcat -s DriveRuleEvents:I`에는 출처·순번·background 여부만 남긴다. 이름·좌표·차량 ID가 없다. `background=true`는 이 앱 Activity가 보이지 않았다는 관찰이며 실제 운전 상태가 아니다.
- 알림 권한이 없어도 이벤트를 처리한다. 시스템에 FGS 알림은 제공하지만 권한 거부 시 알림센터 노출이 제한될 수 있다. 선택적 카드 알림은 홈의 알림 설정으로 허용할 수 있다.

## 공식 플랫폼 경계

[Android FGS 시작](https://developer.android.com/develop/background-work/services/fgs/launch)에 따라 화면이 보이는 동안 서비스를 시작하고 즉시 foreground로 승격한다. Debug manifest에 `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `specialUse` 타입과 구체적인 subtype 설명을 선언한다. [specialUse 규칙](https://developer.android.com/develop/background-work/services/fgs/service-types)은 이 앱의 배포 승인을 뜻하지 않는다.

[Pleos BG→CCH 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/bg-to-cch-transition-guide)은 앱 전환만으로 미사용을 단정하지 않지만 사용자 기능이 끝나면 FGS를 종료하도록 한다. 따라서 샘플을 무기한 상주시키지 않고 10분 만료·사용자 중지 시 모든 타이머와 알림을 정리한다. 실제 배포 세션의 시작/종료 조건은 실제 이벤트 adapter·앱 카테고리·심사를 확인한 뒤 결정한다.

[Android 알림 권한](https://developer.android.com/develop/ui/compose/notifications/notification-permission)은 FGS 실행 자체의 필수 권한이 아니다. 이벤트 수신과 알림 허용을 결합하지 않는다.

샘플 카드는 기본 `Notification`의 정직한 `CATEGORY_EVENT`를 사용한다. [AAOS 알림 규칙](https://developer.android.com/training/cars/platforms/automotive-os/notifications)은 일반 앱 HUN을 HIGH 이상의 CALL/MESSAGE/NAVIGATION으로 제한하므로 타 앱 위 자동 팝업을 보장하지 않는다. 알림 요청 수락은 화면 노출 성공이 아니다. [Pleos System Notification](https://document.pleos.ai/en/docs/connect/guide/docs-design/components/notification/system-notification)은 시스템 알림 디자인 근거이며 사용자 정의 overlay 권한의 근거가 아니다. 임의 overlay, 위장 카테고리, full-screen intent, 백그라운드 Activity 실행은 사용하지 않는다.

[NaviHelper 이벤트](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners)에 TBT/CameraAlert/CautionPoint 등의 listener는 있지만 실제 제공은 내비 앱 구현과 상태에 달려 있다. 다음 단계에서 샘플 발생기를 검증된 SDK listener로 교체하고 출처·신선도·백그라운드 수신을 다시 검증한다. 음성 출력도 별도 SDK·오디오 포커스·내비 안내 공존 정책 검증 뒤 연결한다.

## Debug / release

서비스·자동 시작 구현·FGS/POST_NOTIFICATIONS 권한·샘플 문자열·테스트 패널은 `src/debug`에만 있다. Release의 host는 no-op이다. 순수 coordinator/정책은 main에 두지만 release에서 세션을 시작하지 않는다. API 준비·운전자 Room 저장·온디바이스 STT·공유 폴더는 변경하지 않는다.

## 검증 방법

1. Android Studio에서 debug 앱 Run → 홈의 실행 중 상태를 확인한다.
2. 설정 또는 내비 앱으로 전환하고 45초 이상 기다린다.
3. 앱으로 돌아와 백그라운드 수신 수와 최근 이벤트를 확인한다. 알림 게시가 0이어도 수신이 늘면 이벤트 경로는 동작한다.
4. 실행 중지 후 다시 앱을 오가도 수신 수가 늘지 않아야 한다. 샘플 실행은 새 세션을 시작하며 카운터를 초기화한다.
5. 알림 허용은 선택이다. 허용 시 이벤트 카드는 최대 8초 동안만 게시되고 세션 종료 시 제거한다.

JVM은 가상 시간으로 중복·교체·만료·10분 종료·scope 취소·게시 실패를 검증한다. `SampleGuidanceServiceTest`는 `backgroundSmoke=true`를 명시한 경우에만 Settings 또는 지정한 Activity 전환 후 샘플 2건과 중지를 검증한다. 기존 프로필/권한 설정을 임의 초기화하지 않는다.

## 실행 결과

- JDK 17: `lintDebug`, `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest` 성공. `compileReleaseKotlin`도 성공.
- JVM **70개 통과**, 실패/오류/건너뛰기 0. 신규 coordinator 테스트 9개 포함.
- lint **오류 0 / 경고 46**. 기존 도구·의존성 업데이트 권고 45개와 targetSdk 권고 1개이며 신규 백그라운드 경고는 없다.
- Release merged manifest에서 샘플 서비스·receiver·FGS·POST_NOTIFICATIONS 권한이 제외됨을 확인했다.
- Pleos Connect v2.1.0 / API 34 / user 10의 읽기 전용 콜드 부팅 인스턴스에서 기기 테스트 **1개 통과** (49.56초).
- 전면 전환 대상: `ai.umos.maps.android.navigation.app/.MainActivity`. 테스트 출력: `received=2, background=2, posted=0, notificationPermission=-1`.
- 따라서 알림 권한 거부 상태에서도 내비 Activity 뒤에서 샘플 이벤트 2건을 처리했다. 명시 중지 후 알림 정리·수신 정지와 앱 재진입 시 중지 유지도 같은 테스트에서 확인했다.
- 별도 연속 실행에서 내비 Activity가 전면인 상태와 FGS 유지, 30초 간격의 background=true 로그를 확인했다. 홈 복귀 화면에서 **전체 수신 6건 / 백그라운드 수신 5건 / 알림 게시 0건**을 직접 확인했다. 라이트 테마에서 상태·카운터·버튼이 겹치지 않으며 아래 기존 홈 내용은 스크롤로 접근한다. 다크는 기존 테마 토큰을 사용하지만 이번 기기 실행에서 별도 시각 확인하지 않았다.
- 기존 사용자 프로필·권한을 삭제하거나 초기화하지 않았다. 테스트용 sample 설정은 원래 값으로 복원했다. 실제 차량 이벤트, TTS, 알림 허용 시 HUN/카드 노출, 절전/전원 전환, 배포 승인은 미검증이다.

### 기기 환경에서 발견한 제한

최초 기존 AVD 실행에서는 앱 설치 전 `UserDataPreparer`/system provider 초기화 오류가 발생했다. 데이터 삭제 없이 `-read-only -no-snapshot-load -no-snapshot-save`로 재실행해 앱 검증을 진행했다. 이 인스턴스에서도 `com.android.car`의 user 0 credential-encrypted storage 초기화 오류가 관찰되었다. Settings 전환 첫 테스트는 Settings가 닫혀 앱이 다시 전면으로 돌아오면서 기대한 백그라운드 2건을 채우지 못했다(전체 2건/백그라운드 1건). 이 실패를 앱 수신 성공으로 처리하지 않았다.

설정 앱과 분리하여 정상적으로 전면을 유지한 실제 내비 Activity를 지정한 최종 테스트는 통과했다. 이는 해당 조건의 앱 이벤트 수명을 검증한 결과이며 기존 AVD의 시스템 상태를 복구하거나 모든 차량 기능을 검증한 결과가 아니다. 임시 새 AVD·userdata 초기화는 수행하지 않았다. 테스트 APK의 읽기 전용 인스턴스 설치는 종료 후 보존되지 않으므로 Android Studio에서 최종 APK를 다시 Run한다.

기기 테스트 실행 예시(실제 현재 user와 serial을 먼저 확인):

```powershell
adb -s emulator-5554 shell am instrument --user 10 -w -r `
  -e backgroundSmoke true `
  -e backgroundActivity ai.umos.maps.android.navigation.app/.MainActivity `
  -e class com.jochana.driverule.core.platform.background.SampleGuidanceServiceTest `
  com.jochana.driverule.test/androidx.test.runner.AndroidJUnitRunner
```

`backgroundActivity`를 생략하면 Settings를 사용한다. 설정·경로를 변경하지 않고 Activity 전환만 한다. 이 검증 단계에서는 커밋·푸시를 수행하지 않았다.
