import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.rajatxo.micup"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rajatxo.micup"
        minSdk = 21
        targetSdk = 35
        versionCode = 20800
        versionName = "2.0.8"
        vectorDrawables { useSupportLibrary = true }

        // Agora App ID — baked into the APK via BuildConfig so the
        // app can read it at runtime without a config file.
        buildConfigField("String", "AGORA_APP_ID", "\"2ebcfb44568b429ab383394e75b6f1cc\"")

        // arm64-v8a ONLY — this is the 64-bit ARM ABI used by every phone
        // from 2017+ (including Realme 9 5G SE, OnePlus, Samsung, Xiaomi,
        // etc.). Including other ABIs (armeabi-v7a, x86, x86_64) would
        // double the APK size because Agora's native libs are ~12MB per
        // ABI. Dropping to arm64-only cuts the universal APK from ~78MB
        // to ~40MB — small enough to send over WhatsApp/Telegram.
        //
        // If you need 32-bit support for very old phones, add "armeabi-v7a"
        // to the list below (APK grows by ~12MB).
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // R8 disabled temporarily — the obfuscated Agora stack trace
            // (s1.b.a, s1.n.a, etc.) suggests R8 might be stripping or
            // renaming something Agora needs internally. Re-enable once
            // voice init is confirmed working.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    lint {
        disable += "NullSafeMutableLiveData"
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Agora voice SDK — the engine that powers voice.
    // Replaces WebRTC entirely. Same SDK Free Fire uses.
    implementation(libs.agora.voice.sdk)
}
