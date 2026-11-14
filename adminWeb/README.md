# DRIVE RULE Admin

React + TypeScript 기반의 로컬 콘텐츠 운영 웹입니다. 별도 로그인·검수자 관리 화면 없이 **로컬 운영자 1명**이 작성, 검수, 게시, 회수합니다.

## 바로 실행: 백엔드 없는 데모

Node.js 20.19+ 또는 22.12+를 사용하세요.

~~~powershell
# 저장소 루트에서 실행
cd .\adminWeb
npm install
npm run dev:demo
~~~

[http://127.0.0.1:5173](http://127.0.0.1:5173)에서 엽니다.

- 데모 API: 127.0.0.1:8081. API 키는 개발 예시값 local입니다.
- 샘플 데이터와 데모 표시가 있는 독립 실행 모드입니다. 실제 서버를 호출하지 않습니다.
- 저장·검수·게시·회수·법령 분류·신고 처리가 동작합니다. 서버를 종료하면 초기화됩니다.
- 법령 수집도 외부 요청 없는 모의 응답이며, 일부 실패 표시를 확인할 수 있습니다.
- 종료: 실행 터미널에서 Ctrl+C. 같은 5173 포트를 사용하는 웹이 있다면 먼저 종료하세요.

## 실제 로컬 백엔드 연결

### 1. 서버 실행

Docker Desktop을 실행하고 JDK 21을 준비합니다. 아래는 PowerShell 기준입니다.

~~~powershell
# 저장소 루트에서 실행
cd .\server
# PATH가 Java 17이라면 설치된 JDK 21 경로를 지정합니다.
# 이 PC에서는 Android Studio 내장 JBR도 Java 21입니다.
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ADMIN_API_KEY = "local"
$env:SPRING_PROFILES_ACTIVE = "local"
.\gradlew.bat bootRun
~~~

- local 프로필은 승인 필요 수를 1로 설정합니다.
- 기본 프로필은 기존처럼 서로 다른 검수자의 승인 2건을 유지합니다.
- 웹은 승인 버튼을 누를 때 이름이 로컬 운영자인 검수자를 재사용하고, 없으면 한 번 등록합니다. 두 번째 검수자를 대신 생성하지 않습니다.
- MySQL은 기존 server/compose.yaml로 실행됩니다. 기존 데이터베이스를 초기화하지 않습니다.
- 확인: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health).
- 8080을 다른 프로그램이 사용하면 SERVER_PORT와 아래 API_TARGET을 같은 포트로 변경하세요.

### 2. 웹 실행

~~~powershell
# 저장소 루트에서 실행
cd .\adminWeb
Copy-Item .env.example .env
npm install
npm run dev
~~~

.env에서 API_TARGET을 설정합니다.

~~~dotenv
API_TARGET=http://localhost:8080
# 선택: 화면에서 키를 입력하는 대신 로컬 개발 프록시에 설정
ADMIN_API_KEY=local
~~~

.env를 변경하면 개발 서버를 다시 실행합니다. 기존 .env.local 파일이 있으면 .env보다 우선 적용되므로 API_TARGET도 함께 확인하세요. 키를 설정하지 않았다면 왼쪽 **연결 설정**에서 API 키를 입력하세요. UI에 입력한 키는 현재 페이지 메모리에만 보관하며 새로고침하면 지워집니다.

Vite의 /api 프록시를 통해 백엔드 CORS 변경 없이 연결합니다. .env의 ADMIN_API_KEY는 개발 서버가 관리자 요청에만 주입하며 브라우저 번들에 포함하지 않습니다. 키를 VITE_ 접두사의 환경변수에 넣지 마세요. 개발 서버는 127.0.0.1에만 바인딩됩니다.

## 화면과 동작

- **대시보드**: 실제 API 데이터 기준 카드 상태, 검수 대기, 미처리 신고, 최근 수정, 답변 보류 주제.
- **카드 관리**: 검색·상태 필터·페이지 이동, 모든 필드 작성/수정, JSON 결정 테이블과 조합 검사, 대표 주제 지정/해제.
- **카드 상세**: 검수 요청, 승인/반려, 버전별 기록, 게시된 카드의 앱 동기화 응답 확인, 확인 절차가 있는 영구 회수.
- **법령 개정**: 수동 등록, 해당 여부 분류, 수집 실행과 법령별 부분 성공/실패.
- **신고 대기열**: 오류 신고·답변 보류 필터, 연결 카드 열기, 처리 완료·종결·다시 열기.
- **모바일**: 접이식 메뉴, 반응형 레이아웃, 표 내부 가로 스크롤.

수정은 전체 PUT으로 모든 필드를 전송하며 DRAFT로 돌아갑니다. 게시 이력이 있는 카드의 topic은 수정 폼에서 보존합니다. 대표 주제 변경은 서버 정책에 따라 게시 중일 때 별도 API로 처리합니다. 회수는 되돌릴 수 없습니다.

첨부 설명서보다 현재 서버 코드를 우선해 조건 목록에 전방 신호 yellow, 보행 신호 green_flashing을 반영했습니다. 오류 응답의 code, detail, errors: string[]를 표시합니다.

## 확인 명령

~~~powershell
npm test
npm run build
npm run test:e2e
~~~

- 단위/HTTP 테스트: 조건 테이블의 누락·중복, 수정 필드 보존, 버전·게시·회수·인증 등.
- 브라우저 테스트: 대시보드, 작성→승인→앱 동기화, 법령 등록/분류, 신고 처리/재개, 인증 오류, 모바일 화면.
- Windows는 설치된 Chrome 또는 Edge를 사용합니다. 다른 환경에서는 npx playwright install chromium을 먼저 실행하세요.
- 브라우저 테스트는 데모 서버와 데이터를 사용하며, 데모가 아닌 서버에서는 변경 테스트를 중단합니다.
- npm run preview는 정적 빌드 확인용이며 API 프록시를 제공하지 않습니다. 로컬 API 연결은 npm run dev로 확인하세요.

## 구조

~~~text
src/
  App.tsx                  화면 구성·데이터 갱신·연결 설정
  Dashboard.tsx            운영 현황
  CardsPage.tsx             카드 검색·목록
  CardEditor.tsx            전체 필드 편집
  CardDetail.tsx            검수·주제·회수·앱 게시 확인
  ManagementPages.tsx       법령·신고 관리
  api.ts                    API 인증·타임아웃·오류 처리
  card-validation.ts        필드·결정 테이블 검증
  types.ts                  실제 서버 DTO에 맞춘 타입
scripts/
  dev-demo.mjs              데모 API와 웹 동시 실행
  mock-server.mjs           메모리 기반 데모 API
e2e/admin.spec.ts           브라우저 흐름 검증
~~~

배포 설정은 포함하지 않았습니다. 실제 백엔드 API 설명은 ../docs/api/admin-api.md와 ../server/src/main 코드를 참고하세요.


## 실제 API 검증 기록

실제 Spring + MySQL을 사용한 HTTP 검증 75개를 통과했습니다. 전체 연결 범위와 법령 수집 인증값 제한은 [검증 기록](docs/api-verification.md)을 참고하세요. 재검증 명령은 npm run test:api이며 테스트 전용 로컬 DB에서만 실행합니다.
