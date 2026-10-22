plugins {
    id("com.android.application") version "9.3.1"
}

android {
    namespace = "dev.reminderos.preview"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.reminderos.alpha"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0-alpha"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
