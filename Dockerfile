# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy build files first so dependency resolution is cached separately from source changes
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
# Tests run in CI separately (and need a live MongoDB) — skip them in the image build itself
RUN ./gradlew bootJar --no-daemon -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd -r app && useradd -r -g app app
COPY --from=build /app/build/libs/*.jar app.jar
RUN chown app:app app.jar
USER app

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
