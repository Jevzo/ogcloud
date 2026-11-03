plugins {
    id("ogcloud.mc-plugin")
}

dependencies {
    implementation(project(":integrations:node-core"))
    // Paper provides its API, Adventure and Gson at runtime.
    compileOnly(libs.paper.api)
    testImplementation(libs.paper.api)
    testImplementation(libs.mockbukkit)
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("plugin.yml") { expand("version" to version) }
}
