import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * :feature-shiori as Kotlin Multiplatform (HANDOFF_renzo-hub_desktop-exe.md
 * §2, step 3). Conversion strategy: ALL existing sources sit in androidMain —
 * behaviourally identical to the old android-library — and files are hoisted
 * to commonMain/jvmShared as their platform seams are cut, per the handoff's
 * 25-file coupling table. Android must stay green at every hoist.
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.serialization)
}

compose.resources {
    publicResClass = true
    // Keeps the generated accessor out of the default `<module>.generated` spot
    // and inside the app package the screens already import from.
    packageOfResClass = "app.renzoshiori.client.resources"
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":core"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.coil.compose)
                implementation(compose.components.resources)
                implementation(libs.jb.lifecycle.viewmodel.compose)
                implementation(libs.jb.lifecycle.runtime.compose)
                implementation(libs.jb.navigation.compose)
            }
        }
        // JVM-family shared (Retrofit/OkHttp API layer as it hoists).
        val jvmShared by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.retrofit)
                implementation(libs.retrofit.serialization)
                implementation(libs.okhttp)
                implementation(libs.okhttp.logging)
                implementation(libs.coil.network.okhttp)
            }
        }
        val androidMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation(libs.androidx.core.ktx)
                // SAF folder picking for offline chapter downloads.
                implementation(libs.androidx.documentfile)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.lifecycle.runtime.ktx)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.navigation.compose)
                implementation(libs.androidx.security.crypto)
            }
        }
        val desktopMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}

android {
    // Same package the standalone Shiori client used, for the same reason as
    // :feature-renzo — all 99 source files moved with no package edits.
    namespace = "app.renzoshiori.client"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        buildConfigField("boolean", "PLACEHOLDER_ART", "false")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
