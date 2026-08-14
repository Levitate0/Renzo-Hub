import org.jetbrains.compose.desktop.application.dsl.TargetFormat

/*
 * hub-desktop — the Windows/desktop entry point (HANDOFF_renzo-hub_desktop-exe.md).
 * A plain JVM module: it consumes :core's `desktop` target and, as the
 * feature modules gain desktop source sets, will host the same Compose
 * screens the APK ships. This module holds ONLY the window, wiring and
 * packaging — screens live in the feature modules (§2: do not fork screens).
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.core)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

compose.desktop {
    application {
        mainClass = "top.levitatemedia.renzo.hub.desktop.MainKt"
        nativeDistributions {
            // jpackage cannot cross-build; Windows artifacts come from the
            // uber-jar + the NSIS/osslsigncode pipeline (handoff §7). These
            // formats are for building ON the matching OS.
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Renzo Hub"
            packageVersion = "1.0.0"
            vendor = "Levitate Media"
        }
    }
}
