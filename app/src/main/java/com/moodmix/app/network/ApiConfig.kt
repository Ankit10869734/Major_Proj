package com.moodmix.app.network

import java.io.File

/**
 * ============================================================================
 * MOODMIX BACKEND & API CONFIGURATION
 * ============================================================================
 *
 * BACKEND TEAM NOTE:
 * Change [BASE_URL] below to point to your backend server or Cloudflare tunnel.
 * Set [USE_MOCK_DATA] = false when testing live backend model server.
 *
 * Examples:
 * - Local Flask/FastAPI server: "http://10.0.2.2:8000" (Android Emulator)
 * - Local LAN server:           "http://192.168.1.50:8000"
 * - Cloudflare Quick Tunnel:   "https://YOUR-SUBDOMAIN.trycloudflare.com"
 * ============================================================================
 */
object ApiConfig {

    /**
     * Set to true to test UI flow locally without needing a live backend server.
     * Set to false when connecting to your team's live model server.
     */
    const val USE_MOCK_DATA = true

    /*
     * PASTE YOUR TEAM'S SERVER URL HERE
     */
    const val BASE_URL = "https://YOUR-TEAM-BACKEND-URL"

    /*
     * API ENDPOINTS
     */
    val GENERATE_ENDPOINT: String
        get() = "$BASE_URL/generate"

    val RECOMMENDATIONS_ENDPOINT: String
        get() = "$BASE_URL/recommendations"

    val VOICE_SEARCH_ENDPOINT: String
        get() = "$BASE_URL/voice-search"

    val SONG_ANALYSIS_ENDPOINT: String
        get() = "$BASE_URL/analyze-song"

    val LYRICS_INTELLIGENCE_ENDPOINT: String
        get() = "$BASE_URL/lyrics-intelligence"

    val AI_DJ_ENDPOINT: String
        get() = "$BASE_URL/ai-dj"

    /*
     * TIMEOUT SETTINGS
     * Music and lyrics generation can take longer than normal API calls.
     */
    const val CONNECT_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L
    const val READ_TIMEOUT_MINUTES = 10L
    const val CALL_TIMEOUT_MINUTES = 10L
}

/**
 * Outgoing payload sent to the backend model server for music generation.
 */
data class MusicGenerationRequest(
    val prompt: String,
    val maxNewTokens: Int = 256,
    val generateLyrics: Boolean = false,
    val genre: String? = null,
)

/**
 * Response payload received from the backend model server when returning JSON.
 */
data class MusicGenerationResponse(
    val status: String? = "success",
    val songTitle: String? = null,
    val audioUrl: String? = null,
    val lyrics: String? = null,
    val message: String? = null,
)

/**
 * Complete result structure containing the downloaded audio file and optional lyrics metadata.
 */
data class MusicGenerationResult(
    val audioFile: File,
    val lyrics: String? = null,
    val songTitle: String? = null,
    val rawResponse: MusicGenerationResponse? = null,
)

/**
 * Recommendation models
 */
data class RecommendationTrack(
    val title: String,
    val artist: String,
    val audioUrl: String? = null,
)

data class RecommendationsResponse(
    val status: String? = "success",
    val tracks: List<RecommendationTrack> = emptyList(),
)

/**
 * Voice search models
 */
data class VoiceSearchResponse(
    val status: String? = "success",
    val recognizedText: String? = null,
    val message: String? = null,
)

/**
 * Song analysis models
 */
data class SongAnalysisResponse(
    val status: String? = "success",
    val bpm: Int? = null,
    val keySignature: String? = null,
    val energyLevel: Float? = null,
    val primaryMood: String? = null,
)

/**
 * Lyrics intelligence models
 */
data class LyricsIntelligenceRequest(
    val lyricsText: String,
    val action: String = "analyze",
)

data class LyricsIntelligenceResponse(
    val status: String? = "success",
    val sentiment: String? = null,
    val rhymeScheme: String? = null,
    val summary: String? = null,
)

/**
 * AI DJ models
 */
data class AiDjRequest(
    val trackAUrl: String? = null,
    val trackBUrl: String? = null,
    val crossfadeDurationSeconds: Int = 4,
)

data class AiDjResponse(
    val status: String? = "success",
    val transitionAudioUrl: String? = null,
    val message: String? = null,
)
