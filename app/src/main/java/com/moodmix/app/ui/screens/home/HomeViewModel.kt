package com.moodmix.app.ui.screens.home

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moodmix.app.data.repository.MusicRepository
import com.moodmix.app.domain.model.DiscoverContent
import com.moodmix.app.domain.model.PlayableTrack
import com.moodmix.app.playback.PlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val content: DiscoverContent = DiscoverContent(),
    val header: HomeHeader = HomePersonalizer.build(emptyList(), emptyList(), emptyList()),
    val errorMessage: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository.getInstance(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var hasLoaded = false
    private var lastFeedTerm: String? = null

    private suspend fun buildHeader(): HomeHeader {
        return try {
            HomePersonalizer.build(
                recent = repository.getRecentlyPlayed(),
                liked = repository.getLikedTracks(),
                queries = repository.getRecentSearchQueries()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _uiState.value.header
        }
    }

    fun loadDiscoverContent(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            // Rebuilt every time Home is shown, so the text/chips follow the user's latest taste
            val header = buildHeader()
            _uiState.update { it.copy(header = header) }

            val alreadyLoaded = hasLoaded && !forceRefresh && _uiState.value.errorMessage == null
            if (alreadyLoaded) {
                refreshPersonalFeed(header)
                return@launch
            }

            if (_uiState.value.content.trendingSongs.isEmpty()) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }
            try {
                val data = repository.loadDiscoverContent(moodTerm = header.feedTerm)
                lastFeedTerm = header.feedTerm
                _uiState.update { it.copy(isLoading = false, content = data, errorMessage = null) }
                hasLoaded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load music discovery feed.")
                }
            }
        }
    }

    // Only re-fetches the "Because you searched..." row, and only when the taste term changed
    private suspend fun refreshPersonalFeed(header: HomeHeader) {
        if (header.feedTerm == lastFeedTerm) return
        val tracks = try {
            header.feedTerm?.let { repository.getMoodTracks(it) } ?: emptyList()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        lastFeedTerm = header.feedTerm
        _uiState.update { it.copy(content = it.content.copy(moodRecommendations = tracks)) }
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