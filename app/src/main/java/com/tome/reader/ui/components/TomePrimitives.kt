package com.tome.reader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tome.reader.ui.theme.GlassSpec
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private val fallbackGradients = listOf(
    Color(0xFF1A0E3A) to Color(0xFF6D28D9),
    Color(0xFF0D1F42) to Color(0xFF1D4ED8),
    Color(0xFF062022) to Color(0xFF0891B2),
    Color(0xFF2D0A18) to Color(0xFFBE185D),
    Color(0xFF0D2208) to Color(0xFF15803D),
    Color(0xFF2A1400) to Color(0xFFC2410C),
    Color(0xFF200A2A) to Color(0xFF7E22CE),
    Color(0xFF001A20) to Color(0xFF0F766E),
    Color(0xFF2A1000) to Color(0xFFB45309),
    Color(0xFF0A0A2E) to Color(0xFF4338CA),
    Color(0xFF1A0808) to Color(0xFFB91C1C),
    Color(0xFF041A20) to Color(0xFF0369A1),
)

fun tomeInitials(title: String): String {
    val words = tomeDisplayTitle(title).split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "TO"
        words.size == 1 -> words.first().take(2)
        else -> "${words[0].first()}${words[1].first()}"
    }.uppercase()
}

fun tomeDisplayTitle(
    value: String?,
    fallbackUrl: String? = null,
    fallback: String = "Untitled",
): String {
    val cleaned = cleanDisplayTitle(value)
    if (cleaned != null && !isBrokenDisplayTitle(cleaned)) return cleaned

    val fromValueUrl = cleaned?.let(::titleFromUrl)
    if (!fromValueUrl.isNullOrBlank()) return fromValueUrl

    return titleFromUrl(fallbackUrl) ?: fallback
}

fun tomeGradient(seed: String): Pair<Color, Color> {
    val index = tomeDisplayTitle(seed).hashCode().and(Int.MAX_VALUE) % fallbackGradients.size
    return fallbackGradients[index]
}

fun tomeCoverFallbackModels(primaryUrl: String?, pageUrl: String?): List<String> {
    val page = pageUrl
        ?.substringBefore('#')
        ?.substringBefore('?')
        ?.trim()
        ?.trimEnd('/')
        ?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        ?: return emptyList()
    val parent = page.substringBeforeLast('/', missingDelimiterValue = page)
    val slug = page.substringAfterLast('/').takeIf { it.isNotBlank() }

    return buildList {
        if (slug != null) {
            add("$parent/$slug.jpg")
            add("$parent/$slug.png")
            add("$parent/$slug.webp")
        }
        add("$page/cover.jpg")
        add("$page/cover.png")
        add("$page/poster.jpg")
        add("$page/thumbnail.jpg")
        add("$parent/cover.jpg")
        add("$parent/poster.jpg")
        add("$parent/thumbnail.jpg")
    }
        .filterNot { it == primaryUrl }
        .distinct()
        .take(4)
}

private fun cleanDisplayTitle(value: String?): String? {
    val normalized = value
        ?.replace('\u00A0', ' ')
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.trim('-', '–', '|', '/', '\\')
        ?.trim()
    return normalized?.takeIf { it.isNotBlank() }
}

private fun isBrokenDisplayTitle(title: String): Boolean {
    val lower = title.lowercase()
    return lower == "null" ||
        lower == "n/a" ||
        lower == "none" ||
        lower == "untitled" ||
        lower == "no title" ||
        lower == "..." ||
        (title.contains('/') && !title.contains(' ') && title.length > 8) ||
        title.startsWith("http://", ignoreCase = true) ||
        title.startsWith("https://", ignoreCase = true)
}

