plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "dev.receiptscript.sample"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.receiptscript.sample"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = project.version.toString()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
dependencies {
    if (findProject(":core") != null) {
        implementation(project(":core"))
        implementation(project(":drivers:escpos"))
    } else {
        implementation(files(rootProject.file("../../lib/receiptscript-core-${project.version}.jar")))
        implementation(files(rootProject.file("../../lib/receiptscript-escpos-${project.version}.jar")))
    }
    implementation(project(":drivers:android"))
    implementation(project(":ui:compose"))
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
