@file:Suppress("UnstableApiUsage")

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            val version = requested.version
            when (requested.id.id) {
                "com.android.application",
                "com.android.library",
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
        maven { setUrl("https://jitpack.io") }
        maven { setUrl("https://maven.aliyun.com/repository/public") }
    }
}

// F-Droid doesn't support foojay-resolver plugin
// plugins {
//     id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
// }

rootProject.name = "convx"
include(":app")
include(":canvas")
include(":innertube")
include(":lyrics")
include(":integrations")
include(":spotify")
include(":artwork")
include(":network-storage")
include(":spine")
include(":hig")
include(":lyrics-core", ":canvas-core", ":playback-core", ":history-core", ":library-core", ":search-core", ":design-system")
project(":lyrics-core").projectDir = file("shared-kmp/lyrics-core")
project(":canvas-core").projectDir = file("shared-kmp/canvas-core")
project(":playback-core").projectDir = file("shared-kmp/playback-core")
project(":history-core").projectDir = file("shared-kmp/history-core")
project(":library-core").projectDir = file("shared-kmp/library-core")
project(":search-core").projectDir = file("shared-kmp/search-core")
project(":design-system").projectDir = file("shared-kmp/design-system")

// Use a local copy of NewPipe Extractor by uncommenting the lines below.
// We assume, that convx and NewPipe Extractor have the same parent directory.
// If this is not the case, please change the path in includeBuild().
//
// For this to work you also need to change the implementation in innertube/build.gradle.kts
// to one which does not specify a version.
// From:
//      implementation(libs.newpipe.extractor)
// To:
//      implementation("com.github.teamnewpipe:NewPipeExtractor")
//includeBuild("../NewPipeExtractor") {
//    dependencySubstitution {
//        substitute(module("com.github.teamnewpipe:NewPipeExtractor")).using(project(":extractor"))
//    }
//}
