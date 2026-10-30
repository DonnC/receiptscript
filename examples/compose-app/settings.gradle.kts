pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    plugins { kotlin("jvm") version "2.3.20" }
}
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "ReceiptScriptStarter"
include(":app")
// Source checkouts use project modules; the binary ZIP uses its bundled release JARs.
if (file("../../kotlin/build.gradle.kts").isFile) {
    include(":kotlin", ":drivers:escpos")
    project(":kotlin").projectDir = file("../../kotlin")
    project(":drivers:escpos").projectDir = file("../../drivers/escpos")
}
