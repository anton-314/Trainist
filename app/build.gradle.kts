import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// The app's version is a running build counter — the number of commits on the checked-out
// history — so every commit on any branch produces its own, always-increasing version.
// `prepare-commit-msg` (scripts/git-hooks/) stamps each commit with the version it will produce.
fun gitCommitCount(): Int = try {
    val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    process.waitFor()
    process.inputStream.bufferedReader().readText().trim().toIntOrNull() ?: 1
} catch (e: Exception) {
    1
}

android {
    namespace = "dev.antonlammers.trainist"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "dev.antonlammers.trainist"
        minSdk = 24
        targetSdk = 36
        val buildNumber = gitCommitCount()
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing: read from a local, gitignored `keystore.properties` (dev machines) with a
    // fallback to environment variables (CI, where the upload keystore is injected from secrets).
    val keystoreProperties = Properties().apply {
        val propsFile = rootProject.file("keystore.properties")
        if (propsFile.exists()) {
            propsFile.inputStream().use { load(it) }
        }
    }
    fun signingProp(key: String, envVar: String): String? =
        keystoreProperties.getProperty(key) ?: System.getenv(envVar)

    signingConfigs {
        create("release") {
            signingProp("storeFile", "RELEASE_KEYSTORE_PATH")?.let { storeFile = file(it) }
            storePassword = signingProp("storePassword", "RELEASE_KEYSTORE_PASSWORD")
            keyAlias = signingProp("keyAlias", "RELEASE_KEY_ALIAS")
            keyPassword = signingProp("keyPassword", "RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Export Room's schema JSON per version so migrations can be validated against the real schema.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.text.google.fonts)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Glance (App Widgets)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // WorkManager (daily meal reminder)
    implementation(libs.androidx.work.runtime)

    // CameraX + ML Kit Barcode
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode)

    // Retrofit + Moshi
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
