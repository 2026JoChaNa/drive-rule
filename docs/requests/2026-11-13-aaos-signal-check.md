# AAOS 차량 신호(기어 · 시동 · 속도) 확인 요청

Nov 13, 2026 · 백엔드 → 앱(프론트)

Pleos Connect 에뮬레이터에서 **기어 · 시동 · 주차 브레이크 · 차속** 값이 있는지, 그 값을 읽는 권한이 일반 앱에 열려 있는지 확인을 부탁드립니다. adb 명령만 쓰고 **앱 코드는 고치지 않습니다.** 10분 정도 걸립니다.

## 왜 필요한가

- 기획 v6 이벤트 ② '주정차 · 고속도로'(시연 카드 A)는 **고속도로에서 기어가 P로 바뀔 때**를 판정합니다
- 기어는 Pleos SDK에 없고 Android Automotive 표준 API(`CarPropertyManager`)로만 읽습니다. Pleos 차가 일반 앱에 이 값을 열어 주는지는 Pleos 문서에 없습니다 ([SDK 상황 신호 정리](../android/references/pleos-sdk-signals-2026-11-11.md) '11/18 기술 검증 항목')
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

2026-11-14 · 앱(프론트) 작성 · 브랜치 `chore/android-aaos-car-probe`

**요약 (에뮬레이터 기준)**

- **우리 앱이 일반 앱 권한으로 기어 · 주차 브레이크 · 차속을 읽었습니다.** 기어 변경 콜백도 등록되고, 등록 직후 현재 값(P)이 왔습니다
- 시동(`IGNITION_STATE`)은 이 에뮬레이터에 **없는 속성**입니다
- `CAR_POWERTRAIN`은 `normal`(설치 때 자동 허용), `CAR_SPEED`는 **`dangerous`** 입니다. 실행 중 요청 창이 **'위치 정보' 권한** 문구로 뜹니다
- 에뮬레이터 이미지가 **user 빌드**라서 adb의 `car_service get-property-value` · `inject-vhal-event`가 막힙니다. 그래서 **adb로 기어 D → P를 넣어 콜백을 확인하는 일은 하지 못했습니다**

### 환경

Pleos Connect Emulator v2.1.0 (`Pleos/crp_avd/crp_avd_x86_64:14/RELEASE.release_connect_v2.1.0/release_connect_v2.1.0:user/test-keys`), API 34, x86_64, RAM 4GB, 2560×1440 · 160dpi, 현재 사용자 10, `emulator-5554`. 확인용 debug APK는 이 브랜치(`4aef389`)에서 빌드했습니다.

### 1~5번 adb 명령 — 출력 원문

<details>
<summary>펼쳐 보기 (adb 실행 경로만 <code>adb</code>로 줄였고 나머지는 그대로)</summary>

