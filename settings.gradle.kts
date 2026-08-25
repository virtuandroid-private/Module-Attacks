pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        maven {
            url = uri("https://jitpack.io")
        }
        google()
        mavenCentral()
        maven { url = uri("https://artifactory.appodeal.com/appodeal") }
    }
}

include(":fingerprint-spoofer")
include(":file-spoofer")
include(":network-interceptor")
include(":guest-attacker")
rootProject.name = "attacks"