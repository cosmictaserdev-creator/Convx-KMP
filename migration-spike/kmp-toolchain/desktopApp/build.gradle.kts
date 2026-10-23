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
    implementation(libs.koin.compose)
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.convx.migration.desktop.MainKt"
    }
}
