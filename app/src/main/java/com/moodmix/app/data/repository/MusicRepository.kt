package com.moodmix.app.data.repository

import android.content.Context
import com.moodmix.app.data.provider.iTunesMusicProvider
import com.moodmix.app.data.storage.LocalLibraryStorage
import com.moodmix.app.domain.model.Album
import com.moodmix.app.domain.model.Artist
import com.moodmix.app.domain.model.DiscoverContent
import com.moodmix.app.domain.model.PlayableTrack
import com.moodmix.app.domain.model.Playlist
import com.moodmix.app.domain.model.SearchFilter
import com.moodmix.app.domain.model.SearchQueryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class MusicRepository private constructor(context: Context) {

    private val applicationContext = context.applicationContext
    private val provider = iTunesMusicProvider()
    private val storage = LocalLibraryStorage(applicationContext)

    private val _likedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val likedTrackIds: StateFlow<Set<String>> = _likedTrackIds.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: MusicRepository? = null

        fun getInstance(context: Context): MusicRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicRepository(context).also { INSTANCE = it }
            }
        }
    }

    suspend fun loadDiscoverContent(): DiscoverContent = withContext(Dispatchers.IO) {
        val history = storage.getRecentlyPlayedTracks()
        val trending = provider.getTrendingSongs()
        val artists = provider.getPopularArtists()
        val albums = provider.getNewReleases()
        val playlists = provider.getFeaturedPlaylists()
        val moodTracks = provider.getMoodTracks("calm study")

        val likedList = storage.getLikedTracks()
        _likedTrackIds.update { likedList.map { track -> track.id }.toSet() }

        DiscoverContent(
            recentlyPlayed = history,
            madeForYou = playlists,
            trendingSongs = trending,
            popularArtists = artists,
            newReleases = albums,
            moodRecommendations = moodTracks
        )
    }

    suspend fun search(query: String, filter: SearchFilter = SearchFilter.ALL): SearchQueryResult = withContext(Dispatchers.IO) {
        if (query.isNotBlank()) {
            storage.addSearchQuery(query)
        }
        provider.search(query, filter)
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
        if (currentlyLiked) {
            storage.removeLikedTrack(track.id)
            _likedTrackIds.update { it - track.id }
            false
        } else {
            storage.saveLikedTrack(track.copy(isLiked = true))
            _likedTrackIds.update { it + track.id }
            true
        }
    }

    suspend fun recordPlayedTrack(track: PlayableTrack) = withContext(Dispatchers.IO) {
        storage.recordPlayedTrack(track)
    }

    suspend fun getRecentlyPlayed(): List<PlayableTrack> = withContext(Dispatchers.IO) {
        storage.getRecentlyPlayedTracks()
    }
}
