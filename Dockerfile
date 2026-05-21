# Chorus Observe Server — Standalone Production Dockerfile
# No gradle wrapper required. Gradle is installed directly.

FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app

# Install Gradle 9.1.0
ENV GRADLE_VERSION=9.1.0
RUN apk add --no-cache wget unzip \
    && wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip \
    && unzip -q gradle-${GRADLE_VERSION}-bin.zip \
    && rm gradle-${GRADLE_VERSION}-bin.zip \
    && mv gradle-${GRADLE_VERSION} /opt/gradle
ENV PATH="/opt/gradle/bin:${PATH}"

# Copy build files and source
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src

# Build
RUN gradle bootJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S chorus && adduser -S chorus -G chorus

COPY --from=builder /app/build/libs/app.jar app.jar

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

USER chorus

EXPOSE 8080 4317 4318

ENTRYPOINT ["java", "--enable-preview", "--add-modules", "jdk.incubator.vector", "-jar", "app.jar"]
