package com.moodmix.app.ui.screens.home

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moodmix.app.data.repository.MusicRepository
import com.moodmix.app.domain.model.DiscoverContent
import com.moodmix.app.domain.model.PlayableTrack
import com.moodmix.app.playback.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val content: DiscoverContent = DiscoverContent(),
    val errorMessage: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository.getInstance(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun loadDiscoverContent(forceRefresh: Boolean = false) {
        if (hasLoaded && !forceRefresh && _uiState.value.errorMessage == null) {
            return
        }

        viewModelScope.launch {
            if (_uiState.value.content.trendingSongs.isEmpty()) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }
            try {
                val data = repository.loadDiscoverContent()
                _uiState.update { it.copy(isLoading = false, content = data, errorMessage = null) }
                hasLoaded = true
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load music discovery feed.") }
            }
        }
    }

    fun playTrack(track: PlayableTrack, queue: List<PlayableTrack>) {
        PlayerController.play(track, queue)
    }

    fun toggleLike(track: PlayableTrack) {
        viewModelScope.launch {
            repository.toggleLikeTrack(track)
        }
    }
}
