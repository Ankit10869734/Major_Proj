package com.moodmix.app.ui.screens.search

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moodmix.app.data.repository.MusicRepository
import com.moodmix.app.domain.model.PlayableTrack
import com.moodmix.app.domain.model.SearchFilter
import com.moodmix.app.domain.model.SearchQueryResult
import com.moodmix.app.network.QwenApi
import com.moodmix.app.playback.PlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SearchUiState(
    val query: String = "",
    val activeFilter: SearchFilter = SearchFilter.ALL,
    val isSearching: Boolean = false,
    val searchResult: SearchQueryResult = SearchQueryResult(""),
    val recentQueries: List<String> = emptyList(),
    val errorMessage: String? = null
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository.getInstance(application)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun loadRecentQueries() {
        viewModelScope.launch {
            val queries = repository.getRecentSearchQueries()
            _uiState.update { it.copy(recentQueries = queries) }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery, errorMessage = null) }

        searchJob?.cancel()
        if (newQuery.isBlank()) {
            _uiState.update { it.copy(isSearching = false, searchResult = SearchQueryResult("")) }
            loadRecentQueries()
            return
        }

        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(isSearching = true) }
            try {
                val refinedQuery = QwenApi.interpretSearchQuery(newQuery) ?: newQuery
                val result = repository.search(refinedQuery, _uiState.value.activeFilter)
                _uiState.update { it.copy(isSearching = false, searchResult = result) }
                loadRecentQueries()
            } catch (e: CancellationException) {
                // CancellationException is normal when user types rapidly. Rethrow it!
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearching = false, errorMessage = e.message ?: "Search failed.") }
            }
        }
    }

    fun onFilterSelected(filter: SearchFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
        val currentQuery = _uiState.value.query
        if (currentQuery.isNotBlank()) {
            onQueryChanged(currentQuery)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
            _uiState.update { it.copy(recentQueries = emptyList()) }
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
