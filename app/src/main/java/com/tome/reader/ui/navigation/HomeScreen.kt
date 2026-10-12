package com.tome.reader.ui.navigation

import com.tome.reader.update.UpdatePrompt
import com.tome.reader.update.AppUpdater
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import com.tome.reader.ui.welcome.WelcomeLetter
import com.tome.reader.appContainer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import com.tome.reader.ui.components.LocalHazeState
import dev.chrisbanes.haze.haze
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tome.reader.online.OnlineModule
import com.tome.reader.scraper.ScrapedSeries
import com.tome.reader.scraper.ScraperCategory
import com.tome.reader.scraper.SeriesCache
import com.tome.reader.ui.components.TomeGlassSurface
import com.tome.reader.ui.theme.GlassSpec
import com.tome.reader.ui.components.TomeGlassBackdrop
import com.tome.reader.ui.history.HistoryScreen
import com.tome.reader.ui.library.LibraryScreen
import com.tome.reader.ui.settings.SettingsScreen

private enum class HomeTab(val label: String, val icon: ImageVector) {
    LIBRARY("Library", Icons.AutoMirrored.Filled.MenuBook),
    BROWSE("Browse", Icons.Default.Explore),
    HISTORY("History", Icons.Default.History),
    SETTINGS("Settings", Icons.Default.Settings),
}

/**
 * The four top-level destinations under one bottom navigation bar (the redesign's
 * primary structure). Tab content is swapped in place; tapping a cover/source still
 * pushes the detail routes registered in [ReaderNavHost].
 */
@Composable
fun HomeScreen(navController: NavHostController) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.LIBRARY) }

    // System back returns to Library first, then exits — matching common tab apps.
    BackHandler(enabled = tab != HomeTab.LIBRARY) { tab = HomeTab.LIBRARY }

    TomeGlassBackdrop(modifier = Modifier.fillMaxSize()) {
        // No bottom inset here: screens scroll underneath the pill so its
        // translucency has something to show. Each list adds its own
        // GlassSpec.NavClearance so the final row still clears it.
        val hazeState = LocalHazeState.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (hazeState != null) Modifier.haze(state = hazeState) else Modifier),
        ) {
            when (tab) {
                HomeTab.LIBRARY -> LibraryScreen(
                    onOpenBook = { navController.navigate(Routes.reader(it)) },
                    onOpenSeries = { navController.navigate(Routes.series(it)) },
                    onOpenBrowser = { tab = HomeTab.BROWSE },
                    onOpenOnlineSeries = { bm ->
                        val series = ScrapedSeries(
                            title = bm.title,
                            author = bm.author,
                            coverUrl = bm.coverUrl,
                            url = bm.url,
                            category = bm.category,
                        )
                        SeriesCache.put(bm.scraperId, series)
                        navController.navigate(Routes.scraperSeries(bm.scraperId, bm.url))
                    },
                )

                HomeTab.BROWSE -> OnlineModule.BrowseTab(navController)

                HomeTab.HISTORY -> HistoryScreen(
                    onOpenBook = { navController.navigate(Routes.reader(it)) },
                    onOpenOnline = { entry ->
                        val series = ScrapedSeries(
                            title = entry.title,
                            author = entry.author,
                            coverUrl = entry.coverUrl,
                            url = entry.seriesUrl,
                            category = runCatching { ScraperCategory.valueOf(entry.category) }
                                .getOrDefault(ScraperCategory.MANGA),
                        )
                        SeriesCache.put(entry.scraperId, series)
                        navController.navigate(Routes.scraperRead(entry.scraperId, entry.seriesUrl, -1))
                    },
                )

                HomeTab.SETTINGS -> SettingsScreen()
            }
        }

        TomeBottomNav(
            tab = tab,
            onSelect = { tab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )

        // Shown once, on the very first launch, above everything else.
        val context = LocalContext.current
        val settingsRepo = remember(context) { context.applicationContext.appContainer.settingsRepository }
        val appSettings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = null)
        val scope = rememberCoroutineScope()
        if (appSettings?.seenWelcomeLetter == false) {
            WelcomeLetter(
                onDismiss = { scope.launch { settingsRepo.setSeenWelcomeLetter(true) } },
            )
        }

        // The app is sideloaded, so it looks after its own updates. This is
        // silent unless a newer release actually exists, and throttled so it is
        // at most one API call every few hours.
        LaunchedEffect(Unit) { AppUpdater.checkInBackground(context) }
        UpdatePrompt()
    }
}

/**
 * The redesign's floating navigation pill: one glass capsule inset from the screen
 * edges, with the active tab sitting on a raised glass slab in the accent colour.
 */
@Composable
private fun TomeBottomNav(
    tab: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GlassSpec.NavSideInset)
            .padding(bottom = GlassSpec.NavBottomInset - 24.dp),
    ) {
        TomeGlassSurface(
            shape = RoundedCornerShape(GlassSpec.NavRadius),
            // No elevation: the pill is translucent, so an elevation shadow is
            // drawn under the whole shape and shows through it as a rectangle.
            elevation = 0.dp,
            blurBackdrop = true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(GlassSpec.NavPadding),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeTab.entries.forEach { entry ->
                    val active = tab == entry
                    val tint = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(GlassSpec.NavTabHeight)
                            .clip(RoundedCornerShape(GlassSpec.NavTabRadius))
                            .background(
                                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else Color.Transparent,
                            )
                            .clickable { onSelect(entry) },
                    ) {
                        Icon(
                            entry.icon,
                            contentDescription = entry.label,
                            tint = tint,
                            modifier = Modifier.size(GlassSpec.NavIcon),
                        )
                        Text(
                            text = entry.label,
                            style = GlassSpec.NavLabel,
                            color = tint,
                        )
                    }
                }
            }
        }
    }
}
