plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}
kotlin {
    jvmToolchain(17)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
    sourceSets.named("test") { kotlin.srcDir("../../examples/kotlin-drivers") }
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
}
dependencies {
    api(project(":kotlin"))
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
}
tasks.withType<Jar>().configureEach {
    archiveBaseName.set("receiptscript-escpos")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.jar { from(project.file("../../LICENSE")) { into("META-INF") } }
publishing {
    publications {
        create<MavenPublication>("escpos") {
            from(components["java"])
            artifactId = "receiptscript-escpos"
            pom {
                name.set("ReceiptScript ESC/POS")
                description.set("Optional portable and desktop thermal printer drivers")
                url.set("https://github.com/DonnC/receiptscript")
                licenses { license { name.set("MIT"); url.set("https://opensource.org/license/mit") } }
            }
        }
    }
}
