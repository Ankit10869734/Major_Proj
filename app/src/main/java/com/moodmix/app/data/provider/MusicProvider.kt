package com.moodmix.app.data.provider

import com.moodmix.app.domain.model.Album
import com.moodmix.app.domain.model.Artist
import com.moodmix.app.domain.model.PlayableTrack
import com.moodmix.app.domain.model.Playlist
import com.moodmix.app.domain.model.SearchFilter
import com.moodmix.app.domain.model.SearchQueryResult

interface MusicProvider {
    val providerName: String
    suspend fun search(query: String, filter: SearchFilter = SearchFilter.ALL): SearchQueryResult
    suspend fun getTrendingSongs(): List<PlayableTrack>
    suspend fun getPopularArtists(): List<Artist>
    suspend fun getNewReleases(): List<Album>
    suspend fun getFeaturedPlaylists(): List<Playlist>
    suspend fun getMoodTracks(moodPrompt: String): List<PlayableTrack>
    suspend fun getArtistTopTracks(artistName: String): List<PlayableTrack>
    suspend fun getAlbumTracks(albumTitle: String, artistName: String): List<PlayableTrack>
}
