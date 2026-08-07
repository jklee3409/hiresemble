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
val jsoupVersion = "1.23.1"
val webpImageIoVersion = "0.3.3"
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
    implementation("io.github.darkxanter:webp-imageio:$webpImageIoVersion")

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
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
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
    systemProperty("hiresemble.ai.provider", "none")
    systemProperty("hiresemble.search.provider", "none")
    systemProperty("spring.ai.model.chat", "none")
    systemProperty("spring.ai.model.embedding", "none")
    systemProperty("spring.ai.vectorstore.type", "none")
    systemProperty("hiresemble.ai.allow-test-provider", "true")
    systemProperty("spring.datasource.hikari.maximum-pool-size", "3")
}

tasks.named<Test>("test") {
    exclude("**/CodexRealProviderTest.class")
    exclude("**/P4BrowserE2eTest.class")
    exclude("**/P5BrowserE2eTest.class")
    exclude("**/P6BrowserE2eTest.class")
    exclude("**/P7BrowserE2eTest.class")
    exclude("**/P8BrowserE2eTest.class")
}

fun registerCodexRealProviderTask(taskName: String, capability: String) =
    tasks.register<Test>(taskName) {
        group = "verification"
        description = "Runs the bounded, opt-in $capability real-provider verification."
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        include("**/CodexRealProviderTest.class")
        systemProperty("spring.profiles.active", "local")
        systemProperty("hiresemble.ai.provider", "openai")
        systemProperty("hiresemble.search.provider", "tavily")
        systemProperty("spring.ai.model.chat", "openai")
        systemProperty("spring.ai.model.embedding", "openai")
        systemProperty("spring.ai.vectorstore.type", "none")
        systemProperty("codex.real-provider.capability", capability)
        onlyIf {
            System.getenv("CODEX_REAL_PROVIDER_TEST_ENABLED")
                    ?.equals("true", ignoreCase = true) == true
                    && !System.getenv("AI_PROVIDER_API_KEY").isNullOrBlank()
                    && !System.getenv("TAVILY_API_KEY").isNullOrBlank()
        }
        shouldRunAfter(tasks.named("check"))
    }

registerCodexRealProviderTask("codexRealOpenAiChatTest", "CHAT")
registerCodexRealProviderTask("codexRealOpenAiEmbeddingTest", "EMBEDDING")
registerCodexRealProviderTask("codexRealTavilySearchTest", "SEARCH")
registerCodexRealProviderTask("codexRealProviderTest", "ALL")

tasks.register<Test>("p4BrowserE2eTest") {
    group = "verification"
    description = "Runs the isolated P4 Spring, PostgreSQL, MinIO, Vue, SSE, and Chromium E2E."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("**/P4BrowserE2eTest.class")
    shouldRunAfter(tasks.named("test"))
}

tasks.register<Test>("p5BrowserE2eTest") {
    group = "verification"
    description = "Runs the isolated P5 Spring, PostgreSQL, Vue, Job workflow, Scheduler, SSE, and Chromium E2E."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("**/P5BrowserE2eTest.class")
    shouldRunAfter(tasks.named("test"))
}

tasks.register<Test>("p6BrowserE2eTest") {
    group = "verification"
    description = "Runs the isolated P6 Spring, PostgreSQL, Fake AI, Vue, SSE, and Chromium E2E."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("**/P6BrowserE2eTest.class")
    shouldRunAfter(tasks.named("test"))
}

tasks.register<Test>("p7BrowserE2eTest") {
    group = "verification"
    description = "Runs the isolated P7 Spring, PostgreSQL, MinIO, Fake AI, Vue, SSE, and Chromium E2E."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("**/P7BrowserE2eTest.class")
    shouldRunAfter(tasks.named("test"))
}

tasks.register<Test>("p8BrowserE2eTest") {
    group = "verification"
    description = "Runs the isolated P8 Spring, PostgreSQL, Fake Chat/Search, Vue, SSE, and Chromium E2E."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("**/P8BrowserE2eTest.class")
    shouldRunAfter(tasks.named("test"))
}
