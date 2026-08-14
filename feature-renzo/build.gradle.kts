plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures { compose = true }
}

dependencies {
    api(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.tv.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // Renzo-only weight. Keeping the manga half out of a module that pulls four
    // media3 artifacts is the concrete reason this is a feature module and not
    // one flat :app.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)
}
