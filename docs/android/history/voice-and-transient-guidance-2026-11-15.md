# 음성 안내와 자동 종료 배너

2026-11-15. 사용자의 2번 작업 요청에 따라 TTS 출력과 짧은 시각 안내를 함께 구현한다. 기존 카드 동기화와 운전자 저장을 유지한다. 실제 차량 이벤트 수신·운전 상황별 카드 선택과 제품용 실행 host는 다음 단계이며, 오늘은 기기 테스트를 실행하지 않는다.

## 사용자에게 보이는 동작

- 앱을 보고 있을 때는 앱 영역 하단에 읽기 전용 안내 배너를 보여 준다. 기존 테마의 색상·글꼴을 사용하며 라이트/다크를 지원한다. 긴 근거·조건 조작을 배너에 넣지 않는다.
- 다른 앱이 전면이면 기존 시스템 `CATEGORY_EVENT` 알림으로 전달한다. 차량이 팝업을 허용하는지는 시스템 정책·채널 설정에 달려 있다. 알림 게시 수락과 실제 화면 노출을 구분한다.
- 시각 안내는 이벤트 수락 시점부터 최대 8초이며 이벤트가 먼저 만료되거나 세션이 끝나면 즉시 닫는다. 앱 전환·재구성·TTS 완료가 이 시간을 다시 시작하지 않는다. 8초는 기존 debug 시연 정책이며 차량 검증을 마친 제품 고정값이 아니다.
- 음성은 알림 허용과 독립적으로 시도한다. 통신·알림 실패로 음성을 무한 재시도하지 않는다. 이름 등록·추가 등록·최초 권한 안내가 진행 중이면 음성을 억제하고, 진행 중 발화도 중단한다.
- 새 이벤트는 기존 발화를 취소한다. 이벤트 유효시간·세션 종료·사용자 중지·오디오 포커스 상실도 발화를 끝낸다. 포커스가 돌아왔다고 지난 안내를 자동 재생하지 않는다.
- SDK 완료 콜백은 발화 완료 관측일 뿐 실제 사용자 청취·확인 이력이 아니다. 배너 자동 종료도 확인 이력으로 저장하지 않는다.

새 배너는 이번 요청에 따른 추가 UI다. 기존 Figma의 주차 상세 화면이나 주행 중 시연 말풍선을 그대로 구현한 화면이 아니다. 글자 물리 크기, 1/2 창·큰 글자, 주야간 대비의 실제 화면 검수는 테스트 환경에서 진행한다. 다른 앱 위에 임의 overlay·Activity·full-screen intent를 띄우거나 알림 카테고리를 위장하지 않는다.

## 음성 출력 경계

`GuidanceCoordinator → GuidanceSpeechOutput → PleosGuidanceSpeechOutput`으로 연결한다. 카드 출력과 음성 결과는 별도 상태다. UI Flow 수집은 음성 호출을 시작하지 않는다. coordinator가 이벤트 세대와 유효시간, 발화 Job을 소유하고 늦게 도착한 이전 결과를 제외한다.