```text
# 0) devices
$ adb devices
List of devices attached
emulator-5554	device

[exit 0]

$ adb shell getprop ro.build.fingerprint
Pleos/crp_avd/crp_avd_x86_64:14/RELEASE.release_connect_v2.1.0/release_connect_v2.1.0:user/test-keys
[exit 0]

$ adb shell am get-current-user
10
[exit 0]

# 1)
$ adb shell getprop ro.build.characteristics
nosdcard,automotive
[exit 0]

$ adb shell getprop ro.build.version.sdk
34
[exit 0]

# 2)
$ adb shell cmd car_service get-property-value 0x11400400

Exception occurred while executing 'get-property-value':
java.lang.SecurityException: The command 'get-property-value' requires non-user build
	at com.android.car.CarShellCommand.exec(CarShellCommand.java:1141)
	at com.android.car.CarShellCommand.onCommand(CarShellCommand.java:593)
	at com.android.modules.utils.BasicShellCommandHandler.exec(BasicShellCommandHandler.java:97)
	at com.android.car.ICarImpl.lambda$new$0(ICarImpl.java:196)
	at com.android.car.ICarImpl.$r8$lambda$s2-UoM6N3NTsvCgdggGoRDZI3aQ(Unknown Source:0)
	at com.android.car.ICarImpl$$ExternalSyntheticLambda0.onShellCommand(Unknown Source:2)
	at android.car.builtin.os.BinderHelper.onTransactForCmd(BinderHelper.java:132)
	at com.android.car.ICarImpl.onTransact(ICarImpl.java:944)
	at android.os.Binder.execTransactInternal(Binder.java:1337)
	at android.os.Binder.execTransact(Binder.java:1278)
[exit 255]

$ adb shell cmd car_service get-property-value 0x11400401

Exception occurred while executing 'get-property-value':
java.lang.SecurityException: The command 'get-property-value' requires non-user build
	at com.android.car.CarShellCommand.exec(CarShellCommand.java:1141)
	at com.android.car.CarShellCommand.onCommand(CarShellCommand.java:593)
	at com.android.modules.utils.BasicShellCommandHandler.exec(BasicShellCommandHandler.java:97)
	at com.android.car.ICarImpl.lambda$new$0(ICarImpl.java:196)
	at com.android.car.ICarImpl.$r8$lambda$s2-UoM6N3NTsvCgdggGoRDZI3aQ(Unknown Source:0)
	at com.android.car.ICarImpl$$ExternalSyntheticLambda0.onShellCommand(Unknown Source:2)
	at android.car.builtin.os.BinderHelper.onTransactForCmd(BinderHelper.java:132)
	at com.android.car.ICarImpl.onTransact(ICarImpl.java:944)
	at android.os.Binder.execTransactInternal(Binder.java:1337)
	at android.os.Binder.execTransact(Binder.java:1278)
[exit 255]

$ adb shell cmd car_service get-property-value 0x11400409

Exception occurred while executing 'get-property-value':
java.lang.SecurityException: The command 'get-property-value' requires non-user build
	at com.android.car.CarShellCommand.exec(CarShellCommand.java:1141)
	at com.android.car.CarShellCommand.onCommand(CarShellCommand.java:593)
	at com.android.modules.utils.BasicShellCommandHandler.exec(BasicShellCommandHandler.java:97)
	at com.android.car.ICarImpl.lambda$new$0(ICarImpl.java:196)
	at com.android.car.ICarImpl.$r8$lambda$s2-UoM6N3NTsvCgdggGoRDZI3aQ(Unknown Source:0)
	at com.android.car.ICarImpl$$ExternalSyntheticLambda0.onShellCommand(Unknown Source:2)
	at android.car.builtin.os.BinderHelper.onTransactForCmd(BinderHelper.java:132)
	at com.android.car.ICarImpl.onTransact(ICarImpl.java:944)
	at android.os.Binder.execTransactInternal(Binder.java:1337)
	at android.os.Binder.execTransact(Binder.java:1278)
[exit 255]

$ adb shell cmd car_service get-property-value 0x11200402

Exception occurred while executing 'get-property-value':
java.lang.SecurityException: The command 'get-property-value' requires non-user build
	at com.android.car.CarShellCommand.exec(CarShellCommand.java:1141)
	at com.android.car.CarShellCommand.onCommand(CarShellCommand.java:593)
	at com.android.modules.utils.BasicShellCommandHandler.exec(BasicShellCommandHandler.java:97)
	at com.android.car.ICarImpl.lambda$new$0(ICarImpl.java:196)
	at com.android.car.ICarImpl.$r8$lambda$s2-UoM6N3NTsvCgdggGoRDZI3aQ(Unknown Source:0)
	at com.android.car.ICarImpl$$ExternalSyntheticLambda0.onShellCommand(Unknown Source:2)
	at android.car.builtin.os.BinderHelper.onTransactForCmd(BinderHelper.java:132)
	at com.android.car.ICarImpl.onTransact(ICarImpl.java:944)
	at android.os.Binder.execTransactInternal(Binder.java:1337)
	at android.os.Binder.execTransact(Binder.java:1278)
[exit 255]

$ adb shell cmd car_service get-property-value 0x11600207

Exception occurred while executing 'get-property-value':
java.lang.SecurityException: The command 'get-property-value' requires non-user build
	at com.android.car.CarShellCommand.exec(CarShellCommand.java:1141)
	at com.android.car.CarShellCommand.onCommand(CarShellCommand.java:593)
	at com.android.modules.utils.BasicShellCommandHandler.exec(BasicShellCommandHandler.java:97)
	at com.android.car.ICarImpl.lambda$new$0(ICarImpl.java:196)
	at com.android.car.ICarImpl.$r8$lambda$s2-UoM6N3NTsvCgdggGoRDZI3aQ(Unknown Source:0)
	at com.android.car.ICarImpl$$ExternalSyntheticLambda0.onShellCommand(Unknown Source:2)
	at android.car.builtin.os.BinderHelper.onTransactForCmd(BinderHelper.java:132)
	at com.android.car.ICarImpl.onTransact(ICarImpl.java:944)
	at android.os.Binder.execTransactInternal(Binder.java:1337)
	at android.os.Binder.execTransact(Binder.java:1278)
[exit 255]

# 3)
$ adb shell pm list permissions -f | grep -A5 -E 'permission:android.car.permission.(CAR_POWERTRAIN|CAR_SPEED)$'
+ permission:android.car.permission.CAR_POWERTRAIN
  package:com.android.car.updatable
  label:차량 파워 트레인 정보 읽기
  description:차량 파워 트레인 정보에 액세스합니다.
  protectionLevel:normal
+ permission:ai.pleos.servicemodepro.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
[exit 0]

# 4)
$ adb shell dumpsys car_service --services CarUxRestrictionsManagerService | head -40
** Dumping CarUxRestrictionsManagerService

*CarUxRestrictionsManagerService*
UX Restrictions Clients:
  callbacks: 5
  killed: false
  broadcasts count: -1
Display id: 0 UXR: DO: false UxR: 0 time: 56699947127
UX Restriction configurations:
Physical display port: null
Occupant zone id of the display: -1
Display type: 0
===========================================
baseline mode UXR:
-------------------------------------------
State:unknown num restrictions:1
Requires DO? true
Restrictions: 0x1ff
Speed Range: None
-------------------------------------------
State:parked num restrictions:1
Requires DO? false
Restrictions: 0x0
Speed Range: None
-------------------------------------------
State:idling num restrictions:1
Requires DO? true
Restrictions: 0x50
Speed Range: None
-------------------------------------------
State:moving num restrictions:1
Requires DO? true
Restrictions: 0x50
Speed Range: None
-------------------------------------------
Max String length: 120
Max Cumulative Content Items: 21
Max Content depth: 3
===========================================
UX Restriction change log:
[exit 0]

# 5)
$ adb shell cmd car_service inject-vhal-event 0x11400400 8

Exception occurred while executing 'inject-vhal-event':
java.lang.SecurityException: The command 'inject-vhal-event' requires non-user build
	at com.android.car.CarShellCommand.exec(CarShellCommand.java:1141)
	at com.android.car.CarShellCommand.onCommand(CarShellCommand.java:593)
	at com.android.modules.utils.BasicShellCommandHandler.exec(BasicShellCommandHandler.java:97)
	at com.android.car.ICarImpl.lambda$new$0(ICarImpl.java:196)
	at com.android.car.ICarImpl.$r8$lambda$s2-UoM6N3NTsvCgdggGoRDZI3aQ(Unknown Source:0)
	at com.android.car.ICarImpl$$ExternalSyntheticLambda0.onShellCommand(Unknown Source:2)
	at android.car.builtin.os.BinderHelper.onTransactForCmd(BinderHelper.java:132)
	at com.android.car.ICarImpl.onTransact(ICarImpl.java:944)
	at android.os.Binder.execTransactInternal(Binder.java:1337)
	at android.os.Binder.execTransact(Binder.java:1278)
[exit 255]

$ adb shell cmd car_service get-property-value 0x11400400

Exception occurred while executing 'get-property-value':
java.lang.SecurityException: The command 'get-property-value' requires non-user build
	at com.android.car.CarShellCommand.exec(CarShellCommand.java:1141)
	at com.android.car.CarShellCommand.onCommand(CarShellCommand.java:593)
	at com.android.modules.utils.BasicShellCommandHandler.exec(BasicShellCommandHandler.java:97)
	at com.android.car.ICarImpl.lambda$new$0(ICarImpl.java:196)
	at com.android.car.ICarImpl.$r8$lambda$s2-UoM6N3NTsvCgdggGoRDZI3aQ(Unknown Source:0)
	at com.android.car.ICarImpl$$ExternalSyntheticLambda0.onShellCommand(Unknown Source:2)
	at android.car.builtin.os.BinderHelper.onTransactForCmd(BinderHelper.java:132)
	at com.android.car.ICarImpl.onTransact(ICarImpl.java:944)
	at android.os.Binder.execTransactInternal(Binder.java:1337)
	at android.os.Binder.execTransact(Binder.java:1278)
[exit 255]

$ adb shell cmd car_service inject-vhal-event 0x11400400 4

Exception occurred while executing 'inject-vhal-event':
java.lang.SecurityException: The command 'inject-vhal-event' requires non-user build
	at com.android.car.CarShellCommand.exec(CarShellCommand.java:1141)
	at com.android.car.CarShellCommand.onCommand(CarShellCommand.java:593)
	at com.android.modules.utils.BasicShellCommandHandler.exec(BasicShellCommandHandler.java:97)
	at com.android.car.ICarImpl.lambda$new$0(ICarImpl.java:196)
	at com.android.car.ICarImpl.$r8$lambda$s2-UoM6N3NTsvCgdggGoRDZI3aQ(Unknown Source:0)
	at com.android.car.ICarImpl$$ExternalSyntheticLambda0.onShellCommand(Unknown Source:2)
	at android.car.builtin.os.BinderHelper.onTransactForCmd(BinderHelper.java:132)
	at com.android.car.ICarImpl.onTransact(ICarImpl.java:944)
	at android.os.Binder.execTransactInternal(Binder.java:1337)
	at android.os.Binder.execTransact(Binder.java:1278)
[exit 255]

$ adb shell cmd car_service get-property-value 0x11400400

Exception occurred while executing 'get-property-value':
java.lang.SecurityException: The command 'get-property-value' requires non-user build
	at com.android.car.CarShellCommand.exec(CarShellCommand.java:1141)
	at com.android.car.CarShellCommand.onCommand(CarShellCommand.java:593)
	at com.android.modules.utils.BasicShellCommandHandler.exec(BasicShellCommandHandler.java:97)
	at com.android.car.ICarImpl.lambda$new$0(ICarImpl.java:196)
	at com.android.car.ICarImpl.$r8$lambda$s2-UoM6N3NTsvCgdggGoRDZI3aQ(Unknown Source:0)
	at com.android.car.ICarImpl$$ExternalSyntheticLambda0.onShellCommand(Unknown Source:2)
	at android.car.builtin.os.BinderHelper.onTransactForCmd(BinderHelper.java:132)
	at com.android.car.ICarImpl.onTransact(ICarImpl.java:944)
	at android.os.Binder.execTransactInternal(Binder.java:1337)
	at android.os.Binder.execTransact(Binder.java:1278)
[exit 255]
```

