plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":mobile-harness-runtime"))
    // Putting a feature module on the CLI classpath is enough for ServiceLoader discovery.
    implementation(project(":sample-profile"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

application {
    mainClass.set("com.example.mobilecli.MainKt")
    applicationName = "mobile-cli"
}
