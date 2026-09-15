plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.noven.ncrawler"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.noven.ncrawler"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
    }

    composeOptions {
        // CHANGE: 1.5.3 → 1.5.11
        // Compose compiler 1.5.3 requires Kotlin 1.9.10 exactly.
        // Kotlin 1.9.23 is installed on this machine, so we need 1.5.11
        // which is the compiler extension version that targets Kotlin 1.9.23.
        // Advantage : build compiles without suppressKotlinVersionCompatibilityCheck hack.
        // Disadvantage : none — 1.5.11 is a stable patch release in the 1.5.x line.
        kotlinCompilerExtensionVersion = "1.5.11"
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(composeBom)

    // Compose core
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Activity + ViewModel
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.4")

    // Coil — image loading
    implementation("io.coil-kt:coil-compose:2.4.0")

    // Room — local DB
    implementation("androidx.room:room-runtime:2.6.0")
    implementation("androidx.room:room-ktx:2.6.0")
    annotationProcessor("androidx.room:room-compiler:2.6.0")

    // OkHttp + Jsoup — scraping
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("org.jsoup:jsoup:1.16.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Palette — dynamic cover-art colour extraction
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Material Components — provides Theme.Material3.* XML styles for AAPT
    implementation("com.google.android.material:material:1.11.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