private fun titleFromUrl(raw: String?): String? {
    val source = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val candidate = source
        .substringBefore('#')
        .substringBefore('?')
        .trimEnd('/')
        .substringAfterLast('/')
        .substringAfterLast('=')
        .takeIf { it.isNotBlank() }
        ?: return null

    val decoded = runCatching {
        URLDecoder.decode(candidate, StandardCharsets.UTF_8.name())
    }.getOrDefault(candidate)

    val humanized = decoded
        .replace(Regex("\\.(html?|php|aspx?)$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[_-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .takeIf { it.length > 1 && !it.all { char -> char.isDigit() } }
        ?: return null

    return cleanDisplayTitle(humanized)?.takeIf { !isBrokenDisplayTitle(it) }?.let(::titleCaseSlug)
}

private fun titleCaseSlug(title: String): String =
    title.split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase() else char.toString()
        }
    }

@Composable
fun TomeIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    light: Boolean = false,
) {
    Surface(
        color = if (light) {
            Color.White.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        },
        contentColor = if (light) {
            Color.White.copy(alpha = 0.9f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(
            0.5.dp,
            if (light) Color.White.copy(alpha = 0.20f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f),
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .size(38.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun TomeGlassCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.White.copy(alpha = 0.14f),
        contentColor = Color.White,
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.20f)),
        shape = CircleShape,
        modifier = modifier
            .size(38.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun TomeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Design: padding 7x14, radius 99, 13sp/600. Unselected chips are glass with
    // a hairline; selected chips fill with the accent.
    val glass = rememberTomeGlassTokens()
    val shape = RoundedCornerShape(GlassSpec.ChipRadius)
    Text(
        text = label,
        style = GlassSpec.Chip,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else glass.fill)
            .border(0.5.dp, if (selected) Color.Transparent else glass.hairline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = GlassSpec.ChipPaddingH, vertical = GlassSpec.ChipPaddingV),
    )
}

@Composable
fun TomePill(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.12f),
    contentColor: Color = Color.White.copy(alpha = 0.82f),
) {
    Surface(
        color = color,
        contentColor = contentColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun TomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = GlassSpec.SourceName,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            val actionModifier = if (onAction != null) {
                Modifier.clickable(onClick = onAction)
            } else {
                Modifier
            }
            Text(
                text = action,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = actionModifier,
            )
        }
    }
}

@Composable
fun TomeCoverArt(
    title: String,
    model: Any?,
    modifier: Modifier = Modifier,
    fallbackModels: List<Any> = emptyList(),
    progress: Float = 0f,
    badge: String? = null,
    unread: Int = 0,
    compact: Boolean = false,
    contentDescription: String? = title,
) {
    val gradient = tomeGradient(title)
    val imageModels = remember(model, fallbackModels) {
        (listOfNotNull(model) + fallbackModels)
            .distinctBy { it.toString() }
    }
    var modelIndex by remember(imageModels) { mutableIntStateOf(0) }
    val currentModel = imageModels.getOrNull(modelIndex)
    Box(
        modifier = modifier
            .aspectRatio(0.67f)
            .clip(RoundedCornerShape(if (compact) 8.dp else 12.dp))
            .background(Brush.linearGradient(listOf(gradient.first, gradient.second))),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = tomeInitials(title),
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White.copy(alpha = 0.88f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(10.dp),
            )
        }

        if (currentModel != null) {
            AsyncImage(
                model = currentModel,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = {
                    if (modelIndex < imageModels.lastIndex) modelIndex += 1
                },
            )
        }

        if (badge != null) {
            TomePill(
                label = badge.uppercase(),
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            )
        }

        if (unread > 0) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(99.dp),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            ) {
                Text(
                    text = if (progress <= 0f) "NEW" else if (unread > 99) "99+" else unread.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.12f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
fun TomeStatsRow(
    stats: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        stats.forEachIndexed { index, stat ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .height(34.dp)
                        .background(rememberTomeGlassTokens().hairline),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stat.second,
                    style = GlassSpec.StatValue,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                Text(
                    text = stat.first.uppercase(),
                    style = GlassSpec.StatLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun TomePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    Surface(
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(8.dp))
            }
            Text(text = text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TomeInlineMeta(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
