package com.moodmix.app.network

import java.io.File

/**
 * ============================================================================
 * MOODMIX BACKEND & API CONFIGURATION
 * ============================================================================
 *
 * PRODUCTION BACKEND:
 * Hosted on Render at https://moodmix-backend.onrender.com
 *
 * QWEN LLM API:
 * Hosted at https://valiant-program-parched.ngrok-free.dev
 * ============================================================================
 */
object ApiConfig {

    /*
     * DEPLOYED FASTAPI PRODUCTION BACKEND SERVER URL
     */
    const val BASE_URL = "https://moodmix-backend.onrender.com"

    /*
     * QWEN LLM API CONFIGURATION
     */
    const val QWEN_BASE_URL = "https://valiant-program-parched.ngrok-free.dev"

    val QWEN_ANALYZE_ENDPOINT: String
        get() = "$QWEN_BASE_URL/analyze"

    /*
     * API ENDPOINTS FOR DAYDREAM & FASTAPI BACKEND
     */
    val GENERATE_ENDPOINT: String
        get() = "$BASE_URL/generate"

    val STATUS_ENDPOINT: String
        get() = "$BASE_URL/status"

    val GENERATE_AND_WAIT_ENDPOINT: String
        get() = "$BASE_URL/generate-and-wait"

    val HEALTH_ENDPOINT: String
        get() = "$BASE_URL/health"

    val USAGE_ENDPOINT: String
        get() = "$BASE_URL/usage"

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
     */
    const val CONNECT_TIMEOUT_SECONDS = 45L
    const val WRITE_TIMEOUT_SECONDS = 45L
    const val READ_TIMEOUT_MINUTES = 10L
    const val CALL_TIMEOUT_MINUTES = 10L
}

/**
 * Outgoing payload sent to the backend server for music generation.
 */
data class MusicGenerationRequest(
    val prompt: String,
    val maxNewTokens: Int = 256,
    val generateLyrics: Boolean = false,
    val genre: String? = null,
    val bpm: Int? = null,
)

/**
 * Response payload received from the backend server when returning JSON.
 */
data class MusicGenerationResponse(
    val status: String? = "success",
    val songId: String? = null,
    val songTitle: String? = null,
    val audioUrl: String? = null,
    val lyrics: String? = null,
    val message: String? = null,
)

/**
 * Daydream polling status response payload.
 */
data class DaydreamStatusResponse(
    val status: String? = "completed",
    val songId: String? = null,
    val audioUrl: String? = null,
    val error: String? = null,
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
