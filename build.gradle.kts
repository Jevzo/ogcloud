plugins {
    id("ogcloud.spotless")
}

// build-logic cannot apply its own conventions, so the root formats it.
spotless {
    kotlin {
        target("build-logic/src/**/*.kt")
    }
    kotlinGradle {
        target("*.gradle.kts", "build-logic/*.gradle.kts", "build-logic/src/**/*.gradle.kts")
    }
}

tasks.named("check") {
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
}
