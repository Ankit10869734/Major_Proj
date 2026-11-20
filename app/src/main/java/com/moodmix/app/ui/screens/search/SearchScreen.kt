package com.moodmix.app.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moodmix.app.data.repository.MusicRepository
import com.moodmix.app.domain.model.Album
import com.moodmix.app.domain.model.Artist
import com.moodmix.app.domain.model.SearchFilter
import com.moodmix.app.playback.PlayerController
import com.moodmix.app.ui.components.AlbumDetailSheet
import com.moodmix.app.ui.components.ArtistDetailSheet
import com.moodmix.app.ui.components.CoverArt
import com.moodmix.app.ui.components.EmptyState
import com.moodmix.app.ui.components.ErrorState
import com.moodmix.app.ui.components.LoadingState
import com.moodmix.app.ui.components.MoodChip
import com.moodmix.app.ui.components.NeonSearchBar
import com.moodmix.app.ui.components.PillSearchInput
import com.moodmix.app.ui.components.SectionHeader
import com.moodmix.app.ui.components.TrackCard
import com.moodmix.app.ui.components.TrackRow
import com.moodmix.app.ui.theme.MintPrimary
import kotlinx.coroutines.delay

@Immutable
data class SearchCategory(
    val title: String,
    val promptText: String,
    val gradientColors: List<Color>,
    /** What actually gets searched when the tile is tapped. */
    val searchTerm: String = title
)

val searchCategories = listOf(
    SearchCategory("Pop & Upbeat", "Upbeat pop track with energetic synth beats", listOf(Color(0xFF8E24AA), Color(0xFFD81B60)), "upbeat pop"),
    SearchCategory("Lo-Fi & Study", "Chill lo-fi study beats with soft piano", listOf(Color(0xFF3949AB), Color(0xFF1E88E5)), "lofi study"),
    SearchCategory("Ambient & Calm", "Peaceful ambient soundscape for relaxation", listOf(Color(0xFF00897B), Color(0xFF43A047)), "ambient calm"),
    SearchCategory("Cinematic Epic", "Dramatic epic orchestral soundtrack for movies", listOf(Color(0xFFE65100), Color(0xFFF57C00)), "epic cinematic orchestral"),
    SearchCategory("Acoustic Vibes", "Warm acoustic guitar melody with gentle tempo", listOf(Color(0xFF6D4C41), Color(0xFF8D6E63)), "acoustic guitar"),
    SearchCategory("Electronic EDM", "High-energy electronic dance track with heavy bass", listOf(Color(0xFFC2185B), Color(0xFF7B1FA2)), "electronic dance edm")
)

@Composable
fun SearchScreen(
    onNavigateToGenerate: (String) -> Unit = {},
    viewModel: SearchViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var selectedArtist by remember { mutableStateOf<Artist?>(null) }
    var showVoiceSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadRecentQueries() }

    // System back from the search page returns to the landing page.
    BackHandler(enabled = uiState.isSearchOpen) { viewModel.closeSearch() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SearchLanding(
            recentQueries = uiState.recentQueries,
            onOpenSearch = { viewModel.openSearch() },
            onMicClick = { showVoiceSheet = true },
            onRecentClick = { viewModel.openSearch(it) },
            onClearHistory = { viewModel.clearSearchHistory() },
            onCategoryClick = { viewModel.openSearch(it.searchTerm) }
        )

        AnimatedVisibility(
            visible = uiState.isSearchOpen,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 16 },
            exit = fadeOut(tween(140))
        ) {
            SearchPage(
                uiState = uiState,
                viewModel = viewModel,
                onBack = { viewModel.closeSearch() },
                onMicClick = { showVoiceSheet = true },
                onArtistClick = { selectedArtist = it },
                onAlbumClick = { selectedAlbum = it }
            )
        }
    }

    if (showVoiceSheet) {
        VoiceSearchSheet(
            onDismiss = { showVoiceSheet = false },
            onQuery = { text ->
                showVoiceSheet = false
                viewModel.openSearch(text)
            }
        )
    }

    selectedAlbum?.let { album ->
        AlbumDetailSheet(album = album, onDismiss = { selectedAlbum = null })
    }
    selectedArtist?.let { artist ->
        ArtistDetailSheet(artist = artist, onDismiss = { selectedArtist = null })
    }
}

