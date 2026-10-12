package com.tome.reader.online

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tome.reader.appContainer
import com.tome.reader.notifications.NotificationPermissionEffect
import com.tome.reader.notifications.ReaderNotifications
import com.tome.reader.scraper.BrowserIntegration
import com.tome.reader.scraper.HttpClient
import kotlinx.coroutines.launch
import java.io.File

/**
 * In-app browser (online flavor only). Browse any site; when you reach a
 * comic archive or PDF it downloads and imports it into your library.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    startUrl: String = DEFAULT_BROWSER_URL,
    onBack: () -> Unit,
    onScraperOpened: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    NotificationPermissionEffect()

    var address by remember { mutableStateOf(startUrl) }
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var detectedScraper by remember { mutableStateOf<String?>(null) }

    fun importFile(file: File) {
        scope.launch {
            busy = true
            val result = runCatching {
                context.appContainer.importBooks(listOf(Uri.fromFile(file)), emptyList())
            }.getOrNull()
            busy = false
            snackbar.showSnackbar(
                if (result != null && result.added > 0) "Added to library: ${file.name}"
                else "Couldn't read ${file.name} as a comic",
            )
            if (result != null && result.added > 0) {
                ReaderNotifications.showDownloadComplete(
                    context,
                    "Added to Mogu Reader",
                    file.name,
                )
            } else {
                ReaderNotifications.showDownloadFailed(
                    context,
                    "Import failed",
                    "Couldn't read ${file.name} as a comic",
                )
            }
        }
    }

    fun startDownload(url: String, ua: String?, contentDisposition: String?, mime: String?) {
        if (busy) return
        scope.launch {
            busy = true
            val notificationTitle = "Downloading file"
            ReaderNotifications.showDownloadRunning(context, notificationTitle, url)
            scope.launch { snackbar.showSnackbar("Downloading comic...") }
            val result = try {
                WebDownloader.download(context, url, ua, contentDisposition, mime)
            } finally {
                busy = false
                ReaderNotifications.clearDownloadRunning(context, notificationTitle)
            }
            result.onSuccess { importFile(it) }
                .onFailure {
                    ReaderNotifications.showDownloadFailed(context, "Download failed", url)
                    scope.launch { snackbar.showSnackbar(it.message ?: "Download failed") }
                }
        }
    }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            // Match OkHttp's pinned UA so a Cloudflare check solved here yields a
            // cf_clearance cookie that's valid for the scraper's own requests
            // (Cloudflare binds the cookie to the solving User-Agent).
            settings.userAgentString = HttpClient.USER_AGENT
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    if (url != null) address = url
                    canGoBack = view?.canGoBack() == true
                    detectedScraper = if (url != null && BrowserIntegration.isScraperSupported(url)) {
                        BrowserIntegration.detectScraper(url, context.appContainer.scraperRegistry)?.name
                    } else null
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    canGoBack = view?.canGoBack() == true
                    // Persist any cookies (e.g. Cloudflare's cf_clearance) so the
                    // scraper's OkHttp client can read them via the cookie bridge.
                    CookieManager.getInstance().flush()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                }
            }
            setDownloadListener { url, ua, contentDisposition, mime, _ ->
                startDownload(url, ua, contentDisposition, mime)
            }
            loadUrl(address)
        }
    }

    DisposableEffect(Unit) { onDispose { webView.destroy() } }
    BackHandler(enabled = canGoBack) { webView.goBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(shadowElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Library")
                        }
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Search or enter address") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                webView.loadUrl(normalizeUrl(address))
                            }),
                        )
                        IconButton(onClick = { webView.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (detectedScraper != null) {
                                    DropdownMenuItem(
                                        text = { Text("Search on $detectedScraper") },
                                        onClick = {
                                            menuOpen = false
                                            onScraperOpened?.invoke(address)
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Download current link") },
                                    onClick = {
                                        menuOpen = false
                                        if (WebDownloader.isSupportedFileUrl(address)) {
                                            startDownload(address, webView.settings.userAgentString, null, null)
                                        } else {
                                            scope.launch {
                                                snackbar.showSnackbar("Open a direct CBZ, CBR, ZIP, RAR, or PDF comic link first")
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    if (busy || progress in 1..99) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
    ) { padding ->
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        )
    }
}

/** Default page used when the browser is opened without a specific source. */
const val DEFAULT_BROWSER_URL = "https://mangadex.org/"

/** Turn raw address-bar input into a URL (or a web search). */
private fun normalizeUrl(input: String): String {
    val text = input.trim()
    return when {
        text.isEmpty() -> DEFAULT_BROWSER_URL
        text.startsWith("http://") || text.startsWith("https://") -> text
        !text.contains(".") || text.contains(" ") -> "https://duckduckgo.com/?q=" + Uri.encode(text)
        else -> "https://$text"
    }
}


