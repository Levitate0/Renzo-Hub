package top.levitatemedia.renzo.hub.core

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient

/**
 * The Hub's single Coil loader.
 *
 * Coil exposes one ImageLoader per process, so it cannot simply inherit either
 * half's auth. Instead every image request bound for one of the user's servers
 * is signed by that server's half, via [HubSession.interceptor]; images from
 * anywhere else (AniList's poster CDN, a source's thumbnail host) go out
 * without credentials.
 */
object HubImageLoader {
    fun build(context: PlatformContext): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor(HubSession.interceptor)
            // A redirect off the server must not carry the signature along
            // (and Coil per-request headers can add a Cookie of their own).
            .addNetworkInterceptor(credentialGuard { HubSession.targetFor(it) != null })
            .build()

        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .build()
    }
}
