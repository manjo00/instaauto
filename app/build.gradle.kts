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

    // Static analysis. `abortOnError` makes a lint Error fail the build rather than
    // printing a report nobody reads. Warnings stay warnings for now — we fix them
    // as we touch files instead of blocking on a wall of pre-existing ones.
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
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
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.ui.tooling)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── Unit tests (app/src/test) — pure JVM, no device, run in seconds ──
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // ── Instrumented tests (app/src/androidTest) — need an emulator/device ──
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
