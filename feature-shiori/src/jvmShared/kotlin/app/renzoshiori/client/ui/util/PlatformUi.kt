package app.renzoshiori.client.ui.util

import androidx.compose.runtime.Composable

/*
 * The platform-UI seams (HANDOFF_renzo-hub_desktop-exe.md §1 table): every
 * place a screen used to reach for an Intent, an ActivityResult contract or a
 * SAF picker goes through one of these instead. Android keeps its exact old
 * behaviour behind the actuals; the desktop answers with AWT/Swing choosers.
 */

/** System back (gesture/remote). Desktop maps it to nothing by default — screens keep their explicit back affordances. */
@Composable
expect fun HubBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** Fire-and-forget notice. Android: Toast; desktop: stderr (screens prefer snackbars). */
expect fun hubToast(message: String)

/** Pick an image (avatar upload): returns launch(), delivers bytes + mime type, nulls on cancel. */
@Composable
expect fun rememberImagePicker(onPicked: (bytes: ByteArray?, mimeType: String?) -> Unit): () -> Unit

/** Pick any file to read (backup import, extension APK): bytes + display name, nulls on cancel. */
@Composable
expect fun rememberFileOpenPicker(onPicked: (bytes: ByteArray?, name: String?) -> Unit): () -> Unit

/**
 * Pick the offline download folder and POINT THE STORE AT IT (SAF tree on
 * Android, plain directory on desktop). Delivers the new folder label, or null
 * when cancelled.
 */
@Composable
expect fun rememberOfflineFolderPicker(onPicked: (label: String?) -> Unit): () -> Unit
