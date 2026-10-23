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
        // Side-by-side package protects V0.3d data. Future signed alpha builds
        // must KEEP this package ID, signing key and increasing versionCode.
        applicationId = "dev.reminderos.alpha.v03e"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.3.0e-alpha"
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
