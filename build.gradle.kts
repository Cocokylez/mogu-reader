// Top-level build file. Plugins are declared here with `apply false` and applied
// in the module-level build.gradle.kts. Versions come from gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
}
