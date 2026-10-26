import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("ogcloud.kotlin-library")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.springframework.boot")
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
}
