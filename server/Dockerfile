# DRIVE RULE 서버 이미지
# jar 는 먼저 빌드한다:  ./gradlew bootJar   →  build/libs/app.jar
# 그다음:               docker build -t drive-rule-server .
#
# CI(server-cd) 는 Gradle 캐시를 쓰려고 러너에서 jar 를 만든 뒤 이 파일로 이미지만 굽는다.

FROM eclipse-temurin:21-jre

ENV TZ=Asia/Seoul
WORKDIR /app

# root 가 아닌 사용자로 실행
RUN groupadd --system app && useradd --system --gid app --home-dir /app app

COPY --chown=app:app build/libs/app.jar app.jar

USER app
EXPOSE 8080

# 컨테이너 메모리 한도에 맞춰 힙을 잡는다
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
