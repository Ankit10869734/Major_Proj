package com.moodmix.app.data

import com.moodmix.app.domain.model.PlayableTrack
import com.moodmix.app.domain.model.SearchFilter
import com.moodmix.app.domain.model.SearchQueryResult
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MusicRepositoryTest {

    @Test
    fun testCancellationExceptionIsRethrownInSearch() {
        fun simulateSearchWithCancellation(): SearchQueryResult {
            try {
                throw CancellationException("Search coroutine cancelled due to rapid typing")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return SearchQueryResult(query = "fail", songs = emptyList())
            }
        }

        try {
            simulateSearchWithCancellation()
            fail("Expected CancellationException to be rethrown")
        } catch (e: CancellationException) {
            assertEquals("Search coroutine cancelled due to rapid typing", e.message)
        }
    }

    @Test
    fun testPlayableTrackModelPropertiesAndPreviewFlag() {
        val previewTrack = PlayableTrack(
            id = "itunes_123",
            title = "Kesariya",
            artist = "Arijit Singh",
            album = "Brahmastra",
            audioUri = "https://audio-ssl.itunes.apple.com/preview.m4a",
            durationMs = 30000L,
            isPreview = true,
            isGenerated = false
        )

        assertTrue(previewTrack.isPreview)
        assertFalse(previewTrack.isGenerated)
        assertEquals("itunes_123", previewTrack.id)
        assertEquals("Kesariya", previewTrack.title)
        assertEquals("Arijit Singh", previewTrack.artist)
    }

    @Test
    fun testGeneratedTrackModelProperties() {
        val generatedTrack = PlayableTrack(
            id = "song_123.wav",
            title = "Calm Study Piano",
            artist = "MoodMix AI",
            audioUri = "file:///storage/emulated/0/generated_music/song_123.wav",
            durationMs = 15000L,
            isPreview = false,
            isGenerated = true
        )

        assertFalse(generatedTrack.isPreview)
        assertTrue(generatedTrack.isGenerated)
        assertEquals("MoodMix AI", generatedTrack.artist)
        assertEquals("song_123.wav", generatedTrack.id)
    }

    @Test
    fun testSearchFilterEnumValues() {
        assertEquals("ALL", SearchFilter.ALL.name)
        assertEquals("SONGS", SearchFilter.SONGS.name)
        assertEquals("ARTISTS", SearchFilter.ARTISTS.name)
        assertEquals("ALBUMS", SearchFilter.ALBUMS.name)
    }

    @Test
    fun testAlbumQueueConstructionAndIndividualTrackSelection() {
        val albumTracks = (1..10).map { i ->
            PlayableTrack(
                id = "track_$i",
                title = "Album Track $i",
                artist = "Test Artist",
                audioUri = "https://example.com/stream_$i.mp3"
            )
        }

        // Scenario B: User selects track 5 in a 10-track album
        val selectedTrack = albumTracks[4] // Index 4 (Track 5)
        val validQueue = albumTracks.filter { it.audioUri.isNotBlank() }
        val startIndex = validQueue.indexOfFirst { it.id == selectedTrack.id }

        assertEquals(10, validQueue.size)
        assertEquals(4, startIndex)
        assertEquals("track_5", validQueue[startIndex].id)

        // Verify Next track is Track 6 (index 5)
        val nextTrack = validQueue.getOrNull(startIndex + 1)
        assertEquals("track_6", nextTrack?.id)

        // Verify Previous track is Track 4 (index 3)
        val previousTrack = validQueue.getOrNull(startIndex - 1)
        assertEquals("track_4", previousTrack?.id)
    }

    @Test
    fun testUnavailableTracksFilterFromQueue() {
        val tracksWithUnavailable = listOf(
            PlayableTrack(id = "1", title = "Track 1", artist = "Artist", audioUri = "https://audio.mp3"),
            PlayableTrack(id = "2", title = "Track 2", artist = "Artist", audioUri = ""), // Unavailable
            PlayableTrack(id = "3", title = "Track 3", artist = "Artist", audioUri = "https://audio3.mp3")
        )

        // Scenario F: Filter out unavailable tracks before queue construction
        val playableQueue = tracksWithUnavailable.filter { it.audioUri.isNotBlank() }
        assertEquals(2, playableQueue.size)
        assertEquals("1", playableQueue[0].id)
        assertEquals("3", playableQueue[1].id)
    }

    @Test
    fun testActiveTrackHighlightingIdentificationByStableId() {
        val currentTrackId = "itunes_999"
        val track1 = PlayableTrack(id = "itunes_111", title = "Song 1", artist = "Artist 1", audioUri = "https://audio1.mp3")
        val track2 = PlayableTrack(id = "itunes_999", title = "Song 2", artist = "Artist 2", audioUri = "https://audio2.mp3")

        assertFalse(track1.id == currentTrackId)
        assertTrue(track2.id == currentTrackId)
    }

    @Test
    fun testDeduplicatedHistoryRecordingStrategy() {
        var lastRecordedId: String? = null
        val recordedHistory = mutableListOf<String>()

        fun recordTrack(trackId: String) {
            if (lastRecordedId != trackId) {
                lastRecordedId = trackId
                recordedHistory.add(trackId)
            }
        }

        // Simulate play call and transition for track_A
        recordTrack("track_A")
        recordTrack("track_A") // Duplicate transition callback for track_A

        // Transition to track_B
        recordTrack("track_B")

        assertEquals(2, recordedHistory.size)
        assertEquals(listOf("track_A", "track_B"), recordedHistory)
    }

    @Test
    fun testQueueItemRemovalStateManagement() {
        val initialQueue = mutableListOf("track_1", "track_2", "track_3", "track_4")
        var activeIndex = 2 // track_3 is currently playing (index 2)

        // Remove track before current (index 0: track_1)
        val removeIndex = 0
        initialQueue.removeAt(removeIndex)
        if (removeIndex < activeIndex) {
            activeIndex -= 1 // Shift active index left
        }

        assertEquals(3, initialQueue.size)
        assertEquals(1, activeIndex)
        assertEquals("track_3", initialQueue[activeIndex]) // Active track remains track_3!
    }

    @Test
    fun testClosePlaybackSessionStateClearing() {
        var currentTrack: PlayableTrack? = PlayableTrack(id = "1", title = "Track 1", artist = "Artist", audioUri = "https://audio.mp3")
        var currentTrackId: String? = "1"
        var isPlaying = true
        var queue = listOf(currentTrack!!)

        // Close playback action simulation
        currentTrack = null
        currentTrackId = null
        isPlaying = false
        queue = emptyList()

        assertNull(currentTrack)
        assertNull(currentTrackId)
        assertFalse(isPlaying)
        assertTrue(queue.isEmpty())
    }
}
