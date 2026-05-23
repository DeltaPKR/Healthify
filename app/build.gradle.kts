import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.devtools.ksp")
}

// Load release signing config from `keystore.properties` at repo root if present.
// File is gitignored. Missing file → release build falls back to debug signing
// so day-to-day builds keep working; CI / release machine must provide it.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
}
val hasReleaseSigning = keystoreProps.getProperty("storeFile")?.isNotBlank() == true

android {
    namespace   = "com.healthify.app"
    compileSdk  = 36        // Required by androidx.health.connect:connect-client 1.1.0+

    defaultConfig {
        applicationId = "com.DeltaPKR.Healthify"
        minSdk        = 26        // Android 8.0+; Health Connect needs 9+, enforced at runtime
        targetSdk     = 36        // Forward-compatible with Aug-2026 Play target-API requirement
        versionCode   = 9
        versionName   = "1.0.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile     = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias      = keystoreProps.getProperty("keyAlias")
                keyPassword   = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                // Fallback so devs without the keystore can still assemble release for testing.
                // Real Play Store uploads MUST run with keystore.properties present.
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    // App-bundle (AAB) split configuration. Each user only downloads the
    // language + density + ABI slice they need, which keeps install size
    // small on Play.
    bundle {
        language { enableSplit = true }
        density  { enableSplit = true }
        abi      { enableSplit = true }
    }

    // Strip BuildConfig + intermediate test artifacts from release output.
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    // ── Compose BOM ───────────────────────────────────────────────────────────
    // Pinned to a BOM compatible with the Compose compiler extension below.
    // Bumping requires Kotlin 1.9.25+ and compiler ext 1.5.15+; defer that
    // to a separate change so the API-35 upgrade stays bounded.
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ── Core AndroidX ─────────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ── Room ──────────────────────────────────────────────────────────────────
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ── WorkManager ───────────────────────────────────────────────────────────
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ── Health Connect ────────────────────────────────────────────────────────
    implementation("androidx.health.connect:connect-client:1.1.0")

    // ── Firebase ──────────────────────────────────────────────────────────────
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")

    // ── DataStore (user prefs) ────────────────────────────────────────────────
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ── Accompanist (permissions) ─────────────────────────────────────────────
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
