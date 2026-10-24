plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidLibrary {
        namespace = "com.music.innertube"
        compileSdk = 36
        minSdk = 26
    }

    iosArm64()
    iosSimulatorArm64()
    jvm("desktop")

    val jvmMain by creating {
        dependsOn(sourceSets.commonMain.get())
        dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.newpipeextractor)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":catalog-core"))
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.encoding)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }

        androidMain {
            dependsOn(jvmMain)
        }

        desktopMain {
            dependsOn(jvmMain)
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        desktopTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
        }
    }
}
