plugins {
    id("com.android.application") version "9.3.1"
}

// No secrets are committed. Signed alpha builds are opt-in with all four values
// injected by an authorized GitHub Actions environment / local shell.
val signingStoreFile = System.getenv("RPOS_SIGNING_STORE_FILE")
val signingStorePassword = System.getenv("RPOS_SIGNING_STORE_PASSWORD")
val signingKeyAlias = System.getenv("RPOS_SIGNING_KEY_ALIAS")
val signingKeyPassword = System.getenv("RPOS_SIGNING_KEY_PASSWORD")
val signingFields = listOf(signingStoreFile, signingStorePassword, signingKeyAlias, signingKeyPassword)
val signingConfigured = signingFields.all { !it.isNullOrBlank() }
if (signingFields.any { !it.isNullOrBlank() } && !signingConfigured) {
    throw GradleException("Incomplete alpha signing environment. All signing fields must be set.")
}

android {
    namespace = "dev.reminderos.preview"
    compileSdk = 36

    defaultConfig {
        // Separate V0.5b test package protects prior alpha data. Future signed alpha builds
        // must KEEP this package ID, signing key and increasing versionCode.
        applicationId = "dev.reminderos.alpha.v05b"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "0.5.0b-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    if (signingConfigured) {
        signingConfigs {
            create("privateAlpha") {
                storeFile = file(requireNotNull(signingStoreFile))
                storePassword = requireNotNull(signingStorePassword)
                keyAlias = requireNotNull(signingKeyAlias)
                keyPassword = requireNotNull(signingKeyPassword)
            }
        }
    }
    buildTypes {
        getByName("release") {
            if (signingConfigured) {
                signingConfig = signingConfigs.getByName("privateAlpha")
            }
            isMinifyEnabled = false
        }
    }
    // The alpha's standalone Gradle build compiles the EXACT common Kotlin
    // source files used by :shared. No copied/rewritten recurrence logic.
    // Replace this source bridge with a binary KMP module dependency when the
    // Android build and root multiplatform Gradle builds are unified.
    sourceSets.getByName("main").java.srcDirs(
        "../shared/src/commonMain/kotlin",
        "../spikes/core-model/src/commonMain/kotlin"
    )
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
