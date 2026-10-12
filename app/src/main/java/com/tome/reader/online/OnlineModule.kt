package com.tome.reader.online

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tome.reader.appContainer
import com.tome.reader.domain.model.AppSettings
import com.tome.reader.scraper.ScrapedSeries
import com.tome.reader.scraper.Scraper
import com.tome.reader.scraper.SeriesCache
import com.tome.reader.scraper.ui.DiagnosticsScreen
import com.tome.reader.scraper.ui.DownloadsScreen
import com.tome.reader.scraper.ui.DownloadsViewModel
import com.tome.reader.scraper.ui.ScraperReadScreen
import com.tome.reader.scraper.ui.ScraperSeriesScreen
import com.tome.reader.scraper.ui.ScraperViewModel
import com.tome.reader.scraper.ui.SourceBrowserScreen
import com.tome.reader.scraper.ui.SourceSearchScreen
import com.tome.reader.ui.navigation.Routes
import kotlinx.coroutines.launch

object OnlineModule {
    const val available: Boolean = true

    private fun fallbackSeries(scraper: Scraper, seriesUrl: String): ScrapedSeries =
        ScrapedSeries(
            title = seriesUrl.substringAfterLast('/').ifBlank { seriesUrl },
            author = null,
            coverUrl = null,
            url = seriesUrl,
            category = scraper.category,
        )

    private fun sameUrl(a: String?, b: String?): Boolean =
        a?.trim()?.trimEnd('/')?.lowercase() == b?.trim()?.trimEnd('/')?.lowercase()

    @Composable
    private fun activityScraperVm(): ScraperViewModel {
        val context = LocalContext.current
        val activity = context as ComponentActivity
        val container = context.applicationContext.appContainer
        return viewModel(
            viewModelStoreOwner = activity,
            factory = ScraperViewModel.Factory(
                application = context.applicationContext as Application,
                registry = container.scraperRegistry,
                repository = container.scraperRepository,
                downloader = container.scraperDownloader,
                onlineHistoryRepository = container.onlineHistoryRepository,
            ),
        )
    }

    /**
     * Browse rendered as a bottom-nav tab (no back arrow). Taps push into the same
     * source/search/diagnostics/downloads routes registered by [register].
     */
    @Composable
    fun BrowseTab(navController: NavHostController) {
        val vm = activityScraperVm()
        SourceBrowserScreen(
            vm = vm,
            onBack = null,
            onOpenSource = { sourceId, series ->
                SeriesCache.put(sourceId, series)
                navController.navigate(Routes.scraperSeries(sourceId, series.url))
            },
            onOpenSourceBrowse = { sourceId ->
                navController.navigate(Routes.scraperSourceSearch(sourceId))
            },
            onOpenInBrowser = { url -> navController.navigate(Routes.browser(url)) },
            onDiagnostics = { navController.navigate(Routes.SCRAPER_DIAGNOSTICS) },
            onDownloads = { navController.navigate(Routes.SCRAPER_DOWNLOADS) },
        )
    }

