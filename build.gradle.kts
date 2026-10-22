// AGP 9 built-in Kotlin uses its bundled compiler unless a higher compatible
// KGP version is explicitly placed on the root buildscript classpath.
// The KMP library uses 2.4.20: the Android app must read the same metadata.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}
