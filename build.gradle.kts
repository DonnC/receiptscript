plugins {
    id("com.android.library") version "8.13.2"
    id("com.android.application") version "8.13.2" apply false
    kotlin("android") version "2.3.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
    `maven-publish`
}
allprojects {
    group = "io.github.donnclab"
    version = rootProject.file("VERSION").readText().trim()
}

// One Compose BOM for the library and the example app, so the example runs what consumers get.
val composeBom by extra("2025.11.01")
android {
    namespace = "io.github.donnclab.rct"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/receiptNotices"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions.unitTests.isIncludeAndroidResources = true
    testOptions.unitTests.all {
        it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    }
    publishing { singleVariant("release") { withSourcesJar() } }
}
kotlin {
    // Every public declaration must be marked public on purpose; everything else stays internal.
    explicitApi()
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
}
val prepareNotices by tasks.registering(Copy::class) {
    from("LICENSE") { rename { "ReceiptScript-MIT.txt" } }
    from("THIRD_PARTY_NOTICES.md")
    into(layout.buildDirectory.dir("generated/receiptNotices/licenses"))
}
tasks.named("preBuild") { dependsOn(prepareNotices) }
tasks.withType<Jar>().configureEach {
    from("LICENSE") { into("META-INF") }
}
dependencies {
    api(platform("androidx.compose:compose-bom:$composeBom"))
    api("androidx.compose.foundation:foundation")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
tasks.withType<Zip>().configureEach {
    if (name == "bundleReleaseAar") archiveFileName.set("receiptscript-${project.version}.aar")
}
tasks.register<Exec>("jvmJar") {
    group = "distribution"
    description = "Builds and verifies the single JVM JAR (engine and ESC/POS)."
    commandLine("node", "tools/scripts/build-kotlin-release.mjs")
}
publishing {
    publications {
        create<MavenPublication>("receiptScript") {
            artifactId = "receiptscript"
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("ReceiptScript")
                description.set("Thermal receipt templates for Android POS apps: render, preview, export and print on any printer SDK")
                url.set("https://github.com/DonnC/receiptscript")
                licenses { license { name.set("MIT"); url.set("https://opensource.org/license/mit") } }
                developers { developer { id.set("DonnC"); name.set("DonnC"); url.set("https://github.com/DonnC") } }
                scm {
                    url.set("https://github.com/DonnC/receiptscript")
                    connection.set("scm:git:https://github.com/DonnC/receiptscript.git")
                    developerConnection.set("scm:git:ssh://git@github.com/DonnC/receiptscript.git")
                }
            }
        }
    }
}
