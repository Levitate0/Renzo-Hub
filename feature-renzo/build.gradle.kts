import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * :feature-renzo as Kotlin Multiplatform (HANDOFF_renzo-hub_desktop-exe.md §2).
 * Same strategy as :feature-shiori: everything starts in androidMain
 * (identical to the old android-library), hoisted to commonMain as seams are
 * cut. The desktop target exists but is empty until the tv.material3 →
 * material3 mapping and the VideoPlayer seam (§6) land.
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
    packageOfResClass = "top.levitatemedia.renzo.tv.resources"
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
                implementation(compose.materialIconsExtended)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.coil.compose)
                implementation(compose.components.resources)
            }
        }
        // Both targets are JVM-family, so JVM-only libraries (OkHttp, Coil's
        // OkHttp fetcher, material3) are legal in a shared intermediate —
        // the same trick :feature-shiori uses for its whole UI.
        val jvmShared by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(compose.material3)
                implementation(libs.okhttp)
                implementation(libs.coil.network.okhttp)
            }
        }
        val androidMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation(libs.tv.material)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.lifecycle.runtime.compose)
                implementation(libs.androidx.core.ktx)

                // Renzo-only weight. Keeping the manga half out of a module that
                // pulls four media3 artifacts is the concrete reason this is a
                // feature module and not one flat :app.
                implementation(libs.media3.exoplayer)
                implementation(libs.media3.exoplayer.hls)
                implementation(libs.media3.ui)
                implementation(libs.media3.datasource.okhttp)
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
    // Deliberately the SAME package the standalone tv-native client used. With
    // android.nonTransitiveRClass this module keeps its own R, so all 32 source
    // files moved across without a single import or package edit.
    namespace = "top.levitatemedia.renzo.tv"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildTypes {
        /**
         * The fixture-backed screenshot build. Only this variant compiles
         * src/demo, so the real DemoMode and its placeholder art cannot reach a
         * shipping APK. :core and :feature-shiori have no `demo` of their own —
         * they fall back to debug.
         */
        create("demo") {
            initWith(getByName("debug"))
            matchingFallbacks += "debug"
        }
    }

    sourceSets {
        // Shipping builds get the no-op DemoMode from src/stub instead, so
        // every DemoMode call site folds away to nothing.
        getByName("debug") { java.srcDir("src/stub/java") }
        getByName("release") { java.srcDir("src/stub/java") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

