plugins {
    id("ogcloud.kotlin-library")
}

dependencies {
    api(project(":integrations:node-core"))
    // The PacketEvents plugin provides its API and Adventure at runtime (D-0137).
    compileOnly(libs.packetevents.api)
    compileOnly(libs.adventure.api)
    testImplementation(libs.packetevents.api)
    testImplementation(libs.adventure.api)
}
