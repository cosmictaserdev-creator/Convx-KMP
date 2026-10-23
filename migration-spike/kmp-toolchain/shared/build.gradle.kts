plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
            dependencies {
                api(project(":lyrics-core"))
                api(project(":canvas-core"))
                api(project(":playback-core"))
                api(project(":history-core"))
                api(project(":design-tokens"))
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
