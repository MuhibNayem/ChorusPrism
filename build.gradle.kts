plugins {
    id("java-library")
    id("io.spring.dependency-management") version "1.1.7"
    id("org.springframework.boot") version "4.0.0"
    id("com.vanniktech.maven.publish") version "0.33.0"
}

group = "io.github.muhibnayem"
version = providers.gradleProperty("releaseVersion")
    .orElse(
        providers.exec {
            commandLine("git", "describe", "--tags", "--always")
            isIgnoreExitValue = true
        }.standardOutput.asText.map { it.trim().removePrefix("v") }
    )
    .orElse("0.1.0-SNAPSHOT")
    .get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        url = uri("https://build.shibboleth.net/maven/releases/")
        mavenContent {
            includeGroup("org.opensaml")
            includeGroup("net.shibboleth")
        }
    }
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.0"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.aspectj:aspectjweaver:1.9.22")
    implementation("org.springframework.boot:spring-boot-starter-mail:4.0.0")

    // JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.0")

    // Security (versions managed by Spring Boot BOM)
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-crypto")
    implementation("org.springframework.security:spring-security-config")
    implementation("org.springframework.security:spring-security-web")
    implementation("org.springframework.security:spring-security-oauth2-client")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.security:spring-security-saml2-service-provider")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Database
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.clickhouse:clickhouse-jdbc:0.7.1")

    // Parquet export
    implementation("com.jerolba:carpet-record:0.6.1")

    // S3 / MinIO export
    implementation("software.amazon.awssdk:s3:2.29.0")

    // Chorus Engine modules (version managed by BOM)
    implementation(platform("io.github.muhibnayem:chorus-engine-bom:0.1.7"))
    implementation("io.github.muhibnayem:chorus-engine-evals")
    implementation("io.github.muhibnayem:chorus-engine-guardrails")
    implementation("io.github.muhibnayem:chorus-engine-tokenizer")

    // OTLP gRPC intake
    implementation("io.grpc:grpc-netty-shaded:1.68.1")
    implementation("io.grpc:grpc-protobuf:1.68.1")
    implementation("io.grpc:grpc-stub:1.68.1")
    implementation("io.opentelemetry.proto:opentelemetry-proto:1.4.0-alpha")

    // OpenAPI / Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.0")

    // Structured logging
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    // Cryptography (for self-signed cert generation)
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78")

    // Null safety
    compileOnly("org.jspecify:jspecify:1.0.0")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:4.0.0")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test:4.0.0")
    testImplementation("com.h2database:h2")

    // Benchmarking
    testImplementation("org.openjdk.jmh:jmh-core:1.37")
    testImplementation("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--enable-preview", "--add-modules", "jdk.incubator.vector"))
    options.release.set(25)
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--enable-preview", "--add-modules", "jdk.incubator.vector")
    maxHeapSize = "2g"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("-enable-preview", true)
        addStringOption("-add-modules", "jdk.incubator.vector")
        addStringOption("source", "25")
    }
    isFailOnError = false
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    archiveFileName.set("app.jar")
}
