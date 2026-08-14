package top.levitatemedia.renzo.hub.core

import java.io.File

actual fun hubCrashFile(): File = File(HubContextHolder.context.filesDir, "last-crash.txt")
