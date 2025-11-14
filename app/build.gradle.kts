plugins {
    id("com.android.application") version "8.3.2"
    kotlin("android") version "1.9.24"
}

android {
    namespace = "com.caio.vuzixhello"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.caio.vuzixhello"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {

    // Jetpack Compose (usa BOM para sincronizar versões)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation(libs.compose.ui.tooling)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit
    implementation(libs.mlkit.face)

    // AWS SDK
    implementation(libs.aws.core)
    implementation(libs.aws.rekognition)
}
