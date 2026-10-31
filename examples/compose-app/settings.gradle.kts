pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    plugins { kotlin("jvm") version "2.3.20" }
}
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "ReceiptScriptStarter"
include(":app")
// Source checkouts use project modules; the binary ZIP uses its bundled release JARs.
if (file("../../core/build.gradle.kts").isFile) {
    include(":core", ":drivers:escpos")
    project(":core").projectDir = file("../../core")
    project(":drivers:escpos").projectDir = file("../../drivers/escpos")
}
include(":drivers:android")
project(":drivers:android").projectDir = file("../../drivers/android")
