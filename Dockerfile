# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk-noble AS build

WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x ./gradlew
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    for attempt in 1 2 3; do \
      ./gradlew bootJar --no-daemon && exit 0; \
      echo "Gradle build attempt ${attempt} failed; retrying in 10 seconds"; \
      sleep 10; \
    done; \
    exit 1

FROM eclipse-temurin:25-jre-noble

WORKDIR /app
RUN addgroup --system --gid 10001 neko \
    && adduser --system --uid 10001 --ingroup neko neko \
    && mkdir -p /app/storage /app/logs \
    && chown -R neko:neko /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

USER neko
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
