package com.moodmix.app.data.repository

import android.content.Context
import com.moodmix.app.data.provider.iTunesMusicProvider
import com.moodmix.app.data.storage.LocalLibraryStorage
import com.moodmix.app.domain.model.DiscoverContent
import com.moodmix.app.domain.model.PlayableTrack
import com.moodmix.app.domain.model.SearchFilter
import com.moodmix.app.domain.model.SearchQueryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class MusicRepository private constructor(context: Context) {

    private val applicationContext = context.applicationContext
    private val provider = iTunesMusicProvider()
    private val storage = LocalLibraryStorage(applicationContext)

    private val _likedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val likedTrackIds: StateFlow<Set<String>> = _likedTrackIds.asStateFlow()

    private val _libraryUpdateSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val libraryUpdateSignal: SharedFlow<Unit> = _libraryUpdateSignal.asSharedFlow()

    companion object {
        @Volatile
        private var INSTANCE: MusicRepository? = null

        fun getInstance(context: Context): MusicRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicRepository(context).also { INSTANCE = it }
            }
        }
    }

    fun notifyLibraryUpdated() {
        _libraryUpdateSignal.tryEmit(Unit)
    }

    suspend fun loadDiscoverContent(moodTerm: String? = null): DiscoverContent = withContext(Dispatchers.IO) {
        coroutineScope {
            val trending = async { provider.getTrendingSongs() }
            val artists = async { provider.getPopularArtists() }
            val albums = async { provider.getNewReleases() }
            val playlists = async { provider.getFeaturedPlaylists() }
            val moodTracks = async<List<PlayableTrack>> {
                if (moodTerm != null) provider.getMoodTracks(moodTerm) else emptyList()
            }
            val liked = async { storage.getLikedTracks() }

            _likedTrackIds.update { liked.await().map { track -> track.id }.toSet() }

            DiscoverContent(
                madeForYou = playlists.await(),
                trendingSongs = trending.await(),
                popularArtists = artists.await(),
                newReleases = albums.await(),
                moodRecommendations = moodTracks.await()
            )
        }
    }

    suspend fun getMoodTracks(term: String): List<PlayableTrack> = withContext(Dispatchers.IO) {
        provider.getMoodTracks(term)
    }

    suspend fun search(
        query: String,
        filter: SearchFilter = SearchFilter.ALL,
        recordHistory: Boolean = true
    ): SearchQueryResult = withContext(Dispatchers.IO) {
        if (recordHistory && query.isNotBlank()) {
            storage.addSearchQuery(query)
        }
        provider.search(query, filter)
    }

    suspend fun saveSearchQuery(query: String) = withContext(Dispatchers.IO) {
        if (query.isNotBlank()) {
            storage.addSearchQuery(query)
        }
    }

    suspend fun getArtistTopTracks(artistName: String): List<PlayableTrack> = withContext(Dispatchers.IO) {
        provider.getArtistTopTracks(artistName)
    }

    suspend fun getAlbumTracks(albumTitle: String, artistName: String): List<PlayableTrack> = withContext(Dispatchers.IO) {
        provider.getAlbumTracks(albumTitle, artistName)
    }

    suspend fun getRecentSearchQueries(): List<String> = withContext(Dispatchers.IO) {
        storage.getRecentSearchQueries()
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        storage.clearSearchHistory()
    }

    suspend fun getGeneratedTracks(): List<PlayableTrack> = withContext(Dispatchers.IO) {
        storage.migrateAndGetGeneratedTracks()
    }

    suspend fun getLikedTracks(): List<PlayableTrack> = withContext(Dispatchers.IO) {
        val tracks = storage.getLikedTracks()
        _likedTrackIds.update { tracks.map { it.id }.toSet() }
        tracks
    }

    suspend fun toggleLikeTrack(track: PlayableTrack): Boolean = withContext(Dispatchers.IO) {
        val currentlyLiked = storage.isTrackLiked(track.id)
        val isLiked = if (currentlyLiked) {
            storage.removeLikedTrack(track.id)
            _likedTrackIds.update { it - track.id }
            false
        } else {
            storage.saveLikedTrack(track.copy(isLiked = true))
            _likedTrackIds.update { it + track.id }
            true
        }
        notifyLibraryUpdated()
        isLiked
    }

    suspend fun recordPlayedTrack(track: PlayableTrack) = withContext(Dispatchers.IO) {
        storage.recordPlayedTrack(track)
        notifyLibraryUpdated()
    }

    suspend fun getRecentlyPlayed(): List<PlayableTrack> = withContext(Dispatchers.IO) {
        storage.getRecentlyPlayedTracks()
    }
}
