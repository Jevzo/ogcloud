pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "ogcloud"

include(":contracts")
include(":control-plane:domain")
include(":control-plane:persistence")
include(":control-plane:livestate")
include(":control-plane:messaging")
