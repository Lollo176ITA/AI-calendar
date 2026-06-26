// Root build file — declares plugins for subprojects (applied in module build files).
plugins {
    // AGP 9 compiles Kotlin natively (built-in Kotlin) — no kotlin-android plugin.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
