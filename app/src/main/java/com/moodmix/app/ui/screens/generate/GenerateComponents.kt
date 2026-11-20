package com.moodmix.app.ui.screens.generate

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodmix.app.data.storage.ChatMessage
import com.moodmix.app.data.storage.ChatSession
import com.moodmix.app.data.storage.GeneratedFiles
import com.moodmix.app.data.storage.MessageStatus
import com.moodmix.app.domain.model.PlayableTrack
import com.moodmix.app.playback.PlayerController
import com.moodmix.app.ui.components.MoodChip
import com.moodmix.app.ui.theme.BrandPinkAccent
import com.moodmix.app.ui.theme.MintPrimary
import com.moodmix.app.data.storage.friendlyTitle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

internal val BrandBrush: Brush = Brush.linearGradient(listOf(MintPrimary, BrandPinkAccent))

internal fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

/** Soft mint glow top-left and pink glow bottom-right. Apply after .background(). Fades out at the top edge. */
internal fun Modifier.glowBackground(): Modifier = this.drawBehind {
    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
    drawRect(
        Brush.radialGradient(
            listOf(MintPrimary.copy(alpha = 0.16f), Color.Transparent),
            center = Offset(size.width * 0.15f, 90.dp.toPx()),
            radius = size.width * 0.9f
        )
    )
    drawRect(
        Brush.radialGradient(
            listOf(BrandPinkAccent.copy(alpha = 0.13f), Color.Transparent),
            center = Offset(size.width * 0.95f, size.height * 0.85f),
            radius = size.width * 0.9f
        )
    )
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

// ───────────────────────── Empty state ─────────────────────────

private val suggestions = listOf(
    "Lo-fi study" to "Lo-fi hip hop, mellow piano, soft vinyl crackle, rainy night, relaxed tempo",
    "Epic cinematic" to "Cinematic orchestral, soaring strings, powerful drums, heroic and emotional",
    "Gym energy" to "Upbeat electronic dance, driving bass, bright synths, high energy workout",
    "Calm acoustic" to "Soft acoustic guitar, warm and calm, gentle fingerpicking, sunset evening"
)

@Composable
fun EmptyChatState(onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(BrandBrush),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, null, tint = Color.Black, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "What should we create?",
            style = MaterialTheme.typography.headlineMedium.copy(brush = BrandBrush),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Describe the genre, mood and instruments. MoodMix turns it into a track.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        suggestions.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { (label, prompt) ->
                    MoodChip(text = label, onClick = { onSuggestion(prompt) })
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ───────────────────────── Messages ─────────────────────────

@Composable
fun UserBubble(message: ChatMessage) {
    val shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(Brush.linearGradient(listOf(MintPrimary.copy(alpha = 0.22f), BrandPinkAccent.copy(alpha = 0.22f))))
                .border(1.dp, Brush.linearGradient(listOf(MintPrimary.copy(alpha = 0.5f), BrandPinkAccent.copy(alpha = 0.5f))), shape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(message.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "${message.durationSeconds}s track",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AssistantMessage(message: ChatMessage, statusText: String, onExport: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(BrandBrush),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, null, tint = Color.Black, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            when (message.status) {
                MessageStatus.GENERATING -> GeneratingCard(statusText)
                MessageStatus.DONE -> TrackAttachment(message, onExport)
                MessageStatus.ERROR -> Text(
                    message.errorText ?: "Generation failed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
                MessageStatus.CANCELLED -> Text(
                    "Generation stopped.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun GeneratingCard(statusText: String) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape)
            .padding(16.dp)
    ) {
        GeneratingWave(Modifier.fillMaxWidth().height(44.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            statusText.ifBlank { "Composing your track…" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun GeneratingWave(modifier: Modifier = Modifier, bars: Int = 28) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    Canvas(modifier) {
        val gap = 3.dp.toPx()
        val barW = (size.width - gap * (bars - 1)) / bars
        val brush = Brush.horizontalGradient(listOf(MintPrimary, BrandPinkAccent), 0f, size.width)
        for (i in 0 until bars) {
            val level = 0.25f + 0.75f * abs(sin(phase + i * 0.45f))
            val h = size.height * level
            drawRoundRect(
                brush = brush,
                topLeft = Offset(i * (barW + gap), (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f)
            )
        }
    }
}

// ───────────────────────── Track attachment ─────────────────────────

@Composable
fun TrackAttachment(message: ChatMessage, onExport: () -> Unit) {
    val context = LocalContext.current
    val file = remember(message.audioFileName) {
        message.audioFileName?.let { GeneratedFiles.find(context, it) }
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, Brush.linearGradient(listOf(MintPrimary.copy(alpha = 0.45f), BrandPinkAccent.copy(alpha = 0.45f))), shape)
            .padding(14.dp)
    ) {
        if (file == null) {
            Text(
                "This audio file is no longer available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            AttachmentBody(file, message, onExport)
        }
    }
}

@Composable
private fun AttachmentBody(file: File, message: ChatMessage, onExport: () -> Unit) {
    val track = remember(file.path, message.text) {
        PlayableTrack(
            id = file.name,
            title = friendlyTitle(message.text),
            artist = "MoodMix AI",
            audioUri = Uri.fromFile(file).toString(),
            isGenerated = true
        )
    }
    val currentTrack by PlayerController.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by PlayerController.isPlaying.collectAsStateWithLifecycle()
    val isCurrent = currentTrack?.id == track.id
    val showPause = isCurrent && isPlaying

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(BrandBrush)
                    .clickable { if (isCurrent) PlayerController.togglePlay() else PlayerController.play(track) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (showPause) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (showPause) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "WAV • ${formatTime(message.durationSeconds * 1000L)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (message.exportedUri != null) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Saved to Music/MoodMix",
                    tint = MintPrimary,
                    modifier = Modifier.padding(12.dp)
                )
            } else {
                IconButton(onClick = onExport) {
                    Icon(Icons.Default.Download, contentDescription = "Download to Music folder")
                }
            }
        }
        if (isCurrent) PlaybackProgress()
    }
}

@Composable
private fun PlaybackProgress() {
    val position by PlayerController.position.collectAsStateWithLifecycle()
    val duration by PlayerController.duration.collectAsStateWithLifecycle()
    if (duration > 0L) {
        Slider(
            value = position.toFloat().coerceIn(0f, duration.toFloat()),
            onValueChange = { PlayerController.seekTo(it.toLong()) },
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(thumbColor = MintPrimary, activeTrackColor = MintPrimary)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatTime(duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ───────────────────────── Composer (chat-style input) ─────────────────────────

@Composable
fun ChatComposer(
    input: String,
    onInputChange: (String) -> Unit,
    duration: Float,
    onDurationChange: (Float) -> Unit,
    generatingHere: Boolean,
    busyElsewhere: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDuration by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    // The border animation only runs while generating.
    val phase: State<Float> = if (generatingHere) {
        val transition = rememberInfiniteTransition(label = "composerGlow")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
            label = "phase"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    val placeholder = if (busyElsewhere) "Another track is still generating…" else "Describe the music you want…"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val sw = 1.5.dp.toPx()
                val topLeft = Offset(sw / 2, sw / 2)
                val rectSize = Size(size.width - sw, size.height - sw)
                val radius = CornerRadius(28.dp.toPx())
                if (generatingHere || focused) {
                    val shift = size.width * phase.value
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(MintPrimary, BrandPinkAccent, MintPrimary),
                            start = Offset(shift, 0f),
                            end = Offset(shift + size.width, 0f),
                            tileMode = TileMode.Repeated
                        ),
                        topLeft = topLeft, size = rectSize, cornerRadius = radius, style = Stroke(sw)
                    )
                } else {
                    drawRoundRect(
                        color = outline,
                        topLeft = topLeft, size = rectSize, cornerRadius = radius, style = Stroke(sw)
                    )
                }
            },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 10.dp)) {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                enabled = !generatingHere,
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MintPrimary),
                maxLines = 5,
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        inner()
                    }
                }
            )

            AnimatedVisibility(visible = showDuration && !generatingHere) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Track length: ${duration.toInt()} s",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = duration,
                        onValueChange = onDurationChange,
                        valueRange = 30f..120f,
                        steps = 8, // 30, 40, … 120
                        colors = SliderDefaults.colors(thumbColor = MintPrimary, activeTrackColor = MintPrimary)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                DurationChip(
                    seconds = duration.toInt(),
                    enabled = !generatingHere,
                    onClick = { showDuration = !showDuration }
                )
                if (input.length > 320) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${input.length}/400",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (input.length >= 400) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "ACE-Step Turbo",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                SendStopButton(
                    generatingHere = generatingHere,
                    canSend = input.isNotBlank() && !busyElsewhere,
                    onSend = onSend,
                    onStop = onStop
                )
            }
        }
    }
}

@Composable
private fun DurationChip(seconds: Int, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Timer, contentDescription = "Track length", tint = MintPrimary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("${seconds}s", style = MaterialTheme.typography.labelLarge)
    }
}

/** One button: arrow = generate, square = stop (same position, same button). */
@Composable
private fun SendStopButton(
    generatingHere: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val active = generatingHere || canSend
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) BrandBrush else SolidColor(Color.White.copy(alpha = 0.10f)))
            .clickable(enabled = active) { if (generatingHere) onStop() else onSend() },
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = generatingHere, label = "sendStop") { stopping ->
            Icon(
                if (stopping) Icons.Default.Stop else Icons.Default.ArrowUpward,
                contentDescription = if (stopping) "Stop generating" else "Generate",
                tint = if (active) Color.Black else Color.White.copy(alpha = 0.4f)
            )
        }
    }
}

// ───────────────────────── History sidebar ─────────────────────────

@Composable
fun HistoryDrawer(
    sessions: List<ChatSession>,
    currentId: String,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (ChatSession) -> Unit
) {
    val chats = remember(sessions) {
        sessions.filter { it.messages.isNotEmpty() }.sortedByDescending { it.updatedAt }
    }
    ModalDrawerSheet {
        Text(
            "Chats",
            style = MaterialTheme.typography.titleLarge.copy(brush = BrandBrush),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 12.dp)
        )
        NavigationDrawerItem(
            label = { Text("New chat") },
            icon = { Icon(Icons.Default.Add, null) },
            selected = false,
            onClick = onNewChat,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(8.dp))

        if (chats.isEmpty()) {
            Text(
                "No chats yet. Generate a track and it will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(chats, key = { it.id }) { chat ->
                    val tracks = chat.messages.count { it.audioFileName != null }
                    val date = remember(chat.updatedAt) {
                        SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(chat.updatedAt))
                    }
                    val selected = chat.id == currentId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) MintPrimary.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { onSelect(chat.id) }
                            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                chat.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "$tracks track${if (tracks == 1) "" else "s"} • $date",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDelete(chat) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}