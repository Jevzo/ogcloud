plugins {
    id("com.diffplug.spotless")
}

spotless {
    val ktlintVersion = catalogVersion("ktlint")
    kotlin {
        target("src/**/*.kt")
        // Generated sources are checked by the drift check, not reformatted.
        targetExclude("src/generated/**")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(ktlintVersion)
    }
}
