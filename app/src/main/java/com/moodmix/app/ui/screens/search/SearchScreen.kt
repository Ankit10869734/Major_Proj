package com.moodmix.app.ui.screens.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.content.ContextCompat
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
import com.moodmix.app.ui.components.SearchField
import com.moodmix.app.ui.components.SectionHeader
import com.moodmix.app.ui.components.TrackCard
import com.moodmix.app.ui.components.TrackRow
import java.util.Locale

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    RESULT,
    ERROR
}

@Immutable
data class SearchCategory(
    val title: String,
    val promptText: String,
    val gradientColors: List<Color>
)

val searchCategories = listOf(
    SearchCategory("Pop & Upbeat", "Upbeat pop track with energetic synth beats", listOf(Color(0xFF8E24AA), Color(0xFFD81B60))),
    SearchCategory("Lo-Fi & Study", "Chill lo-fi study beats with soft piano", listOf(Color(0xFF3949AB), Color(0xFF1E88E5))),
    SearchCategory("Ambient & Calm", "Peaceful ambient soundscape for relaxation", listOf(Color(0xFF00897B), Color(0xFF43A047))),
    SearchCategory("Cinematic Epic", "Dramatic epic orchestral soundtrack for movies", listOf(Color(0xFFE65100), Color(0xFFF57C00))),
    SearchCategory("Acoustic Vibes", "Warm acoustic guitar melody with gentle tempo", listOf(Color(0xFF6D4C41), Color(0xFF8D6E63))),
    SearchCategory("Electronic EDM", "High-energy electronic dance track with heavy bass", listOf(Color(0xFFC2185B), Color(0xFF7B1FA2)))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToGenerate: (String) -> Unit = {},
    viewModel: SearchViewModel = viewModel()
) {
    val context = LocalContext.current
    val repository = remember { MusicRepository.getInstance(context) }
    val likedTrackIds by repository.likedTrackIds.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val currentTrackId by PlayerController.currentTrackId.collectAsStateWithLifecycle()
    val isPlaying by PlayerController.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by PlayerController.isBuffering.collectAsStateWithLifecycle()

    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var selectedArtist by remember { mutableStateOf<Artist?>(null) }
    var showVoiceSearchSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadRecentQueries()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        SearchField(
            value = uiState.query,
            onValueChange = { viewModel.onQueryChanged(it) },
            placeholderText = "Search songs, artists, or albums...",
            onMicClick = { showVoiceSearchSheet = true }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Filter Chips Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.activeFilter == SearchFilter.ALL,
                onClick = { viewModel.onFilterSelected(SearchFilter.ALL) },
                label = { Text("All") }
            )
            FilterChip(
                selected = uiState.activeFilter == SearchFilter.SONGS,
                onClick = { viewModel.onFilterSelected(SearchFilter.SONGS) },
                label = { Text("Songs") }
            )
            FilterChip(
                selected = uiState.activeFilter == SearchFilter.ARTISTS,
                onClick = { viewModel.onFilterSelected(SearchFilter.ARTISTS) },
                label = { Text("Artists") }
            )
            FilterChip(
                selected = uiState.activeFilter == SearchFilter.ALBUMS,
                onClick = { viewModel.onFilterSelected(SearchFilter.ALBUMS) },
                label = { Text("Albums") }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (uiState.isSearching) {
            LoadingState(message = "Searching music catalogue...")
        } else if (uiState.errorMessage != null) {
            ErrorState(message = uiState.errorMessage!!, onRetry = { viewModel.onQueryChanged(uiState.query) })
        } else if (uiState.query.isNotBlank()) {
            // Live Search Results View
            val results = uiState.searchResult
            val hasResults = results.songs.isNotEmpty() || results.artists.isNotEmpty() || results.albums.isNotEmpty()

            if (!hasResults) {
                EmptyState(
                    title = "No search results",
                    subtitle = "We couldn't find any songs, artists or albums matching \"${uiState.query}\". Check spelling or search another keyword."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (results.songs.isNotEmpty()) {
                        item(key = "songs_header") {
                            SectionHeader(title = "Songs (${results.songs.size})")
                        }
                        items(
                            items = results.songs,
                            key = { it.id }
                        ) { track ->
                            val isLiked = track.id in likedTrackIds
                            val isActive = (track.id == currentTrackId)
                            TrackRow(
                                title = track.title,
                                subtitle = "${track.artist} • 30s Preview",
                                coverModel = track.artworkUrl,
                                isActive = isActive,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                onClick = { viewModel.playTrack(track, results.songs) },
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

                    if (results.artists.isNotEmpty()) {
                        item(key = "artists_header") {
                            SectionHeader(title = "Artists")
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(
                                    items = results.artists,
                                    key = { it.id }
                                ) { artist ->
                                    ArtistCard(artist = artist, onClick = { selectedArtist = artist })
                                }
                            }
                        }
                    }

                    if (results.albums.isNotEmpty()) {
                        item(key = "albums_header") {
                            SectionHeader(title = "Albums")
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(
                                    items = results.albums,
                                    key = { it.id }
                                ) { album ->
                                    TrackCard(
                                        title = album.title,
                                        subtitle = album.artist,
                                        coverModel = album.artworkUrl,
                                        onClick = { selectedAlbum = album }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Empty Query: Show Recent Searches + Browse Categories
            Column(modifier = Modifier.fillMaxSize()) {
                if (uiState.recentQueries.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "Recent Searches")
                        TextButton(onClick = { viewModel.clearSearchHistory() }) {
                            Text("Clear", color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(
                            items = uiState.recentQueries,
                            key = { it }
                        ) { query ->
                            MoodChip(
                                text = "🕒 $query",
                                onClick = { viewModel.onQueryChanged(query) }
                            )
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
                    items(
                        items = searchCategories,
                        key = { it.title }
                    ) { category ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.6f)
                                .clickable {
                                    viewModel.onQueryChanged(category.title)
                                },
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
    }

    if (showVoiceSearchSheet) {
        ModalBottomSheet(
            onDismissRequest = { showVoiceSearchSheet = false }
        ) {
            VoiceSearchWorkspace(
                context = context,
                onSearchSubmit = { recognizedQuery ->
                    viewModel.onQueryChanged(recognizedQuery)
                    showVoiceSearchSheet = false
                },
                onClose = { showVoiceSearchSheet = false }
            )
        }
    }

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
}

@Composable
fun VoiceSearchWorkspace(
    context: Context,
    onSearchSubmit: (String) -> Unit,
    onClose: () -> Unit
) {
    var voiceState by remember { mutableStateOf(VoiceState.IDLE) }
    var partialText by remember { mutableStateOf("") }
    var recognizedText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isRecognitionAvailable = remember(context) {
        SpeechRecognizer.isRecognitionAvailable(context)
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val listener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                voiceState = VoiceState.LISTENING
                errorMessage = null
            }

            override fun onBeginningOfSpeech() {
                voiceState = VoiceState.LISTENING
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                voiceState = VoiceState.PROCESSING
            }

            override fun onError(error: Int) {
                voiceState = VoiceState.ERROR
                val friendlyMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network connection error"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try speaking again."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy. Please try again."
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Language not supported"
                    else -> "Speech recognition failed (Error $error)"
                }
                errorMessage = friendlyMessage

                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    try {
                        recognizer?.cancel()
                        recognizer?.destroy()
                        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    } catch (_: Exception) {}
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    recognizedText = text
                    voiceState = VoiceState.RESULT
                } else {
                    voiceState = VoiceState.ERROR
                    errorMessage = "No speech recognized. Try speaking again."
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    partialText = text
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    DisposableEffect(context) {
        if (isRecognitionAvailable) {
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = sr
        }
        onDispose {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
        }
    }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        }
    }

    fun startListening() {
        errorMessage = null
        partialText = ""
        voiceState = VoiceState.LISTENING
        try {
            recognizer?.setRecognitionListener(listener)
            recognizer?.startListening(speechIntent)
        } catch (e: Exception) {
            voiceState = VoiceState.ERROR
            errorMessage = e.message ?: "Could not start speech recognizer"
        }
    }

    fun stopListening() {
        voiceState = VoiceState.PROCESSING
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "🎤 Voice Search & Recognition",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (!isRecognitionAvailable) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = "Speech recognition isn't available on this device",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (!hasPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Microphone Permission Required",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Grant audio recording permissions to search songs by voice.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text("Grant Permission")
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (voiceState) {
                        VoiceState.LISTENING -> {
                            Button(
                                onClick = { stopListening() },
                                shape = CircleShape,
                                modifier = Modifier.size(76.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("⏹", style = MaterialTheme.typography.headlineMedium)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Listening... Speak song or artist name",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (partialText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "\"$partialText\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        VoiceState.PROCESSING -> {
                            CircularProgressIndicator(strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Processing speech...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        VoiceState.IDLE, VoiceState.RESULT, VoiceState.ERROR -> {
                            Button(
                                onClick = { startListening() },
                                shape = CircleShape,
                                modifier = Modifier.size(76.dp)
                            ) {
                                Text("🎤", style = MaterialTheme.typography.headlineMedium)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (voiceState == VoiceState.RESULT) "Tap to speak again" else "Tap to start listening",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (recognizedText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Recognized Search Query (Edit if needed):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = recognizedText,
                            onValueChange = { recognizedText = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { onSearchSubmit(recognizedText) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = recognizedText.isNotBlank()
                        ) {
                            Text("Search Catalogue ▶")
                        }
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Close")
        }

        Spacer(modifier = Modifier.height(24.dp))
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
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
