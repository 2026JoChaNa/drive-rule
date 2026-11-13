# AAOS 차량 신호(기어 · 시동 · 속도) 확인 요청

Nov 13, 2026 · 백엔드 → 앱(프론트)

Pleos Connect 에뮬레이터에서 **기어 · 시동 · 주차 브레이크 · 차속** 값이 있는지, 그 값을 읽는 권한이 일반 앱에 열려 있는지 확인을 부탁드립니다. adb 명령만 쓰고 **앱 코드는 고치지 않습니다.** 10분 정도 걸립니다.

## 왜 필요한가

- 기획 v6 이벤트 ② '주정차 · 고속도로'(시연 카드 A)는 **고속도로에서 기어가 P로 바뀔 때**를 판정합니다
- 기어는 Pleos SDK에 없고 Android Automotive 표준 API(`CarPropertyManager`)로만 읽습니다. Pleos 차가 일반 앱에 이 값을 열어 주는지는 Pleos 문서에 없습니다 ([SDK 상황 신호 정리](android/references/pleos-sdk-signals-2026-11-11.md) '11/18 기술 검증 항목')
- 결과에 따라 서버가 고속도로 노선 좌표를 준비할지, 이벤트 판정 방식을 바꿀지(기획 v6 6장 '기획에서 정할 것' 2 · 3번)가 갈립니다
- 시동 · 주차 브레이크는 '주행 1회'의 경계와 '주차 후' 화면 진입에도 쓰입니다
- 백엔드 PC는 디스크 공간이 부족해 에뮬레이터를 설치하지 못했습니다

## 준비

- Pleos Connect Emulator **v2.1.0** (API 34)을 켜고 홈 화면까지 부팅된 상태
- PC에서 `adb devices` 를 실행했을 때 `emulator-5554 device` 가 보이는 상태

## 실행할 명령

PowerShell이나 터미널에 그대로 붙여 넣으면 됩니다. 따옴표 안의 `grep` 은 에뮬레이터 안에서 실행되므로 Windows에서도 동작합니다. **에러가 나도 그대로 붙여 주세요** — 에러도 답입니다.

```sh
# 1) 기기 정보 — 자동차용 빌드인지, Android 버전
adb shell getprop ro.build.characteristics
adb shell getprop ro.build.version.sdk

# 2) 차량 속성 값이 있는지 (기어 · 현재 기어 · 시동 · 주차 브레이크 · 차속)
adb shell cmd car_service get-property-value 0x11400400
adb shell cmd car_service get-property-value 0x11400401
adb shell cmd car_service get-property-value 0x11400409
adb shell cmd car_service get-property-value 0x11200402
adb shell cmd car_service get-property-value 0x11600207

# 3) 그 값을 읽는 권한이 어떤 등급인지 (normal / dangerous / signature · privileged)
adb shell "pm list permissions -f | grep -A5 -E 'permission:android.car.permission.(CAR_POWERTRAIN|CAR_SPEED)$'"

# 4) 지금 화면 제한(운전 중 UX 제한) 상태
adb shell "dumpsys car_service --services CarUxRestrictionsManagerService | head -40"
```

2번에서 `Unknown command` 같은 말이 나오면 아래를 대신 실행해 주세요.

```sh
adb shell "cmd car_service help | grep -i -E 'prop|vhal'"
```

### 5) (선택) 기어를 P로 바꿔 보기 — 시연 준비용

에뮬레이터는 차량 값을 직접 넣을 수 있습니다. 되면 PT 1단계(카드 A, 고속도로 정차)를 에뮬레이터에서 재현할 수 있습니다.

```sh
# 기어를 D(8)로 → 확인 → P(4)로 → 확인
adb shell cmd car_service inject-vhal-event 0x11400400 8
adb shell cmd car_service get-property-value 0x11400400
adb shell cmd car_service inject-vhal-event 0x11400400 4
adb shell cmd car_service get-property-value 0x11400400
```

## 명령에 쓴 번호

