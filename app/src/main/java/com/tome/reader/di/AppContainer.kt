package com.tome.reader.di

import android.content.Context
import com.tome.reader.data.local.AppDatabase
import com.tome.reader.data.backup.BackupManager
import com.tome.reader.data.repository.CollectionRepository
import com.tome.reader.data.repository.LibraryRepository
import com.tome.reader.data.repository.OnlineHistoryRepository
import com.tome.reader.data.repository.SettingsRepository
import com.tome.reader.data.source.BookSourceFactory
import com.tome.reader.domain.usecase.ApplyLibraryQuery
import com.tome.reader.domain.usecase.DeleteBookUseCase
import com.tome.reader.domain.usecase.ImportBooksUseCase
import com.tome.reader.domain.usecase.OpenImageBookUseCase
import com.tome.reader.domain.usecase.SaveProgressUseCase
import com.tome.reader.scraper.ScraperDownloader
import com.tome.reader.scraper.ScraperRegistry
import com.tome.reader.scraper.ScraperRepository
import com.tome.reader.scraper.ui.ScraperViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency container. A lightweight service locator built once in
 * [com.tome.reader.ReaderApp]. Kept deliberately simple (no Hilt) so the project
 * builds with zero annotation-processing surprises; swapping in Hilt later only
 * touches this class and the ViewModel constructors.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    // Data
    val database = AppDatabase.build(appContext)
    private val sourceFactory = BookSourceFactory(appContext, appContext)

    val libraryRepository =
        LibraryRepository(appContext, database.bookDao(), database.seriesDao(), sourceFactory)
    val collectionRepository = CollectionRepository(database.collectionDao())
    val settingsRepository = SettingsRepository(appContext)
    val onlineHistoryRepository = OnlineHistoryRepository(database.onlineHistoryDao())

    // Scrapers
    val scraperRegistry = ScraperRegistry()
    val scraperRepository = ScraperRepository(appContext)
    val scraperDownloader = ScraperDownloader(
        appContext,
        database.bookDao(),
        database.seriesDao(),
        libraryRepository,
    )

    // Backup/restore of follows, online history, settings, and library DB rows.
    val backupManager = BackupManager(
        database.bookDao(),
        database.seriesDao(),
        database.collectionDao(),
        database.onlineHistoryDao(),
        scraperRepository,
        settingsRepository,
        database = database,
    )

    // Domain
    val importBooks = ImportBooksUseCase(libraryRepository)
    val deleteBook = DeleteBookUseCase(libraryRepository)
    val saveProgress = SaveProgressUseCase(libraryRepository)
    val openImageBook = OpenImageBookUseCase(libraryRepository)
    val applyLibraryQuery = ApplyLibraryQuery()

    // Long-lived scope for startup maintenance and fire-and-forget writes that must
    // survive a screen leaving (e.g. saving the last reading position on navigate-away).
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Rescue downloads written by older builds into persistent storage.
        appScope.launch { scraperDownloader.migrateLegacyDownloads() }
    }
}
