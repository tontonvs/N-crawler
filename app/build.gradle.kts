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
        // 1.5.11 supports Kotlin 1.9.23 — do not change
        kotlinCompilerExtensionVersion = "1.5.11"
    }
}

dependencies {
    // ── CHANGE: BOM bumped 2023.10.01 → 2024.09.00 ───────────────────────
    // Advantage: unlocks HorizontalDivider (M3 1.2+), Icons.AutoMirrored.*
    //   (icons 1.7.x), LinearProgressIndicator lambda form (M3 1.2+)
    // Disadvantage: larger download on first sync; 2024.09 still targets
    //   compileSdk 34 so no manifest changes needed
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    // Compose core (versions managed by BOM)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Activity + ViewModel
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // ── CHANGE: lifecycle-runtime-compose added ────────────────────────────
    // Provides collectAsStateWithLifecycle() used in every screen
    // Advantage: lifecycle-aware state collection — pauses collection when
    //   app is backgrounded, saving battery
    // Disadvantage: minor: ties state collection to lifecycle; any flow that
    //   should keep emitting in background must use .collectAsState() instead
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.2")

    // Coil — image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Room — local DB
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // OkHttp + Jsoup — scraping
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ── CHANGE: WorkManager added ──────────────────────────────────────────
    // NCrawlerApp implements Configuration.Provider, NovelRepository and
    // ChapterDownloadWorker all import from androidx.work.*
    // Advantage: background chapter downloads survive app backgrounding
    // Disadvantage: +~500 KB AAR; WorkManager initialises a foreground
    //   service which adds a notification permission requirement on API 33+
    //   (already minSdk 30 so this is fine; no extra manifest changes needed)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Palette — dynamic cover-art colour extraction
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Material Components — Theme.Material3.* XML styles for AAPT
    implementation("com.google.android.material:material:1.12.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
