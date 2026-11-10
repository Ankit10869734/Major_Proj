package com.moodmix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodmix.app.data.repository.MusicRepository
import com.moodmix.app.domain.model.Album
import com.moodmix.app.domain.model.Artist
import com.moodmix.app.domain.model.PlayableTrack
import com.moodmix.app.domain.model.Playlist
import com.moodmix.app.playback.PlayerController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailSheet(
    album: Album,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { MusicRepository.getInstance(context) }
    var tracks by remember { mutableStateOf<List<PlayableTrack>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val currentTrackId by PlayerController.currentTrackId.collectAsStateWithLifecycle()
    val isPlaying by PlayerController.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by PlayerController.isBuffering.collectAsStateWithLifecycle()

    LaunchedEffect(album) {
        isLoading = true
        tracks = repository.getAlbumTracks(album.title, album.artist).distinctBy { it.id }
        isLoading = false
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isAlbumActive = currentTrackId != null && tracks.any { it.id == currentTrackId }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                    model = album.artworkUrl,
                    contentDescription = album.title,
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${album.artist} • ${album.releaseYear ?: "Album"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isLoading) {
                LoadingState(message = "Loading album tracks...")
            } else if (tracks.isEmpty()) {
                EmptyState(
                    title = "No audio preview available",
                    subtitle = "This album title is listed in the catalogue, but no 30s preview stream is available from the provider."
                )
            } else {
                PrimaryButton(
                    text = if (isAlbumActive && isPlaying) "⏸ Pause Album Previews" else "▶ Play Album Previews",
                    onClick = {
                        if (isAlbumActive) {
                            PlayerController.togglePlay()
                        } else {
                            PlayerController.play(tracks.first(), tracks)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                SectionHeader(title = "Album Tracks (${tracks.size})")

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(300.dp)
                ) {
                    itemsIndexed(
                        items = tracks,
                        key = { index, track -> "${track.id}_$index" }
                    ) { index, track ->
                        val isActive = (track.id == currentTrackId)
                        TrackRow(
                            title = track.title,
                            subtitle = "${track.artist} • 30s Preview",
                            coverModel = track.artworkUrl ?: album.artworkUrl,
                            isActive = isActive,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            onClick = { PlayerController.play(track, tracks) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailSheet(
    artist: Artist,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { MusicRepository.getInstance(context) }
    var topTracks by remember { mutableStateOf<List<PlayableTrack>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val currentTrackId by PlayerController.currentTrackId.collectAsStateWithLifecycle()
    val isPlaying by PlayerController.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by PlayerController.isBuffering.collectAsStateWithLifecycle()

    LaunchedEffect(artist) {
        isLoading = true
        topTracks = repository.getArtistTopTracks(artist.name).distinctBy { it.id }
        isLoading = false
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isArtistActive = currentTrackId != null && topTracks.any { it.id == currentTrackId }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CoverArt(
                        model = artist.artworkUrl,
                        contentDescription = artist.name,
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Artist • ${artist.genre ?: "Music"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isLoading) {
                LoadingState(message = "Loading top songs...")
            } else if (topTracks.isEmpty()) {
                EmptyState(
                    title = "No preview tracks found",
                    subtitle = "No 30s preview streams were found for ${artist.name}."
                )
            } else {
                PrimaryButton(
                    text = if (isArtistActive && isPlaying) "⏸ Pause Top Songs" else "▶ Play Top Songs",
                    onClick = {
                        if (isArtistActive) {
                            PlayerController.togglePlay()
                        } else {
                            PlayerController.play(topTracks.first(), topTracks)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                SectionHeader(title = "Top Songs")

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(300.dp)
                ) {
                    itemsIndexed(
                        items = topTracks,
                        key = { index, track -> "${track.id}_$index" }
                    ) { index, track ->
                        val isActive = (track.id == currentTrackId)
                        TrackRow(
                            title = track.title,
                            subtitle = "${track.album ?: artist.name} • 30s Preview",
                            coverModel = track.artworkUrl ?: artist.artworkUrl,
                            isActive = isActive,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            onClick = { PlayerController.play(track, topTracks) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailSheet(
    playlist: Playlist,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { MusicRepository.getInstance(context) }
    var tracks by remember { mutableStateOf<List<PlayableTrack>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val currentTrackId by PlayerController.currentTrackId.collectAsStateWithLifecycle()
    val isPlaying by PlayerController.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by PlayerController.isBuffering.collectAsStateWithLifecycle()

    LaunchedEffect(playlist) {
        isLoading = true
        tracks = repository.search(playlist.title).songs.distinctBy { it.id }
        isLoading = false
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isPlaylistActive = currentTrackId != null && tracks.any { it.id == currentTrackId }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                    model = playlist.artworkUrl,
                    contentDescription = playlist.title,
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = playlist.description ?: "Curated Playlist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isLoading) {
                LoadingState(message = "Loading collection...")
            } else if (tracks.isEmpty()) {
                EmptyState(
                    title = "Collection preview",
                    subtitle = "This collection contains curated music flow ideas."
                )
            } else {
                PrimaryButton(
                    text = if (isPlaylistActive && isPlaying) "⏸ Pause Collection" else "▶ Play Collection",
                    onClick = {
                        if (isPlaylistActive) {
                            PlayerController.togglePlay()
                        } else {
                            PlayerController.play(tracks.first(), tracks)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                SectionHeader(title = "Featured Songs (${tracks.size})")

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(300.dp)
                ) {
                    itemsIndexed(
                        items = tracks,
                        key = { index, track -> "${track.id}_$index" }
                    ) { index, track ->
                        val isActive = (track.id == currentTrackId)
                        TrackRow(
                            title = track.title,
                            subtitle = "${track.artist} • 30s Preview",
                            coverModel = track.artworkUrl ?: playlist.artworkUrl,
                            isActive = isActive,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            onClick = { PlayerController.play(track, tracks) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
