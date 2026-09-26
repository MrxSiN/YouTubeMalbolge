plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.mrxsin.ytmalbolge.bindinglab"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.mrxsin.ytmalbolge.bindinglab"
        minSdk = 32
        targetSdk = 37
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("org.luckypray:dexkit:2.3.0")
}
