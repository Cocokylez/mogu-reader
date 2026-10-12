package com.tome.reader.ui.settings

import com.tome.reader.domain.model.TextLayoutMode
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Article
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.shadow
import com.tome.reader.ui.components.rememberTomeGlassTokens
import com.tome.reader.ui.theme.GlassSpec
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tome.reader.appContainer
import com.tome.reader.domain.model.AppSettings
import com.tome.reader.domain.model.ReadingDirection
import com.tome.reader.domain.model.ReadingQuality
import com.tome.reader.domain.model.ThemeOption
import com.tome.reader.ui.components.TomeChip
import com.tome.reader.ui.components.TomeGlassSurface
import com.tome.reader.ui.components.TomeHeader
import com.tome.reader.updates.FollowUpdateWorker
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(context.appContainer))
    val settings by vm.settings.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val backup = remember { context.appContainer.backupManager }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = backup.export()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                            ?: error("Couldn't open the file")
                    }
                }.onSuccess {
                    Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "Backup failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use {
                            it.readBytes().decodeToString()
                        } ?: error("Couldn't read the file")
                    }
                    backup.import(json)
                }.onSuccess { r ->
                    Toast.makeText(
                        context,
                        "Restored ${r.books} library items · ${r.follows} follows · ${r.onlineHistory} history",
                        Toast.LENGTH_LONG,
                    ).show()
                }.onFailure {
                    Toast.makeText(context, "Restore failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = { TomeHeader("Settings") },
        containerColor = Color.Transparent,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = GlassSpec.NavClearance),
        ) {
            SectionLabel("Accent color")
            AccentPicker(selected = settings.accentColor, onSelect = vm::setAccent)

            SectionLabel("Appearance")
            ChipRow(
                options = themeChipOrder,
                selected = settings.theme,
                label = ::themeLabel,
                onSelect = vm::setTheme,
            )

            SectionLabel("Reading")
            SettingsCard {
                SettingChoiceRow(
                    icon = Icons.Default.GridView,
                    label = "Reading quality",
                    value = qualityLabel(settings.readingQuality),
                    onClick = { vm.setReadingQuality(settings.readingQuality.next()) },
                )
                SettingsDivider()
                SettingChoiceRow(
                    icon = Icons.Default.SwapHoriz,
                    label = "Reading direction",
                    value = directionLabel(settings.defaultDirection),
                    onClick = { vm.setDirection(settings.defaultDirection.next()) },
                )
                SettingsDivider()
                SettingChoiceRow(
                    icon = Icons.Default.Article,
                    label = "Text layout",
                    value = textLayoutLabel(settings.textLayout),
                    onClick = {
                        vm.setTextLayout(
                            if (settings.textLayout == TextLayoutMode.SCROLL) {
                                TextLayoutMode.PAGED
                            } else {
                                TextLayoutMode.SCROLL
                            }
                        )
                    },
                )
                SettingsDivider()
                ToggleRow(
                    icon = Icons.Default.Lightbulb,
                    label = "Keep screen on",
                    checked = settings.keepScreenOn,
                    onChange = vm::setKeepScreenOn,
                )
                SettingsDivider()
                ToggleRow(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    label = "Volume key paging",
                    checked = settings.volumeKeyPaging,
                    onChange = vm::setVolumeKeyPaging,
                )
                SettingsDivider()
                ToggleRow(
                    icon = Icons.Default.Sync,
                    label = "Auto-update follows",
                    checked = settings.autoUpdateFollows,
                    onChange = vm::setAutoUpdateFollows,
                )
                SettingsDivider()
                ToggleRow(
                    icon = Icons.Default.Download,
                    label = "Auto-download chapters",
                    checked = settings.autoDownloadNewChapters,
                    onChange = vm::setAutoDownloadNewChapters,
                )
                SettingsDivider()
                ToggleRow(
                    icon = Icons.Default.Visibility,
                    label = "Show 18+ content",
                    checked = settings.showAdultContent,
                    onChange = vm::setShowAdultContent,
                )
            }

            // Not in the design, but a real feature of this app — kept in its own
            // small section rather than padding out the reading card.
            SectionLabel("Updates")
            SettingsCard {
                SettingChoiceRow(
                    icon = Icons.Default.Refresh,
                    label = "Check for updates now",
                    value = "Run",
                    onClick = {
                        FollowUpdateWorker.runNow(context)
                        Toast.makeText(
                            context,
                            "Checking followed series for new chapters…",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }

            SectionLabel("Library")
            SettingsCard {
                InfoRow(icon = Icons.Default.GridView, label = "Grid size", value = "Adaptive")
                SettingsDivider()
                InfoRow(icon = Icons.AutoMirrored.Filled.Sort, label = "Sort order", value = "Choose in Library")
            }

            SectionLabel("Backup")
            SettingsCard {
                SettingChoiceRow(
                    icon = Icons.Default.Save,
                    label = "Export backup",
                    value = "Save file",
                    onClick = { exportLauncher.launch("tomereader-backup.json") },
                )
                SettingsDivider()
                SettingChoiceRow(
                    icon = Icons.Default.Restore,
                    label = "Restore backup",
                    value = "Open file",
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                )
            }

            SectionLabel("About")
            SettingsCard {
                InfoRow(
                    icon = Icons.Default.Info,
                    label = "Version",
                    value = remember {
                        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                            .getOrNull() ?: "Unknown"
                    },
                )
                SettingsDivider()
                InfoRow(icon = Icons.Default.Security, label = "Privacy", value = "No tracking")
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Your library and reading progress are stored on this device. Online sources, downloads, and enabled follow updates use the internet. Backups contain your library records and settings; downloaded files are kept on this device.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = com.tome.reader.ui.theme.GlassSpec.SectionLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    TomeGlassSurface(
        shape = RoundedCornerShape(GlassSpec.StatsRadius),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = rememberTomeGlassTokens().hairline,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp),
    )
}

@Composable
private fun AccentPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppSettings.ACCENT_CHOICES.forEach { choice ->
            val isSelected = choice == selected
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(choice))
                    .border(
                        width = if (isSelected) 2.5.dp else 2.5.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(choice) },
            )
        }
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRowShell(icon = icon, label = label, onClick = { onChange(!checked) }) {
        TomeToggle(checked = checked, onChange = onChange)
    }
}