</details>

3번 명령은 `CAR_POWERTRAIN`만 보여 주고 `CAR_SPEED`는 나오지 않았습니다. 권한 그룹으로 묶어 출력하는 `-g`를 붙이면 위치 그룹 아래에 있습니다.

```text
$ adb shell "pm list permissions -f -g | grep -n -B1 -A5 'CAR_SPEED'"
696-  description:이 기기의 위치정보에 액세스
697:  + permission:android.car.permission.CAR_SPEED
698-    package:com.android.car.updatable
699-    label:차량 속도 읽기
700-    description:차량 속도에 액세스
701-    protectionLevel:dangerous

$ adb shell "dumpsys package com.android.car.updatable | grep -i -A3 'CAR_SPEED'"
  Permission [android.car.permission.CAR_SPEED] (ab5eb02):
    sourcePackage=com.android.car.updatable
    uid=10153 gids=[] type=0 prot=dangerous
    perm=PermissionInfo{29bdc13 android.car.permission.CAR_SPEED}
```

4번: 지금 디스플레이 0의 UX 제한은 `DO: false UxR: 0`(제한 없음)입니다. 설정상 `parked`는 제한 없음(`0x0`), `idling` · `moving`은 `0x50`, `unknown`은 `0x1ff`입니다.

### 앱에서 읽기

