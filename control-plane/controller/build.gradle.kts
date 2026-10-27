plugins {
    id("ogcloud.control-plane-service")
}

dependencies {
    implementation(libs.fabric8.kubernetes.client)
    implementation("org.springframework.boot:spring-boot-starter-grpc-server")

    testImplementation("org.testcontainers:testcontainers-k3s")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("io.grpc:grpc-netty")
    testImplementation("io.grpc:grpc-services")
}