- 공식 `ai.pleos.playground:TextToSpeech:3.3.1`과 `pleos.car.permission.TTS_SERVICE`를 추가했다.
- `Mode.ON_DEVICE`와 `Locale.KOREA`를 명시한다. 기본 HYBRID 모드·서버 등록·다른 TTS 엔진으로 자동 전환하지 않는다.
- `CONTENT_TYPE_SPEECH`와 `USAGE_NOTIFICATION`을 SDK와 `AudioFocusRequest`에 동일하게 설정한다. 법령 정보 알림을 내비·Gleo·안전·긴급 오디오로 분류하지 않는다. `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`을 요청하고 지연 획득은 허용하지 않는다. 포커스 상실·일시 상실·duck 요청 시 이번 발화를 종료한다.
- 공개 SDK 콜백에는 발화 ID가 없다. 로컬 세대 검사와 발화별 연결로 완료 대상을 구분하며, 원격 stop이 앱 package 단위라는 점도 고려한다.
- SDK의 `stop()`은 비동기 IPC다. 중지 확인 전에 연결을 해제하거나 다음 발화를 시작하지 않도록 프로세스 공통 종료 확인 경계를 둔다. `stop()`을 이미 요청했으면 늦은 자연 종료 `onDone`만으로 대기 중인 stop IPC 실행을 증명할 수 없으므로 `onStop`을 기다린다. 2초 안에 확인되지 않거나 정리가 실패하면 리소스를 정리하고 현재 프로세스의 새 합성을 차단해 `Unavailable`로 반환한다. 이 상태는 서비스만 재시작해 우회하지 않으며 앱 프로세스 재시작과 기기 원인 확인이 필요하다. 초기화 취소·포커스 거절처럼 `speak()` 전인 경로에는 `stop()`을 보내지 않는다. 정상 `onDone`/`onStop` 이후에는 다시 중지를 요청하지 않는다.
- SDK 오류·권한 부재·지원 불가·포커스 거부를 결과로 돌려주며 원문·이름·좌표·SDK 예외 본문을 로그로 남기지 않는다. 종료 때 SDK listener·연결·오디오 포커스를 해제한다.
- 이름 입력 화면은 새 안내를 먼저 억제하고, `GuidanceAwareNameSpeechEngine`이 공통 TTS 종료 확인을 기다린 뒤 STT SDK를 생성한다. 중지 요청만 보내고 즉시 마이크를 열지 않는다. 종료 확인 실패 시 음성 입력은 `Unavailable`로 처리하고 수동 입력을 유지하며, 기다리는 동안 화면을 벗어나면 대기를 취소한다.

Pleos Speech Service 내부의 오디오 포커스 처리와 실제 통화·내비·Gleo 공존은 클라이언트 JAR만으로 확인할 수 없다. 공식 선행 묵음 가이드의 150–200ms는 오디오 데이터 패딩 권고다. SDK 공개 출력에는 PCM/묵음 패딩 API를 확인하지 못했으므로 단순 `delay`를 넣고 권고를 충족했다고 하지 않는다. 실제 첫 음절 잘림과 플랫폼 적용 여부를 기기에서 확인한다.

## 실행 범위

현재 실행은 기존 debug `SampleGuidanceService`의 최대 10분 세션과 타이머 이벤트를 사용한다. 음성 문구는 테스트임을 밝히며 실제 법령을 생성하거나 운영 데이터처럼 읽지 않는다. 등록한 프로필·테마·서버 캐시를 초기화하지 않는다.

release에는 adapter와 재사용 가능한 coordinator가 포함되지만 샘플 발생기·샘플 서비스·자동 실행은 포함되지 않는다. 실제 차량 신호, 카드 선정/회수 취소 정책, 제품용 백그라운드 host를 연결하기 전까지 실차 안내 서비스 완성을 의미하지 않는다. 서버 게시 카드가 없는 상태에서 샘플 음성을 검수 법령 음성으로 보고하지 않는다.

## 검증

