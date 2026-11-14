# A02. 로컬 프로필과 최초 설정·샘플 홈의 구현 범위

- 날짜: 2026-11-12
- 상태: 채택 — 이번 Android UI 개발 범위
- 관련: [구조](../architecture.md), [결정 A01](A01-android-guidance-architecture.md), [구현·검증 기록](../history/ui-implementation-2026-11-12.md)

## 배경

사용자는 Figma의 운전자 이름 최초 설정에서 샘플 홈까지 먼저 구현하고, 백엔드 API는 나중에 연결하도록 요청했다. 라이트·다크 화면을 함께 제공하되 기존의 이름만 수집·최대 3명·개인정보 로컬 보관 결정을 유지한다.

디자인 출발점은 [이름 최초 설정, node 1844:33051](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1844-33051)과 사용자가 제공한 홈 이미지다. 차량 속도계·Driving view·시스템 dock은 앱이 그리는 영역이 아니다.

## 이번 구현

1. 최초 실행에서 이름을 입력한다. 건너뛰면 리소스의 기본 이름 ‘운전자 1’로 프로필을 만든다.
2. 프로필은 추가·선택할 수 있으며 최대 3명이다. 생성과 선택 저장은 Room 트랜잭션에서 수행해 동시에 저장해도 상한을 넘지 않게 한다.
3. 이름은 앞뒤 공백을 제거한 뒤 빈 값을 거부한다. 최대 20 Unicode code point를 임시 UI 한도로 사용한다. UTF-16 길이와 구분하며 서버/API의 이름 제한을 확정한 결정은 아니다.
4. 선택한 프로필을 재시작 후 복원한다. 입력 중 이름 초안은 ViewModel에만 두며 프로세스 종료 후 복원하지 않는다.
5. 시스템 설정 따름·라이트·다크 선택을 Preferences DataStore에 저장한다. 화면은 공통 색상·글꼴·배치 token을 사용한다.
6. 홈은 `HomePreviewEntry` 표시 모델과 debug 더미 목록으로 만든다. 실제 법령 Repository·API DTO·주행 이력과 분리한다.

## 데이터와 의존성

Room DB v1은 기존 ERD의 `driver_profile(id, name, last_selected_at)`을 따른다. 이름 외에 운전 상황·VIN·면허 정보는 수집하지 않는다. 현재 선택 ID는 Android 전용 `local_profile_selection` singleton 행과 FK로 저장한다. 마지막 선택 시각의 대소로 현재 프로필을 추정하지 않아 시스템 시계가 뒤로 바뀌어도 선택을 유지한다.

이 선택 테이블은 Android 내부 구현 상태이며 공유 ERD 또는 백엔드 스키마 변경이 아니다. 프로필·선택·테마는 기기 내부에 보관하고 네트워크·분석 로그로 전송하지 않는다. 앱의 기존 백업·기기 전송 제외 규칙을 유지한다.

DB schema를 `android/app/schemas/`에 내보내 버전 관리한다. 기존 배포 DB가 없는 첫 v1이므로 과거 버전 migration은 없으며 이후 schema 변경에는 명시적인 migration과 검증을 추가한다. DB 오류를 빈 프로필 목록으로 처리하지 않고 오류·재시도 상태를 제공한다. destructive migration으로 프로필을 지우지 않는다.

사용 버전은 Room 2.7.2, KSP 2.1.20-1.0.32, Preferences DataStore 1.1.1, Coil/SVG 2.7.0이다. 버전 카탈로그가 실행 기준이다. 공식 Asta Sans 배포본을 번들로 사용하며 SIL Open Font License 1.1 원문을 `android/app/src/main/assets/licenses/ASTA_SANS_OFL.txt`에 포함한다. SVG도 로컬 asset을 사용한다.

## 시연과 실제 차량의 경계

`debug` 소스 세트의 `app/AppEnvironment.kt`는 화면 시연을 위해 UX 상태를 `Unrestricted`로 가정하고 더미 목록을 공급한다. 이것은 실제 차량에서 주차 상태를 판정한 결과가 아니다. 화면에서 샘플임을 표시하며 개발용 화면 확인에만 사용한다.

`src/release`의 같은 `AppEnvironment`는 더미 목록을 공급하지 않고 실제 UX adapter가 연결될 때까지 `Unknown`을 유지한다. `Unknown/Restricted`에서는 이름 입력·프로필 변경·상세 조작을 허용하지 않는다. debug 가정을 release에 복사하거나 속도 0·기어 P만으로 해제하지 않는다.

더미 홈의 제목·시행 상태·표시 시간은 디자인 확인용이다. 법적 정확성·시행일·위반 여부가 검증됐다는 뜻이 아니며 실제 TTS·주행 이력·사용자 확인으로 기록하지 않는다. 현재 목록을 프로필별 실제 안내 기록으로 취급하지 않는다.

## 이번 범위 밖

- 백엔드 API, 검수 법령 캐시·동기화, 회수 처리와 프로필별 확인/노출 이력
- Pleos/AAOS의 실제 UX 상태, 운전 이벤트, TTS·음성 이름 입력, 백그라운드 표시
- Gleo 질의응답과 실제 변경 법령 조회
- 프로필 이름 수정·삭제, 관련 이력 삭제·보존 정책

홈의 연결 전 진입점은 샘플 또는 준비 중 상태를 알려 주며 동작하는 서비스로 표시하지 않는다. 실제 주행 안내는 결정 A01의 domain/coordinator/platform 경계를 따른다.

## 검증 기준

이름·상한·중복 저장·UX 상태 전환·오류 복구는 JVM 테스트, Room의 동시 생성·선택 복원·재오픈은 계측 테스트로 검증한다. 최초 설정과 홈은 라이트·다크, 창 크기, 키보드·긴 입력 등 화면 시나리오를 확인한다. 작성한 테스트와 실행해 통과한 테스트는 구분하며 결과는 [구현 기록](../history/ui-implementation-2026-11-12.md)에 남긴다.

빌드 성공·샘플 화면 확인은 실제 차량의 CarUxRestrictions 연동, 물리 글자 크기, Pleos 승인이나 주행 안전성 검증을 대신하지 않는다.
