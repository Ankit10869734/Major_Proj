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
import kotlinx.coroutines.withTimeoutOrNull

@Immutable
data class SearchUiState(
    val query: String = "",
    val activeFilter: SearchFilter = SearchFilter.ALL,
    val isSearchOpen: Boolean = false,
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

    /** Opens the full-screen search page. With a non-blank [initialQuery] it also runs that search. */
    fun openSearch(initialQuery: String = "") {
        _uiState.update { it.copy(isSearchOpen = true) }
        if (initialQuery.isBlank()) {
            loadRecentQueries()
        } else {
            submitQuery(initialQuery)
        }
    }

    /** Back to the normal search landing page. */
    fun closeSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                isSearchOpen = false,
                query = "",
                activeFilter = SearchFilter.ALL,
                isSearching = false,
                searchResult = SearchQueryResult(""),
                errorMessage = null
            )
        }
        loadRecentQueries()
    }

    /** Called on every keystroke: debounced live search that does NOT pollute search history. */
    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery, errorMessage = null) }
        if (newQuery.isBlank()) {
            searchJob?.cancel()
            _uiState.update { it.copy(isSearching = false, searchResult = SearchQueryResult("")) }
            loadRecentQueries()
            return
        }
        launchSearch(newQuery, debounceMs = 350, saveToHistory = false)
    }

    /** Keyboard "search", voice result, recent-search tap, category tap: runs immediately and is saved to history. */
    fun submitQuery(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        _uiState.update { it.copy(query = q, errorMessage = null) }
        launchSearch(q, debounceMs = 0, saveToHistory = true)
    }

    fun retry() {
        val q = _uiState.value.query
        if (q.isNotBlank()) launchSearch(q, debounceMs = 0, saveToHistory = false)
    }

    /** Filter chips are applied in the UI on the already-fetched results, so they switch instantly. */
    fun onFilterSelected(filter: SearchFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    /** Remember the current query when the user actually plays something from the results. */
    fun saveCurrentQuery() {
        val q = _uiState.value.query.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            repository.saveSearchQuery(q)
            loadRecentQueries()
        }
    }

    private fun launchSearch(query: String, debounceMs: Long, saveToHistory: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            _uiState.update { it.copy(isSearching = true, errorMessage = null) }
            try {
                val term = refine(query)
                val result = repository.search(term, SearchFilter.ALL, recordHistory = false)
                _uiState.update { it.copy(isSearching = false, searchResult = result) }
                if (saveToHistory) {
                    repository.saveSearchQuery(query)
                    loadRecentQueries()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearching = false, errorMessage = e.message ?: "Search failed.") }
            }
        }
    }

    /**
     * Only descriptive, sentence-like queries ("songs for a rainy night drive") go through Qwen.
     * Short queries like "harry styles" are searched literally: faster and no risk of being rewritten.
     */
    private suspend fun refine(query: String): String {
        val wordCount = query.trim().split(Regex("\\s+")).size
        if (wordCount < 4) return query
        val refined = withTimeoutOrNull(1500) { QwenApi.interpretSearchQuery(query) }
        return refined?.takeIf { it.isNotBlank() } ?: query
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
