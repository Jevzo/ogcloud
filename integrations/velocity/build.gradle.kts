plugins {
    id("ogcloud.mc-plugin")
}

dependencies {
    implementation(project(":integrations:node-core"))
    // Velocity and ViaVersion are on the proxy at runtime (ViaVersion through the runtime bundle, D-0079).
    compileOnly(libs.velocity.api)
    compileOnly(libs.viaversion.api)
    testImplementation(libs.velocity.api)
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("velocity-plugin.json") { expand("version" to version) }
}
