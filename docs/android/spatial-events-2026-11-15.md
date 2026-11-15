# Figma B·H·I 이벤트 연결 기록

작성일: 2026-11-15 · 상태: 구현·JVM/lint/Debug/Release/테스트 APK 빌드 통과 / 기기 미실행

## 원본 확인

Figma 연결 후 `✅ 최종 · 현대차 v6.0 · 이벤트 안내 A·B·H·I (2026-11-14 최종 검토)`의 구조와 각 설명·스크린샷을 확인했다. 원본은 네 장면이며 주정차 A/B를 묶으면 세 분류다. 이번 구현은 B/H를 추가해 B/H/I를 지원하고 A는 입력 미확보 상태로 구분한다.

| 장면 | 원본 | 구현 입력 |
| --- | --- | --- |
| A 고속도로 주정차 | [프레임](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G?node-id=1854-29281), 설명1854:30421 | 미지원: 주차 브레이크만으로 현재 고속도로 여부를 알 수 없음 |
| B 목적지 주변 보호구역 주정차 | [프레임](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G?node-id=1854-29587), 설명1854:30425 | 목적지 좌표·남은 경로거리 + 학교 시설 사본 |
| H 보호구역 진입 | [프레임](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G?node-id=1854-29853), 설명1854:30429 | FusedLocation 연속 위치 + 학교 시설 사본 |
| I 우회전 접근 | [프레임](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G?node-id=1854-30119), 설명1854:30433 | 기존 첫 TURN_RIGHT·거리 |

전체 화면을 다시 만들지 않고 기존 Compose·주야간 토큰·안내 출력을 사용했다. 권한 안내의 차량 항목은 위치 사용 목적까지 설명하고 연결 진단에 위치 상태를 추가했다. 실제 렌더링·물리적 글자 크기·주야간 대비·반쪽 창·CarUxRestrictions는 기기 검증 전이며 이번 연결 작업으로 디자인 적합성 완료를 주장하지 않는다.

A의 주차 브레이크 신호는 기존 AAOS adapter가 `PARKING_BRAKE_ON`으로 이미 구독한다. 부족한 입력은 현재 고속도로 영역 판정이다. 확인한 NaviHelper 3.0.5/navi-data 3.0.4의 CurrentLocationInfo에는 도로 분류가 없으며, `RouteOption.HIGHWAY`는 경로 옵션, IC TBT·공사/주차 주의 지점은 전방 안내다. 이 값들을 현재 고속도로 위치의 대체 증거로 사용하지 않는다.

## 공식 근거와 계약

- [FusedLocation 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions): custom dangerous permission과 개인정보 차단을 명시하며 일반 Android 위치·백그라운드 정책은 보장하지 않는다.
- [FusedLocation 1.1.3 공식 소스](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/FusedLocation/1.1.3/FusedLocation-1.1.3-sources.jar): 연속 콜백, LocationData 필드, 초기화·해제·내부 재시도를 확인했다. timestamp clock과 confidence 단위는 미정의다. on-device 실측 확인이 남아 있다.
- [NaviHelper 3.0.5 소스](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/NaviHelper/3.0.5/NaviHelper-3.0.5-sources.jar), [navi-data 3.0.4 소스](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/navi-data/3.0.4/navi-data-3.0.4-sources.jar): getDestinationInfo/onDestinationInfo와 좌표·remainDistance를 대조했다.
- [Android location FGS](https://developer.android.com/develop/background-work/services/fgs/service-types#location): 서비스 유형, 권한, 시스템 위치 활성화와 while-in-use 시작 조건을 구현에 반영했다.
- [기존 앱 API](../api/app-api.md), [공유 학교 데이터 결정010](../decisions/010-school-zone-dataset.md): manifest 기준 상대 URL, 압축 해제 SHA-256, 전체 사본 교체, 미보유 시 B/H 보류. 현재 파일은 same-origin만 허용한다. 향후 CDN origin을 추가하면 명시적 허용 정책을 함께 수정해야 한다.

정책과 대안은 [Android 결정012](decisions/012-school-and-destination-events.md)에 기록했다. 서버 endpoint·공유 기획·ERD·루트 README는 수정하지 않는다.

## 검증

실제 저장소 `android/`에서 JDK17·SDK34로 아래 명령을 실행했다. 2026-11-15, 7분44초, 종료코드0, 130개 task 실행으로 완료했다.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --max-workers=2 --console=plain
```

| 항목 | 결과 |
| --- | --- |
| JVM | 34개 테스트 클래스, 366개 전부 통과, 실패/오류/skip 0 |
| 이번 추가 | 학교 캐시/전송23, 위치14, 내비3, 공간 정책25, 실행 조정4 = 69개 |
| lint | 오류0, 기존 경고49(GradleDependency45, AndroidGradlePluginVersion3, OldTargetApi1) |
| Debug·Release(R8) | 모두 성공. Release는 서명 전 빌드 산출물 |
| AndroidTest APK | 컴파일 성공, 기기 테스트는 미실행 |
| Release merged manifest | 실제 서비스 `specialUse|location`과 custom/일반 위치 권한 확인, 샘플 서비스·overlay·background location 권한 없음 |

테스트는 최초 안쪽 관측, 경계/재무장, 겹치는 이벤트 우선순위, 예약 중 지도/위치/목적지 변경, 늦은 콜백, 위치 단절·재연결, 다운로드 중 캐시 읽기, 해시/스키마/좌표 불일치, gzip 크기 제한, 404·ETag·취소·동시 갱신 등을 다룬다. 네트워크 테스트는 JVM 모의 서버를 사용하며 운영 데이터 변경이나 차량 좌표 전송을 하지 않는다. 기기 렌더링·foreground 권한 전환을 JVM으로 검증했다는 뜻은 아니다.

사용자가 별도로 준비하기로 한 기기 환경은 이번에 실행하지 않았다. 특히 다음은 실차/에뮬레이터 확인이 필요하다.

1. 권한 허용·거부·철회 및 시스템 위치 토글 후 앱 복귀, 다른 앱 전면에서도 continuous 위치 수신, 서비스 중지 후 재바인딩 차단.
2. 첫 위치가 이미 시설 주변일 때 무발화, 바깥→370m 진입, 470m 재무장, 데이터 교체·지연·재연결·겹친 시설 반경에서 중복 여부.
3. 목적지가 시설300m 안이고 600m 바깥→500m 접근, 목적지 변경·재탐색·취소 시 구 안내 취소.
4. 우회전과 학교 접근이 겹칠 때 한 안내만 출력, 원문 TTS 종료 여유, 영속 예약 제한, 다른 내비 음성과 공존.
5. 앱 내부 배너 및 시스템 EVENT 알림의 실제 표시·자동 종료와 주야간. 알림 게시와 heads-up 팝업 노출을 구분한다.

위치 출처의 절대 측정 나이·정확도, 현재 고속도로 분류, 배포 승인과 실제 TTS/알림 노출은 빌드·JVM 테스트로 확인할 수 없다.
