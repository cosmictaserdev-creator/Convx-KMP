plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val syncPortablePlaybackSources = tasks.register<org.gradle.api.tasks.Sync>("syncPortablePlaybackSources") {
    from(fileTree("../../../app/src/main/kotlin") {
        include("com/convx/music/playback/ShuffleOrderPolicy.kt")
        include("com/convx/music/playback/PlaybackSnapshot.kt")
        include("com/convx/music/playback/PlaybackController.kt")
    })
    into(layout.buildDirectory.dir("generated/portablePlaybackMain"))
}

kotlin {
    androidLibrary {
        namespace = "com.convx.migration.shared"
        compileSdk = 37
        minSdk = 26
    }

    iosArm64()
    iosSimulatorArm64()
    jvm("desktop")

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "ConvxShared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(syncPortablePlaybackSources)
            dependencies {
                api(project(":lyrics-core"))
                api(project(":canvas-core"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                api(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
