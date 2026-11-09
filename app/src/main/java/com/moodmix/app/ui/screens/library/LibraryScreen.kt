package com.moodmix.app.ui.screens.library

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moodmix.app.domain.model.PlayableTrack
import com.moodmix.app.playback.PlayerController
import com.moodmix.app.ui.components.CoverArt
import com.moodmix.app.ui.components.EmptyState
import com.moodmix.app.ui.components.ErrorState
import com.moodmix.app.ui.components.LoadingState
import com.moodmix.app.ui.components.SearchField
import com.moodmix.app.ui.components.SectionHeader
import com.moodmix.app.ui.components.TrackRow
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToGenerate: (String) -> Unit = {},
    viewModel: LibraryViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val currentTrack by PlayerController.currentTrack.collectAsStateWithLifecycle()
    val currentTrackId by PlayerController.currentTrackId.collectAsStateWithLifecycle()
    val isPlaying by PlayerController.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by PlayerController.isBuffering.collectAsStateWithLifecycle()
    val position by PlayerController.position.collectAsStateWithLifecycle()
    val duration by PlayerController.duration.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadLibraryData()
    }

    val sheetState = rememberModalBottomSheetState()

    val displayTracks = remember(uiState.activeTab, uiState.searchQuery, uiState.generatedTracks, uiState.likedTracks, uiState.recentlyPlayedTracks) {
        val baseList = when (uiState.activeTab) {
            LibraryTab.ALL -> (uiState.generatedTracks + uiState.likedTracks + uiState.recentlyPlayedTracks).distinctBy { it.id }
            LibraryTab.GENERATED -> uiState.generatedTracks
            LibraryTab.LIKED -> uiState.likedTracks
            LibraryTab.RECENTLY_PLAYED -> uiState.recentlyPlayedTracks
        }
        if (uiState.searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter {
                it.title.contains(uiState.searchQuery, ignoreCase = true) ||
                it.artist.contains(uiState.searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Your Library",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Saved AI creations, liked songs & listening history",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        SearchField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            placeholderText = "Filter library by title or artist..."
        )

        if (!uiState.libraryInsights.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🤖 Qwen Listening Insights",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.libraryInsights!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Filter Tabs Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = uiState.activeTab == LibraryTab.ALL,
                    onClick = { viewModel.onTabSelected(LibraryTab.ALL) },
                    label = { Text("All (${(uiState.generatedTracks + uiState.likedTracks + uiState.recentlyPlayedTracks).distinctBy { it.id }.size})") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.activeTab == LibraryTab.GENERATED,
                    onClick = { viewModel.onTabSelected(LibraryTab.GENERATED) },
                    label = { Text("🤖 Generated (${uiState.generatedTracks.size})") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.activeTab == LibraryTab.LIKED,
                    onClick = { viewModel.onTabSelected(LibraryTab.LIKED) },
                    label = { Text("♥ Liked (${uiState.likedTracks.size})") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.activeTab == LibraryTab.RECENTLY_PLAYED,
                    onClick = { viewModel.onTabSelected(LibraryTab.RECENTLY_PLAYED) },
                    label = { Text("🕒 History (${uiState.recentlyPlayedTracks.size})") }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (uiState.isLoading) {
            LoadingState(message = "Loading library...")
        } else if (uiState.errorMessage != null) {
            ErrorState(
                message = uiState.errorMessage!!,
                onRetry = { viewModel.loadLibraryData() }
            )
        } else if (displayTracks.isEmpty()) {
            EmptyState(
                title = if (uiState.activeTab == LibraryTab.GENERATED) "No AI generated tracks yet" else "No tracks found",
                subtitle = if (uiState.activeTab == LibraryTab.GENERATED) "Create your first AI track in the Create tab!" else "Your library is empty. Discover or create tracks to build your library.",
                actionText = "Create track now →",
                onActionClick = { onNavigateToGenerate("") }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "section_header") {
                    SectionHeader(title = "${uiState.activeTab.name.replace('_', ' ')} (${displayTracks.size})")
                    Spacer(modifier = Modifier.height(6.dp))
                }

                items(
                    items = displayTracks,
                    key = { it.id }
                ) { track ->
                    val isTrackLiked = track.id in (uiState.likedTracks.map { it.id }.toSet())
                    val badgeText = if (track.isGenerated) "🤖 MoodMix AI" else track.artist
                    val isActive = (track.id == currentTrackId)

                    TrackRow(
                        title = track.title,
                        subtitle = "$badgeText • ${track.genre ?: "Music"}",
                        coverModel = track.artworkUrl,
                        isActive = isActive,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        onClick = {
                            viewModel.selectTrack(track)
                            viewModel.playTrack(track, displayTracks)
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.toggleLike(track) }) {
                                Icon(
                                    imageVector = if (isTrackLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Like track",
                                    tint = if (isTrackLiked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    // Modal Bottom Sheet for Selected Track Actions
    if (uiState.selectedTrack != null) {
        val track = uiState.selectedTrack!!
        val isCurrentTrackPlaying = currentTrack?.id == track.id

        ModalBottomSheet(
            onDismissRequest = { viewModel.selectTrack(null) },
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
                    CoverArt(
                        model = track.artworkUrl,
                        contentDescription = track.title,
                        modifier = Modifier.size(64.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = if (track.isGenerated) "🤖 MoodMix AI Generation" else track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (isCurrentTrackPlaying && duration > 0L) {
                    Slider(
                        value = position.toFloat().coerceIn(0f, duration.toFloat()),
                        onValueChange = { newPos ->
                            PlayerController.seekTo(newPos.toLong())
                        },
                        valueRange = 0f..duration.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(position), style = MaterialTheme.typography.bodySmall)
                        Text(formatTime(duration), style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (isCurrentTrackPlaying) {
                                PlayerController.togglePlay()
                            } else {
                                viewModel.playTrack(track, displayTracks)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isCurrentTrackPlaying && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(if (isCurrentTrackPlaying && isPlaying) "Pause" else "Play Track")
                        }
                    }

                    if (track.isGenerated && track.audioUri.startsWith("file:/")) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val file = File(Uri.parse(track.audioUri).path ?: "")
                                    if (file.exists()) {
                                        shareAudioFile(context, file)
                                    } else {
                                        Toast.makeText(context, "Audio file not found locally.", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (_: Exception) {
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Share File")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
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
