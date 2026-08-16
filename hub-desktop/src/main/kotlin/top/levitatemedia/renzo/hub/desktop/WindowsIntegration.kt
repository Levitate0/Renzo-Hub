package top.levitatemedia.renzo.hub.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.WString

/**
 * Windows taskbar identity. The running app is javaw.exe, so without an
 * explicit AppUserModelID the taskbar groups the window under Java and
 * "Pin to taskbar" pins a bare javaw that can't relaunch the app. Setting
 * the SAME id here and on the installer's Start Menu/Desktop shortcuts
 * (windows/set-aumid.ps1) makes Windows treat window and shortcut as one
 * app: correct icon, correct grouping, and pinning relaunches via the
 * shortcut.
 */
object WindowsIntegration {
    /** Must match AUMID in renzohub-installer.nsi / set-aumid.ps1. */
    private const val APP_USER_MODEL_ID = "LevitateMedia.RenzoHub"

    fun installAppUserModelId() {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("win")) return
        runCatching {
            Native.load("shell32", Shell32::class.java)
                .SetCurrentProcessExplicitAppUserModelID(WString(APP_USER_MODEL_ID))
        }
    }

    @Suppress("FunctionName")
    private interface Shell32 : Library {
        fun SetCurrentProcessExplicitAppUserModelID(appId: WString): Int
    }
}
