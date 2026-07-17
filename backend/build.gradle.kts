plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.hiresemble"
version = "0.0.1-SNAPSHOT"
description = "Hiresemble backend"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val springAiVersion = "2.0.0"
val springdocVersion = "3.0.3"
val tikaVersion = "3.3.1"
val pdfboxVersion = "3.0.8"
val poiVersion = "5.5.1"
val jsoupVersion = "1.22.2"
val awsSdkVersion = "2.48.2"
val testcontainersVersion = "2.0.5"
val wiremockVersion = "3.13.2"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")

    implementation(platform("org.springframework.ai:spring-ai-bom:$springAiVersion"))
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
    implementation("org.springframework.ai:spring-ai-vector-store-advisor")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    implementation("org.apache.tika:tika-core:$tikaVersion")
    implementation("org.apache.tika:tika-parsers-standard-package:$tikaVersion")
    implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")
    implementation("org.apache.poi:poi-ooxml:$poiVersion")
    implementation("org.jsoup:jsoup:$jsoupVersion")

    implementation(platform("software.amazon.awssdk:bom:$awsSdkVersion"))
    implementation("software.amazon.awssdk:s3")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-session-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation(platform("org.testcontainers:testcontainers-bom:$testcontainersVersion"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("user.timezone", "UTC")
}
