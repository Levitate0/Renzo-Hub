import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * :core is Kotlin Multiplatform (HANDOFF_renzo-hub_desktop-exe.md §2): the
 * same module feeds the Android app and the hub-desktop exe.
 *
 * Source sets:
 *   commonMain  — pure Kotlin/Compose: session models, offline models/bus,
 *                 manifest migration, TvFit, the GeistFamily `expect`.
 *   jvmShared   — JVM-but-not-Android (OkHttp, Coil-OkHttp): HubSession,
 *                 TvPairing, HubImageLoader. Both targets are JVM-family, so
 *                 this compiles into each without a metadata split.
 *   androidMain — SAF offline store, foreground download service, device
 *                 checks, R.font-backed fonts.
 *   desktopMain — classpath-backed fonts; grows as desktop `actual`s land.
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.serialization)
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
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.coroutines.core)
            }
        }
        val jvmShared by creating {
            dependsOn(commonMain)
            dependencies {
                api(libs.okhttp)
                api(libs.coil.compose)
                api(libs.coil.network.okhttp)
            }
        }
        val androidMain by getting {
            dependsOn(jvmShared)
            dependencies {
                api(libs.androidx.core.ktx)
                api(libs.androidx.security.crypto)
                // SAF folder access for the shared offline store.
                api(libs.androidx.documentfile)
            }
        }
        val desktopMain by getting {
            dependsOn(jvmShared)
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.junit)
            }
        }
    }
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
    buildFeatures { compose = true }
}
