plugins {
    // NOTE: AGP 9 has built-in Kotlin — do NOT apply org.jetbrains.kotlin.android
    // (it conflicts with the pre-registered 'kotlin' extension).
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Firebase configuration is deployment-owned. Local builds remain useful
// without google-services.json; configured builds apply the official plugin.
if (file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.irinteractivestudios.kabadiwalaconnect"
    val testingApiBaseUrl = providers.gradleProperty("testingApiBaseUrl")
        // No remote host is implicit. Supply -PtestingApiBaseUrl for a local
        // emulator/device backend or an explicitly approved staging host.
        // The invalid fallback also activates the existing debug-only local
        // repository boundary without enabling cleartext traffic.
        .orElse("https://api.invalid/api/v1/")
        .get()
        .let { if (it.endsWith('/')) it else "$it/" }
    // A release build must be pointed at an explicitly provisioned production
    // API. Keeping an invalid fallback here makes it too easy to distribute a
    // signed APK that starts successfully but can never reach the backend.
    val configuredProductionApiBaseUrl = providers.gradleProperty("productionApiBaseUrl")
        .orNull
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { if (it.endsWith('/')) it else "$it/" }
    // Gradle configures every build type even when only a debug task is run.
    // Validate the production endpoint when a release-capable task is
    // requested, without blocking local unit tests and IDE sync.
    val releaseBuildRequested = gradle.startParameter.taskNames.any { taskName ->
        val task = taskName.substringAfterLast(':').lowercase()
        task == "build" || task == "assemble" || task == "bundle" || task.contains("release")
    }
    val productionVariantRequested = gradle.startParameter.taskNames.any { taskName ->
        taskName.substringAfterLast(':').contains("production", ignoreCase = true)
    }
    if (productionVariantRequested) {
        requireNotNull(configuredProductionApiBaseUrl) {
            "Missing -PproductionApiBaseUrl. Production variants must target an explicitly configured HTTPS API."
        }
    }
    if (releaseBuildRequested) {
        val releaseApiBaseUrl = requireNotNull(configuredProductionApiBaseUrl) {
            "Missing -PproductionApiBaseUrl. Release builds must target an explicitly configured HTTPS production API."
        }
        require(releaseApiBaseUrl.startsWith("https://")) {
            "productionApiBaseUrl must use HTTPS"
        }
        val allowPlaceholderProductionApiUrl = providers.gradleProperty("allowPlaceholderProductionApiUrl").orNull?.toBooleanStrictOrNull() == true
        if (!allowPlaceholderProductionApiUrl) require(
            !releaseApiBaseUrl.contains(".invalid", ignoreCase = true)
                && !releaseApiBaseUrl.contains("example.com", ignoreCase = true)
                && !releaseApiBaseUrl.contains("example.org", ignoreCase = true)
                && !releaseApiBaseUrl.contains("localhost", ignoreCase = true)
                && !releaseApiBaseUrl.contains("127.0.0.1")
        ) {
            "productionApiBaseUrl must be a real HTTPS host, not a placeholder/example/local host"
        }
    }
    val productionSigningStoreFile = providers.gradleProperty("productionSigningStoreFile").orNull?.trim()?.takeIf { it.isNotBlank() }
    val productionSigningStorePassword = providers.gradleProperty("productionSigningStorePassword").orNull?.takeIf { it.isNotBlank() }
    val productionSigningKeyAlias = providers.gradleProperty("productionSigningKeyAlias").orNull?.trim()?.takeIf { it.isNotBlank() }
    val productionSigningKeyPassword = providers.gradleProperty("productionSigningKeyPassword").orNull?.takeIf { it.isNotBlank() }
    val productionSigningConfigured = listOf(
        productionSigningStoreFile,
        productionSigningStorePassword,
        productionSigningKeyAlias,
        productionSigningKeyPassword
    ).all { it != null }
    val productionSigningRequired = providers.gradleProperty("requireProductionSigning").orNull?.toBooleanStrictOrNull() ?: releaseBuildRequested
    if (releaseBuildRequested && productionSigningRequired) {
        require(productionSigningConfigured) {
            "Production signing is required. Supply productionSigningStoreFile, productionSigningStorePassword, productionSigningKeyAlias, and productionSigningKeyPassword through CI secrets or -P properties."
        }
    }
    val productionSigning = if (productionSigningConfigured) {
        signingConfigs.create("production") {
            storeFile = file(productionSigningStoreFile!!)
            storePassword = productionSigningStorePassword
            keyAlias = productionSigningKeyAlias
            keyPassword = productionSigningKeyPassword
        }
    } else {
        null
    }
    // compileSdk 37: required by androidx.lifecycle 2.11.0. Kept in step
    // with the newest installed SDK platform; minSdk stays low for
    // entry-level devices (see defaultConfig below).
    compileSdk = 37

    flavorDimensions += "environment"

    productFlavors {
        // AGP reserves flavor names beginning with "test" for the test
        // component, so the testing environment is intentionally named
        // envTesting (APP_ENVIRONMENT remains TESTING).
        create("envTesting") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", "\"$testingApiBaseUrl\"")
            buildConfigField("String", "APP_ENVIRONMENT", "\"TESTING\"")
            buildConfigField("String", "APP_UPDATE_MANIFEST_URL", "\"${testingApiBaseUrl.removeSuffix("api/v1/")}app/update.json\"")
            manifestPlaceholders["apiUsesCleartext"] = testingApiBaseUrl.startsWith("http://").toString()
        }
        create("production") {
            dimension = "environment"
            val productionUrl = configuredProductionApiBaseUrl.orEmpty()
            buildConfigField("String", "API_BASE_URL", "\"$productionUrl\"")
            buildConfigField("String", "APP_ENVIRONMENT", "\"PRODUCTION\"")
            buildConfigField("String", "APP_UPDATE_MANIFEST_URL", "\"${productionUrl.removeSuffix("api/v1/")}app/update.json\"")
            manifestPlaceholders["apiUsesCleartext"] = "false"
        }
    }

    defaultConfig {
        applicationId = "com.irinteractivestudios.kabadiwalaconnect"
        // minSdk 23 (Android 6.0): covers entry-level devices in the field
        // while supporting Room / DataStore / WorkManager / security-crypto.
        minSdk = 23
        targetSdk = 37
        versionCode = 40
        versionName = "0.0.39-beta"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Keep the debug/testing variant debuggable and unminified so
            // instrumentation tests do not invoke the separate test APK R8
            // shrinker. Production release remains optimized below.
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            signingConfig = productionSigning
            isMinifyEnabled = true
            isShrinkResources = true
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
    implementation("androidx.exifinterface:exifinterface:1.3.7")
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

    // FCM push token/message support. The BoM keeps Firebase modules compatible.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

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