`CarProbeActivity`를 `adb shell am start --user 10 -n com.jochana.driverule/.core.platform.carprobe.CarProbeActivity`로 두 번 실행했습니다.

- 1회차: `CAR_SPEED` 요청 창에서 '허용'
- 2회차: 권한을 `pm revoke`로 되돌린 뒤 '허용하지 않고 다시 묻지 않음'
- 확인 뒤 '다시 묻지 않음' 표시는 `pm clear-permission-flags`로 지워 두었습니다. 지금 `CAR_SPEED`는 허용 안 된 상태이고, 다음 실행 때 요청 창이 다시 뜹니다

| 속성 | 번호 | 권한 | `CAR_SPEED` 허용 | `CAR_SPEED` 거부 |
|---|---|---|---|---|
| `GEAR_SELECTION` | `0x11400400` | `CAR_POWERTRAIN` | **성공** · `4` (P) | **성공** · `4` (P) |
| `IGNITION_STATE` | `0x11400409` | `CAR_POWERTRAIN` | **미지원** (config 없음) | **미지원** |
| `PARKING_BRAKE_ON` | `0x11200402` | `CAR_POWERTRAIN` | **성공** · `false` | **성공** · `false` |
| `PERF_VEHICLE_SPEED` | `0x11600207` | `CAR_SPEED` | **성공** · `0.0` (m/s) | **권한 거부** (SecurityException) |
| 기어 변경 콜백 등록 | `0x11400400` | `CAR_POWERTRAIN` | **성공** · 등록 직후 현재 값 `4` (P) 수신 | **성공** · 같음 |
| 기어 D → P 주입 후 콜백 | | | **확인 못 함** — 아래 이유 | 같음 |

