# 빌드 도구는 이 단계에서만 사용하고 실행 이미지에는 JAR와 JRE만 남긴다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradle gradle
COPY gradlew gradlew.bat build.gradle settings.gradle ./
COPY src src
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/ai-love-workout-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
