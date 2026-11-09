package com.moodmix.app.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Artist(
    val id: String,
    val name: String,
    val genre: String? = null,
    val artworkUrl: String? = null
)

@Immutable
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val releaseYear: String? = null,
    val trackCount: Int? = null
)

@Immutable
data class Playlist(
    val id: String,
    val title: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val trackCount: Int? = null,
    val tracks: List<PlayableTrack> = emptyList()
)

enum class SearchFilter {
    ALL,
    SONGS,
    ARTISTS,
    ALBUMS,
    PLAYLISTS
}

@Immutable
data class SearchQueryResult(
    val query: String,
    val songs: List<PlayableTrack> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)

@Immutable
data class DiscoverContent(
    val recentlyPlayed: List<PlayableTrack> = emptyList(),
    val madeForYou: List<Playlist> = emptyList(),
    val trendingSongs: List<PlayableTrack> = emptyList(),
    val popularArtists: List<Artist> = emptyList(),
    val newReleases: List<Album> = emptyList(),
    val moodRecommendations: List<PlayableTrack> = emptyList()
)
