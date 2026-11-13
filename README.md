# DRIVE RULE

> 한 줄 소개: (기획서 1번 확정 문장을 넣는다)

현대자동차그룹 차량용 앱 개발 공모전(Pleos App Market) 출품작이다.

## 저장소 구조

```
drive-rule/
├── apk/       제출용 release APK (조차나_DRIVERULE_release.apk)
├── android/   차량 앱 (Pleos Connect, Kotlin) : Android Studio로 이 폴더만 연다
└── server/    백엔드 (Spring Boot 4, Java 21, MySQL) : IntelliJ로 이 폴더만 연다
```

두 프로젝트는 Gradle 빌드를 공유하지 않는다.

## 실행 방법

### 앱
(android 프로젝트 생성 후 작성)

### 서버 (로컬)

필요한 것: JDK 21, Docker Desktop(실행 중)

```powershell
cd server
.\gradlew.bat bootRun
```

- `compose.yaml`의 MySQL 8.4 컨테이너가 자동으로 뜨고, Flyway가 `db/migration`의 SQL을 적용한다.
- 확인: http://localhost:8080/actuator/health 가 `{"status":"UP"}`이면 정상이다.
- DB를 처음부터 다시 만들 때: `docker compose down -v` (server 폴더에서)

### API 문서

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs (YAML: `/v3/api-docs.yaml`)
- 끄기: 환경변수 `API_DOCS_ENABLED=false`

### API 요약

설계: [`docs/decisions/009-core-features-v6-alignment.md`](docs/decisions/009-core-features-v6-alignment.md) (008 일부 대체)

사용 설명서: 차량 앱 [`docs/api/app-api.md`](docs/api/app-api.md) · 검수 CMS(검수자 · 운영자) [`docs/api/admin-api.md`](docs/api/admin-api.md)

| 쓰는 곳 | 경로 | 인증 |
|---|---|---|
| 차량 앱 | `GET /api/v1/cards/sync` · `GET /api/v1/geo/school-zones` (보호구역 좌표 manifest, 010) · `POST /api/v1/cards/{id}/error-reports` · `POST /api/v1/answer-holds` | 없음 (식별자 없이 보냄) |
| 검수 CMS | `/api/v1/admin/**` (법령 개정 · 검수자 · 카드 · 검수 요청 대기열) | 헤더 `X-Admin-Key` = 환경변수 `ADMIN_API_KEY` |

- Gleo 질문(바뀐 규칙 · 조건별 답변)은 서버로 오지 않는다 — 앱이 동기화한 사본으로 답한다
- 조건별 답 고르기 규칙은 서버·앱 공용 명세: [`spec/rule-matching/`](spec/rule-matching/README.md) (테스트 케이스 `cases.json` 을 양쪽이 돌린다)
- `ADMIN_API_KEY` 가 비어 있으면 CMS API 는 모두 401 이다. 로컬에서 쓰려면 `$env:ADMIN_API_KEY="local"` 후 `bootRun`
- 시연 카드 넣기 (DRAFT): `BASE_URL=http://localhost:8080 ADMIN_API_KEY=local node server/seed/seed.mjs`
- 법령 개정 자동 수집: 매일 06:30 국가법령정보 Open API 로 도로교통법 · 시행령 · 시행규칙의 최근 90일 공포분을 받아 없는 개정만 **분류 대기(PENDING)** 로 넣는다. 카드는 만들지 않는다. 지금 돌리기 `POST /api/v1/admin/law-revisions/sync`. 환경변수 `LAW_API_OC` 가 비어 있으면 꺼진다 (수동 실행은 503). 인증값 신청 때 서버 IP 등록 필요 ([011](docs/decisions/011-law-revision-collector.md))
- 보호구역 좌표 갱신 (반기마다): `node server/geo/build-school-zones.mjs` → `server/src/main/resources/geo/school-zones.json` 을 PR 로 올린다. 서버는 부팅 때 파일을 검사하고, 잘못되면 뜨지 않는다 ([010](docs/decisions/010-school-zone-dataset.md))

### 서버 테스트

```powershell
cd server
.\gradlew.bat test
```

Testcontainers가 MySQL 8.4 컨테이너를 띄워 테스트한다. Docker Desktop이 실행 중이어야 한다.

### CI

- `.github/workflows/server-ci.yml`: `server/` 가 바뀐 PR과 `main` 푸시에서 `./gradlew test` 를 돌린다.
- 실패하면 테스트 리포트가 Actions 아티팩트(`server-test-reports`)로 올라간다.

### CD

- `.github/workflows/server-cd.yml`: `main` 에서 CI 가 성공하면 Docker 이미지를 Docker Hub 에 올리고 EC2 에 배포한다.
- GitHub secrets, EC2 준비: [`docs/deploy.md`](docs/deploy.md)

## 테스트 환경

- Pleos Connect Emulator v2.1.0 (Android 14, API 34)
- (필요한 설정 작성)

## APK 위치

`apk/조차나_DRIVERULE_release.apk`
