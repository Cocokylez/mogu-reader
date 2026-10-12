package com.tome.reader.update

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tome.reader.ui.components.TomeGlassSurface
import kotlinx.coroutines.launch

/**
 * The update flow, shown over whatever the reader was doing.
 *
 * Deliberately only appears once there is something to act on: the automatic
 * check runs silently and this stays invisible unless a newer release exists.
 * Checking from Settings is the one case where "you are up to date" is worth
 * saying, because the reader asked.
 */
@Composable
fun UpdatePrompt() {
    val status by AppUpdater.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Nothing to show for the quiet states.
    if (status is UpdateStatus.Idle || status is UpdateStatus.Checking) return

    // Once the APK is on disk, go straight to the installer rather than making
    // the reader tap a second time for no reason.
    LaunchedEffect(status) {
        val ready = status as? UpdateStatus.ReadyToInstall ?: return@LaunchedEffect
        AppUpdater.install(context, ready.apk)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                // A download in flight should not be dismissed by a stray tap.
                if (status !is UpdateStatus.Downloading) AppUpdater.dismiss()
            },
        contentAlignment = Alignment.Center,
    ) {
        TomeGlassSurface(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                when (val state = status) {
                    is UpdateStatus.Available -> {
                        Text(
                            text = "Update available",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Mogu Reader ${state.version} " +
                                "(you have ${AppUpdater.currentVersion})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (state.notes.isNotBlank()) {
                            Text(
                                text = state.notes.lineSequence().take(6).joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        Actions(
                            primary = "Update" + sizeSuffix(state.sizeBytes),
                            onPrimary = { scope.launch { AppUpdater.download(context, state) } },
                            onDismiss = { AppUpdater.dismiss() },
                        )
                    }

                    is UpdateStatus.Downloading -> {
                        Text(
                            text = "Downloading ${state.version}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        LinearProgressIndicator(
                            progress = { state.fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                        )
                        Text(
                            text = "${(state.fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    is UpdateStatus.ReadyToInstall -> {
                        Text(
                            text = "Ready to install",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Android will ask you to confirm. If nothing happened, " +
                                "allow Mogu Reader to install unknown apps and tap Install again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Actions(
                            primary = "Install",
                            onPrimary = { AppUpdater.install(context, state.apk) },
                            onDismiss = { AppUpdater.dismiss() },
                        )
                    }

                    is UpdateStatus.UpToDate -> {
                        Text(
                            text = "You are up to date",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Mogu Reader ${AppUpdater.currentVersion} is the latest release.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Actions(primary = null, onPrimary = {}, onDismiss = { AppUpdater.dismiss() })
                    }

                    is UpdateStatus.Failed -> {
                        Text(
                            text = "Update check failed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Actions(
                            primary = "Try again",
                            onPrimary = { scope.launch { AppUpdater.check(context) } },
                            onDismiss = { AppUpdater.dismiss() },
                        )
                    }

                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun Actions(primary: String?, onPrimary: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (primary == null) "Close" else "Not now",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
        if (primary != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onPrimary)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

private fun sizeSuffix(bytes: Long): String =
    if (bytes <= 0) "" else " · ${"%.1f".format(bytes / 1_048_576.0)} MB"
