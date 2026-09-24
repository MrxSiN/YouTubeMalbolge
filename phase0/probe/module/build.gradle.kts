plugins {
    id("com.android.application")
}

val probeGeneration = providers.gradleProperty("probeGeneration").get().toInt()
val probeHooks = providers.gradleProperty("probeHooks").get()
val probeFail = providers.gradleProperty("probeFail").getOrElse("")

android {
    namespace = "io.github.mrxsin.ytmprobe"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.mrxsin.ytmprobe"
        minSdk = 28
        targetSdk = 37
        // Each generation must be a new version so Vector sees a module update.
        versionCode = probeGeneration
        versionName = "phase0-g$probeGeneration"
        buildConfigField("int", "PROBE_GENERATION", "$probeGeneration")
        buildConfigField("String", "PROBE_HOOKS", "\"$probeHooks\"")
        buildConfigField("String", "PROBE_FAIL", "\"$probeFail\"")
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
}
