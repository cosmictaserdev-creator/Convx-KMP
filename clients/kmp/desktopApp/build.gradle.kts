plugins {
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
}

val javaFxPlatform = when {
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "win"
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) ->
        if (System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64")) "mac-aarch64" else "mac"
    else -> "linux"
}
val javaFxVersion = libs.versions.javafx.get()

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":account-core"))
    implementation(project(":account-presentation"))
    implementation(project(":catalog-presentation"))
    implementation(project(":innertube"))
    implementation(project(":playback-core"))
    implementation(project(":design-system"))
    implementation(project(":hig"))
    implementation(libs.koin.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.jna)
    implementation(libs.jetpackSfSymbols)
    implementation("org.openjfx:javafx-base:$javaFxVersion:$javaFxPlatform")
    implementation("org.openjfx:javafx-graphics:$javaFxVersion:$javaFxPlatform")
    implementation("org.openjfx:javafx-media:$javaFxVersion:$javaFxPlatform")
    implementation("org.openjfx:javafx-swing:$javaFxVersion:$javaFxPlatform")
    implementation("org.openjfx:javafx-web:$javaFxVersion:$javaFxPlatform")
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
            mainClass = "com.convx.music.client.desktop.MainKt"
    }
}
