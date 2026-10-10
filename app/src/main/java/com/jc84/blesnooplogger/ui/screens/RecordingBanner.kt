package com.jc84.blesnooplogger.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jc84.blesnooplogger.ui.theme.RecColor
import com.jc84.blesnooplogger.ui.theme.RecFilteredColor

/**
 * Persistent "we are writing to disk right now" bar, shown only while the live CSV writer
 * is open. Red when every device is being recorded, amber when the device filter is
 * narrowing what reaches the file — the filter silently scoping the recording is the
 * easiest thing to get wrong, so it gets its own colour and an explicit ONLY label.
 *
 * Deliberately separate from the packet counter in the top bar: that counter is about the
 * in-memory ring buffer (and the Save button), which has nothing to do with this file.
 */
@Composable
fun RecordingBanner(
    csvFile: String,
    rowCount: Long,
    deviceScope: String?,
    modifier: Modifier = Modifier,
) {
    val accent = if (deviceScope == null) RecColor else RecFilteredColor

    // Slow pulse so the bar reads as live even when packets are sparse. Keeps pulsing while
    // the display is paused, which is exactly when users doubt the recording is still running.
    val transition = rememberInfiniteTransition(label = "rec-pulse")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rec-dot-alpha",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .alpha(dotAlpha)
                .background(accent, CircleShape),
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "REC",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    text = csvFile,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = buildString {
                    if (deviceScope == null) append("all devices") else append("ONLY $deviceScope")
                    append(" · ")
                    append(rowCount)
                    append(if (rowCount == 1L) " row" else " rows")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
