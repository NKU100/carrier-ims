import java.util.Properties

plugins {
    alias(libs.plugins.agp.app)
}

// Release signing is read from keystore.properties, then from the environment.
// Anything missing falls back to the debug key, so a fresh clone still builds an
// installable APK — check the signer before publishing one.
val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String, variable: String): String? =
    signing.getProperty(key) ?: System.getenv(variable)

val storePath = signingValue("storeFile", "CARRIER_IMS_KEYSTORE")
val storeSecret = signingValue("storePassword", "CARRIER_IMS_KEYSTORE_PASSWORD")
val keyName = signingValue("keyAlias", "CARRIER_IMS_KEY_ALIAS")
val keySecret = signingValue("keyPassword", "CARRIER_IMS_KEY_PASSWORD")
val hasReleaseKey = storePath != null && storeSecret != null && keyName != null &&
        keySecret != null && file(storePath).exists()

if (!hasReleaseKey) {
    logger.warn("carrier-ims: no release keystore configured, release builds use the debug key")
}

android {
    namespace = "io.github.nku100.carrierims"
    compileSdk = 37
    compileSdkMinor = 2
    buildToolsVersion = "37.0.0"

    enableKotlin = false

    defaultConfig {
        applicationId = "io.github.nku100.carrierims"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(storePath!!)
                storePassword = storeSecret
                keyAlias = keyName
                keyPassword = keySecret
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
            signingConfig = signingConfigs[if (hasReleaseKey) "release" else "debug"]
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    testImplementation(libs.junit)
}
