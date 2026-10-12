package com.tome.reader.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tome.reader.domain.model.Book
import com.tome.reader.domain.model.BookFormat
import com.tome.reader.domain.model.Series
import java.io.File

/** A compact format chip, e.g. "CBZ", "EPUB". Dark glass over the cover art. */
@Composable
fun FormatBadge(format: BookFormat, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xB3000000),
        contentColor = Color(0xFFF0EFFA),
        shape = RoundedCornerShape(5.dp),
    ) {
        Text(
            text = format.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 0.4.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Library grid item: cover, format badge, reading-progress bar and title. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCoverCard(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayTitle = tomeDisplayTitle(book.title, fallback = "Untitled")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        val coverFile = book.coverPath?.let { File(it) }?.takeIf { it.exists() }
        TomeCoverArt(
            title = displayTitle,
            model = coverFile,
            badge = book.format.label,
            progress = book.progress.percent,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
        )

        Text(
            text = displayTitle,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
    }
}

/** Library grid item for a series: cover, "Series" badge, unread count, progress. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesCoverCard(
    series: Series,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayTitle = tomeDisplayTitle(series.title, fallback = "Untitled")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        val coverFile = series.coverPath?.let { File(it) }?.takeIf { it.exists() }
        TomeCoverArt(
            title = displayTitle,
            model = coverFile,
            badge = "Series",
            unread = series.unreadCount,
            progress = series.progress,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
        )

        Text(
            text = displayTitle,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            text = "${series.readCount}/${series.chapterCount} chapters",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
private fun PlaceholderCover(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/** Simple centered message for empty / loading / error states. */
@Composable
fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
    }
}
