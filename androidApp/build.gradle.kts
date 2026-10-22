plugins {
    id("com.android.application") version "9.3.1"
}

android {
    namespace = "dev.reminderos.preview"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.reminderos.preview"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-preview"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
