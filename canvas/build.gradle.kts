plugins {
    alias(libs.plugins.kotlin.serialization)
    id("kotlin")
}

kotlin {
    jvmToolchain(21)
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
    source(fileTree("../shared-kmp/canvas-core/src/commonMain/kotlin") {
        include("**/*.kt")
    })
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation(libs.ktor.client.encoding)
    testImplementation(libs.junit)
}
