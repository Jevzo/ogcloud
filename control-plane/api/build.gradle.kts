plugins {
    id("ogcloud.control-plane-service")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(libs.springdoc.webmvc.api)

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}
