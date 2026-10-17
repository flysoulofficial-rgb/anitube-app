package ua.anitube.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import ua.anitube.app.data.Store
import ua.anitube.app.net.Site

class AniTubeApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Site.init(this)
        Store.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        val client: OkHttpClient = Site.client.newBuilder().addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", Site.UA)
                    .header("Referer", Site.BASE + "/").build()
            )
        }.build()
        return ImageLoader.Builder(this).okHttpClient(client).crossfade(true).build()
    }
}
