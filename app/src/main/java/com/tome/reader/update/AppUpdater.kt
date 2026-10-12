package com.tome.reader.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.tome.reader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Where the updater is in its cycle; the UI renders straight off this. */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(
        val version: String,
        val notes: String,
        val downloadUrl: String,
        val sizeBytes: Long,
    ) : UpdateStatus

    data class Downloading(val fraction: Float, val version: String) : UpdateStatus
    data class ReadyToInstall(val apk: File, val version: String) : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

/**
 * Keeps the installed app in step with GitHub Releases.
 *
 * The app is sideloaded, so there is no store to push updates. This checks the
 * repository's latest release, and when it names a newer version downloads that
 * release's APK and hands it to Android's package installer.
 *
 * Android does not allow an ordinary app to install silently -- that needs
 * device-owner privileges -- so the last step is always a system prompt the
 * reader confirms. One tap is the best an app in this position can do, and it is
 * what every sideloaded updater does.
 *
 * An update only installs over the existing app if both are signed with the same
 * key; see keystore.properties in the project root.
 */
object AppUpdater {

    /** Public repository, so the release API needs no credentials. */
    private const val LATEST_RELEASE =
        "https://api.github.com/repos/Cocokylez/mogu-reader/releases/latest"

    /** GitHub allows 60 anonymous API calls an hour; once every 6h is nothing. */
    private val CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(6)

    private const val PREFS = "app_updates"
    private const val KEY_LAST_CHECK = "last_check_at"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    fun dismiss() {
        _status.value = UpdateStatus.Idle
    }

    /**
     * The automatic path, run at launch. Stays quiet unless there is genuinely a
     * newer release: a failure here is never surfaced, because a reader opening
     * their library has not asked about updates and does not need to see that
     * GitHub was unreachable.
     */
    suspend fun checkInBackground(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        runCatching { check(context, quiet = true) }
    }

    /**
     * Looks up the latest release. [quiet] keeps a failure or an up-to-date
     * result out of the UI, for the automatic check.
     */
    suspend fun check(context: Context, quiet: Boolean = false) {
        if (!quiet) _status.value = UpdateStatus.Checking
        val result = runCatching { fetchLatest() }
        val release = result.getOrElse { error ->
            if (!quiet) {
                _status.value = UpdateStatus.Failed(
                    "Could not reach GitHub: ${error.message?.take(90) ?: "no connection"}"
                )
            }
            return
        }
        if (release == null || !isNewer(release.version, BuildConfig.VERSION_NAME)) {
            if (!quiet) _status.value = UpdateStatus.UpToDate
            return
        }
        _status.value = UpdateStatus.Available(
            version = release.version,
            notes = release.notes,
            downloadUrl = release.downloadUrl,
            sizeBytes = release.sizeBytes,
        )
    }

    private data class Release(
        val version: String,
        val notes: String,
        val downloadUrl: String,
        val sizeBytes: Long,
    )

    private suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "MoguReader/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val json = JSONObject(response.body?.string().orEmpty())
            val tag = json.optString("tag_name").removePrefix("v").trim()
            if (tag.isEmpty()) return@withContext null
            val assets = json.optJSONArray("assets") ?: return@withContext null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                return@withContext Release(
                    version = tag,
                    notes = json.optString("body").trim(),
                    downloadUrl = asset.optString("browser_download_url"),
                    sizeBytes = asset.optLong("size"),
                )
            }
            null
        }
    }

    /**
     * Compares dotted versions numerically, so 2.10.0 correctly beats 2.9.0 --
     * a plain string comparison would get that backwards.
     */
    internal fun isNewer(candidate: String, installed: String): Boolean {
        fun parts(v: String) = v.split('.', '-', '_')
            .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
        val a = parts(candidate)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Streams the release APK into the cache, reporting progress as it goes. */
    suspend fun download(context: Context, update: UpdateStatus.Available) {
        _status.value = UpdateStatus.Downloading(0f, update.version)
        val outcome = runCatching {
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "updates").apply {
                    // Only ever keep the APK being installed right now.
                    deleteRecursively()
                    mkdirs()
                }
                val apk = File(dir, "mogu-reader-${update.version}.apk")
                val request = Request.Builder()
                    .url(update.downloadUrl)
                    .header("User-Agent", "MoguReader/${BuildConfig.VERSION_NAME}")
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body ?: error("empty download")
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val total = if (update.sizeBytes > 0) update.sizeBytes else body.contentLength()
                    body.byteStream().use { input ->
                        apk.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var copied = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                copied += read
                                if (total > 0) {
                                    _status.value = UpdateStatus.Downloading(
                                        (copied.toFloat() / total).coerceIn(0f, 1f),
                                        update.version,
                                    )
                                }
                            }
                        }
                    }
                }
                apk
            }
        }
        outcome.fold(
            onSuccess = { _status.value = UpdateStatus.ReadyToInstall(it, update.version) },
            onFailure = {
                _status.value = UpdateStatus.Failed(
                    "Download failed: ${it.message?.take(90) ?: "unknown error"}"
                )
            },
        )
    }

    /**
     * Hands the APK to the system installer.
     *
     * Sideloaded installs need "install unknown apps" for this app. Rather than
     * failing with a silent no-op, the reader is sent to the exact settings page
     * that grants it; the next attempt then goes straight through.
     */
    fun install(context: Context, apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val permission = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(permission) }
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(install) }
            .onFailure {
                _status.value = UpdateStatus.Failed("Could not open the installer.")
            }
    }
}
