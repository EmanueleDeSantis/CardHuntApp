import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.cardhunt.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cardhunt.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Load secrets from secrets.properties
        val secretsProperties = Properties()
        val secretsFile = rootProject.file("secrets.properties")
        if (secretsFile.exists()) {
            secretsFile.inputStream().use { secretsProperties.load(it) }
        }

        buildConfigField("String", "API_BASE_URL",
            "\"${secretsProperties.getProperty("API_BASE_URL", "PLACEHOLDER_URL")}\"")
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME",
            "\"${secretsProperties.getProperty("CLOUDINARY_CLOUD_NAME", "PLACEHOLDER_CLOUD")}\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET",
            "\"${secretsProperties.getProperty("CLOUDINARY_UPLOAD_PRESET", "PLACEHOLDER_PRESET")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    configurations.all {
        resolutionStrategy {
            // Force all modules of Fresco to use the 16 KB-aligned 3.6.0 version
            force("com.facebook.fresco:fresco:3.6.0")
            force("com.facebook.fresco:imagepipeline:3.6.0")
            force("com.facebook.fresco:nativefilters:3.6.0")
            force("com.facebook.fresco:nativeimagetranscoder:3.6.0")
            force("com.facebook.fresco:animated-webp:3.6.0")
            force("com.facebook.fresco:webpsupport:3.6.0")
        }
    }

}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.foundation.layout)
    implementation(libs.androidx.room3.common)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.geometry)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Map — OpenStreetMap via osmdroid
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Camera
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    implementation("com.cloudinary:cloudinary-android:2.6.1")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Concurrency
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("io.coil-kt:coil-compose:2.7.0") // remote images (photos, avatars)
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-analytics")

    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
}