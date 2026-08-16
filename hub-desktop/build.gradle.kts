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

/*
 * The only OS-specific piece of a Compose Desktop app is the Skiko native
 * runtime. jpackage cannot cross-build, but an uber JAR can: build with
 * -PhubDesktopOs=windows on this Linux host and the jar carries the Windows
 * natives — the NSIS pipeline (tools/build-windows-exe.sh) wraps it with a
 * jlink'd Windows JRE.
 */
val hubDesktopOs = (findProperty("hubDesktopOs") as String?) ?: "current"

dependencies {
    implementation(project(":core"))
    implementation(project(":feature-shiori"))
    implementation(project(":feature-renzo"))
    implementation(
        when (hubDesktopOs) {
            "windows" -> compose.desktop.windows_x64
            "linux" -> compose.desktop.linux_x64
            "mac" -> compose.desktop.macos_arm64
            else -> compose.desktop.currentOs
        },
    )
    implementation(compose.material3)
    // For painterResource(DrawableResource) — the picker tiles pull each
    // half's wordmark straight from the feature modules' compose resources.
    implementation(compose.components.resources)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    // Windows taskbar identity: SetCurrentProcessExplicitAppUserModelID, so
    // the javaw-hosted window groups with (and pins as) the app's shortcut.
    implementation("net.java.dev.jna:jna:5.14.0")
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
