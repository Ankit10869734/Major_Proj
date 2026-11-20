package com.moodmix.app.ui.screens.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodmix.app.domain.model.PlayableTrack
import com.moodmix.app.ui.components.AnimatedEqualizerIndicator
import com.moodmix.app.ui.components.CoverArt
import com.moodmix.app.ui.theme.BrandPinkAccent
import com.moodmix.app.ui.theme.MintPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun formatDuration(ms: Long): String {
    val s = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Faint violet glow top-right, barely-there lime left. Fades out at the top edge so it blends into the status bar. */
internal fun Modifier.libraryGlow(): Modifier = this.drawBehind {
    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
    drawRect(
        Brush.radialGradient(
            listOf(BrandPinkAccent.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(size.width, 90.dp.toPx()),
            radius = size.width * 0.9f
        )
    )
    drawRect(
        Brush.radialGradient(
            listOf(MintPrimary.copy(alpha = 0.05f), Color.Transparent),
            center = Offset(0f, size.height * 0.35f),
            radius = size.width * 0.8f
        )
    )
    // Fade everything drawn above to nothing at the very top edge.
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, Color.Black),
            startY = 0f,
            endY = 140.dp.toPx()
        ),
        blendMode = BlendMode.DstIn
    )
    canvas.restore()
}

@Composable
internal fun LibraryHeader() {
    Column {
        Text(
            "MOODMIX",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            fontWeight = FontWeight.SemiBold,
            color = MintPrimary.copy(alpha = 0.85f)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Your Library",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Saved AI creations, liked songs and listening history",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun LibraryFilterChip(
    label: String,
    count: Int,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(50)
    val content = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) primary.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "$label  $count",
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
internal fun LibraryInsightCard(text: String) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, primary.copy(alpha = 0.22f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = primary,
            modifier = Modifier.padding(top = 2.dp).size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Listening insights", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = primary)
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun LibrarySectionTitle(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "$count ${if (count == 1) "track" else "tracks"}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val coverBars = floatArrayOf(0.35f, 0.65f, 0.95f, 0.55f, 0.8f, 0.4f, 0.6f)

/** Muted violet-to-lime tile with a small waveform glyph, used for AI-generated tracks. */
@Composable
internal fun GeneratedCover(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(BrandPinkAccent.copy(alpha = 0.40f), MintPrimary.copy(alpha = 0.25f)))),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(28.dp)) {
            val n = coverBars.size
            val gap = 2.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            coverBars.forEachIndexed { i, h ->
                val bh = size.height * h
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.88f),
                    topLeft = Offset(i * (bw + gap), (size.height - bh) / 2f),
                    size = Size(bw, bh),
                    cornerRadius = CornerRadius(bw / 2f)
                )
            }
        }
    }
}

@Composable
private fun AiTag() {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(primary.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text("AI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = primary)
    }
}

private fun buildSubtitle(t: PlayableTrack): String =
    if (t.isGenerated) {
        val dur = if (t.durationMs > 0) formatDuration(t.durationMs) else null
        val date = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(t.addedTimestamp))
        listOfNotNull(dur, date).joinToString(" · ")
    } else {
        listOf(t.artist, t.genre ?: "Music").joinToString(" · ")
    }

@Composable
internal fun LibraryTrackRow(
    track: PlayableTrack,
    isActive: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isLiked: Boolean,
    onClick: () -> Unit,
    onToggleLike: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)
    val subtitle = remember(track.id, track.durationMs) { buildSubtitle(track) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isActive) primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
            .border(
                1.dp,
                if (isActive) primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                shape
            )
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (track.isGenerated) {
                GeneratedCover(Modifier.size(52.dp))
            } else {
                CoverArt(
                    model = track.artworkUrl,
                    contentDescription = track.title,
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = primary, strokeWidth = 2.dp)
                    } else {
                        AnimatedEqualizerIndicator(isPlaying = isPlaying, barColor = primary)
                    }
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isGenerated) {
                    AiTag()
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = onToggleLike) {
            Icon(
                if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Like track",
                tint = if (isLiked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}