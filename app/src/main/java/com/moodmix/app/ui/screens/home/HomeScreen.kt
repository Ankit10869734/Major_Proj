package com.moodmix.app.ui.screens.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moodmix.app.data.theme.RegionalThemePreferences
import com.moodmix.app.domain.model.Album
import com.moodmix.app.domain.model.Artist
import com.moodmix.app.domain.model.PlayableTrack
import com.moodmix.app.domain.model.Playlist
import com.moodmix.app.domain.model.RegionalThemeState
import com.moodmix.app.playback.PlayerController
import com.moodmix.app.ui.components.AlbumDetailSheet
import com.moodmix.app.ui.components.ArtistDetailSheet
import com.moodmix.app.ui.components.CoverArt
import com.moodmix.app.ui.components.ErrorState
import com.moodmix.app.ui.components.MoodChip
import com.moodmix.app.ui.components.PlaylistDetailSheet
import com.moodmix.app.ui.components.SectionHeader
import com.moodmix.app.ui.components.ShimmerBox
import com.moodmix.app.ui.components.TrackCard

// Kept in case other files still reference it. Safe to delete if nothing does.
val baseMoodItems = listOf(
    "😊 Happy" to "Upbeat happy energetic pop track",
    "😌 Calm" to "Peaceful calm ambient acoustic music",
    "🔥 Energetic" to "High energy workout electronic music",
    "💭 Focus" to "Instrumental lo-fi beats for deep study",
    "🌙 Sad" to "Melancholic slow piano melody"
)

