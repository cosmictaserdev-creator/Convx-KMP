import org.gradle.api.tasks.Copy

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
val ffmpegPlatform = when {
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "windows-x86_64"
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) ->
        if (System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64")) "macosx-arm64" else "macosx-x86_64"
    else ->
        if (System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64")) "linux-arm64" else "linux-x86_64"
}
val javaFxVersion = libs.versions.javafx.get()

kotlin {
    jvmToolchain(21)
}

tasks.named<Copy>("processResources") {
    from(rootProject.file("shared/src/commonMain/composeResources/files/po_token.html"))
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":account-core"))
    implementation(project(":account-presentation"))
    implementation(project(":library-data"))
    implementation(project(":catalog-presentation"))
    implementation(project(":innertube"))
    implementation(project(":playback-core"))
    implementation(project(":playback-data"))
    implementation(project(":playback-presentation"))
    implementation(project(":design-system"))
    implementation(project(":hig"))
    implementation(libs.room.runtime)
    implementation(libs.koin.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jna)
    implementation(libs.jetpackSfSymbols)
    implementation("org.bytedeco:javacv:1.5.11")
    runtimeOnly("org.bytedeco:ffmpeg:7.1-1.5.11:$ffmpegPlatform")
    implementation("org.openjfx:javafx-base:$javaFxVersion:$javaFxPlatform")
    implementation("org.openjfx:javafx-graphics:$javaFxVersion:$javaFxPlatform")
    implementation("org.openjfx:javafx-controls:$javaFxVersion:$javaFxPlatform")
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
