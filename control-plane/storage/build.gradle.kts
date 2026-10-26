plugins {
    id("ogcloud.spring-library")
}

dependencies {
    api(project(":control-plane:domain"))
    implementation(platform(libs.aws.sdk.bom))
    implementation("software.amazon.awssdk:s3")

    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
