import java.net.URI

plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The API origin is deployment configuration, never a credential or a driver identifier.
val backendBaseUrl = providers.gradleProperty("driveRuleApiBaseUrl")
    .orElse("http://13.124.175.42:8080/").get().trimEnd('/') + "/"
val backendUri = URI(backendBaseUrl)
require(backendUri.scheme in setOf("http", "https") && backendUri.host != null &&
    backendUri.rawUserInfo == null && backendUri.rawQuery == null &&
    backendUri.rawFragment == null && backendUri.rawPath == "/" &&
    (backendUri.port == -1 || backendUri.port in 1..65535)) {
    "driveRuleApiBaseUrl must be an HTTP(S) origin without credentials, query, or path"
}

android {
    namespace = "com.jochana.driverule"
    compileSdk = 34
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.jochana.driverule"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "API_BASE_URL", "\"$backendBaseUrl\"")
        buildConfigField("boolean", "SAMPLE_GUIDANCE", "false")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")

    buildTypes {
        debug {
            // Explicit opt-in UI/sample mode; release always reads actual platform state.
            buildConfigField("boolean", "SAMPLE_GUIDANCE",
                providers.gradleProperty("driveRuleSampleMode").orElse("false").get().toBooleanStrict().toString())
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp.mockwebserver)
    implementation(libs.pleos.speech.to.text)
    implementation(libs.pleos.text.to.speech)
    implementation(libs.pleos.navi.helper)
    implementation(libs.pleos.fused.location)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // Runtime implementation is the device's optional android.car shared library, never bundled.
    compileOnly(files(android.sdkDirectory.resolve("platforms/android-34/optional/android.car.jar")))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
