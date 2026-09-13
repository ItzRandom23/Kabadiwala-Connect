plugins {
    // NOTE: AGP 9 has built-in Kotlin — do NOT apply org.jetbrains.kotlin.android
    // (it conflicts with the pre-registered 'kotlin' extension).
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.irinteractivestudios.kabadiwalaconnect"
    val testingApiBaseUrl = providers.gradleProperty("testingApiBaseUrl")
        // Do not ship a debug build pointed at a shared host by accident.
        // Supply -PtestingApiBaseUrl explicitly for emulator/device testing.
        .orElse("https://api.invalid/api/v1/")
        .get()
        .let { if (it.endsWith('/')) it else "$it/" }
    val productionApiBaseUrl = providers.gradleProperty("productionApiBaseUrl")
        .orElse("https://api.invalid/api/v1/")
        .get()
        .let { if (it.endsWith('/')) it else "$it/" }
    // compileSdk 37: required by androidx.lifecycle 2.11.0. Kept in step
    // with the newest installed SDK platform; minSdk stays low for
    // entry-level devices (see defaultConfig below).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.irinteractivestudios.kabadiwalaconnect"
        // minSdk 23 (Android 6.0): covers entry-level devices in the field
        // while supporting Room / DataStore / WorkManager / security-crypto.
        minSdk = 23
        targetSdk = 37
        versionCode = 25
        versionName = "0.0.24-beta"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$testingApiBaseUrl\"")
            buildConfigField("String", "APP_ENVIRONMENT", "\"TESTING\"")
            buildConfigField("String", "APP_UPDATE_MANIFEST_URL", "\"${testingApiBaseUrl.removeSuffix("api/v1/")}app/update.json\"")
            // Cleartext is limited to the debug/testing variant. Move this host
            // behind TLS before supplying it to a production build.
            manifestPlaceholders["apiUsesCleartext"] = testingApiBaseUrl.startsWith("http://").toString()
        }
        release {
            require(productionApiBaseUrl.startsWith("https://")) {
                "Production API URL must use HTTPS"
            }
            buildConfigField("String", "API_BASE_URL", "\"$productionApiBaseUrl\"")
            buildConfigField("String", "APP_ENVIRONMENT", "\"PRODUCTION\"")
            buildConfigField("String", "APP_UPDATE_MANIFEST_URL", "\"${productionApiBaseUrl.removeSuffix("api/v1/")}app/update.json\"")
            manifestPlaceholders["apiUsesCleartext"] = "false"
            optimization {
                enable = false
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // No kotlinOptions block: with AGP built-in Kotlin, jvmTarget defaults
    // to compileOptions.targetCompatibility (Java 11) automatically.
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // LocaleManager switches language in-app, so every bundled locale must remain available.
    bundle {
        language {
            enableSplit = false
        }
    }
}

// Room KSP code generation in Kotlin.
ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    // Compose + Material 3 UI.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation (bottom tabs: Home | Prices | Recyclers | Earnings | Settings).
    implementation(libs.androidx.navigation.compose)

    // Local database (Room) + reactive streams.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Settings / locale persistence (offline-first).
    implementation(libs.androidx.datastore.preferences)

    // WorkManager synchronization for offline collector actions.
    implementation(libs.androidx.work.runtime.ktx)

    // Retrofit/OkHttp API integration layer.
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") { isTransitive = false }

    // Coroutines.
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Encrypted local credentials via Android Keystore.
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
