plugins {
    id("ogcloud.control-plane-service")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework:spring-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-authorization-server")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    // Login sessions shared by all replicas (D-0091).
    implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
    // Passkeys (D-0085); webauthn4j is pinned above the version spring-security-webauthn brings.
    implementation("org.springframework.security:spring-security-webauthn")
    implementation(libs.webauthn4j.core)
    implementation(libs.springdoc.webmvc.api)
    // springdoc reads Kotlin nullability through it to mark required fields (D-0096).
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    // Headless Chromium for the login journey, passkeys included (D-0099).
    testImplementation(libs.playwright)
}

// Only Chromium's headless shell, installed once per machine; the tests must not fetch every browser (D-0099).
val installChromium by tasks.registering(JavaExec::class) {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "com.microsoft.playwright.CLI"
    args("install", "--only-shell", "chromium")
}

tasks.test {
    dependsOn(installChromium)
    environment("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")
}
