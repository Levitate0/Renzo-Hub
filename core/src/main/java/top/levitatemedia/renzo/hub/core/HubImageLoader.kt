package top.levitatemedia.renzo.hub.core

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient

/**
 * The Hub's single Coil loader.
 *
 * Coil exposes one ImageLoader per process, so it cannot simply inherit either
 * half's auth. Instead every image request is signed by whichever half is
 * currently on screen, via [HubSession]. Renzo's poster URLs are mostly
 * unauthenticated and Shiori's always need a Bearer token, so signing the
 * active half is both sufficient and strictly more correct than the standalone
 * clients were.
 */
object HubImageLoader {
    fun build(context: PlatformContext): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                HubSession.signActive(builder)
                chain.proceed(builder.build())
            }
            .build()

        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .build()
    }
}
