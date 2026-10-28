plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ir.maghz.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "ir.maghz.app"
        minSdk = 26
        targetSdk = 34
        val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = build
        versionName = "1.0.$build"
    }

    // Every build is signed with the same key so a new APK installs over the old one
    // and keeps reading progress and bookmarks.
    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = System.getenv("MAGHZ_KEYSTORE_PASSWORD") ?: "maghz-key-2026"
            keyAlias = "maghz"
            keyPassword = System.getenv("MAGHZ_KEYSTORE_PASSWORD") ?: "maghz-key-2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    androidResources {
        // keep the .json and .ttf assets uncompressed-friendly; nothing to exclude
        noCompress += listOf("ttf")
    }
}
