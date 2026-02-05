plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.reflekt"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.reflekt"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildFeatures {
        compose = true
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    /* -------------------- CORE -------------------- */
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")

    /* -------------------- COMPOSE -------------------- */
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    /* -------------------- LIFECYCLE -------------------- */
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    /* -------------------- CAMERA X -------------------- */
    val camerax_version = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")

    /* -------------------- MEDIAPIPE FACE LANDMARKING -------------------- */
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    /* -------------------- PERMISSIONS -------------------- */
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    /* -------------------- COROUTINES -------------------- */
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