logcat 원문 (`adb logcat -d -s DriveRuleCarProbe`, 시각 · 프로세스 번호 열은 뺐습니다):

```text
# 1회차 — CAR_SPEED 허용
I DriveRuleCarProbe: probe start: sdk=34 automotive=true
I DriveRuleCarProbe: permission: android.car.permission.CAR_POWERTRAIN level=normal granted=true
I DriveRuleCarProbe: permission: android.car.permission.CAR_SPEED level=dangerous granted=false
I DriveRuleCarProbe: runtime request: [android.car.permission.CAR_SPEED]
I DriveRuleCarProbe: runtime result: android.car.permission.CAR_SPEED granted=true
I DriveRuleCarProbe: GEAR_SELECTION(0x11400400) config=present access=1 changeMode=1
I DriveRuleCarProbe: GEAR_SELECTION(0x11400400) read: SUCCESS value=4 (P) status=0
I DriveRuleCarProbe: IGNITION_STATE(0x11400409) config=null
I DriveRuleCarProbe: IGNITION_STATE(0x11400409) read: UNSUPPORTED property ID is not in carPropertyConfig list, and so it is not supported: IGNITION_STATE
I DriveRuleCarProbe: PARKING_BRAKE_ON(0x11200402) config=present access=1 changeMode=1
I DriveRuleCarProbe: PARKING_BRAKE_ON(0x11200402) read: SUCCESS value=false status=0
I DriveRuleCarProbe: PERF_VEHICLE_SPEED(0x11600207) config=present access=1 changeMode=2
I DriveRuleCarProbe: PERF_VEHICLE_SPEED(0x11600207) read: SUCCESS value=0.0 status=0
I DriveRuleCarProbe: GEAR callback register: registered=true
I DriveRuleCarProbe: GEAR callback: value=4 (P) status=0 timestampNs=9638745705

# 2회차 — CAR_SPEED 거부
I DriveRuleCarProbe: probe start: sdk=34 automotive=true
I DriveRuleCarProbe: permission: android.car.permission.CAR_POWERTRAIN level=normal granted=true
I DriveRuleCarProbe: permission: android.car.permission.CAR_SPEED level=dangerous granted=false
I DriveRuleCarProbe: runtime request: [android.car.permission.CAR_SPEED]
I DriveRuleCarProbe: runtime result: android.car.permission.CAR_SPEED granted=false
I DriveRuleCarProbe: GEAR_SELECTION(0x11400400) config=present access=1 changeMode=1
I DriveRuleCarProbe: GEAR_SELECTION(0x11400400) read: SUCCESS value=4 (P) status=0
I DriveRuleCarProbe: IGNITION_STATE(0x11400409) config=null
I DriveRuleCarProbe: IGNITION_STATE(0x11400409) read: UNSUPPORTED property ID is not in carPropertyConfig list, and so it is not supported: IGNITION_STATE
I DriveRuleCarProbe: PARKING_BRAKE_ON(0x11200402) config=present access=1 changeMode=1
I DriveRuleCarProbe: PARKING_BRAKE_ON(0x11200402) read: SUCCESS value=false status=0
I DriveRuleCarProbe: PERF_VEHICLE_SPEED(0x11600207) config=null
I DriveRuleCarProbe: PERF_VEHICLE_SPEED(0x11600207) read: PERMISSION_DENIED requires android.car.permission.CAR_SPEED
I DriveRuleCarProbe: GEAR callback register: registered=true
I DriveRuleCarProbe: GEAR callback: value=4 (P) status=0 timestampNs=9638745705
```

