# 관리자 API 연동 검증

검증일: 2026-11-14 (서울)

## 결과

실제 Spring Boot 4.1.2 + Java 21 + MySQL 8.0.31에서 **HTTP 검사 75개 통과, 실패 0개**.

- 실제 백엔드: http://127.0.0.1:8090
- 웹 개발 프록시를 통한 추가 브라우저 검증: http://127.0.0.1:5174
- 기존 DB와 분리된 테스트 데이터베이스, local 프로필의 1인 승인 정책 사용.
- 운영 서버에는 요청하지 않았습니다.
- UI 색상: 배경 #EDEEF2, 포인트 #0064FF.

## 연결 범위

| 기능 | 메서드 / 경로 (/api/v1 기준) | 결과 |
|---|---|---|
| 카드 목록·필터 / 작성 | GET, POST /admin/cards | 통과 |
| 카드 상세 / 전체 수정 | GET, PUT /admin/cards/{id} | 통과 |
| 검수 요청 | POST /admin/cards/{id}/submit | 통과 |
| 검수 기록 / 승인·반려 | GET, POST /admin/cards/{id}/reviews | 통과 |
| 대표 주제 지정·해제 | PUT /admin/cards/{id}/topic | 통과 |
| 회수 | POST /admin/cards/{id}/retire | 통과 |
| 운영자 조회 / 자동 등록 | GET, POST /admin/reviewers | 통과 |
| 법령 목록·필터 / 등록 | GET, POST /admin/law-revisions | 통과 |
| 법령 분류 | PATCH /admin/law-revisions/{id}/triage | 통과 |
| 법령 수집 | POST /admin/law-revisions/sync | 인증값 미설정 503 응답 확인 |
| 신고 목록·필터 / 처리 | GET /admin/review-requests, PATCH /admin/review-requests/{id} | 통과 |
| 앱 게시·회수 동기화 | GET /cards/sync | 통과 |
| 앱 오류 신고 / 답변 보류 접수 | POST /cards/{id}/error-reports, POST /answer-holds | 통과 |

관리자 API 17개 메서드·경로 조합과 공개 앱 API 3개를 확인했습니다. 목록 화면의 필터·검색은 전체 조회 결과에서 처리합니다.

## 확인한 시나리오

- 카드 3종 전체 필드 저장·수정과 결정 테이블의 황색/점멸 조건
- 반려 후 재검수 버전 증가, 1인 승인 게시, 게시 후 수정 및 재검수
- 현재 버전의 검수 기록, 대표 주제 중복·변경 제한, 회수 후 재수정 차단
- 게시 카드가 실제 앱 동기화 응답에 포함되고 회수 후 제외되는지
- 오류 신고·답변 보류 접수, 완료·종결·다시 열기
- 400, 401, 404, 409, 410, 422, 503 응답 및 서버 오류 코드

## 남은 외부 설정

법령 자동 수집의 성공 경로는 LAW_API_OC 인증값이 필요합니다. 이번에는 인증값이 없어 LAW_API_NOT_CONFIGURED 오류 및 화면 안내를 확인했습니다. 이를 수집 성공으로 보지 않습니다.

## 재실행

테스트용 로컬 DB임을 확인하고 실행합니다. 기존 레코드는 수정하지 않고 고유한 테스트 데이터를 만듭니다. 생성한 카드는 마지막에 회수하지만, 삭제 API가 없는 법령·운영자 기록은 남습니다.

~~~powershell
$env:API_TEST_BASE = "http://127.0.0.1:8090"
$env:API_TEST_ISOLATED = "1"
$env:ADMIN_API_KEY = "local"
npm run test:api
~~~

결과는 기본적으로 .local/api-verification.json에 저장됩니다. API 키는 결과에 기록하지 않습니다.


브라우저 검증도 14개 통과했습니다. 실제 프록시를 통한 카드 작성→1인 승인→앱 게시 확인→회수, 수집 미설정 오류 표시, 신고 목록, 모바일 가로 넘침, JavaScript 오류 0개를 확인했습니다. 마지막으로 주 웹 포트 5173의 실제 API 연결과 색상 RGB 값을 다시 확인했습니다.
