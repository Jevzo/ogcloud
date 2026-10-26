import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("ogcloud.kotlin-library")
    id("com.gradleup.shadow")
}

// Third-party plugins on the same server ship their own copies of these libraries.
private val shadedPackages =
    listOf("io.grpc", "io.netty", "com.google.protobuf", "com.google.common", "kotlin", "kotlinx")
private val shadedPrefix = "io.ogwars.cloud.shaded"

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(MINECRAFT_JAVA_VERSION.toString())
        freeCompilerArgs.add("-Xjdk-release=$MINECRAFT_JAVA_VERSION")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = MINECRAFT_JAVA_VERSION
}

tasks.shadowJar {
    mergeServiceFiles()
    shadedPackages.forEach { relocate(it, "$shadedPrefix.$it") }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