/**
 * The design's switch: a 48x29 track that fills with the accent when on, and a
 * 25dp white knob that slides between 2dp and 21dp. Material3's Switch has its
 * own thumb sizing, outline and icon slot, and reads nothing like this.
 */
@Composable
private fun TomeToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val glass = rememberTomeGlassTokens()
    val knobX by animateDpAsState(if (checked) 21.dp else 2.dp, label = "knob")
    val track by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else glass.raised,
        label = "track",
    )
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 29.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(track)
            .clickable { onChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .padding(start = knobX, top = 2.dp)
                .size(25.dp)
                .shadow(3.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun SettingChoiceRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    SettingRowShell(icon = icon, label = label, onClick = onClick) {
        Text(
            text = value,
            style = GlassSpec.SettingValue,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    SettingRowShell(icon = icon, label = label) {
        Text(
            text = value,
            style = GlassSpec.SettingValue,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingRowShell(
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        // 34dp glass tile with a hairline, matching the design's glyph chip.
        val glass = rememberTomeGlassTokens()
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(glass.raised)
                .border(0.5.dp, glass.hairline, RoundedCornerShape(10.dp)),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
        }
        Text(
            text = label,
            style = GlassSpec.SettingLabel,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            TomeChip(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

/** The design lists appearance as Dark, Light, Sepia, System. */
private val themeChipOrder = listOf(
    ThemeOption.DARK,
    ThemeOption.LIGHT,
    ThemeOption.SEPIA,
    ThemeOption.SYSTEM,
)

private fun themeLabel(option: ThemeOption): String = when (option) {
    ThemeOption.SYSTEM -> "System"
    ThemeOption.LIGHT -> "Light"
    ThemeOption.DARK -> "Dark"
    ThemeOption.SEPIA -> "Sepia"
}

private fun directionLabel(direction: ReadingDirection): String = when (direction) {
    ReadingDirection.LTR -> "Left to right"
    ReadingDirection.RTL -> "Right to left"
    ReadingDirection.WEBTOON -> "Webtoon"
}


private fun textLayoutLabel(mode: TextLayoutMode): String = when (mode) {
    TextLayoutMode.SCROLL -> "Continuous scroll"
    TextLayoutMode.PAGED -> "Paged"
}

private fun qualityLabel(quality: ReadingQuality): String = when (quality) {
    ReadingQuality.COMPACT -> "Compact"
    ReadingQuality.COMFY -> "Comfy"
    ReadingQuality.IMAGE_FULLSCREEN -> "Full image"
}


private fun ReadingQuality.next(): ReadingQuality = when (this) {
    ReadingQuality.COMPACT -> ReadingQuality.COMFY
    ReadingQuality.COMFY -> ReadingQuality.IMAGE_FULLSCREEN
    ReadingQuality.IMAGE_FULLSCREEN -> ReadingQuality.COMPACT
}

private fun ReadingDirection.next(): ReadingDirection = when (this) {
    ReadingDirection.LTR -> ReadingDirection.RTL
    ReadingDirection.RTL -> ReadingDirection.WEBTOON
    ReadingDirection.WEBTOON -> ReadingDirection.LTR
}
