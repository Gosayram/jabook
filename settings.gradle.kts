pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "jabook"
include(":app")
include(":core-net")
include(":core-endpoints")
include(":core-auth")
include(":core-parse")
include(":core-torrent")
include(":core-stream")
include(":core-player")
include(":core-logging")