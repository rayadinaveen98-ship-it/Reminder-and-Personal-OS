import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.4.20"
    id("com.android.kotlin.multiplatform.library") version "9.3.1"
}

kotlin {
    android {
        namespace = "dev.reminderos.shared"
        compileSdk = 36
        minSdk = 26
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm()
    iosArm64 {
        binaries.framework {
            baseName = "ReminderCore"
            isStatic = true
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "ReminderCore"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            // Existing JVM-checked portable proof of concept; do not duplicate code.
            kotlin.srcDir("../spikes/core-model/src/commonMain/kotlin")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
