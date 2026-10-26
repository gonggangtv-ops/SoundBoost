plugins {
    id("com.android.application")
}

android {
    namespace = "com.soundboost.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.soundboost.app"
        minSdk = 26          // Android 8.0 ขึ้นไป
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