| 번호 | 이름 | 뜻 | 읽기 권한 (AOSP 기준) |
|---|---|---|---|
| `0x11400400` | `GEAR_SELECTION` | 운전자가 고른 기어 (P=4 · R=2 · N=1 · D=8) | `CAR_POWERTRAIN` |
| `0x11400401` | `CURRENT_GEAR` | 실제 기어 | `CAR_POWERTRAIN` |
| `0x11400409` | `IGNITION_STATE` | 시동 상태 | `CAR_POWERTRAIN` |
| `0x11200402` | `PARKING_BRAKE_ON` | 주차 브레이크 | `CAR_POWERTRAIN` |
| `0x11600207` | `PERF_VEHICLE_SPEED` | 차속 (m/s) | `CAR_SPEED` |

## 결과를 보고 정하는 것

| 결과 | 뜻 | 다음 |
|---|---|---|
| 2번에 값이 나오고, 3번이 `normal` | 앱 매니페스트에 권한만 넣으면 읽을 가능성이 높다 | 프론트가 권한을 넣고 한 번 읽어 보면 확정 (아래 '이걸로 모르는 것') |
| 3번이 `dangerous` | 실행 중 권한 요청 화면이 필요하다 | 최초 권한 안내 화면에 항목 추가 검토 |
| 3번이 `signature` · `privileged` | 일반 앱은 못 읽는다 | 이벤트 ② 고속도로 판정을 바꿔야 함 → 기획에 전달. 서버는 고속도로 좌표를 준비하지 않음 |
| 2번이 '지원하지 않는 속성' | 에뮬레이터에 이 값이 없다 | 실제 차는 다를 수 있음 → 11/18 실차 확인으로 넘김 |
| 5번이 된다 | 에뮬레이터에서 기어 P 를 만들 수 있다 | 카드 A 시연을 위치 주입 + 기어 주입으로 준비 |

## 이걸로 알 수 없는 것

- **우리 앱이 실제로 읽을 수 있는지.** 위 명령은 시스템 권한으로 실행됩니다. '값이 있다', '권한 등급이 이렇다'까지만 알 수 있습니다. 확정하려면 앱에 권한을 넣고 `CarPropertyManager` 로 한 번 읽어 봐야 합니다. 이건 앱 코드라 백엔드가 하지 않습니다
- **실제 Pleos 차와 같은지.** 에뮬레이터 결과입니다. 차종 · 소프트웨어에 따라 다를 수 있습니다 ([Pleos 기능 가용성](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability))

## 결과 붙일 곳

이 문서 아래에 명령별 출력을 그대로 붙여 주시거나, 메신저로 보내 주세요.

## 결과

2026-11-13 · 앱(프론트) 작성 · 브랜치 `chore/android-aaos-car-probe`

**요약: 에뮬레이터 확인은 아직 하지 못했습니다.** 작업한 PC에 Pleos Connect Emulator v2.1.0이 설치되어 있지 않았습니다. 앱이 직접 읽어 보는 확인용 코드(debug 전용)는 만들어 두었습니다. 에뮬레이터가 있는 PC에서 아래 '다시 할 때' 순서로 돌리면 이 절을 채울 수 있습니다.

### 1~5번 adb 명령 — 확인 못 함

이유: 이 PC에서 확인한 환경은 다음과 같습니다. 그래서 1~5번 명령을 하나도 실행하지 못했고, 출력 원문도 없습니다.

```text
$ adb devices
List of devices attached
                                  ← 연결된 기기 없음

$ emulator -list-avds
ilgayo_test                       ← Android 35 google_apis 이미지. 자동차용 빌드가 아니고 Pleos 이미지도 아님

C 드라이브 남은 공간 약 1.8GB     ← Pleos 에뮬레이터 이미지를 새로 설치하기 어려움
```

`ilgayo_test`는 자동차용 빌드가 아니어서 `car_service`가 없습니다. 이 AVD로 돌린 결과를 Pleos 결과로 쓸 수 없어서 대신 실행하지도 않았습니다.

### 앱에서 읽기 — 코드만 준비, 실행 확인 못 함

| 속성 | 번호 | 권한 | 결과 |
|---|---|---|---|
| `GEAR_SELECTION` | `0x11400400` | `CAR_POWERTRAIN` | 확인 못 함 (에뮬레이터 없음) |
| `IGNITION_STATE` | `0x11400409` | `CAR_POWERTRAIN` | 확인 못 함 (에뮬레이터 없음) |
| `PARKING_BRAKE_ON` | `0x11200402` | `CAR_POWERTRAIN` | 확인 못 함 (에뮬레이터 없음) |
| `PERF_VEHICLE_SPEED` | `0x11600207` | `CAR_SPEED` | 확인 못 함 (에뮬레이터 없음) |
| 기어 변경 콜백 (D → P 주입) | `0x11400400` | `CAR_POWERTRAIN` | 확인 못 함 (에뮬레이터 없음) |

