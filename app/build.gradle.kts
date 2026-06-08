import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Load secrets.properties (git-ignored). Falls back to empty strings so the project
// still builds before credentials exist. See secrets.properties.example.
val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String, default: String = ""): String =
    (secrets.getProperty(key) ?: default)

android {
    namespace = "com.autoinsta"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autoinsta"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Secrets surfaced to code via BuildConfig (never hardcode in source).
        buildConfigField("String", "META_APP_ID", "\"${secret("META_APP_ID")}\"")
        buildConfigField("String", "META_APP_SECRET", "\"${secret("META_APP_SECRET")}\"")
        buildConfigField("String", "META_GRAPH_VERSION", "\"${secret("META_GRAPH_VERSION", "v21.0")}\"")
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${secret("CLOUDINARY_CLOUD_NAME")}\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"${secret("CLOUDINARY_UPLOAD_PRESET")}\"")
        manifestPlaceholders["oauthRedirectScheme"] = secret("OAUTH_REDIRECT_SCHEME", "autoinsta")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    debugImplementation(libs.androidx.ui.tooling)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}
