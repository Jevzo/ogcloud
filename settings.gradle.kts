pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        // Plugin APIs come only from their publishers' repositories, each limited to its own groups (D-0124).
        exclusiveContent {
            forRepository { maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" } }
            filter {
                includeGroup("com.velocitypowered")
                includeGroup("io.papermc.paper")
                // paper-api's own dependencies, published there rather than on Maven Central.
                includeGroup("com.mojang")
                includeGroup("net.md-5")
            }
        }
        exclusiveContent {
            forRepository { maven("https://repo.viaversion.com/") { name = "viaversion" } }
            filter { includeGroup("com.viaversion") }
        }
        exclusiveContent {
            forRepository { maven("https://repo.codemc.io/repository/maven-releases/") { name = "codemc" } }
            filter { includeGroup("com.github.retrooper") }
        }
    }
}

rootProject.name = "ogcloud"

include(":contracts")
include(":control-plane:domain")
include(":control-plane:persistence")
include(":control-plane:livestate")
include(":control-plane:messaging")
include(":control-plane:storage")
include(":control-plane:service-support")
include(":control-plane:controller")
include(":control-plane:api")
include(":integrations:sdk")
include(":integrations:node-client")
include(":integrations:node-core")
include(":integrations:npc")
include(":integrations:velocity")
include(":integrations:paper")
