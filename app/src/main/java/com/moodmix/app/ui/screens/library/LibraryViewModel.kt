package com.moodmix.app.ui.screens.library

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moodmix.app.data.repository.MusicRepository
import com.moodmix.app.domain.model.PlayableTrack
import com.moodmix.app.network.QwenApi
import com.moodmix.app.playback.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibraryTab {
    ALL,
    GENERATED,
    LIKED,
    RECENTLY_PLAYED
}

@Immutable
data class LibraryUiState(
    val activeTab: LibraryTab = LibraryTab.ALL,
    val searchQuery: String = "",
    val generatedTracks: List<PlayableTrack> = emptyList(),
    val likedTracks: List<PlayableTrack> = emptyList(),
    val recentlyPlayedTracks: List<PlayableTrack> = emptyList(),
    val selectedTrack: PlayableTrack? = null,
    val libraryInsights: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository.getInstance(application)

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var hasLoadedData = false

    fun loadLibraryData(forceRefresh: Boolean = false) {
        if (hasLoadedData && !forceRefresh && _uiState.value.errorMessage == null) {
            return
        }

        viewModelScope.launch {
            if (_uiState.value.generatedTracks.isEmpty() && _uiState.value.likedTracks.isEmpty()) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }

            try {
                val generated = withContext(Dispatchers.IO) { repository.getGeneratedTracks() }
                val liked = withContext(Dispatchers.IO) { repository.getLikedTracks() }
                val history = withContext(Dispatchers.IO) { repository.getRecentlyPlayed() }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        generatedTracks = generated,
                        likedTracks = liked,
                        recentlyPlayedTracks = history,
                        errorMessage = null
                    )
                }
                hasLoadedData = true

                // Fetch Qwen AI Insights asynchronously in the background so UI isn't blocked
                viewModelScope.launch {
                    val insights = QwenApi.generateLibraryInsights(history, liked)
                    if (insights != null) {
                        _uiState.update { it.copy(libraryInsights = insights) }
                    }
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load library.")
                }
            }
        }
    }

    fun onTabSelected(tab: LibraryTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectTrack(track: PlayableTrack?) {
        _uiState.update { it.copy(selectedTrack = track) }
    }

    fun playTrack(track: PlayableTrack, queue: List<PlayableTrack>) {
        PlayerController.play(track, queue)
    }

    fun toggleLike(track: PlayableTrack) {
        viewModelScope.launch {
            repository.toggleLikeTrack(track)
            loadLibraryData(forceRefresh = true)
        }
    }
}
