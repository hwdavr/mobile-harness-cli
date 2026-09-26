plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.mobileharness.android"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":mobile-harness-annotations"))
    api("androidx.activity:activity-ktx:1.9.1")
    api("androidx.lifecycle:lifecycle-viewmodel:2.8.4")
    implementation("androidx.test:core:1.6.1")
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