JDK 17에서 다음 작업을 실행해 `BUILD SUCCESSFUL`을 확인했다.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --console=plain
```

- JVM 테스트 **181개 통과**, 실패·오류·skip 0개. 이벤트 교체·세션 종료·알림 거부와 음성의 독립성·늦은 콜백·SDK 정리 대기열·이름 인식 대기 취소·앱 전환 시 배너 만료 시각 보존을 포함한다.
- lint 오류 0개. 경고 49개는 `AndroidGradlePluginVersion` 3개, `GradleDependency` 45개, `OldTargetApi` 1개다. 버전 변경은 Pleos 호환성 확인 범위이므로 이 작업에서 일괄 업그레이드하지 않았다.
- debug/release APK 및 계측 APK 빌드 성공. 주야간 배너·400dp/1.3배 글꼴·상태 제거·이름 STT 진입 순서·홈 composition 수명·저장소 오류의 UI 회귀 테스트는 **컴파일만 확인했으며 실행하지 않았다**.
- release 병합 manifest에서 TTS 권한을 확인했다. 샘플 서비스·foreground service 권한·overlay·full-screen intent·직접 마이크 권한은 포함되지 않는다.
- `git diff --check` 통과. 이번 변경은 `android/`와 `docs/android/`에 한정했다. 이전 법령 캐시·운전자 저장 등 이번 변경 밖의 staging 기준 274개 파일은 해시가 동일하다.

배너의 불투명 테마 토큰 대비 계산은 다크 제목 12.80:1·본문 7.73:1, 라이트 제목 18.42:1·본문 9.41:1로 4.5:1을 넘는다. 이는 색상 계산이며 실제 글자 크기·화면 배치 검증을 대신하지 않는다. 사용자의 일정에 따라 실제 기기·내비 전환·스피커 출력·배너 캡처는 다음 테스트 환경에서 확인한다.

## 다음 기기 검증

1. 기존 프로필로 debug 홈에 진입한다. 서비스가 사용자가 중지한 상태라면 ‘샘플 실행’, 실행 중이면 ‘샘플 발생’을 누른다. 테스트임을 밝히는 짧은 음성과 배너의 최대 8초 자동 종료를 확인한다.
2. 배너가 보이는 동안 내비로 전환했다가 돌아온다. 원래 만료 시각이 유지되고 같은 음성이 다시 시작되지 않아야 한다. 알림 권한 거부 시에도 음성 경로와 이벤트 수신이 독립적으로 동작하는지 확인한다. 시스템 팝업이 없다는 이유만으로 이벤트 수신 실패로 보지 않는다.
3. 이름 추가·권한 안내 중에는 샘플 배너와 새 발화를 억제한다. 진행 중 발화의 종료 확인 후에만 이름 STT가 시작되는지 실제 Speech Service에서 확인한다. 홈 Activity가 파괴되거나 재생성 로딩 중이어도 이미 준비된 백그라운드 음성 허용 상태가 사라지지 않아야 한다. 프로세스 최초 기본값은 억제이며 프로필 읽기 오류도 억제한다.
4. 음악·내비 음성·통화·Gleo와의 포커스 처리, 오프라인 한국어 합성, 첫 음절 잘림을 확인한다. 중단한 오래된 안내를 포커스 복귀 때 자동 재생하지 않아야 한다.
5. 빠른 이벤트 교체·실행 중지·서비스 종료에서 SDK 중지 확인과 자원 정리를 확인한다. 중지 확인이 누락되어 경로가 차단되면 SDK/기기 로그를 확인하되 이름·좌표·음성 원문을 수집하지 않는다.

기존 앱 데이터 삭제·권한 초기화·AVD 초기화는 이 검증의 전제조건이 아니다.

## 확인한 공식 근거

- [TTS 모드와 진입점](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro), [의존성](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/importing-sdk), [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/declaring-permissions)
- [초기화·해제](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/initialization-and-release), [이벤트 콜백](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/event-listeners), [합성 시작·중지](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/start-and-stop)
- [Pleos 오디오 포커스](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development), [오디오 속성 의미](https://developer.android.com/reference/android/media/AudioAttributes), [선행 묵음](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/voice-guidance-preroll-silence-guide)
- [AAOS 알림 규칙](https://developer.android.com/training/cars/platforms/automotive-os/notifications), [Pleos 시스템 알림 디자인](https://document.pleos.ai/en/docs/connect/guide/docs-design/components/notification/system-notification)

공식 Maven의 3.3.1 AAR에서 실제 메서드 서명을 `javap`로 확인했다. `setAudioAttributes(AudioAttributes)`, `speak(String, boolean)`, `EventListener` 콜백과 on-device stop/release의 비동기 경계를 구현 근거로 사용했다. SDK 바이너리·작업용 검사 파일은 저장소에 포함하지 않는다.
