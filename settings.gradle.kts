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

rootProject.name = "renzo-hub"

include(":app")
include(":core")
include(":feature-renzo")
include(":feature-shiori")
// Desktop (.exe) entry point — see docs/HANDOFF_renzo-hub_desktop-exe.md.
include(":hub-desktop")
