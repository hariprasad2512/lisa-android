// AGP 9.x has built-in Kotlin: the kotlin-android plugin must NOT be applied.
// The KGP version AGP uses is chosen via the buildscript classpath below
// (AGP 9.4 ships KGP 2.2.10; we pin 2.4.20 to match the Compose compiler plugin).
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
