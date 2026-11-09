package com.moodmix.app.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class PlayableTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val audioUri: String,
    val genre: String? = null,
    val durationMs: Long = 0L,
    val isPreview: Boolean = false,
    val isGenerated: Boolean = false,
    val isLiked: Boolean = false,
    val addedTimestamp: Long = System.currentTimeMillis()
)
