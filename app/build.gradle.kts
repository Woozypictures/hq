plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing key comes from GitHub Actions secrets (HQ_KEYSTORE_B64, HQ_KEYSTORE_PASS), never from the repo.
val ksPath: String? = System.getenv("HQ_KEYSTORE_PATH")
val ksPass: String = System.getenv("HQ_KEYSTORE_PASS") ?: ""

android {
    namespace = "uk.woozy.hq"
    compileSdk = 35
    defaultConfig {
        applicationId = "uk.woozy.hq"
        minSdk = 28
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.1"
    }
    signingConfigs {
        create("release") {
            storeFile = ksPath?.let { file(it) }
            storePassword = ksPass
            keyAlias = "hq"
            keyPassword = ksPass
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (ksPath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint { checkReleaseBuilds = false; abortOnError = false }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}