// ---------------------------------------------------------------------------------------------
// Page 1: landing
// ---------------------------------------------------------------------------------------------

@Composable
private fun SearchLanding(
    recentQueries: List<String>,
    onOpenSearch: () -> Unit,
    onMicClick: () -> Unit,
    onRecentClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    onCategoryClick: (SearchCategory) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(20.dp))

        NeonSearchBar(
            placeholderText = "Search songs, artists, or albums",
            onClick = onOpenSearch,
            onMicClick = onMicClick
        )

        Spacer(modifier = Modifier.height(26.dp))

        if (recentQueries.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(title = "Recent Searches", modifier = Modifier.weight(1f))
                TextButton(onClick = onClearHistory) {
                    Text("Clear", color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items = recentQueries, key = { it }) { query ->
                    MoodChip(text = "🕒 $query", onClick = { onRecentClick(query) })
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        SectionHeader(title = "Browse Categories")

        Spacer(modifier = Modifier.height(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(items = searchCategories, key = { it.title }) { category ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.6f)
                        .clickable { onCategoryClick(category) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(category.gradientColors))
                            .padding(14.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Page 2: full-screen black search page
// ---------------------------------------------------------------------------------------------

@Composable
private fun SearchPage(
    uiState: SearchUiState,
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onMicClick: () -> Unit,
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (Album) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { MusicRepository.getInstance(context) }
    val likedTrackIds by repository.likedTrackIds.collectAsStateWithLifecycle()
    val currentTrackId by PlayerController.currentTrackId.collectAsStateWithLifecycle()
    val isPlaying by PlayerController.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by PlayerController.isBuffering.collectAsStateWithLifecycle()

    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Focus the field right away when the page was opened empty (not when opened by voice / a chip).
    LaunchedEffect(Unit) {
        if (uiState.query.isBlank()) {
            delay(200)
            runCatching { focusRequester.requestFocus() }
        }
    }

    val filter = uiState.activeFilter
    val results = uiState.searchResult
    val songs = if (filter == SearchFilter.ALL || filter == SearchFilter.SONGS) results.songs else emptyList()
    val artists = if (filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS) results.artists else emptyList()
    val albums = if (filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS) results.albums else emptyList()
    val hasResults = songs.isNotEmpty() || artists.isNotEmpty() || albums.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Swallow taps so nothing underneath (the landing page) can be triggered.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(top = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            PillSearchInput(
                value = uiState.query,
                onValueChange = { viewModel.onQueryChanged(it) },
                onSearch = {
                    viewModel.submitQuery(it)
                    keyboard?.hide()
                },
                onMicClick = onMicClick,
                modifier = Modifier.weight(1f),
                focusRequester = focusRequester
            )
        }

        if (uiState.query.isNotBlank()) {
            SearchFilterRow(
                active = filter,
                onSelect = { viewModel.onFilterSelected(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (uiState.isSearching && hasResults) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    color = MintPrimary,
                    trackColor = Color.Transparent
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            when {
                uiState.query.isBlank() -> RecentSearchList(
                    recent = uiState.recentQueries,
                    onPick = { viewModel.submitQuery(it); keyboard?.hide() },
                    onFill = { viewModel.onQueryChanged(it) },
                    onClear = { viewModel.clearSearchHistory() }
                )

                uiState.isSearching && !hasResults -> LoadingState(message = "Searching music catalogue...")

                uiState.errorMessage != null -> ErrorState(
                    message = uiState.errorMessage,
                    onRetry = { viewModel.retry() }
                )

                !hasResults -> EmptyState(
                    title = "No search results",
                    subtitle = "We couldn't find any ${filterLabel(filter)} matching \"${uiState.query}\". Check spelling or try another keyword."
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
                ) {
                    if (songs.isNotEmpty()) {
                        item(key = "songs_header") { SectionHeader(title = "Songs") }
                        items(items = songs, key = { it.id }) { track ->
                            val isLiked = track.id in likedTrackIds
                            TrackRow(
                                title = track.title,
                                subtitle = "${track.artist} • 30s Preview",
                                coverModel = track.artworkUrl,
                                isActive = track.id == currentTrackId,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                onClick = {
                                    keyboard?.hide()
                                    viewModel.saveCurrentQuery()
                                    viewModel.playTrack(track, results.songs)
                                },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.toggleLike(track) }) {
                                        Icon(
                                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Like track",
                                            tint = if (isLiked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )
                        }
                    }

                    if (artists.isNotEmpty()) {
                        item(key = "artists_header") { SectionHeader(title = "Artists") }
                        if (filter == SearchFilter.ARTISTS) {
                            itemsIndexed(artists.chunked(3), key = { i, _ -> "artist_row_$i" }) { _, row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    row.forEach { ArtistCard(artist = it, onClick = { onArtistClick(it) }) }
                                }
                            }
                        } else {
                            item(key = "artists_row") {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    items(items = artists, key = { it.id }) { artist ->
                                        ArtistCard(artist = artist, onClick = { onArtistClick(artist) })
                                    }
                                }
                            }
                        }
                    }

                    if (albums.isNotEmpty()) {
                        item(key = "albums_header") { SectionHeader(title = "Albums") }
                        if (filter == SearchFilter.ALBUMS) {
                            itemsIndexed(albums.chunked(2), key = { i, _ -> "album_row_$i" }) { _, row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    row.forEach { album ->
                                        TrackCard(
                                            title = album.title,
                                            subtitle = album.artist,
                                            coverModel = album.artworkUrl,
                                            onClick = { onAlbumClick(album) }
                                        )
                                    }
                                }
                            }
                        } else {
                            item(key = "albums_row") {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    items(items = albums, key = { it.id }) { album ->
                                        TrackCard(
                                            title = album.title,
                                            subtitle = album.artist,
                                            coverModel = album.artworkUrl,
                                            onClick = { onAlbumClick(album) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun filterLabel(filter: SearchFilter): String = when (filter) {
    SearchFilter.SONGS -> "songs"
    SearchFilter.ARTISTS -> "artists"
    SearchFilter.ALBUMS -> "albums"
    else -> "songs, artists or albums"
}

@Composable
private fun SearchFilterRow(
    active: SearchFilter,
    onSelect: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        SearchFilter.ALL to "All",
        SearchFilter.SONGS to "Songs",
        SearchFilter.ARTISTS to "Artists",
        SearchFilter.ALBUMS to "Albums"
    )
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (filter, label) ->
            FilterChip(
                selected = active == filter,
                onClick = { onSelect(filter) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MintPrimary.copy(alpha = 0.18f),
                    selectedLabelColor = MintPrimary
                )
            )
        }
    }
}

@Composable
private fun RecentSearchList(
    recent: List<String>,
    onPick: (String) -> Unit,
    onFill: (String) -> Unit,
    onClear: () -> Unit
) {
    if (recent.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Search for songs, artists or albums",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Or tap the mic to speak, or to identify a song playing nearby",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "recent_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent searches",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                TextButton(onClick = onClear) {
                    Text("Clear all", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        items(items = recent, key = { it }) { query ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(query) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 16.dp)
                )
                Text(
                    text = query,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = { onFill(query) }) {
                    Icon(
                        imageVector = Icons.Default.NorthWest,
                        contentDescription = "Use this search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistCard(artist: Artist, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(90.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
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

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
