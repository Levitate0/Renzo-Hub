package top.levitatemedia.renzo.tv.demo

import android.app.Activity
import android.content.Context
import android.content.Intent
import top.levitatemedia.renzo.tv.AppServices

/**
 * No-op DemoMode — compiled into the DEBUG and RELEASE builds.
 *
 * The real one lives in `src/demo/java` and only exists in the `demo` build
 * type, so the shipping app carries no placeholder art and no intent back
 * doors: every call site below folds away to nothing.
 */
object DemoMode {
    const val ENABLED = false

    /** Never forced off-device: real builds detect leanback for themselves. */
    val forceTv: Boolean get() = false
    val overlayDrawer: Boolean get() = false
    val overlayAccount: Boolean get() = false

    /** null = show the real video surface. */
    val videoCoverAsset: String? get() = null

    fun install(ctx: Context) {}
    fun readIntent(intent: Intent?) {}
    fun applyNav(app: AppServices) {}
    fun applyWindow(activity: Activity) {}
}
