plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

import java.util.Properties

android {
    namespace = "com.hpsdstudio.lisa"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.hpsdstudio.lisa"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0-m8spike"
        // Spotify client ID lives in git-ignored local.properties (never committed).
        val localProps = Properties()
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) {
            localFile.inputStream().use { localProps.load(it) }
        }
        val spotifyClientId = (localProps.getProperty("spotify.clientId") ?: "").trim()
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
        // Required by AppAuth's manifest (RedirectUriReceiverActivity placeholder).
        manifestPlaceholders["appAuthRedirectScheme"] = "com.hpsdstudio.lisa"
    }

    buildTypes {
        debug {
        }
        release {
            isMinifyEnabled = false
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
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.appauth)
    implementation(libs.androidx.datastore.preferences)
}
