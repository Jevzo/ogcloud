plugins {
    id("com.diffplug.spotless")
}

spotless {
    val ktlintVersion = catalogVersion("ktlint")
    kotlin {
        target("src/**/*.kt")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(ktlintVersion)
    }
}
