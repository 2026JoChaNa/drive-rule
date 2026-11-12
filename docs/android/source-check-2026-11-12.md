# Android 개발 준비 — 출처 및 검증 기록

확인일: 2026-11-12 (KST). 이 문서는 당시 조회 결과이며 SDK/정책 변경 시 해당 공식 문서를 다시 확인한다. 앱 구현 또는 실차 시험 결과를 뜻하지 않는다.

> 이 기록은 Android 기본 구조를 추가하기 전의 점검이다. 이후 추가한 MVVM 구조와 로컬 빌드 결과는 [Android README](../../android/README.md)와 [구조 문서](architecture.md)를 참고한다.

## 1. 저장소와 GitHub

| 점검 | 결과 |
|---|---|
| 대상 | `drive-rule/android` (서버와 독립 Android 프로젝트 위치) |
| 원격 | [2026JoChaNa/drive-rule](https://github.com/2026JoChaNa/drive-rule), fetch/push 모두 동일 HTTPS 주소 |
| 로컬 브랜치 | `main`, 추적 브랜치 `origin/main` |
| 시작 HEAD | `4e45cacd5eb1e2ef91a832467cd6da13107fe0bc` |
| 시작 작업 트리 | 수정 없음 |
| Android | README만 존재. Gradle/Manifest/앱 소스 없음 |
| 서버 | 부트스트랩 상태, 업무 Controller/카드 API/마이그레이션 미구현 |
| 로컬 Git 원격 조회 | 제한 환경에서 접속 실패. 네트워크 접근을 허용한 읽기 전용 재시도에서도 인증 프롬프트를 완료하지 못함 (`could not read Username`) |
| 연결된 GitHub 도구 | 저장소 메타데이터 및 `main` 조회 모두 HTTP 404 |

**판정:** remote 설정과 로컬 추적 관계는 확인했다. 원격 읽기/쓰기 권한과 최신 원격 HEAD는 확인하지 못했다. 404는 저장소 부재, 주소 변경, 비공개 저장소 접근 권한 미부여 등을 구분하지 못한다. 로컬 `origin/main`과 같다는 사실만으로 최신 동기화를 주장하지 않는다.

다음 연결 점검은 GitHub 앱의 해당 저장소 접근 범위와 로컬 Git Credential Manager 인증 상태를 확인한 뒤 `git ls-remote --symref origin HEAD`로 수행한다. 이 작업에서는 토큰을 만들거나 권한·remote를 변경하지 않았고 커밋·푸시하지 않았다.

검토한 기존 자료: 루트/Android README, `docs/decisions/001`~`007`, [v5.2 화면 대조 요청](<../ERD 보완 요청 · 화면 대조 결과 (v5.2 기준).md>), [ERD 원본](../db/erd.data.mjs), [배포 문서](../deploy.md), 현재 서버/CI 구성. 006·007은 초안이며 기존 결정 상태를 바꾸지 않았다.

### 2026-11-12 푸시 준비 중 추가 점검

기본 Git 2.15.1의 Credential Manager는 인증 요청 오류로 실패했지만, 이미 설치된 GitHub Desktop의 Git 2.53.0과 해당 Credential Manager로 동일 원격을 조회하는 데 성공했다. `git ls-remote origin refs/heads/main` 결과는 `4e45cacd5eb1e2ef91a832467cd6da13107fe0bc`로 로컬 시작 HEAD와 같았다. Git 전역 설정·remote·계정 권한은 변경하지 않았다. 연결된 GitHub 도구는 여전히 HTTP 404를 반환했으므로 로컬 Git과 커넥터의 접근 결과를 구분한다. 이 추가 점검은 원격 읽기 성공의 기록이며 푸시·CI 성공은 별도 결과로 확인한다.

## 2. 사용자가 지정한 공식 가이드

다음 4개 URL 모두 본문 접근을 확인했다.

| 자료 | URL | 이번 문서에서의 역할 |
|---|---|---|
| 차량 앱 기획 가이드 | [소개](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/intro) | 차량 앱의 지원 범위와 기획 제약 |
| 디자인 가이드 | [Introduction](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro) | 짧은 정보·가독성·운전 중 주의 분산 최소화 |
| Connect SDK | [Overview](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/) | 실제 SDK와 권한·가용성 확인 |
| Gleo App Integration | [Core Concepts](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts) | App Schema/function, Action/Callback 구조. 2차 범위 |

## 3. SDK 환경과 권한

[개발 환경](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)과 [SDK 릴리즈](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note)에서 확인한 기준:

| 항목 | 문서에서 확인한 값 |
|---|---|
| Android SDK/Gradle 최소 | API 28 이상 / Gradle 8.0 이상 |
| 에뮬레이터 | 2.1.0, API 34 이미지 |
| Vehicle | 2.3.2 |
| NaviHelper | 3.0.5 |
| TTS / STT | 각각 3.3.1 |
| FusedLocation | 1.1.3 |

2026-10-19 릴리즈를 확인 기준으로 기록했다. 실제 Gradle 좌표·AGP/Kotlin/JDK 호환 조합과 compile/targetSdk는 프로젝트 생성 때 검증하고 고정한다. 여기의 표는 의존성 설치 또는 빌드 성공 기록이 아니다.

- NaviHelper의 조회/상황 정보는 [`pleos.car.permission.NAVI_ROUTE`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)를 확인한다. 검색·내비 화면 요청 등 사용하지 않는 권한까지 복사하지 않는다.
- [공식 이벤트 목록](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners)에 TBT·주의 지점·카메라·제한속도 등이 있다. 경로 없음/내비 비활성 상태의 수신 여부, 사전 수신 거리·지연은 별도 측정한다.
- FusedLocation은 [`pleos.car.permission.FUSED_LOCATION`](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions)이라는 dangerous 권한을 사용한다. 런타임 거부/철회와 null을 처리한다. Android 일반 위치 권한이나 Google API 설명을 Pleos의 백그라운드 위치 보장으로 대체하지 않는다.
- [TTS 서비스 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/declaring-permissions)은 `pleos.car.permission.TTS_SERVICE`다. [TTS on-device](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro)는 문서상 네트워크·서버 앱 등록 없이 합성 가능하다. 한국어·오프라인 지연/품질은 실기기 확인 전이다.
- Vehicle/AAOS는 [기능 가용성](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability)과 capability 검사를 따른다. 특정 차종의 기어·차속 접근을 가정하지 않는다. SDK의 normal 보호 수준이 마켓/차량에서의 무조건적인 사용 승인을 뜻하지 않는다.

## 4. 백그라운드·화면·오디오

| 공식 근거 | 확인한 제약과 구현 영향 |
|---|---|
| [주행 규제 구현](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide) | `CarUxRestrictionsManager` 현재 값과 변경을 반영. 속도 0이나 기어 P만으로 입력 제한을 해제하지 않음 |
| [화면 모델](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model) | 앱 창/시스템 Driving view 구분, 화면 크기·복귀·재생성 고려 |
| [System Notification](https://document.pleos.ai/en/docs/connect/guide/docs-design/components/notification/system-notification) | 알림 디자인은 존재. 예시 크기 카드를 다른 앱 위에 띄우는 API/overlay 권한을 확인한 것은 아님 |
| [Maps/Navigation 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide) | 해당 카테고리는 Background 안내 제한이 있음. DRIVE RULE의 카테고리·선제 법령 안내 허용 범위를 별도 확인. 모든 종류의 앱 TTS 금지로 확대하지 않음 |
| [오디오 포커스](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development) | 포커스 획득/변경/상실을 반영. 법령 안내를 임의로 emergency/safety 오디오로 지정하지 않음 |
| [선행 묵음](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/voice-guidance-preroll-silence-guide) | 150~200ms 권장. 지원 출력 경로와 중복 적용 여부 검증 필요 |
| [Android FGS 시작 제한](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [service type](https://developer.android.com/develop/background-work/services/fgs/service-types) | 백그라운드 시작 예외, 종류별 권한과 선행 조건 확인. 상시 실행 보장으로 해석하지 않음 |
| [Background Activity 제한](https://developer.android.com/guide/components/activities/secure-bal) | 임의 Activity 실행으로 주행 팝업 요구를 우회하지 않음 |

제품 구현 전에 확인할 항목은 백그라운드 신호 지속, 선제 TTS 허용, 카드 표시 surface, audio focus 동작, 프로세스 종료/권한 철회 복구다. 각각 성공 증거를 따로 남긴다.

## 5. Figma 접근과 현재 요청 대조

**접근 성공.** Figma 플러그인 계정 연결, 대상 metadata, 원본 텍스트, 전체 섹션 및 A-1 스크린샷 읽기에 성공했다. 원본 수정은 하지 않았다.

- 파일: [멋사멋사](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/)
- 페이지: `lifeDNA` (`0:1`)
- 섹션: [현대차 v6.0 · 12.9인치 · 이벤트 안내 A·B·H·I (2026-11-11 23:59)](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1844-31772)
- 화면 캔버스: 2560×1440. 주간·야간 변형 존재.

| 용도 | 확인한 node |
|---|---|
| 최초 이름 등록 | [1844:33051](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1844-33051) |
| 운전자 선택 | [1844:33114](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1844-33114) |
| 출발 전 안내 | [1844:31778](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1844-31778) |
| A: 고속도로 기어 P | [1854:29281](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1854-29281) |
| B: 목적지 보호구역, 약 500m | [1854:29587](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1854-29587) |
| H: 보호구역, 시설 약 370m | [1854:29853](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1854-29853) |
| I: 우회전 약 150m 전 | [1854:30119](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1854-30119) |
| 예시 이미지와 대응하는 A-1 주차 카드 | [1849:27324](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1849-27324) |
| 화면 원칙 | [1844:32138](https://www.figma.com/design/dltRYbBH0KHuBe86A6Vr2G/?node-id=1844-32138) |

**중요한 차이와 처리**

1. Figma는 주행 중 음성 1문장만 제공하며 말풍선은 시연 주석이라고 명시한다. 사용자 최신 요청의 ‘주행 카드 팝업’을 현재 요구로 채택하고, 기존 주차 카드를 그대로 주행 화면으로 승인된 것으로 간주하지 않는다.
2. Figma의 출발 전 안내는 1단계 시연 제외/후순위다. 이번 요청에서는 주요 서비스이므로 개발 범위에 포함한다.
3. A-1 설명(`1854:30389`)은 음성 종료 **후 8초**에 닫고 근거 열람 시 멈춘다고 적혀 있다. 반면 출발 전 설명(`1844:32993`)에는 음성 5초 이내+여유 3초의 **총 8초**라는 문구가 있다. 타이머 시작점은 원본 내부에서도 일치하지 않는다. A-1 설계값과 출발 전·새 주행 카드의 미확정 정책을 분리한다.
4. 운전자 선택 설명은 2명 이상일 때 선택, 8초 미선택 시 지난 운전자로 진행, 1명이면 생략이다. 이는 관측한 디자인 동작이며 장치의 자동 운전자 식별이 아니다.
5. A의 기어 P 이벤트는 상황 발생 시점의 후보이며 ‘항상 위반 전에 알림’을 보장하지 않는다. 사전 안내가 필요한 경우 별도 접근 신호가 필요하다.

법령 문구와 시행일의 법적 정확성을 이번 Figma 확인으로 검증하지 않았다. Figma는 디자인 근거이며 법령 원문 검수의 대체물이 아니다.

## 6. 첨부 자료와 보정

- [사용자 제공 SDK 상황 신호 정리 원본 사본](references/pleos-sdk-signals-2026-11-11.md): 내용은 원본 그대로 보관. 작성일 2026-11-11.
- [사용자 제공 화면 이미지](assets/driving-card-reference.png): 앱 카드 시각 참조용. 법령·날짜·금액은 운영 데이터로 승인되지 않음.

첨부 문서의 ‘확실’은 실제 법적 상황 확정과 구분한다. TBT는 예정 회전 안내, 근접 알림은 좌표 반경 접근 신호다. 첨부의 ‘오프라인 TTS 미확인’은 공식 문서상 지원 확인으로 보정하되 장치 시험은 남아 있다. FusedLocation은 전용 dangerous 권한을 확인했으며 일반 위치 권한만으로 구현하지 않는다. 첨부가 언급한 결정 007은 여전히 초안이다.

## 7. Android/Codex 기본 구조 참고

- [Android 아키텍처 권장사항](https://developer.android.com/topic/architecture/recommendations): ViewModel, 상태 기반 UI, 단방향 흐름의 근거.
- [Room](https://developer.android.com/training/data-storage/room): 구조화된 로컬 데이터 저장.
- [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager): 지연 가능한 지속 작업. 실시간 이벤트 엔진과 분리.
- [Codex AGENTS.md 공식 문서](https://learn.chatgpt.com/docs/agent-configuration/agents-md): 디렉터리별 지침과 상세 문서 링크 구성에 참고. 초기에는 루트 지침도 만들었으나 사용자 요청에 따라 삭제했고, 현재 Android 지침은 `android/AGENTS.md`에만 둔다. Android 개발 자료는 `android/`와 `docs/android/`에서 관리한다.
