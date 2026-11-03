plugins {
    id("ogcloud.kotlin-library")
    id("com.gradleup.shadow")
}

// Third-party plugins on the same server ship their own copies of these libraries.
private val shadedPackages =
    listOf("io.grpc", "io.netty", "com.google.protobuf", "com.google.common", "kotlin", "kotlinx")
private val shadedPrefix = "io.ogwars.cloud.shaded"

tasks.shadowJar {
    mergeServiceFiles()
    // Shadow drops duplicate entries before merging unless told otherwise, which lost gRPC's DNS name resolver.
    filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
    shadedPackages.forEach { relocate(it, "$shadedPrefix.$it") }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
