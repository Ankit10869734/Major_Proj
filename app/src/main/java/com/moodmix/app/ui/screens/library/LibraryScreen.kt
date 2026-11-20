package com.moodmix.app.ui.screens.library

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moodmix.app.playback.PlayerController
import com.moodmix.app.ui.components.CoverArt
import com.moodmix.app.ui.components.EmptyState
import com.moodmix.app.ui.components.ErrorState
import com.moodmix.app.ui.components.LoadingState
import com.moodmix.app.ui.components.SearchField
import java.io.File

private fun sectionTitle(tab: LibraryTab): String = when (tab) {
    LibraryTab.ALL -> "All tracks"
    LibraryTab.GENERATED -> "AI generated"
    LibraryTab.LIKED -> "Liked songs"
    LibraryTab.RECENTLY_PLAYED -> "Recently played"
}

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

    LaunchedEffect(Unit) { viewModel.loadLibraryData() }

    val sheetState = rememberModalBottomSheetState()

    val likedIds = remember(uiState.likedTracks) { uiState.likedTracks.map { it.id }.toSet() }
    val allCount = remember(uiState.generatedTracks, uiState.likedTracks, uiState.recentlyPlayedTracks) {
        (uiState.generatedTracks + uiState.likedTracks + uiState.recentlyPlayedTracks).distinctBy { it.id }.size
    }

    val displayTracks = remember(uiState.activeTab, uiState.searchQuery, uiState.generatedTracks, uiState.likedTracks, uiState.recentlyPlayedTracks) {
        val baseList = when (uiState.activeTab) {
            LibraryTab.ALL -> (uiState.generatedTracks + uiState.likedTracks + uiState.recentlyPlayedTracks).distinctBy { it.id }
            LibraryTab.GENERATED -> uiState.generatedTracks
            LibraryTab.LIKED -> uiState.likedTracks
            LibraryTab.RECENTLY_PLAYED -> uiState.recentlyPlayedTracks
        }
        if (uiState.searchQuery.isBlank()) baseList
        else baseList.filter {
            it.title.contains(uiState.searchQuery, ignoreCase = true) ||
                it.artist.contains(uiState.searchQuery, ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .libraryGlow()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp)
        ) {
            LibraryHeader()

            Spacer(Modifier.height(18.dp))

            SearchField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholderText = "Filter by title or artist…"
            )

            if (!uiState.libraryInsights.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                LibraryInsightCard(uiState.libraryInsights!!)
            }

            Spacer(Modifier.height(14.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    LibraryFilterChip("All", allCount, Icons.Default.LibraryMusic, uiState.activeTab == LibraryTab.ALL) {
                        viewModel.onTabSelected(LibraryTab.ALL)
                    }
                }
                item {
                    LibraryFilterChip("Generated", uiState.generatedTracks.size, Icons.Default.AutoAwesome, uiState.activeTab == LibraryTab.GENERATED) {
                        viewModel.onTabSelected(LibraryTab.GENERATED)
                    }
                }
                item {
                    LibraryFilterChip("Liked", uiState.likedTracks.size, Icons.Default.Favorite, uiState.activeTab == LibraryTab.LIKED) {
                        viewModel.onTabSelected(LibraryTab.LIKED)
                    }
                }
                item {
                    LibraryFilterChip("History", uiState.recentlyPlayedTracks.size, Icons.Default.History, uiState.activeTab == LibraryTab.RECENTLY_PLAYED) {
                        viewModel.onTabSelected(LibraryTab.RECENTLY_PLAYED)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

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
                    subtitle = if (uiState.activeTab == LibraryTab.GENERATED) "Create your first AI track in the Create tab." else "Your library is empty. Discover or create tracks to build your library.",
                    actionText = "Create a track",
                    onActionClick = { onNavigateToGenerate("") }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item(key = "section_header") {
                        LibrarySectionTitle(sectionTitle(uiState.activeTab), displayTracks.size)
                        Spacer(Modifier.height(2.dp))
                    }

                    items(items = displayTracks, key = { it.id }) { track ->
                        LibraryTrackRow(
                            track = track,
                            isActive = track.id == currentTrackId,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            isLiked = track.id in likedIds,
                            onClick = {
                                viewModel.selectTrack(track)
                                viewModel.playTrack(track, displayTracks)
                            },
                            onToggleLike = { viewModel.toggleLike(track) }
                        )
                    }
                }
            }
        }
    }

    // Bottom sheet for the selected track
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
                    if (track.isGenerated) {
                        GeneratedCover(Modifier.size(64.dp))
                    } else {
                        CoverArt(
                            model = track.artworkUrl,
                            contentDescription = track.title,
                            modifier = Modifier.size(64.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (track.isGenerated) {
                                "AI generated" + if (track.durationMs > 0) " · ${formatDuration(track.durationMs)}" else ""
                            } else track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (isCurrentTrackPlaying && duration > 0L) {
                    Slider(
                        value = position.toFloat().coerceIn(0f, duration.toFloat()),
                        onValueChange = { PlayerController.seekTo(it.toLong()) },
                        valueRange = 0f..duration.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDuration(position), style = MaterialTheme.typography.bodySmall)
                        Text(formatDuration(duration), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(14.dp))
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            if (isCurrentTrackPlaying) PlayerController.togglePlay()
                            else viewModel.playTrack(track, displayTracks)
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
                            Spacer(Modifier.size(6.dp))
                            Text(if (isCurrentTrackPlaying && isPlaying) "Pause" else "Play")
                        }
                    }

                    if (track.isGenerated && track.audioUri.startsWith("file:/")) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val file = File(Uri.parse(track.audioUri).path ?: "")
                                    if (file.exists()) shareAudioFile(context, file)
                                    else Toast.makeText(context, "Audio file not found locally.", Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Share file")
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
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