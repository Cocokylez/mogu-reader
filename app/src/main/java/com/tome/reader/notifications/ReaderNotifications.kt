package com.tome.reader.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tome.reader.R
import kotlin.math.absoluteValue

object ReaderNotifications {
    private const val DOWNLOADS_CHANNEL = "downloads"
    private const val CONTENT_CHANNEL = "content_updates"
    private const val PREFS = "reader_notifications"
    private const val ASKED_PERMISSION = "asked_notification_permission"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                DOWNLOADS_CHANNEL,
                "Downloads",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Download and import status"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CONTENT_CHANNEL,
                "Content updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "New latest and popular content found while browsing"
            }
        )
    }

    fun showDownloadRunning(context: Context, title: String, body: String) {
        notify(
            context = context,
            id = notificationId("running:$title"),
            channel = DOWNLOADS_CHANNEL,
            title = title,
            body = body,
            ongoing = true,
        )
    }

    fun showDownloadComplete(context: Context, title: String, body: String) {
        notify(
            context = context,
            id = notificationId("done:$title"),
            channel = DOWNLOADS_CHANNEL,
            title = title,
            body = body,
        )
    }

    fun showDownloadFailed(context: Context, title: String, body: String) {
        notify(
            context = context,
            id = notificationId("failed:$title"),
            channel = DOWNLOADS_CHANNEL,
            title = title,
            body = body,
        )
    }

    fun clearDownloadRunning(context: Context, title: String) {
        if (!canNotify(context)) return
        NotificationManagerCompat.from(context).cancel(notificationId("running:$title"))
    }

    fun notifyBrowseContentIfChanged(
        context: Context,
        feedLabel: String,
        itemKeys: List<String>,
    ) {
        if (itemKeys.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "browse:${feedLabel.lowercase()}"
        val fingerprint = itemKeys.take(50).joinToString("|")
        val previous = prefs.getString(key, null)
        prefs.edit().putString(key, fingerprint).apply()

        if (previous != null && previous != fingerprint) {
            notify(
                context = context,
                id = notificationId(key),
                channel = CONTENT_CHANNEL,
                title = "New $feedLabel content",
                body = "${itemKeys.size} items refreshed in Browse",
            )
        }
    }

    fun markPermissionAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ASKED_PERMISSION, true)
            .apply()
    }

    fun wasPermissionAsked(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ASKED_PERMISSION, false)
    }

    fun canNotify(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun notify(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        body: String,
        ongoing: Boolean = false,
    ) {
        ensureChannels(context)
        if (!canNotify(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)
            .build()

        // Permission can be revoked between the check above and this call.
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // The next foreground visit can offer the permission prompt again.
        }
    }

    private fun notificationId(seed: String): Int = seed.hashCode().absoluteValue.coerceAtLeast(1)
}

@Composable
fun NotificationPermissionEffect() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        ReaderNotifications.markPermissionAsked(context)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ReaderNotifications.wasPermissionAsked(context) &&
            !ReaderNotifications.canNotify(context)
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
