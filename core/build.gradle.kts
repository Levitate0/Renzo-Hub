plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "top.levitatemedia.renzo.hub.core"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
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
    implementation(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)

    api(libs.androidx.core.ktx)
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
    api(libs.androidx.security.crypto)

    api(libs.coil.compose)
    api(libs.coil.network.okhttp)

    // SAF folder access for the shared offline store.
    api(libs.androidx.documentfile)
    // NotificationCompat for the downloader's foreground notification.
    api(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
