plugins {
    id("ogcloud.mc-plugin")
}

dependencies {
    implementation(project(":integrations:bukkit"))
    // PandaSpigot provides its API at runtime; the PacketEvents plugin provides its API (D-0137).
    compileOnly(libs.pandaspigot.api)
    compileOnly(libs.packetevents.api)
    // No MockBukkit for 1.8 (D-0126): these rules are unit-tested with MockK, the rest by the smoke test.
    testImplementation(libs.pandaspigot.api)
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("plugin.yml") { expand("version" to version) }
}