    fun register(builder: NavGraphBuilder, navController: NavHostController) {
        builder.composable(
            route = Routes.BROWSER_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_URL) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val url = entry.arguments?.getString(Routes.ARG_URL)
            BrowserScreen(
                startUrl = url ?: DEFAULT_BROWSER_URL,
                onBack = { navController.popBackStack() },
                onScraperOpened = { navController.navigate(Routes.SCRAPER_BROWSE) },
            )
        }
        builder.composable(Routes.SCRAPER_BROWSE) {
            val vm = activityScraperVm()
            SourceBrowserScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onOpenSource = { sourceId, series ->
                    SeriesCache.put(sourceId, series)
                    navController.navigate(Routes.scraperSeries(sourceId, series.url))
                },
                onOpenSourceBrowse = { sourceId ->
                    navController.navigate(Routes.scraperSourceSearch(sourceId))
                },
                onOpenInBrowser = { url -> navController.navigate(Routes.browser(url)) },
                onDiagnostics = {
                    navController.navigate(Routes.SCRAPER_DIAGNOSTICS)
                },
                onDownloads = {
                    navController.navigate(Routes.SCRAPER_DOWNLOADS)
                },
            )
        }
        builder.composable(Routes.SCRAPER_DIAGNOSTICS) {
            val ctx = LocalContext.current
            val reg = ctx.applicationContext.appContainer.scraperRegistry
            DiagnosticsScreen(
                registry = reg,
                onBack = { navController.popBackStack() },
            )
        }
        builder.composable(Routes.SCRAPER_DOWNLOADS) {
            val ctx = LocalContext.current
            val container = ctx.applicationContext.appContainer
            val vm: DownloadsViewModel = viewModel(factory = DownloadsViewModel.factory(container))
            DownloadsScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onOpenSeries = { seriesId ->
                    navController.navigate(Routes.series(seriesId))
                },
            )
        }

        builder.composable(
            route = Routes.SCRAPER_SOURCE_SEARCH_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_SCRAPER_ID) { type = NavType.StringType },
            ),
        ) {
            val scraperId = it.arguments?.getString(Routes.ARG_SCRAPER_ID).orEmpty()
            val vm = activityScraperVm()
            val ctx = LocalContext.current
            val reg = ctx.applicationContext.appContainer.scraperRegistry
            val scraper = reg.get(scraperId)
            if (scraper?.isBrowserOnly == true) {
                BrowserScreen(
                    startUrl = scraper.baseUrl,
                    onBack = { navController.popBackStack() },
                    onScraperOpened = { navController.navigate(Routes.SCRAPER_BROWSE) },
                )
            } else if (scraper != null) {
                SourceSearchScreen(
                    vm = vm,
                    scraper = scraper,
                    onBack = { navController.popBackStack() },
                    onOpenSeries = { s, series ->
                        val extUrl = series.externalUrl
                        if (extUrl != null) {
                            navController.navigate(Routes.browser(extUrl))
                        } else {
                            SeriesCache.put(s.id, series)
                            navController.navigate(Routes.scraperSeries(s.id, series.url))
                        }
                    },
                )
            }
        }
        builder.composable(
            route = Routes.SCRAPER_SERIES_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_SCRAPER_ID) { type = NavType.StringType },
                navArgument(Routes.ARG_SERIES_URL) { type = NavType.StringType },
            ),
        ) {
            val scraperId = it.arguments?.getString(Routes.ARG_SCRAPER_ID).orEmpty()
            val seriesUrl = it.arguments?.getString(Routes.ARG_SERIES_URL).orEmpty()
            val vm = activityScraperVm()
            val ctx = LocalContext.current
            val container = ctx.applicationContext.appContainer
            val reg = container.scraperRegistry
            val scraper = reg.get(scraperId)
            val settings = container.settingsRepository.settings.collectAsState(initial = AppSettings()).value
            val scope = rememberCoroutineScope()
            // peek (not take): returning here through the back stack re-creates this
            // destination, and take() would have already emptied the cache → blank screen.
            val series = scraper?.let { source ->
                SeriesCache.peek(scraperId, seriesUrl) ?: fallbackSeries(source, seriesUrl)
            }

            if (scraper != null && series != null) {
                ScraperSeriesScreen(
                    vm = vm,
                    scraper = scraper,
                    series = series,
                    onBack = { navController.popBackStack() },
                    onReadChapter = { chapterIndex ->
                        SeriesCache.put(scraper.id, series)
                        navController.navigate(Routes.scraperRead(scraper.id, series.url, chapterIndex))
                    },
                    chapterListOrder = settings.chapterListOrder,
                    onToggleChapterOrder = {
                        scope.launch {
                            container.settingsRepository.setChapterListOrder(
                                settings.chapterListOrder.toggled()
                            )
                        }
                    },
                    onUnlock = { url -> navController.navigate(Routes.browser(url)) },
                )
            }
        }
        builder.composable(
            route = Routes.SCRAPER_READ_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_SCRAPER_ID) { type = NavType.StringType },
                navArgument(Routes.ARG_SERIES_URL) { type = NavType.StringType },
                navArgument(Routes.ARG_CHAPTER_INDEX) { type = NavType.IntType },
            ),
        ) {
            val scraperId = it.arguments?.getString(Routes.ARG_SCRAPER_ID).orEmpty()
            val seriesUrl = it.arguments?.getString(Routes.ARG_SERIES_URL).orEmpty()
            val chapterIndex = it.arguments?.getInt(Routes.ARG_CHAPTER_INDEX) ?: 0
            val vm = activityScraperVm()
            val state = vm.seriesState.collectAsState()
            val ctx = LocalContext.current
            val reg = ctx.applicationContext.appContainer.scraperRegistry
            val scraper = reg.get(scraperId)
            val series = scraper?.let { source ->
                SeriesCache.peek(scraperId, seriesUrl)
                    ?: state.value.series?.takeIf { current -> sameUrl(current.url, seriesUrl) }
                    ?: fallbackSeries(source, seriesUrl)
            }
            val chapters = if (sameUrl(state.value.series?.url, series?.url)) {
                state.value.chapters
            } else {
                emptyList()
            }

            if (scraper != null && series != null) {
                ScraperReadScreen(
                    scraper = scraper,
                    series = series,
                    initialChapters = chapters,
                    initialIndex = chapterIndex,
                    onBack = { navController.popBackStack() },
                    // Chapters that only exist behind a Cloudflare check or a
                    // publisher's own viewer open in the in-app browser, which is
                    // also where clearing the check issues the cookie the scrapers
                    // then reuse.
                    onOpenInBrowser = { url ->
                        navController.navigate(Routes.browser(url.ifBlank { scraper.baseUrl }))
                    },
                )
            }
        }
    }
}
