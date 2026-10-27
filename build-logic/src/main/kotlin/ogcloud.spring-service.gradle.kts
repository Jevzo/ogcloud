plugins {
    id("ogcloud.spring-library")
    id("org.springframework.boot")
}

// `-PupdateSnapshots=true` lets golden-file tests rewrite their files after a reviewed change.
tasks.withType<Test>().configureEach {
    systemProperty("ogcloud.updateSnapshots", providers.gradleProperty("updateSnapshots").getOrElse("false"))
}
