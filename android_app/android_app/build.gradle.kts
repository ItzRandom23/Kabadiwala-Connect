// Top-level build file where you can add configuration options common to all sub-projects/modules.
// NOTE: org.jetbrains.kotlin.android is intentionally absent — AGP 9 provides
// built-in Kotlin support (see https://developer.android.com/build/migrate-to-built-in-kotlin).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
