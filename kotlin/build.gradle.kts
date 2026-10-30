plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
    signing
}
kotlin {
    jvmToolchain(17)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
    withJavadocJar()
}
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.withType<Jar>().configureEach {
    archiveBaseName.set("receiptscript-core")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.jar { from(project.file("../LICENSE")) { into("META-INF") } }
kotlin.sourceSets.named("test") { kotlin.srcDir("../examples/kotlin") }
tasks.test { testLogging { events("failed", "skipped") } }
publishing {
    publications {
        create<MavenPublication>("receiptScript") {
            from(components["java"])
            artifactId = "receiptscript-core"
            pom {
                name.set("ReceiptScript")
                description.set("Vendor-neutral receipt template engine for Kotlin and Android")
                url.set("https://github.com/DonnC/receiptscript")
                licenses { license { name.set("MIT"); url.set("https://opensource.org/license/mit") } }
                developers { developer { id.set("donnclab"); name.set("Donnclab") } }
                scm { url.set("https://github.com/DonnC/receiptscript"); connection.set("scm:git:https://github.com/DonnC/receiptscript.git") }
            }
        }
    }
}
signing { isRequired = false; sign(publishing.publications) }
