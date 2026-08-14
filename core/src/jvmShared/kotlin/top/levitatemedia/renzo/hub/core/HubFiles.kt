package top.levitatemedia.renzo.hub.core

import java.io.File

/** Where the crash handler persists the last uncaught stack trace. */
expect fun hubCrashFile(): File