준비한 확인용 코드 — 제품 기능이 아니며 debug 빌드에만 들어갑니다.

- `android/app/src/debug/java/com/jochana/driverule/core/platform/carprobe/CarProbeActivity.kt`
  - 화면 내용이 없는 Activity입니다. adb `am start`로만 실행하고 런처에는 보이지 않습니다. 3분 뒤 스스로 닫힙니다
  - 두 권한의 등급(normal / dangerous / signature)과 허용 여부를 남깁니다. 허용되지 않은 권한은 런타임으로 요청합니다
  - 네 속성을 `CarPropertyManager`로 한 번씩 읽습니다. 결과는 `SUCCESS 값` / `PERMISSION_DENIED`(SecurityException) / `UNSUPPORTED`(IllegalArgumentException) / `ERROR 예외 이름` 중 하나로 남깁니다
  - 기어 변경 콜백을 등록하고, 값이 바뀔 때마다 남깁니다
  - 출력은 logcat 태그 `DriveRuleCarProbe`로만 나갑니다
- `android/app/src/debug/AndroidManifest.xml` — `CAR_POWERTRAIN` · `CAR_SPEED` 권한, `android.car` 라이브러리(`required="false"`), 확인용 Activity
- `android/app/build.gradle.kts` — `debugCompileOnly`로 SDK의 `platforms/android-34/optional/android.car.jar`를 참조합니다. release 컴파일 경로와 APK에는 들어가지 않습니다
- 기존 화면 · ViewModel · 안내 구조(결정 008)와 release 소스는 고치지 않았습니다

빌드 확인 (2026-11-13, JDK 17, 이 PC): `.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:lintDebug :app:testDebugUnitTest` 성공. lint 오류 0건이고, 경고 46건 중 확인용 코드에서 나온 것은 없습니다. 컴파일 경고 2건은 `CarPropertyValue.status`가 deprecated라는 내용입니다. 확인용 클래스는 debug APK에만 있고, release APK의 dex와 병합 매니페스트에는 `CarProbeActivity` · `CAR_POWERTRAIN`이 없습니다. **에뮬레이터 설치 · 실행은 하지 않았습니다.**

### 판단표 — 아직 정할 수 없음

판단표 다섯 줄 중 어느 줄에 해당하는지 정하지 못했습니다. 판단에 필요한 2 · 3 · 5번 출력과 앱 읽기 결과가 없기 때문입니다. 이벤트 ② 판정 방식과 서버의 고속도로 좌표 준비 여부도 이 결과가 나올 때까지 보류합니다.

### 다시 할 때 (에뮬레이터가 있는 PC)

```powershell
# 0) 연결 확인과 현재 사용자 (지금까지 Pleos 에뮬레이터는 사용자 10)
adb devices
adb shell am get-current-user

# 1~5) 위 '실행할 명령'을 그대로 실행하고 출력을 이 절에 붙임

# 6) 확인용 debug APK 설치 → 실행 → 로그 보기
cd android
.\gradlew.bat :app:installDebug
adb logcat -c
adb shell am start --user 10 -n com.jochana.driverule/.core.platform.carprobe.CarProbeActivity
#    권한 요청 창이 뜨면 허용 · 거부를 고르고, 무엇을 골랐는지 함께 적음
adb logcat -d -s DriveRuleCarProbe

# 7) 3분 안에 기어 D → P 주입 후 콜백 확인
adb shell cmd car_service inject-vhal-event 0x11400400 8
adb shell cmd car_service inject-vhal-event 0x11400400 4
adb logcat -d -s DriveRuleCarProbe | Select-String "GEAR callback"
```

### 주의

- 결과가 나와도 **에뮬레이터 결과**입니다. 실제 Pleos 차는 차종 · 소프트웨어에 따라 권한 정책과 지원 속성이 다를 수 있습니다. 확정은 11/18 실차 확인에서 합니다
- 확인용 코드는 결과를 기록한 뒤 지우거나, 실제 차량 신호 adapter(`core/platform`)를 만들 때 대체합니다
