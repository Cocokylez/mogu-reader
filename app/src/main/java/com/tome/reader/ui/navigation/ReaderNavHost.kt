package com.tome.reader.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tome.reader.domain.model.AppSettings
import com.tome.reader.online.OnlineModule
import com.tome.reader.ui.reader.ReaderScreen
import com.tome.reader.ui.series.SeriesDetailScreen

object Routes {
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val READER = "reader"
    const val SERIES = "series"
    const val BROWSER = "browser"
    const val SCRAPER_BROWSE = "scraper_browse"
    const val SCRAPER_DIAGNOSTICS = "scraper_diagnostics"
    const val SCRAPER_DOWNLOADS = "scraper_downloads"
    const val SCRAPER_SOURCE_SEARCH = "scraper_source_search"
    const val SCRAPER_SERIES = "scraper_series"
    const val SCRAPER_READ = "scraper_read"
    const val ARG_BOOK_ID = "bookId"
    const val ARG_SERIES_ID = "seriesId"
    const val ARG_URL = "url"
    const val ARG_SCRAPER_ID = "scraperId"
    const val ARG_SERIES_URL = "seriesUrl"
    const val ARG_CHAPTER_INDEX = "chapterIndex"

    const val READER_PATTERN = "$READER/{$ARG_BOOK_ID}"
    const val SERIES_PATTERN = "$SERIES/{$ARG_SERIES_ID}"
    const val BROWSER_PATTERN = "$BROWSER?$ARG_URL={$ARG_URL}"
    const val SCRAPER_SOURCE_SEARCH_PATTERN = "$SCRAPER_SOURCE_SEARCH/{$ARG_SCRAPER_ID}"
    const val SCRAPER_SERIES_PATTERN = "$SCRAPER_SERIES/{$ARG_SCRAPER_ID}/{$ARG_SERIES_URL}"
    const val SCRAPER_READ_PATTERN =
        "$SCRAPER_READ/{$ARG_SCRAPER_ID}/{$ARG_SERIES_URL}/{$ARG_CHAPTER_INDEX}"

    fun reader(bookId: Long) = "$READER/$bookId"
    fun series(seriesId: Long) = "$SERIES/$seriesId"
    fun browser(url: String) = "$BROWSER?$ARG_URL=${Uri.encode(url)}"
    fun scraperSourceSearch(scraperId: String) = "$SCRAPER_SOURCE_SEARCH/$scraperId"
    fun scraperSeries(scraperId: String, seriesUrl: String) =
        "$SCRAPER_SERIES/$scraperId/${Uri.encode(seriesUrl)}"
    fun scraperRead(scraperId: String, seriesUrl: String, chapterIndex: Int) =
        "$SCRAPER_READ/$scraperId/${Uri.encode(seriesUrl)}/$chapterIndex"
}

@Composable
fun ReaderNavHost(settings: AppSettings) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {

        composable(Routes.LIBRARY) {
            // Home hosts Library/Browse/History/Settings under one bottom nav.
            HomeScreen(navController = navController)
        }

        composable(
            route = Routes.READER_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.LongType }),
        ) { entry ->
            val bookId = entry.arguments?.getLong(Routes.ARG_BOOK_ID) ?: -1L
            ReaderScreen(
                bookId = bookId,
                settings = settings,
                onBack = { navController.popBackStack() },
                // Advancing chapters replaces the current reader so Back returns
                // to the series (not through every chapter).
                onOpenChapter = { chapterId ->
                    navController.navigate(Routes.reader(chapterId)) {
                        popUpTo(Routes.READER_PATTERN) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.SERIES_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_SERIES_ID) { type = NavType.LongType }),
        ) { entry ->
            val seriesId = entry.arguments?.getLong(Routes.ARG_SERIES_ID) ?: -1L
            SeriesDetailScreen(
                seriesId = seriesId,
                onBack = { navController.popBackStack() },
                onOpenChapter = { chapterId -> navController.navigate(Routes.reader(chapterId)) },
            )
        }

        // Library/Browse/History/Settings now live as tabs inside HomeScreen; the
        // online browse + reader sub-routes are still registered here.
        OnlineModule.register(this, navController)
    }
}
