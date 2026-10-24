pluginManagement {
    resolutionStrategy {
        eachPlugin {
            val version = requested.version
            when (requested.id.id) {
                "com.android.application",
                "com.android.kotlin.multiplatform.library" ->
                    useModule("com.android.tools.build:gradle:$version")

                "org.jetbrains.kotlin.jvm",
                "org.jetbrains.kotlin.multiplatform" ->
                    useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:$version")

                "org.jetbrains.kotlin.plugin.compose" ->
                    useModule("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$version")

                "org.jetbrains.compose" ->
                    useModule("org.jetbrains.compose:compose-gradle-plugin:$version")
            }
        }
    }

    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "convx-kmp-clients"
include(":shared", ":androidHost", ":desktopApp")
include(":lyrics-core", ":canvas-core", ":playback-core", ":history-core", ":library-core", ":search-core", ":catalog-core", ":design-system", ":hig")
include(":innertube")
project(":innertube").projectDir = file("../../shared-kmp/data/innertube")
project(":lyrics-core").projectDir = file("../../shared-kmp/domain/lyrics")
project(":canvas-core").projectDir = file("../../shared-kmp/domain/canvas")
project(":playback-core").projectDir = file("../../shared-kmp/domain/playback")
project(":history-core").projectDir = file("../../shared-kmp/domain/history")
project(":library-core").projectDir = file("../../shared-kmp/domain/library")
project(":search-core").projectDir = file("../../shared-kmp/domain/search")
project(":catalog-core").projectDir = file("../../shared-kmp/domain/catalog")
project(":design-system").projectDir = file("../../shared-kmp/presentation/design-system")
project(":hig").projectDir = file("../../hig")
