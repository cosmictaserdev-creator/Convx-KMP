plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.convx.music.client.shared"
        compileSdk = 37
        minSdk = 26
    }

    val iosTargets = listOf(iosArm64(), iosSimulatorArm64())
    jvm("desktop")

    iosTargets.forEach { target ->
        target.compilations.getByName("main").cinterops.create("avPlayerCompat") {
            defFile(project.file("src/iosMain/nativeInterop/cinterop/av-player-compat.def"))
            compilerOpts("-I${project.file("src/iosMain/nativeInterop/cinterop").absolutePath}")
        }
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "ConvxShared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":catalog-presentation"))
                implementation(project(":innertube"))
                implementation(project(":playback-core"))
                implementation(libs.compose.runtime)
                implementation(libs.compose.ui)
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.kotlinx.coroutines.core)
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
