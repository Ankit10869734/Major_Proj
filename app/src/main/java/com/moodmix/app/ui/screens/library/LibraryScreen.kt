package com.moodmix.app.ui.screens.library

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LibraryAudioItem(
    val title: String,
    val subtitle: String,
    val file: File? = null,
    val isDemo: Boolean = false,
    val genre: String = "Generated Audio",
    val durationText: String = "WAV Audio"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToGenerate: (String) -> Unit = {}
) {
    val context = LocalContext.current

    var realGeneratedTracks by remember { mutableStateOf<List<LibraryAudioItem>>(emptyList()) }

    val demoPlaylists = listOf(
        LibraryAudioItem("My Favorites (Demo)", "Playlist • 18 tracks", isDemo = true, genre = "Mixed Moods", durationText = "1h 12m"),
        LibraryAudioItem("Study Sessions (Demo)", "Playlist • 24 tracks", isDemo = true, genre = "Lo-Fi & Ambient", durationText = "1h 45m"),
        LibraryAudioItem("Workout Mix (Demo)", "Playlist • 15 tracks", isDemo = true, genre = "Electronic & Energy", durationText = "54m")
    )

    // Load actual generated files from cache directory
    LaunchedEffect(Unit) {
        realGeneratedTracks = loadGeneratedAudioFiles(context)
    }

    var selectedItem by remember { mutableStateOf<LibraryAudioItem?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackPosition by remember { mutableLongStateOf(0L) }
    var playbackDuration by remember { mutableLongStateOf(0L) }

    val player = remember(context) { ExoPlayer.Builder(context).build() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    playbackDuration = player.duration.coerceAtLeast(0L)
                } else if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(selectedItem, isPlaying) {
        while (isActive && isPlaying) {
            playbackPosition = player.currentPosition
            if (player.duration > 0) {
                playbackDuration = player.duration
            }
            delay(250)
        }
    }

    val sheetState = rememberModalBottomSheetState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {

        Text(
            text = "Your Library",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Saved audio tracks, history & playlists",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "Generated Tracks (${realGeneratedTracks.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (realGeneratedTracks.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "No generated tracks yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Create custom music tracks in the Generate tab and they will appear here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { onNavigateToGenerate("") },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Go to Generate →")
                            }
                        }
                    }
                }
            } else {
                items(realGeneratedTracks) { item ->
                    LibraryRowItem(
                        item = item,
                        onClick = {
                            player.pause()
                            selectedItem = item
                            playbackPosition = 0L
                            playbackDuration = 0L
                            if (item.file != null && item.file.exists()) {
                                player.setMediaItem(MediaItem.fromUri(item.file.toUri()))
                                player.prepare()
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Demo Playlists",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            items(demoPlaylists) { item ->
                LibraryRowItem(
                    item = item,
                    onClick = {
                        player.pause()
                        selectedItem = item
                        playbackPosition = 0L
                        playbackDuration = 0L
                        val sampleUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
                        player.setMediaItem(MediaItem.fromUri(sampleUrl))
                        player.prepare()
                    }
                )
            }
        }
    }

    /*
     * Audio Details & Playback Bottom Sheet
     */
    if (selectedItem != null) {
        val item = selectedItem!!

        ModalBottomSheet(
            onDismissRequest = {
                player.pause()
                selectedItem = null
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = if (item.isDemo) "📂" else "♫",
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (playbackDuration > 0L) {
                    Slider(
                        value = playbackPosition.toFloat().coerceIn(0f, playbackDuration.toFloat()),
                        onValueChange = { newPos ->
                            player.seekTo(newPos.toLong())
                            playbackPosition = newPos.toLong()
                        },
                        valueRange = 0f..playbackDuration.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(playbackPosition), style = MaterialTheme.typography.bodySmall)
                        Text(formatTime(playbackDuration), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (player.isPlaying) {
                                player.pause()
                            } else {
                                player.play()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (isPlaying) "Pause" else "▶ Play Audio")
                    }

                    if (item.file != null && item.file.exists()) {
                        OutlinedButton(
                            onClick = { shareAudioFile(context, item.file) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Share File")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                player.pause()
                                selectedItem = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Close")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun LibraryRowItem(
    item: LibraryAudioItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (item.isDemo) "📂" else "♫",
                style = MaterialTheme.typography.headlineSmall
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (item.isDemo) {
                Text(
                    text = "DEMO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun loadGeneratedAudioFiles(context: Context): List<LibraryAudioItem> {
    val items = mutableListOf<LibraryAudioItem>()
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    val audioDir = File(context.cacheDir, "generated_audio")
    if (audioDir.exists()) {
        val files = audioDir.listFiles { _, name -> name.endsWith(".wav") || name.endsWith(".mp3") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        for (file in files) {
            if (file.length() > 0L) {
                val dateStr = dateFormat.format(Date(file.lastModified()))
                val sizeKb = file.length() / 1024
                items.add(
                    LibraryAudioItem(
                        title = file.name,
                        subtitle = "Generated • $dateStr • ${sizeKb}KB",
                        file = file,
                        isDemo = false
                    )
                )
            }
        }
    }

    return items
}

private fun shareAudioFile(context: Context, file: File) {
    val contentUri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("MoodMix track", contentUri)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share Audio Track"))
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
