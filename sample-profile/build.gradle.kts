plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":mobile-harness-annotations"))
    implementation(project(":mobile-harness-runtime"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    ksp(project(":mobile-harness-ksp"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
