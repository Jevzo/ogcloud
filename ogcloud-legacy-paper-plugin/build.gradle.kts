plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

dependencies {
    implementation(project(":ogcloud-common"))

    compileOnly("org.github.paperspigot:paperspigot-api:1.8.8-R0.1-SNAPSHOT")

    implementation("org.apache.kafka:kafka-clients:3.9.2")
    implementation("io.lettuce:lettuce-core:7.5.0.RELEASE")
    implementation("com.google.code.gson:gson:2.13.2")
}

tasks.shadowJar {
    archiveBaseName.set("ogcloud-paper-plugin")
    archiveClassifier.set("")

    relocate("org.apache.kafka", "io.ogwars.cloud.paper.lib.kafka")
    relocate("io.lettuce", "io.ogwars.cloud.paper.lib.lettuce")
    relocate("io.netty", "io.ogwars.cloud.paper.lib.netty")
    relocate("com.google.gson", "io.ogwars.cloud.paper.lib.gson")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
