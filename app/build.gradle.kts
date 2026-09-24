plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.mrxsin.ytmalbolge"
    compileSdk = 37

    val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    val keystoreAlias = System.getenv("ANDROID_KEYSTORE_ALIAS")
    val keystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    val keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
    val releaseSigning = if (
        !keystorePath.isNullOrBlank() && !keystoreAlias.isNullOrBlank() &&
        !keystorePassword.isNullOrBlank() && !keyPassword.isNullOrBlank() &&
        file(keystorePath).isFile
    ) {
        signingConfigs.create("release") {
            storeFile = file(keystorePath)
            storePassword = keystorePassword
            keyAlias = keystoreAlias
            this.keyPassword = keyPassword
        }
    } else {
        null
    }

    defaultConfig {
        applicationId = "io.github.mrxsin.ytmalbolge"
        minSdk = 32
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            releaseSigning?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    // Manager-process access to RemotePreferences (contracts/configuration.md).
    implementation("io.github.libxposed:service:102.0.0")
    implementation(files("../build/generated/module.jar"))
}

// Pinned ASM for the Malbolge backend; the build script checks its digest.
val malbolgeAsm = configurations.create("malbolgeAsm") { isTransitive = false }
dependencies { malbolgeAsm("org.ow2.asm:asm:9.9.1") }

val deviceTest = providers.gradleProperty("deviceTest").map(String::toBoolean).orElse(false)

val generateMalbolgeModule = tasks.register<Exec>("generateMalbolgeModule") {
    workingDir = rootProject.projectDir
    val command = mutableListOf("python", "toolchain/build_development.py")
    if (deviceTest.get()) command += "--device-test"
    commandLine(command)
    inputs.property("deviceTest", deviceTest)
    inputs.files(malbolgeAsm)
    inputs.files(fileTree("../source") { include("**/*.mal") })
    inputs.files(fileTree("../toolchain/backend") { include("**/*.java") })
    inputs.file("src/main/res/drawable-nodpi/ytm_hellfire.png")
    inputs.files(
        "../toolchain/mbx_eval.py", "../toolchain/mbx_frame.py",
        "../toolchain/validate_units.py", "../toolchain/build_development.py",
        "../toolchain/LOCKFILE",
        "../target/current/target-release.lock.yml", "../target/current/verified-binding-set.json"
    )
    outputs.file("../build/generated/module.jar")
    doFirst {
        environment("MALBOLGE_ASM_JAR", malbolgeAsm.singleFile.absolutePath)
        if (deviceTest.get() && gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }) {
            error("deviceTest is only valid for a development APK")
        }
    }
}

tasks.named("preBuild") { dependsOn(generateMalbolgeModule) }
