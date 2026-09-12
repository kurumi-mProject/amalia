import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Load keys from local.properties (local dev) or environment variables (CI)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
fun secret(name: String): String =
    System.getenv(name) ?: localProps.getProperty(name) ?: ""

android {
    namespace = "com.my.amali"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.my.amali"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"

        resourceConfigurations += listOf(
            "en", "ru", "es", "ar", "de", "fr", "hi", "ja", "zh"
        )

        // API keys injected at build time — never stored in source code
        buildConfigField("String", "DEEPGRAM_API_KEY",   "\"${secret("DEEPGRAM_API_KEY")}\"")
        buildConfigField("String", "GROQ_API_KEY",       "\"${secret("GROQ_API_KEY")}\"")
        buildConfigField("String", "FISH_AUDIO_API_KEY", "\"${secret("FISH_AUDIO_API_KEY")}\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi")
        freeCompilerArgs.add("-opt-in=androidx.compose.animation.ExperimentalAnimationApi")
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    // ===== Compose BOM =====
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)

    // ===== Core / Lifecycle =====
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")

    // ===== Compose UI =====
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core:1.6.8")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ===== Navigation =====
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // ===== DataStore (Preferences) =====
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ===== Coroutines + Serialization =====
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // ===== Permissions =====
    implementation("com.google.accompanist:accompanist-permissions:0.37.0")

    // ===== Window Size Class =====
    implementation("androidx.compose.material3:material3-window-size-class")

    // ===== Splash Screen =====
    implementation("androidx.core:core-splashscreen:1.0.1")

    // ===== Networking =====
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ===== Media3 (production-grade audio playback, used in YouTube/Google TV) =====
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-common:1.6.1")

    // ===== Desugaring =====
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}
