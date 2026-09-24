plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.mrxsin.ytmprobe.target"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.mrxsin.ytmprobe.target"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "phase0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
