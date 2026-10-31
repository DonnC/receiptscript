plugins {
    id("com.android.library")
    kotlin("android")
}
android {
    namespace = "zw.co.donnclab.rct.android"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    testOptions.unitTests.all {
        it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
dependencies {
    // Keep the AAR identical whether built from a checkout or the binary starter bundle.
    // Hosts provide the core JAR; it must never be embedded a second time in this adapter.
    val engine: Any = if (findProject(":core") != null) project(":core")
        else files(rootProject.file("../../lib/receiptscript-core-0.3.0.jar"))
    compileOnly(engine)
    testImplementation(engine)
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
