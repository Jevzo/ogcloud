plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(project(":ogcloud-common"))

    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    implementation("org.apache.kafka:kafka-clients:4.2.0")
    implementation("io.lettuce:lettuce-core:7.5.0.RELEASE")
    implementation("com.google.code.gson:gson:2.13.2")
}

tasks.shadowJar {
    archiveBaseName.set("ogcloud-paper-plugin")
    archiveClassifier.set("")
}

tasks.jar {
    archiveClassifier.set("thin")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
