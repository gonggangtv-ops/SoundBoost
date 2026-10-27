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
        versionCode = 6
        versionName = "1.5"
    }

    // ใช้กุญแจเดียวกันทุกครั้ง ทำให้ติดตั้งเวอร์ชันใหม่ทับของเดิมได้
    signingConfigs {
        getByName("debug") {
            storeFile = file("soundboost.jks")
            storePassword = "soundboost"
            keyAlias = "soundboost"
            keyPassword = "soundboost"
        }
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
