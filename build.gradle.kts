plugins {
    kotlin("jvm") version "2.3.20" apply false
}
allprojects {
    group = "io.github.donnclab.receiptscript"
    version = "0.3.0"
}

tasks.register<Exec>("releaseBundle") {
    group = "distribution"
    description = "Builds, tests and verifies the versioned Kotlin JAR distribution. Requires npm --prefix tools ci."
    commandLine("node", "tools/scripts/build-kotlin-release.mjs")
}
