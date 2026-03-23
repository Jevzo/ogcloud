plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("com.gradleup.shadow")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(project(":ogcloud-common"))

    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    kapt("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")

    implementation("org.apache.kafka:kafka-clients:4.2.0")
    implementation("io.lettuce:lettuce-core:7.5.0.RELEASE")
    implementation("org.mongodb:mongodb-driver-sync:5.6.4")
    implementation("com.google.code.gson:gson:2.13.2")
}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.jar {
    archiveClassifier.set("thin")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