**확인 못 함 — 기어 D → P 변경 콜백.** adb 주입(`inject-vhal-event`)이 "requires non-user build"로 막혔습니다. 에뮬레이터 Extended Controls의 차량 데이터 화면에서 기어를 바꾸는 방법이 남아 있지만, 사람이 직접 눌러야 해서 이번에는 하지 못했습니다. '등록 직후 현재 값 수신'은 확인했고, '값이 바뀔 때 콜백이 오는지'는 확인하지 않았습니다.

### 판단표에 대 보면

| 판단표의 줄 | 이번 결과 | 해당 |
|---|---|---|
| 2번에 값이 나오고, 3번이 `normal` | 2번(adb)은 user 빌드라 값이 안 나옴. 대신 앱이 직접 기어 · 주차 브레이크를 읽음. `CAR_POWERTRAIN`은 `normal` | **해당** — 기어 · 주차 브레이크는 매니페스트 권한만으로 읽힘 |
| 3번이 `dangerous` | `CAR_SPEED` | **해당** — 차속은 실행 중 권한 요청이 필요. 요청 창이 '위치 정보' 문구로 뜸 → 최초 권한 안내 화면에 항목 추가 검토 |
| 3번이 `signature` · `privileged` | 두 권한 모두 아님 | 해당 없음 |
| 2번이 '지원하지 않는 속성' | `IGNITION_STATE`만 미지원 | **시동만 해당** → 11/18 실차 확인으로 넘김 |
| 5번이 된다 | adb 주입 불가 (user 빌드) | **해당 안 됨** — 카드 A 시연에 기어 주입을 쓰려면 Extended Controls 수동 조작을 따로 확인해야 함 |

