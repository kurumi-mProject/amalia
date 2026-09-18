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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack нужен ровно для одной зависимости — Silero VAD
        // (`com.github.gkonovalov.android-vad:silero`). Модель в этом
        // репозитории лежит как ONNX-файл внутри AAR, поэтому детектор речи
        // работает офлайн: ни сети, ни ключа, ни задержки на запрос.
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
}

rootProject.name = "Amalia"

include(":app")
