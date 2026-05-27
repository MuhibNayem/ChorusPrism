# Chorus Observe Server — Standalone Production Dockerfile
# No gradle wrapper required. Gradle is installed directly.

FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app

# Install Gradle 9.1.0 + tools
ENV GRADLE_VERSION=9.1.0
RUN apk add --no-cache wget unzip git curl \
    && wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip \
    && unzip -q gradle-${GRADLE_VERSION}-bin.zip \
    && rm gradle-${GRADLE_VERSION}-bin.zip \
    && mv gradle-${GRADLE_VERSION} /opt/gradle
ENV PATH="/opt/gradle/bin:${PATH}"

# ── Dependency cache layer ────────────────────────────────────────────────────
# Copy only build files first so Docker caches the dep-download layer
# separately from source changes.  Re-runs only when build.gradle.kts changes.
COPY build.gradle.kts settings.gradle.kts ./
RUN gradle dependencies --no-daemon --no-watch-fs -q \
    || gradle dependencies --no-daemon --no-watch-fs -q   # retry once on transient DNS blip

# ── Source build ──────────────────────────────────────────────────────────────
COPY src ./src
RUN gradle bootJar --no-daemon --no-watch-fs -x test

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S chorus && adduser -S chorus -G chorus

COPY --from=builder /app/build/libs/app.jar app.jar

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

USER chorus

EXPOSE 8080 4317 4318

ENTRYPOINT ["java", "--enable-preview", "--add-modules", "jdk.incubator.vector", "-jar", "app.jar"]
