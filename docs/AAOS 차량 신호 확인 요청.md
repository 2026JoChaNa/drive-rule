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
