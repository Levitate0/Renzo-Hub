package top.levitatemedia.renzo.hub.core

import java.io.File

actual fun hubCrashFile(): File = File(hubConfigDir(), "last-crash.txt")
