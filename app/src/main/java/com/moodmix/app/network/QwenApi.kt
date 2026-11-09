package com.moodmix.app.network

import com.moodmix.app.audio.SongAnalysis
import com.moodmix.app.domain.model.PlayableTrack
import com.moodmix.app.lyrics.LyricsAnalysis
import com.moodmix.app.lyrics.LyricsAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Centralized Client for the MoodMix Qwen LLM API.
 * Provides task-based Qwen semantic intelligence across Search, Generate, Library, and Extras modules,
 * with automatic fallback to local MoodMix logic on timeout or network error.
 */
object QwenApi {

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun postTask(content: String, taskName: String = "lyrics_analysis"): String = withContext(Dispatchers.IO) {
        val requestJson = JSONObject().apply {
            put("task", taskName)
            put("content", content)
        }

        val request = Request.Builder()
            .url(ApiConfig.QWEN_ANALYZE_ENDPOINT)
            .addHeader("Content-Type", "application/json")
            .addHeader("ngrok-skip-browser-warning", "true")
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = okHttpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Qwen API HTTP error: ${resp.code}")
            }

            val responseBody = resp.body?.string() ?: throw IllegalStateException("Empty response body from Qwen API")
            val json = JSONObject(responseBody)
            val rawResult = json.optString("result", "")
            if (rawResult.isBlank()) {
                throw IllegalStateException("No result string returned from Qwen API")
            }

            stripMarkdownFences(rawResult)
        }
    }

    fun stripMarkdownFences(text: String): String {
        var trimmed = text.trim()
        if (trimmed.startsWith("```")) {
            val firstLineBreak = trimmed.indexOf('\n')
            if (firstLineBreak != -1) {
                trimmed = trimmed.substring(firstLineBreak + 1)
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length - 3)
            }
        }
        return trimmed.trim()
    }

    suspend fun analyzeLyrics(lyricsText: String): LyricsAnalysis {
        val resultText = postTask(lyricsText, "lyrics_analysis")
        return parseLyricsResult(resultText, lyricsText)
    }

    suspend fun interpretSearchQuery(query: String): String? = withContext(Dispatchers.IO) {
        try {
            val prompt = "Extract 2-3 concise search keywords from this search query: '$query'"
            val result = postTask(prompt, "lyrics_analysis")
            if (result.isNotBlank()) {
                val cleaned = stripMarkdownFences(result)
                if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
                    val json = JSONObject(cleaned)
                    val themes = json.optJSONArray("themes")
                    val genres = json.optJSONArray("genres")
                    val keywords = mutableListOf<String>()
                    if (themes != null) {
                        for (i in 0 until themes.length()) keywords.add(themes.optString(i))
                    }
                    if (genres != null) {
                        for (i in 0 until genres.length()) keywords.add(genres.optString(i))
                    }
                    if (keywords.isNotEmpty()) keywords.take(3).joinToString(" ") else null
                } else {
                    cleaned.take(100)
                }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun interpretSongAnalysis(analysis: SongAnalysis): String? = withContext(Dispatchers.IO) {
        try {
            val content = "Audio DSP Metrics: Duration=${analysis.durationSec}s, BPM=${analysis.bpm ?: "unknown"}, Key=${analysis.key ?: "unknown"}, Energy=${(analysis.energy * 100).toInt()}%, Loudness=${analysis.loudnessDb}dB, Vibe=${analysis.mood}."
            val result = postTask("Provide a short 2-sentence musical analysis explanation for these audio metrics: $content", "lyrics_analysis")
            if (result.isNotBlank()) {
                val cleaned = stripMarkdownFences(result)
                if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
                    val json = JSONObject(cleaned)
                    json.optString("explanation", json.optString("summary", cleaned))
                } else cleaned
            } else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun generateLibraryInsights(recent: List<PlayableTrack>, liked: List<PlayableTrack>): String? = withContext(Dispatchers.IO) {
        try {
            val trackTitles = (recent.take(5) + liked.take(5)).map { "${it.title} by ${it.artist}" }.distinct()
            if (trackTitles.isEmpty()) return@withContext null

            val content = "User Listening History & Liked Tracks: ${trackTitles.joinToString("; ")}"
            val result = postTask("Summarize the listener's musical taste in 2 concise sentences based on these saved tracks: $content", "lyrics_analysis")
            if (result.isNotBlank()) {
                val cleaned = stripMarkdownFences(result)
                if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
                    val json = JSONObject(cleaned)
                    json.optString("explanation", json.optString("summary", cleaned))
                } else cleaned
            } else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun interpretAiDjTransition(trackA: String, trackB: String, bpmA: Int?, bpmB: Int?): String? = withContext(Dispatchers.IO) {
        try {
            val content = "Track A: '$trackA' (${bpmA ?: 120} BPM), Track B: '$trackB' (${bpmB ?: 120} BPM)"
            val result = postTask("Provide a short DJ crossfade transition commentary for these tracks: $content", "lyrics_analysis")
            if (result.isNotBlank()) {
                val cleaned = stripMarkdownFences(result)
                if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
                    val json = JSONObject(cleaned)
                    json.optString("explanation", json.optString("summary", cleaned))
                } else cleaned
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLyricsResult(resultText: String, originalLyrics: String): LyricsAnalysis {
        val fallbackHeuristic = LyricsAnalyzer.analyze(originalLyrics)

        return try {
            if (resultText.startsWith("{") && resultText.endsWith("}")) {
                val json = JSONObject(resultText)

                val explanation = json.optString("explanation", "").ifBlank {
                    json.optString("summary", "").ifBlank {
                        json.optString("analysis", "").ifBlank { resultText }
                    }
                }

                val sentiment = json.optString("sentiment", "").ifBlank {
                    json.optString("emotional_tone", "").ifBlank {
                        json.optString("listening_mood", "Positive")
                    }
                }

                val themesArray = json.optJSONArray("themes")
                val themesList = mutableListOf<String>()
                if (themesArray != null) {
                    for (i in 0 until themesArray.length()) {
                        themesList.add(themesArray.optString(i))
                    }
                }

                val themesText = if (themesList.isNotEmpty()) {
                    " (Themes: ${themesList.joinToString(", ")})"
                } else ""

                fallbackHeuristic.copy(
                    summary = "Qwen LLM: $explanation$themesText",
                    sentiment = "Qwen AI: ${sentiment.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }}",
                    sentimentScore = if (sentiment.lowercase(Locale.ROOT).contains("positive")) 0.85f else if (sentiment.lowercase(Locale.ROOT).contains("negative")) -0.85f else 0.0f
                )
            } else {
                fallbackHeuristic.copy(
                    summary = "Qwen LLM: $resultText",
                    sentiment = "Qwen AI Analysis"
                )
            }
        } catch (_: Exception) {
            fallbackHeuristic.copy(
                summary = "Qwen LLM: $resultText",
                sentiment = "Qwen AI Analysis"
            )
        }
    }
}
