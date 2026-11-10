plugins {
    id("ogcloud.kotlin-library")
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    // Uploads a deployment to the Central Portal; only publishAndReleaseToMavenCentral releases it (D-0195).
    publishToMavenCentral()
    // Central needs signatures and the release workflow brings the key; local publishing stays possible without one.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    // The release workflow names the version (D-0193); every other build stays at the development version.
    coordinates(
        group.toString(),
        "ogcloud-${project.name}",
        providers.gradleProperty("ogcloudVersion").getOrElse(DEVELOPMENT_VERSION),
    )
    pom {
        name = "ogcloud-${project.name}"
        description =
            provider { requireNotNull(project.description) { "${project.path} needs a description for its POM" } }
        url = REPOSITORY_URL
        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/license/mit"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = REPOSITORY_OWNER
                name = REPOSITORY_OWNER
                url = "https://github.com/$REPOSITORY_OWNER"
            }
        }
        scm {
            url = REPOSITORY_URL
            connection = "scm:git:$REPOSITORY_URL.git"
            developerConnection = "scm:git:ssh://git@github.com/$REPOSITORY_OWNER/ogcloud.git"
        }
    }
}
