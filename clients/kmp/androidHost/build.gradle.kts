plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

android {
    namespace = "com.convx.music.client.androidhost"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.convx.music.client.androidhost"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":catalog-presentation"))
    implementation(project(":innertube"))
    implementation(project(":playback-core"))
    implementation(project(":account-core"))
    implementation(project(":account-presentation"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.media3.exoplayer)
    implementation(libs.koin.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
}
