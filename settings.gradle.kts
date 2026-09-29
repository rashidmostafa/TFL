pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "TFL"

include(":app")

include(":core:common")
include(":core:model")
include(":core:designsystem")
include(":core:crypto")
include(":core:database")
include(":core:transport")
include(":core:testing")

include(":feature:onboarding")
include(":feature:chats")
include(":feature:contacts")
include(":feature:map")
include(":feature:vault")
include(":feature:tools")
include(":feature:settings")
