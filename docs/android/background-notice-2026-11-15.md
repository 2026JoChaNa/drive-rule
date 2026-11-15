# 내비게이션 전면 안내와 시연 재검증

2026-11-15. 첫 시연의 음성과 내비게이션 위 안내가 보이지 않는다는 사용자 보고를 받고 Pleos Connect 2.1.0 / API 34 에뮬레이터에서 재확인했다. 공개 시설 좌표와 명시된 시연 법령 사본, 모의 주행을 사용했으며 운영 법령 게시나 실차 주행 검증은 아니다.

## 짧은 팝업 시도와 결과

앱 전면의 기존 Compose 안내 배너와 백그라운드 `CATEGORY_EVENT` 알림은 서로 다른 출력이다. EVENT 알림 게시 수락만으로 내비게이션 위 HUN 표시를 보장하지 않는다. 앱 내부 Snackbar도 다른 앱의 화면 위에 표시되는 표면이 아니다.

짧은 상태 문구를 기본 `Toast.makeText`로 보내는 실험을 추가했으나, 이 AVD의 실제 녹화 프레임에 Toast는 나타나지 않았고 표시 콜백도 없었다. SystemUI의 `Attempt to hide non-current toast` 기록도 관측했다. 요청 코드와 JVM 테스트 통과를 표시 성공으로 해석할 수 없다. 해결되지 않은 표시 경로를 제품 기능으로 남기지 않도록 실험용 Toast adapter, router 연결, 문자열과 추가 테스트를 모두 제거했다. 기존 배너·EVENT 알림·음성 경로를 유지한다.

[AOSP Android 14 CarToastUI](https://android.googlesource.com/platform/packages/apps/Car/SystemUI/+/refs/heads/android14-release/src/com/android/systemui/car/toast/CarToastUI.java)는 일반 Android Toast 처리에 차량용 허용 목록·시스템 권한·플랫폼 서명 조건을 추가한다. 관련 설정은 [Car SystemUI config.xml](https://android.googlesource.com/platform/packages/apps/Car/SystemUI/+/refs/heads/android14-release/res/values/config.xml)에 있다. 관측한 미노출은 이 제한과 정합하지만 실제 Pleos OEM 바이너리·설정을 분석한 결과가 아니므로 특정 조건을 원인으로 확정하지 않는다.

[Android Toast API](https://developer.android.com/reference/android/widget/Toast)의 기본 텍스트 지원이나 [Pleos Toast 디자인](https://document.pleos.ai/en/docs/connect/guide/docs-design/components/toast)의 배치 명세는 일반 서드파티 앱의 차량 시스템 위 표시를 보장하지 않는다. 현재 확인된 앱 단독 대체 경로는 없다. 제조사의 공식 표시 API, 허용 대상과 승인·차량 설정 경로를 확인해야 한다. 임의 overlay, custom toast view, 백그라운드 Activity, full-screen intent 또는 알림 카테고리 위장은 추가하지 않았다.

## 이벤트 및 음성 결과의 구분

- 첫 재녹화에서는 위치 공급자 콜백 사이에 5.409초·8.006초 공백이 관측됐다. 앱은 위치 연속성이 5초 이상 끊기면 접근 감지를 다시 준비하도록 설계되어 있어, 470m 밖 관측과 370m 이내 접근 사이의 지연으로 이벤트가 제외됐을 가능성이 있다. 공급자 로그만으로 앱 내부 상태 전이를 확정하지 않는다.
- 후속 실행에서는 첫 TTS 준비에 약 29.6초가 소요됐고 이벤트 deadline에 도달해 종료됐다. 해당 시도의 오디오 출력 `TotalWrittenBytes=0`을 확인했다. SDK 호출이나 초기화 완료를 실제 가청 음성 성공으로 보고하지 않는다.
- 마지막 재시험은 14:49:46.016 발화 요청과 14:49:46.233 `onStart` 뒤, 14:49:52.501 앱 PID20017/main이 `stop()`을 호출했다. `TotalWrittenBytes=0`이며 모델 로드는 14:50:09.094에 끝났다(22.289초). `onStart`를 실제 PCM 출력 시작으로 취급하면 안 된다.
- 마지막 시험의 위치 공급자 콜백은 14:49:46.823→14:49:52.158로 5.335초 간격이었다. 5초 초과 공백에 따른 위치 연속성 해제와 안내 취소가 유력한 경로지만, 앱 취소 사유 로그가 없어 확정하지 않는다. 오디오 포커스는 승인됐으며 stop 전 포커스 상실 증거는 없었다.
- 기존 시연 영상에는 86.5–89.5초 구간의 실제 한국어 발화가 있었다. 원본의 해당 부분을 짧게 추출하고 로컬 음성 인식으로 검증했다. 이 이전 발화는 재시험의 안정적인 TTS 성공을 뜻하지 않는다.
- 이 문서는 백그라운드 팝업 또는 TTS 문제가 해결됐다는 완료 보고가 아니다.

SDK 3.3.1 공개 클래스와 [공식 TTS API 목록](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/entry)에서 무음 prepare/preload 또는 모델 준비 상태 조회 API는 확인되지 않았다. [initialize](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/initialization-and-release) 성공은 서비스/API 사용 가능을 뜻하며 모델 적재 완료와 구분한다. [onReady](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/event-listeners)도 요청 후 콜백이다. 현재 adapter의 요청별 인스턴스를 재사용한다고 모델 적재 지연이 해소된다는 증거는 없다. 제조사 준비 계약과 실제 기기 지연을 확인해야 한다. 이를 숨기기 위한 더미 발화나 이벤트 유효 시간 확대는 하지 않았다.

## 시작 크래시 수정

재빌드한 APK 실행 중 `BackgroundGuidanceHost.Banner`에서 `AbstractMethodError`를 확인했다. `javap` 결과에서 interface는 `Banner(Modifier, Composer, int)`와 기본 인자용 4인자 메서드를 모두 선언했지만 `VehicleBackgroundGuidanceHost`에는 4인자 구현만 있었다. `ComposeDefaultImpls`의 3인자 dispatch와 실제 구현 사이 ABI가 달랐다. 증분 산출물과 컴파일러의 기본 interface 처리 중 어느 쪽이 근본 원인인지는 분리하지 않았다.

`Banner`의 interface 기본 인자와 기본 본문을 제거해 `@Composable fun Banner(modifier: Modifier)`를 명시적 추상 계약으로 바꿨다. 실제·샘플 host의 기존 override와 MainActivity의 명시적 Modifier 전달은 유지한다. 전체 Kotlin 재컴파일 후 앱 기동을 다시 확인했으며 이 수정은 유지한다.

실험용 Toast 변경을 제거한 최종 소스에서 `:app:lintDebug :app:testDebugUnitTest :app:assembleDebug`를 실행해 BUILD SUCCESSFUL을 확인했다. JVM 테스트는 34개 suite, 총 366개이며 실패·오류·건너뛰기는 모두 0개다. JDK 17, Kotlin 전체 재컴파일, 샘플 모드 false로 검증했고 최종 BuildConfig의 API 주소는 기본 운영 주소다. 앞선 실험의 370개에는 제거한 Toast 테스트 4개가 포함되어 있었다. 정적·빌드 검증은 내비게이션 위 표시나 실제 스피커 출력 검증과 구분한다. Banner 기기 기동 검증은 해당 수정과 실험용 Toast가 함께 있던 빌드에서 통과했으며, Toast 제거 후 APK는 재설치하지 않았다.
