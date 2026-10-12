package com.tome.reader

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import com.tome.reader.scraper.ContentFilter
import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.tome.reader.di.AppContainer
import com.tome.reader.notifications.ReaderNotifications
import com.tome.reader.scraper.CoverImageLoader
import com.tome.reader.scraper.OnlineStreamCache
import com.tome.reader.updates.FollowUpdateWorker
import kotlin.concurrent.thread

/** Application entry point; owns the single [AppContainer]. */
class ReaderApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        ReaderNotifications.ensureChannels(this)
        container = AppContainer(this)
        thread(name = "tome-cache-prune", isDaemon = true) {
            OnlineStreamCache.prune(this)
        }
        // Periodically check followed series for new chapters (no-op if none followed).
        FollowUpdateWorker.schedulePeriodic(this)
        // Scrapers read the 18+ preference through ContentFilter, so mirror the
        // stored value into it and keep it current as the toggle changes.
        CoroutineScope(Dispatchers.IO).launch {
            container.settingsRepository.settings
                .map { it.showAdultContent }
                .distinctUntilChanged()
                .collect(ContentFilter::update)
        }
    }

    /** Coil picks this up automatically, so every AsyncImage in the app gets the
     *  Referer and User-Agent that source CDNs require. */
    override fun newImageLoader(): ImageLoader = CoverImageLoader.create(this)
}

/** Convenience accessor used by Compose screens to reach the container. */
val Context.appContainer: AppContainer
    get() = (applicationContext as ReaderApp).container
