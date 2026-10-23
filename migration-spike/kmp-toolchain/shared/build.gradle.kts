plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val syncPortableProductionSources = tasks.register<org.gradle.api.tasks.Sync>("syncPortableProductionSources") {
    from(fileTree("../../../lyrics/src/main/kotlin") {
        include("com/convx/music/lyrics/LyricsSyncLevel.kt")
        include("com/convx/music/lyrics/LyricsEntry.kt")
        include("com/convx/music/lyrics/LyricTimeline.kt")
        include("com/convx/music/lyrics/TimedLyricsParser.kt")
        include("com/convx/music/lyrics/bitchord/LrcParser.kt")
        include("com/convx/music/lyrics/bitchord/LyricLine.kt")
        include("com/convx/music/lyrics/bitchord/LyricGaps.kt")
        include("com/convx/music/lyrics/bitchord/SyllableRuns.kt")
    })
    from(fileTree("../../../app/src/main/kotlin") {
        include("com/convx/music/playback/ShuffleOrderPolicy.kt")
        include("com/convx/music/playback/PlaybackSnapshot.kt")
        include("com/convx/music/playback/PlaybackController.kt")
    })
    from(fileTree("../../../canvas/src/main/kotlin") {
        include("com/convx/music/canvas/ApplePlaylistLink.kt")
        include("com/convx/music/canvas/AppleMusicPlaylistMapper.kt")
        include("com/convx/music/canvas/CanvasArtwork.kt")
        include("com/convx/music/canvas/AppleArtwork.kt")
        include("com/convx/music/canvas/AppleArtist.kt")
        include("com/convx/music/canvas/AppleMusicDataMapper.kt")
    })
    into(layout.buildDirectory.dir("generated/portableCommonMain"))
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
            kotlin.srcDir(syncPortableProductionSources)
            dependencies {
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
