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
    implementation(libs.springdoc.webmvc.api)

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
}
