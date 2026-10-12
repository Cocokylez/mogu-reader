package com.tome.reader.scraper.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.tome.reader.notifications.NotificationPermissionEffect
import com.tome.reader.scraper.ScraperRegistry
import com.tome.reader.scraper.diagnostics.DiagnosticStatus
import com.tome.reader.scraper.diagnostics.DiagnosticsReportStore
import com.tome.reader.scraper.diagnostics.DiagnosticsWorker
import com.tome.reader.scraper.diagnostics.SourceDiagnosticResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    registry: ScraperRegistry,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    val vm: DiagnosticsViewModel = viewModel(viewModelStoreOwner = activity)
    val results by vm.results.collectAsState()
    val isRunning by vm.isRunning.collectAsState()
    val progress by vm.progress.collectAsState()
    val fullReportText by vm.fullReportText.collectAsState()
    var latestSavedReport by remember { mutableStateOf(DiagnosticsReportStore.readLatest(ctx).orEmpty()) }
    val completed = progress.first
    val total = progress.second

    NotificationPermissionEffect()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Source Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            if (isRunning) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        "Testing $completed/$total sources...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        vm.runDiagnostics(registry) { report ->
                            latestSavedReport = report.toFullText()
                            DiagnosticsReportStore.saveLatest(ctx, report)
                            Toast.makeText(ctx, "Diagnostics saved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRunning,
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text(if (isRunning) "Running..." else "Run All Sources")
                }

                OutlinedButton(
                    onClick = {
                        enqueueBackgroundDiagnostics(ctx)
                        Toast.makeText(ctx, "Background diagnostics started", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRunning,
                ) {
                    Icon(Icons.Default.NotificationsActive, null)
                    Text("Run in Background")
                }

                OutlinedButton(
                    onClick = {
                        val saved = DiagnosticsReportStore.readLatest(ctx).orEmpty()
                        if (saved.isNotBlank()) latestSavedReport = saved
                        val copyText = fullReportText.ifBlank { saved.ifBlank { latestSavedReport } }
                        if (copyText.isBlank()) {
                            Toast.makeText(ctx, "No diagnostics report yet", Toast.LENGTH_SHORT).show()
                        } else {
                            copyToClipboard(ctx, "Mogu Reader Full Diagnostics", copyText)
                            Toast.makeText(ctx, "Copied full diagnostics", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.ContentCopy, null)
                    Text("Copy Full Diagnostics")
                }
            }

            Spacer(Modifier.height(4.dp))

            if (results.isEmpty() && !isRunning) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Run now for live results, or run in background and come back after the notification.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "source-health") {
                        SourceHealthSummary(results)
                    }
                    items(results, key = { it.sourceId }) { result ->
                        DiagnosticCard(result)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceHealthSummary(results: List<SourceDiagnosticResult>) {
    val labels = listOf("working", "blocked", "empty parser", "needs JS/browser", "file-only")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Source health",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEach { label ->
                val count = results.count { it.label == label }
                HealthPill(label = label, count = count)
            }
        }
        Text(
            text = "${results.count { it.status == DiagnosticStatus.OK }} working, " +
                "${results.count { it.status == DiagnosticStatus.ERROR }} need attention, " +
                "${results.count { it.status == DiagnosticStatus.SKIPPED }} skipped",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HealthPill(label: String, count: Int) {
    val accent = when (label) {
        "working" -> Color(0xFF4ADE80)
        "blocked" -> MaterialTheme.colorScheme.error
        "needs JS/browser" -> Color(0xFF60A5FA)
        "file-only" -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        color = accent.copy(alpha = 0.16f),
        contentColor = accent,
        shape = RoundedCornerShape(99.dp),
    ) {
        Text(
            text = "$count ${label.uppercase()}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun DiagnosticCard(result: SourceDiagnosticResult) {
    val isError = result.status == DiagnosticStatus.ERROR
    val isSkipped = result.status == DiagnosticStatus.SKIPPED
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isError -> MaterialTheme.colorScheme.errorContainer
                isSkipped -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = result.sourceName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${result.sourceId} | ${result.category} | Browse: ${if (result.isAvailable) "shown" else "hidden"} | ${result.baseUrl}",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (result.status) {
                    DiagnosticStatus.OK -> {
                        val chapters = if (result.chapterCount > 0) ", ${result.chapterCount} chapters" else ""
                        "${result.label.uppercase()} - ${result.itemCount} items$chapters"
                    }
                    DiagnosticStatus.ERROR -> "${result.label.uppercase()} - ${result.error ?: "Unknown"}"
                    DiagnosticStatus.SKIPPED -> "${result.label.uppercase()} - ${result.error ?: "Not available"}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (result.sampleTitles.isNotEmpty()) {
                Text(
                    text = result.sampleTitles.joinToString(prefix = "Samples: ", separator = " | "),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (result.diagnostics.isNotBlank()) {
                Text(
                    text = result.diagnostics,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = if (isError) 30 else 4,
                )
            }
        }
    }
}

private fun enqueueBackgroundDiagnostics(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    val request = OneTimeWorkRequestBuilder<DiagnosticsWorker>()
        .setConstraints(constraints)
        .build()
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
        DiagnosticsWorker.UNIQUE_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        request,
    )
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clip = ClipData.newPlainText(label, text)
    context.getSystemService(Context.CLIPBOARD_SERVICE)?.let { manager ->
        (manager as ClipboardManager).setPrimaryClip(clip)
    }
}
