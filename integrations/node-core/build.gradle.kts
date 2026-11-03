plugins {
    id("ogcloud.kotlin-library")
}

dependencies {
    api(project(":integrations:sdk"))
    api(project(":integrations:node-client"))
}
