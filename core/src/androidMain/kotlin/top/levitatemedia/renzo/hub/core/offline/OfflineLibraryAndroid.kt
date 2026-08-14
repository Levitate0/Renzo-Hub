package top.levitatemedia.renzo.hub.core.offline

import android.net.Uri

/**
 * Android-only: a content Uri for an offline asset (media3 wants Uris, not
 * byte arrays). Lives outside [OfflineLibrary] because Uri does not exist on
 * the desktop JVM.
 */
fun OfflineLibrary.uriOf(asset: OfflineAsset): Uri? =
    (store as? OfflineStore)?.uriFor(asset.relPath)
