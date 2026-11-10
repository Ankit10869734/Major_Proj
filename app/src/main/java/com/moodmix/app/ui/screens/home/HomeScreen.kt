package com.moodmix.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.runtime.Immutable
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
import com.moodmix.app.ui.components.TrackRow
import java.util.Calendar

@Immutable
data class GreetingAndSplash(
    val greeting: String,
    val splashText: String
)

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
    val regionalThemeState by regionalThemePreferences.regionalThemeState.collectAsStateWithLifecycle(initialValue = RegionalThemeState())

    val snackbarHostState = remember { SnackbarHostState() }
    val headerData = remember { getDynamicGreetingAndSplash() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val currentTrackId by PlayerController.currentTrackId.collectAsStateWithLifecycle()
    val isPlaying by PlayerController.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by PlayerController.isBuffering.collectAsStateWithLifecycle()

    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var selectedArtist by remember { mutableStateOf<Artist?>(null) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }

    // Recommendation Prompt selecting contextually based on time of day
    val recommendedPrompt = remember(headerData.greeting) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 0..4 -> "quiet ambient music for sleep and relaxation"
            in 5..11 -> "upbeat acoustic morning music for focus"
            in 12..16 -> "energetic electronic beats for afternoon work"
            else -> "calm acoustic melody for evening relaxation"
        }
    }

    val displayMoodItems = remember(regionalThemeState.activeOccasion) {
        val festiveMoods = regionalThemeState.activeOccasion?.festiveMoods ?: emptyList()
        festiveMoods + baseMoodItems
    }

    LaunchedEffect(Unit) {
        viewModel.loadDiscoverContent(forceRefresh = false)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Clean Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headerData.greeting,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = headerData.splashText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "MoodMix AI Logo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Festive Occasion Badge if Active
            if (regionalThemeState.isFestiveActive) {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✨ ${regionalThemeState.activeOccasion?.name ?: "Festive Theme"} Active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // AI Prompt Recommendation Card (Displayed prompt matches clicked prompt & uses theme contrast colors!)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onNavigateToGenerate(recommendedPrompt)
                    },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Ask MoodMix AI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Try \"$recommendedPrompt\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.9f)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Start creation request →",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Your Mood Section
            SectionHeader(title = if (regionalThemeState.isFestiveActive) "Festive & Mood Vibes" else "Your Mood")

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = displayMoodItems,
                    key = { it.first }
                ) { (label, promptText) ->
                    MoodChip(
                        text = label,
                        onClick = {
                            onNavigateToGenerate(promptText)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (uiState.isLoading) {
                SectionHeader(title = "Loading Discover Feed...")
                Spacer(modifier = Modifier.height(12.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(160.dp))
            } else if (uiState.errorMessage != null) {
                ErrorState(
                    message = uiState.errorMessage!!,
                    onRetry = { viewModel.loadDiscoverContent(forceRefresh = true) }
                )
            } else {
                val content = uiState.content

                // 1. Recently Played Section
                if (content.recentlyPlayed.isNotEmpty()) {
                    SectionHeader(title = "Recently Played")
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        content.recentlyPlayed.take(3).forEach { track ->
                            val isActive = (track.id == currentTrackId)
                            TrackRow(
                                title = track.title,
                                subtitle = track.artist,
                                coverModel = track.artworkUrl,
                                isActive = isActive,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                onClick = { viewModel.playTrack(track, content.recentlyPlayed) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }

                // 2. Trending Songs Carousel
                if (content.trendingSongs.isNotEmpty()) {
                    SectionHeader(title = "Trending Songs")
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(
                            items = content.trendingSongs,
                            key = { index, track -> "${track.id}_$index" }
                        ) { _, track ->
                            val isActive = (track.id == currentTrackId)
                            TrackCard(
                                title = track.title,
                                subtitle = track.artist,
                                coverModel = track.artworkUrl,
                                isActive = isActive,
                                isPlaying = isPlaying,
                                onClick = { viewModel.playTrack(track, content.trendingSongs) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }

                // 3. Artists from Top Charts
                if (content.popularArtists.isNotEmpty()) {
                    SectionHeader(title = "Artists from Top Charts")
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
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

                // 4. New Releases
                if (content.newReleases.isNotEmpty()) {
                    SectionHeader(title = "New Album Releases")
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
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

                // 5. Featured Playlists
                if (content.madeForYou.isNotEmpty()) {
                    SectionHeader(title = "Made For You")
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
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
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Detail Bottom Sheets
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

private fun getDynamicGreetingAndSplash(): GreetingAndSplash {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    val (greetings, splashTexts) = when (hour) {
        in 0..4 -> {
            listOf("Late night session 🌙", "Late night vibes 🌌", "Midnight hours 🦉") to listOf(
                "Want some music to drift off to sleep?",
                "Soft melodies for quiet late-night thoughts ✨",
                "The world is asleep, but the music is alive 🎧"
            )
        }
        in 5..11 -> {
            listOf("Good morning 🌅", "Morning rhythm ☕", "Sunrise beats ☀️") to listOf(
                "Start your day with the perfect rhythm ☕",
                "Fresh morning coffee & fresh beats ☀️",
                "Sunrise melodies to power your morning 🌄"
            )
        }
        in 12..16 -> {
            listOf("Good afternoon ☀️", "Midday flow 🚀", "Afternoon vibes ⚡") to listOf(
                "Midday boost to keep you in the flow 🚀",
                "Unwind with your afternoon soundtrack ☕",
                "High focus or relaxing chill beats?"
            )
        }
        else -> { // 17..23
            listOf("Good evening 🌆", "Evening chill 🕯️", "Twilight soundscape 🌙") to listOf(
                "Time to relax and tune into your vibe 🌆",
                "Unwinding after a long day 🎶",
                "Let the music take over the night ✨"
            )
        }
    }

    val selectedGreeting = greetings.random()
    val selectedSplash = splashTexts.random()

    return GreetingAndSplash(selectedGreeting, selectedSplash)
}
