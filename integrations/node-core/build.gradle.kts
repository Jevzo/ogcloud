plugins {
    id("ogcloud.kotlin-library")
}

dependencies {
    api(project(":integrations:sdk"))
    api(project(":integrations:node-client"))
    // In-game texts are tag texts (D-0200); the servers provide Adventure, which the plugins render with.
    api(project(":contracts:text"))
    testImplementation(libs.adventure.api)
    testImplementation(libs.grpc.inprocess)
}
