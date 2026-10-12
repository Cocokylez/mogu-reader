package com.tome.reader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tome.reader.ui.theme.GlassSpec

/**
 * The redesign's big screen header — a bold title with an accent-coloured period
 * ("Library.", "Discover.", "History.", "Settings."). Optional trailing actions.
 */
@Composable
fun TomeHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = GlassSpec.HeaderStart,
                end = GlassSpec.HeaderEnd,
                top = GlassSpec.HeaderTop,
                bottom = GlassSpec.HeaderBottom,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buildAnnotatedString {
                append(title)
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(".") }
            },
            style = GlassSpec.ScreenTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(GlassSpec.HeaderGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions()
        }
    }
}
