import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing details live in an untracked key.properties. Without it the
// release build falls back to the debug key so the tree still builds anywhere.
val keyProps = Properties().apply {
    val f = rootProject.file("key.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "top.levitatemedia.renzo.hub"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // NOT app.renzoshiori.client. Renzo is the half going onto Google Play
        // and a Play listing is bound to its applicationId permanently, so the
        // Hub has to inherit Renzo's ID and Renzo's signing key. Shiori users
        // reinstall once — that side has only ever shipped via GitHub releases.
        applicationId = "top.levitatemedia.renzo"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // Above tv-native's 44 — the Hub upgrades those installs in place.
        versionCode = 49
        versionName = "1.4.4"
    }

    signingConfigs {
        if (keyProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keyProps.getProperty("keystoreFile"))
                storePassword = keyProps.getProperty("keystorePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        /**
         * EMULATOR-ONLY build used to shoot Play-listing images. Its own
         * applicationId means it installs alongside the real app.
         */
        create("demo") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            matchingFallbacks += "debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keyProps.isNotEmpty())
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
        }
        /**
         * SIDE-BY-SIDE release: identical to `release` but a different
         * applicationId (and launcher label, via src/beta/res), so a test
         * build installs NEXT TO the daily-driver install instead of
         * replacing it. Ship builds stay on `release`.
         */
        create("beta") {
            initWith(getByName("release"))
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            matchingFallbacks += "release"
            signingConfig = if (keyProps.isNotEmpty())
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":feature-renzo"))
    implementation(project(":feature-shiori"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