@Composable
fun HomeScreen(
    paddingValues: PaddingValues = PaddingValues(0.dp),
    onNavigateToGenerate: (String) -> Unit = {},
    onNavigateToTools: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val regionalThemePreferences = remember { RegionalThemePreferences.getInstance(context) }
    val regionalThemeState by regionalThemePreferences.regionalThemeState
        .collectAsStateWithLifecycle(initialValue = RegionalThemeState())

    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val header = uiState.header

    val currentTrackId by PlayerController.currentTrackId.collectAsStateWithLifecycle()
    val isPlaying by PlayerController.isPlaying.collectAsStateWithLifecycle()

    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var selectedArtist by remember { mutableStateOf<Artist?>(null) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }

    val displayMoodItems = remember(header.moods, regionalThemeState.activeOccasion) {
        val festiveMoods = regionalThemeState.activeOccasion?.festiveMoods ?: emptyList()
        festiveMoods + header.moods
    }

    LaunchedEffect(Unit) {
        viewModel.loadDiscoverContent(forceRefresh = false)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(innerPadding),
            contentPadding = PaddingValues(top = 20.dp, bottom = 32.dp)
        ) {
            // Header (no AI button)
            item(key = "header") {
                Crossfade(
                    targetState = header,
                    label = "home_header",
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) { h ->
                    Column {
                        Text(
                            text = h.greeting,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = h.splashText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Festive badge
            if (regionalThemeState.isFestiveActive) {
                item(key = "festive") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = "✨ ${regionalThemeState.activeOccasion?.name ?: "Festive Theme"} Active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // AI prompt card (personalised)
            item(key = "ai_card") {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f)
                                )
                            )
                        )
                        .clickable { onNavigateToGenerate(header.aiPrompt) }
                        .padding(22.dp)
                ) {
                    Column {
                        Text(
                            text = "Create with MoodMix AI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Try \"${header.aiPrompt}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.9f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Start creating →",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            // Mood chips (dynamic)
            item(key = "moods") {
                SectionTitle(
                    if (regionalThemeState.isFestiveActive) "Festive & Mood Vibes" else header.moodSectionTitle
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = displayMoodItems,
                        key = { it.first }
                    ) { (label, promptText) ->
                        MoodChip(
                            text = label,
                            onClick = { onNavigateToGenerate(promptText) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }

            if (uiState.isLoading) {
                item(key = "loading") {
                    SectionTitle("Loading your feed...")
                    Spacer(modifier = Modifier.height(12.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                }
            } else if (uiState.errorMessage != null) {
                item(key = "error") {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        ErrorState(
                            message = uiState.errorMessage!!,
                            onRetry = { viewModel.loadDiscoverContent(forceRefresh = true) }
                        )
                    }
                }
            } else {
                val content = uiState.content
                val feedTitle = header.feedTitle

                // 1. Personal row: "Because you searched ..."
                if (feedTitle != null && content.moodRecommendations.isNotEmpty()) {
                    item(key = "personal_feed") {
                        TrackCarousel(
                            title = feedTitle,
                            tracks = content.moodRecommendations,
                            currentTrackId = currentTrackId,
                            isPlaying = isPlaying,
                            onTrackClick = { viewModel.playTrack(it, content.moodRecommendations) }
                        )
                    }
                }

                // 2. Trending
                if (content.trendingSongs.isNotEmpty()) {
                    item(key = "trending") {
                        TrackCarousel(
                            title = "Trending Songs",
                            tracks = content.trendingSongs,
                            currentTrackId = currentTrackId,
                            isPlaying = isPlaying,
                            onTrackClick = { viewModel.playTrack(it, content.trendingSongs) }
                        )
                    }
                }

                // 3. Artists
                if (content.popularArtists.isNotEmpty()) {
                    item(key = "artists") {
                        SectionTitle("Artists from Top Charts")
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(
                                items = content.popularArtists,
                                key = { index, artist -> "${artist.id}_$index" }
                            ) { _, artist ->
                                ArtistCard(artist = artist, onClick = { selectedArtist = artist })
                            }
                        }
                        Spacer(modifier = Modifier.height(28.dp))
                    }
                }

                // 4. New releases
                if (content.newReleases.isNotEmpty()) {
                    item(key = "new_releases") {
                        SectionTitle("New Album Releases")
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(
                                items = content.newReleases,
                                key = { index, album -> "${album.id}_$index" }
                            ) { _, album ->
                                TrackCard(
                                    title = album.title,
                                    subtitle = "${album.artist} • ${album.releaseYear ?: "New"}",
                                    coverModel = album.artworkUrl,
                                    onClick = { selectedAlbum = album }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(28.dp))
                    }
                }

                // 5. Made for you
                if (content.madeForYou.isNotEmpty()) {
                    item(key = "made_for_you") {
                        SectionTitle("Made For You")
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(
                                items = content.madeForYou,
                                key = { index, playlist -> "${playlist.id}_$index" }
                            ) { _, playlist ->
                                TrackCard(
                                    title = playlist.title,
                                    subtitle = playlist.description ?: "Curated flow",
                                    coverModel = playlist.artworkUrl,
                                    onClick = { selectedPlaylist = playlist }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Detail bottom sheets
        if (selectedAlbum != null) {
            AlbumDetailSheet(
                album = selectedAlbum!!,
                onDismiss = { selectedAlbum = null }
            )
        }

        if (selectedArtist != null) {
            ArtistDetailSheet(
                artist = selectedArtist!!,
                onDismiss = { selectedArtist = null }
            )
        }

        if (selectedPlaylist != null) {
            PlaylistDetailSheet(
                playlist = selectedPlaylist!!,
                onDismiss = { selectedPlaylist = null }
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
        SectionHeader(title = title)
    }
}

@Composable
private fun TrackCarousel(
    title: String,
    tracks: List<PlayableTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onTrackClick: (PlayableTrack) -> Unit
) {
    Column {
        SectionTitle(title)
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(
                items = tracks,
                key = { index, track -> "${track.id}_$index" }
            ) { _, track ->
                TrackCard(
                    title = track.title,
                    subtitle = track.artist,
                    coverModel = track.artworkUrl,
                    isActive = (track.id == currentTrackId),
                    isPlaying = isPlaying,
                    onClick = { onTrackClick(track) }
                )
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun ArtistCard(
    artist: Artist,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(90.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (artist.artworkUrl != null) {
                CoverArt(
                    model = artist.artworkUrl,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
