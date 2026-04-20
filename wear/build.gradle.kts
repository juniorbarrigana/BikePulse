import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Forma mais robusta de ler o local.properties no Gradle
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }

        val clientId = localProperties.getProperty("STRAVA_CLIENT_ID") ?: "223698"
        val clientSecret = localProperties.getProperty("STRAVA_CLIENT_SECRET") ?: "1d38da81705fdf65cf76cba33f6a0779bcabb1e6"
        val bikeName = localProperties.getProperty("BIKE_MODEL_NAME") ?: "My Bicycle"

        buildConfigField("String", "STRAVA_CLIENT_ID", "\"$clientId\"")
        buildConfigField("String", "STRAVA_CLIENT_SECRET", "\"$clientSecret\"")
        buildConfigField("String", "BIKE_MODEL_NAME", "\"$bikeName\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.play.services.wearable)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.compose.ui.tooling)
    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.wear.remote.interactions)
    implementation("androidx.wear:wear-phone-interactions:1.1.0-alpha03")
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.androidx.wear.watchface)
    implementation(libs.androidx.wear.watchface.complications.data)
    implementation(libs.androidx.wear.watchface.complications.rendering)
    implementation(libs.androidx.wear.watchface.data)
    implementation(libs.androidx.wear.watchface.style)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}