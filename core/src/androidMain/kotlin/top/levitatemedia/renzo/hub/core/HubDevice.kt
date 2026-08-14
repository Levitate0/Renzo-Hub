package top.levitatemedia.renzo.hub.core

import android.content.Context
import android.content.pm.PackageManager

/**
 * Is this a leanback device? Both halves need to know, and the shell does too
 * — the picker has to pad for overscan and size its art for a 10-foot read.
 */
fun isTvDevice(context: Context): Boolean {
    val pm = context.packageManager
    return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        pm.hasSystemFeature("android.hardware.type.television")
}

/**
 * Does the device have a usable internet connection right now?
 *
 * Deliberately checks VALIDATED, not merely connected: a captive-portal wifi
 * or a network that dropped upstream reports a connection that carries no
 * traffic, and treating that as online is how you get a spinner instead of an
 * offline library.
 */
fun hasInternet(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        ?: return true // Can't tell — assume online rather than falsely going offline.
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
