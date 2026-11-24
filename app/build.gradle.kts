plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

/**
 * AdMob IDs. Debug always uses Google's public test IDs. Release reads your real IDs from
 * ~/.gradle/gradle.properties (never commit them), e.g. ADMOB_BANNER_ID=ca-app-pub-xxx/yyy,
 * and falls back to test IDs with a build warning if one is missing.
 */
val admobTestIds = mapOf(
    "ADMOB_APP_ID" to "ca-app-pub-3940256099942544~3347511713",
    "ADMOB_BANNER_ID" to "ca-app-pub-3940256099942544/9214589741",
    "ADMOB_NATIVE_ID" to "ca-app-pub-3940256099942544/2247696110",
    "ADMOB_REWARDED_ID" to "ca-app-pub-3940256099942544/5224354917",
    "ADMOB_INTERSTITIAL_ID" to "ca-app-pub-3940256099942544/1033173712",
)

fun admobId(key: String, release: Boolean): String {
    if (!release) return admobTestIds.getValue(key)
    return providers.gradleProperty(key).orNull ?: admobTestIds.getValue(key).also {
        logger.warn("w: $key not set in gradle.properties; release build uses the AdMob TEST id")
    }
}

android {
    namespace = "com.oam.timecard"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.oam.timecard" // change once the app name is decided
        minSdk = 26                         // java.time without desugaring
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            admobTestIds.keys.filter { it != "ADMOB_APP_ID" }.forEach { buildConfigField("String", it, "\"${admobId(it, false)}\"") }
            manifestPlaceholders["admobAppId"] = admobId("ADMOB_APP_ID", false)
        }
        release {
            admobTestIds.keys.filter { it != "ADMOB_APP_ID" }.forEach { buildConfigField("String", it, "\"${admobId(it, true)}\"") }
            manifestPlaceholders["admobAppId"] = admobId("ADMOB_APP_ID", true)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Lets androidTest open the exported schema JSON for migration tests.
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")

    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.billing)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
