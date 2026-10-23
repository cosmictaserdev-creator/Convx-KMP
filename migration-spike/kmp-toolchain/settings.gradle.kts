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

rootProject.name = "convx-kmp-toolchain-proof"
include(":shared", ":androidHost", ":desktopApp")
include(":lyrics-core", ":canvas-core")
project(":lyrics-core").projectDir = file("../../shared-kmp/lyrics-core")
project(":canvas-core").projectDir = file("../../shared-kmp/canvas-core")
