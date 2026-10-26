plugins {
    id("ogcloud.kotlin-library")
    `maven-publish`
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name = project.name
                url = REPOSITORY_URL
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/license/mit"
                    }
                }
                scm {
                    url = REPOSITORY_URL
                }
            }
        }
    }
}
