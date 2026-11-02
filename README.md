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

### 서버 테스트

```powershell
cd server
.\gradlew.bat test
```

Testcontainers가 MySQL 8.4 컨테이너를 띄워 테스트한다. Docker Desktop이 실행 중이어야 한다.

## 테스트 환경

- Pleos Connect Emulator v2.1.0 (Android 14, API 34)
- (필요한 설정 작성)

## APK 위치

`apk/조차나_DRIVERULE_release.apk`
