package com.tome.reader.scraper

import android.content.Context
import coil.ImageLoader
import okhttp3.OkHttpClient

/**
 * The Coil loader used for every cover and page image in the app.
 *
 * Several sources serve their art from a CDN that rejects requests carrying no
 * Referer from the site itself. WEBTOON is why this exists: its listings parsed
 * correctly and every title showed up in Browse, but each cover came back blank
 * and fell through to the initials placeholder, because Coil's default client
 * sends its own User-Agent and no Referer, and pstatic.net answers that with a
 * 403. Routing images through one OkHttp client that mirrors what the scrapers
 * already send fixes it everywhere at once.
 */
object CoverImageLoader {

    // The host -> Referer map lives in SourceReferers so the page downloader and
    // this loader cannot drift apart.

    fun create(context: Context): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val builder = request.newBuilder()
                    .header("User-Agent", HttpClient.USER_AGENT)
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                if (request.header("Referer") == null) {
                    val host = request.url.host
                    // Fall back to the image's own origin: harmless for hosts that
                    // do not check, and correct for the ones that only reject a
                    // missing header.
                    val referer = SourceReferers.forHost(host) ?: "${request.url.scheme}://$host/"
                    builder.header("Referer", referer)
                }
                chain.proceed(builder.build())
            }
            .build()

        return ImageLoader.Builder(context)
            .okHttpClient(client)
            .crossfade(true)
            .build()
    }
}
