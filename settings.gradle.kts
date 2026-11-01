pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
dependencyResolutionManagement { repositories { mavenCentral(); google() } }
rootProject.name = "receiptscript"
include(":core")
include(":drivers:escpos")

// Optional Android output and Compose UI are built through examples/compose-app; this build stays JVM-only.
