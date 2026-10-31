pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
dependencyResolutionManagement { repositories { mavenCentral(); google() } }
rootProject.name = "receiptscript"
include(":core")
include(":drivers:escpos")

// The optional Android adapter is built through examples/compose-app, keeping this build JVM-only.
