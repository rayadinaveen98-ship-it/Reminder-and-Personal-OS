plugins {
    id("com.android.application") version "9.3.1"
}

android {
    namespace = "dev.reminderos.preview"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.reminderos.alpha.v03b"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.0b-alpha"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Only calendar logic is unit-tested here. Device delivery still requires QA.
dependencies {
    testImplementation("junit:junit:4.13.2")
}
