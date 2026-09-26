pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "mobile-harness-ksp-starter"

include(
    ":mobile-harness-annotations",
    ":mobile-harness-runtime",
    ":mobile-harness-ksp",
    ":mobile-harness-android",
    ":sample-profile",
    ":sample-cli",
)
