plugins {
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":playback-core"))
    implementation(libs.koin.compose)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.convx.migration.desktop.MainKt"
    }
}