이벤트 ② '고속도로에서 기어 P' 판정에 필요한 **기어 값은 일반 앱 권한으로 읽혔습니다**(에뮬레이터 기준). 이번 결과만으로는 판정 방식을 바꿀 이유가 보이지 않습니다. 서버의 고속도로 좌표 준비 여부는 실차 결과와 함께 정해 주세요.

### 확인용 코드

제품 기능이 아니며 debug 빌드에만 들어갑니다.

- `android/app/src/debug/java/com/jochana/driverule/core/platform/carprobe/CarProbeActivity.kt`
  - 화면 내용이 없는 Activity입니다. adb `am start`로만 실행하고 런처에는 보이지 않습니다. 3분 뒤 스스로 닫힙니다
  - 두 권한의 등급과 허용 여부를 남기고, 허용되지 않은 권한은 실행 중에 요청합니다
  - 네 속성을 `CarPropertyManager`로 한 번씩 읽어 `SUCCESS 값` / `PERMISSION_DENIED` / `UNSUPPORTED` / `ERROR 예외 이름`으로 남깁니다
  - 기어 변경 콜백을 등록하고, 값이 올 때마다 남깁니다. 출력은 logcat 태그 `DriveRuleCarProbe`로만 나갑니다
- `android/app/src/debug/AndroidManifest.xml` — `CAR_POWERTRAIN` · `CAR_SPEED` 권한, `android.car` 라이브러리(`required="false"`), 확인용 Activity
- `android/app/build.gradle.kts` — `debugCompileOnly`로 SDK의 `platforms/android-34/optional/android.car.jar`만 참조
- 기존 화면 · ViewModel · 안내 구조(Android 결정 A01)와 release 소스는 고치지 않았습니다

빌드 확인 (2026-11-13, JDK 17): `.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:lintDebug :app:testDebugUnitTest` 성공. lint 오류 0건, 경고 46건 중 확인용 코드에서 나온 것은 없습니다. 컴파일 경고 2건은 `CarPropertyValue.status` deprecated입니다. release APK의 dex와 병합 매니페스트에는 `CarProbeActivity` · `CAR_POWERTRAIN`이 없습니다. 2026-11-14에 같은 브랜치로 debug APK를 다시 빌드해 에뮬레이터에 설치했습니다.

### 실차(11/18)에서 다시 할 때

```powershell
adb devices
adb shell am get-current-user          # 사용자 번호 확인 (에뮬레이터는 10)
cd android
.\gradlew.bat :app:installDebug
adb logcat -c
adb shell am start --user <사용자> -n com.jochana.driverule/.core.platform.carprobe.CarProbeActivity
#   CAR_SPEED 요청 창에서 허용/거부 중 무엇을 골랐는지 함께 적음
adb logcat -d -s DriveRuleCarProbe
#   실행 후 3분 안에 기어를 D → P로 바꾸고 "GEAR callback" 줄이 새로 찍히는지 확인
```

### 주의

- **에뮬레이터 결과입니다.** 실제 Pleos 차는 차종 · 소프트웨어에 따라 권한 정책과 지원 속성이 다를 수 있습니다. 특히 시동 속성과 값이 바뀔 때의 콜백은 실차에서 확인해야 합니다
- 에뮬레이터 값은 정차 기본값(P, 0 m/s, 주차 브레이크 해제)입니다. 값이 바뀌는 상황은 보지 못했습니다
- 확인용 코드는 실제 차량 신호 adapter(`core/platform`)를 만들 때 대체하거나 지웁니다
