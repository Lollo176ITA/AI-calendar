import java.util.Properties

plugins {
    // AGP 9 built-in Kotlin: no kotlin-android plugin. Compose compiler plugin still required.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Dev-only OpenRouter key from local.properties (gitignored) → BuildConfig for debug testing.
// Production uses the per-user PKCE flow, not this key.
val openRouterDevKey: String = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("openrouter.devKey", "")

android {
    namespace = "com.lorenzo.aicalendar"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lorenzo.aicalendar"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "OPENROUTER_DEV_KEY", "\"$openRouterDevKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Kotlin jvmTarget inherits from compileOptions.targetCompatibility (17) under AGP 9 built-in Kotlin.
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Dependency injection (Hilt) — processor via KSP (kapt is incompatible with AGP 9 built-in Kotlin)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    compileOnly(libs.errorprone.annotations)

    // Coroutines / Flow
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services) // Task.await() for ML Kit

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Calendar month/week grid
    implementation(libs.kizitonwose.calendar.compose)

    // On-device AI: ML Kit Entity Extraction (date/time, offline, Italian)
    implementation(libs.mlkit.entity.extraction)

    // Cloud AI: OpenRouter via Ktor + kotlinx.serialization
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.kotlinx.serialization.json)

    // Persistence (Room) — processor via KSP
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Unit tests (JVM)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests (emulator)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
