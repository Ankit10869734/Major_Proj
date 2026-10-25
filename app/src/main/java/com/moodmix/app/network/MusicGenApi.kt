package com.moodmix.app.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Service for communicating with the MoodMix backend music & lyrics generation model.
 */
object MusicGenApi {

    /**
     * Toggle flag for testing UI flow without a live server.
     * Managed by [ApiConfig.USE_MOCK_DATA].
     */
    const val USE_MOCK_DATA = ApiConfig.USE_MOCK_DATA

    /*
     * OkHttpClient configured using central settings from [ApiConfig].
     */
    private val client = OkHttpClient.Builder()
        .connectTimeout(ApiConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(ApiConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(ApiConfig.READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        .callTimeout(ApiConfig.CALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Backward-compatible generate method.
     * Accepts prompt, maxNewTokens, and optional generateLyrics flag,
     * returning the generated audio [File].
     */
    suspend fun generate(
        context: Context,
        prompt: String,
        maxNewTokens: Int = 256,
        generateLyrics: Boolean = false,
    ): File = withContext(Dispatchers.IO) {
        val result = generateWithDetails(
            context = context,
            request = MusicGenerationRequest(
                prompt = prompt,
                maxNewTokens = maxNewTokens,
                generateLyrics = generateLyrics,
            )
        )
        result.audioFile
    }

    /**
     * Detailed generation method returning [MusicGenerationResult]
     * which contains the local audio [File], generated lyrics text (if any), and metadata.
     */
    suspend fun generateWithDetails(
        context: Context,
        request: MusicGenerationRequest,
    ): MusicGenerationResult = withContext(Dispatchers.IO) {

        if (USE_MOCK_DATA) {
            // Simulate AI model generation latency
            delay(1500)

            val promptSnippet = request.prompt.trim()
                .take(25)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

            val mockTitle = if (promptSnippet.isNotBlank()) {
                "MoodMix - $promptSnippet"
            } else {
                "MoodMix - Midnight Vibes"
            }

            val mockLyrics = if (request.generateLyrics) {
                """
                [Verse 1]
                Walking through the quiet neon lights,
                Whispering thoughts into the midnight skies.
                Searching for the rhythm in the quiet sound,
                Lost in the music that we finally found.

                [Chorus]
                Oh, MoodMix playing soft and low,
                Feel the wave and let it flow.
                Underneath the starlight glow,
                This is the vibe we know.

                [Verse 2]
                A sudden melody floating through the air,
                Harmonies turning around every care.
                Beat drops softly like autumn rain,
                Washing away the stress and pain.

                [Outro]
                Fading out into the night...
                """.trimIndent()
            } else {
                null
            }

            val mockAudioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"

            val audioFile = try {
                downloadAudioFile(context, mockAudioUrl)
            } catch (_: Exception) {
                createDummyWavFile(context)
            }

            return@withContext MusicGenerationResult(
                audioFile = audioFile,
                lyrics = mockLyrics,
                songTitle = mockTitle,
                rawResponse = MusicGenerationResponse(
                    status = "success",
                    songTitle = mockTitle,
                    audioUrl = mockAudioUrl,
                    lyrics = mockLyrics,
                    message = "Mock generation completed successfully.",
                )
            )
        }

        val json = JSONObject().apply {
            put("prompt", request.prompt)
            put("max_new_tokens", request.maxNewTokens)
            put("generate_lyrics", request.generateLyrics)
            if (!request.genre.isNullOrBlank()) {
                put("genre", request.genre)
            }
        }

        val requestBody = json.toString().toRequestBody(jsonMediaType)

        val httpRequest = Request.Builder()
            .url(ApiConfig.GENERATE_ENDPOINT)
            .post(requestBody)
            .build()

        client.newCall(httpRequest).execute().use { response ->

            if (!response.isSuccessful) {
                val serverMessage = response.body?.string().orEmpty()
                throw IOException(
                    "Server returned ${response.code}" +
                            if (serverMessage.isNotBlank()) ": $serverMessage" else ""
                )
            }

            val responseBody = response.body
                ?: throw IOException("Server returned an empty response.")

            val contentType = responseBody.contentType()?.toString()?.lowercase().orEmpty()

            if (contentType.contains("application/json")) {
                val jsonStr = responseBody.string()
                val jsonObject = JSONObject(jsonStr)

                val status = jsonObject.optString("status", "success")
                val songTitle = if (jsonObject.has("song_title")) jsonObject.optString("song_title")
                                else if (jsonObject.has("title")) jsonObject.optString("title") else null
                val lyrics = if (jsonObject.has("lyrics")) jsonObject.optString("lyrics")
                             else if (jsonObject.has("lyrics_text")) jsonObject.optString("lyrics_text") else null
                val audioUrl = if (jsonObject.has("audio_url")) jsonObject.optString("audio_url")
                               else if (jsonObject.has("audioUrl")) jsonObject.optString("audioUrl") else null
                val message = if (jsonObject.has("message")) jsonObject.optString("message") else null

                val parsedResponse = MusicGenerationResponse(
                    status = status,
                    songTitle = songTitle,
                    audioUrl = audioUrl,
                    lyrics = lyrics,
                    message = message,
                )

                val outputFile: File = if (!audioUrl.isNullOrBlank()) {
                    val fullAudioUrl = if (audioUrl.startsWith("http://") || audioUrl.startsWith("https://")) {
                        audioUrl
                    } else {
                        "${ApiConfig.BASE_URL.removeSuffix("/")}/${audioUrl.removePrefix("/")}"
                    }
                    downloadAudioFile(context, fullAudioUrl)
                } else {
                    throw IOException("Server returned JSON response without an audio URL.")
                }

                MusicGenerationResult(
                    audioFile = outputFile,
                    lyrics = lyrics,
                    songTitle = songTitle,
                    rawResponse = parsedResponse,
                )

            } else {
                // Direct binary audio stream (WAV/MP3/AAC)
                val outputFile = saveStreamToFile(context, responseBody)
                MusicGenerationResult(
                    audioFile = outputFile,
                )
            }
        }
    }

    /**
     * Fetch song recommendations from `POST /recommendations`
     */
    suspend fun fetchRecommendations(
        genre: String? = null
    ): RecommendationsResponse = withContext(Dispatchers.IO) {
        if (USE_MOCK_DATA) {
            delay(800)
            return@withContext RecommendationsResponse(
                status = "success",
                tracks = listOf(
                    RecommendationTrack("Daily Mix 1", "MoodMix AI", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"),
                    RecommendationTrack("Focus Flow", "Lo-Fi Collective", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"),
                    RecommendationTrack("Late Night Beats", "Chillout Zone", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3")
                )
            )
        }

        val json = JSONObject().apply {
            if (!genre.isNullOrBlank()) put("genre", genre)
        }
        val httpRequest = Request.Builder()
            .url(ApiConfig.RECOMMENDATIONS_ENDPOINT)
            .post(json.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Server returned ${response.code}")
            }
            val bodyStr = response.body?.string() ?: throw IOException("Empty response")
            val jsonObj = JSONObject(bodyStr)
            val jsonArray = jsonObj.optJSONArray("tracks") ?: JSONArray()
            val tracksList = mutableListOf<RecommendationTrack>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                tracksList.add(
                    RecommendationTrack(
                        title = item.optString("title", "Track"),
                        artist = item.optString("artist", "Artist"),
                        audioUrl = if (item.has("audio_url")) item.getString("audio_url") else null
                    )
                )
            }
            RecommendationsResponse(
                status = jsonObj.optString("status", "success"),
                tracks = tracksList
            )
        }
    }

    /**
     * Voice Search Speech-to-Text via `POST /voice-search` (Multipart upload)
     */
    suspend fun performVoiceSearch(
        audioFile: File
    ): VoiceSearchResponse = withContext(Dispatchers.IO) {
        if (USE_MOCK_DATA) {
            delay(1500)
            return@withContext VoiceSearchResponse(
                status = "success",
                recognizedText = "Chill lo-fi study music for late night"
            )
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("audio", audioFile.name, audioFile.asRequestBody("audio/*".toMediaType()))
            .build()

        val httpRequest = Request.Builder()
            .url(ApiConfig.VOICE_SEARCH_ENDPOINT)
            .post(requestBody)
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Voice search failed with code ${response.code}")
            }
            val bodyStr = response.body?.string() ?: throw IOException("Empty response")
            val jsonObj = JSONObject(bodyStr)
            VoiceSearchResponse(
                status = jsonObj.optString("status", "success"),
                recognizedText = if (jsonObj.has("recognized_text")) jsonObj.getString("recognized_text") else null,
                message = if (jsonObj.has("message")) jsonObj.getString("message") else null
            )
        }
    }

    /**
     * Song Analysis & Breakdown via `POST /analyze-song` (Multipart upload)
     */
    suspend fun analyzeSong(
        audioFile: File
    ): SongAnalysisResponse = withContext(Dispatchers.IO) {
        if (USE_MOCK_DATA) {
            delay(1200)
            return@withContext SongAnalysisResponse(
                status = "success",
                bpm = 120,
                keySignature = "C Major",
                energyLevel = 0.84f,
                primaryMood = "Energetic & Upbeat"
            )
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("audio", audioFile.name, audioFile.asRequestBody("audio/*".toMediaType()))
            .build()

        val httpRequest = Request.Builder()
            .url(ApiConfig.SONG_ANALYSIS_ENDPOINT)
            .post(requestBody)
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Song analysis failed with code ${response.code}")
            }
            val bodyStr = response.body?.string() ?: throw IOException("Empty response")
            val jsonObj = JSONObject(bodyStr)
            SongAnalysisResponse(
                status = jsonObj.optString("status", "success"),
                bpm = if (jsonObj.has("bpm")) jsonObj.optInt("bpm") else null,
                keySignature = if (jsonObj.has("key_signature")) jsonObj.getString("key_signature") else null,
                energyLevel = if (jsonObj.has("energy_level")) jsonObj.optDouble("energy_level").toFloat() else null,
                primaryMood = if (jsonObj.has("primary_mood")) jsonObj.getString("primary_mood") else null
            )
        }
    }

    /**
     * Lyrics Intelligence via `POST /lyrics-intelligence`
     */
    suspend fun analyzeLyrics(
        request: LyricsIntelligenceRequest
    ): LyricsIntelligenceResponse = withContext(Dispatchers.IO) {
        if (USE_MOCK_DATA) {
            delay(1000)
            return@withContext LyricsIntelligenceResponse(
                status = "success",
                sentiment = "Highly Positive & Uplifting",
                rhymeScheme = "AABB AA",
                summary = "Themes of freedom, music energy, and late night reflection."
            )
        }

        val json = JSONObject().apply {
            put("lyrics_text", request.lyricsText)
            put("action", request.action)
        }

        val httpRequest = Request.Builder()
            .url(ApiConfig.LYRICS_INTELLIGENCE_ENDPOINT)
            .post(json.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Lyrics analysis failed with code ${response.code}")
            }
            val bodyStr = response.body?.string() ?: throw IOException("Empty response")
            val jsonObj = JSONObject(bodyStr)
            LyricsIntelligenceResponse(
                status = jsonObj.optString("status", "success"),
                sentiment = if (jsonObj.has("sentiment")) jsonObj.getString("sentiment") else null,
                rhymeScheme = if (jsonObj.has("rhyme_scheme")) jsonObj.getString("rhyme_scheme") else null,
                summary = if (jsonObj.has("summary")) jsonObj.getString("summary") else null
            )
        }
    }

    /**
     * AI DJ & Smart Transitions via `POST /ai-dj`
     */
    suspend fun generateAiDjTransition(
        context: Context,
        request: AiDjRequest
    ): MusicGenerationResult = withContext(Dispatchers.IO) {
        if (USE_MOCK_DATA) {
            delay(1200)
            val sampleUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
            val file = try {
                downloadAudioFile(context, sampleUrl)
            } catch (_: Exception) {
                createDummyWavFile(context)
            }
            return@withContext MusicGenerationResult(
                audioFile = file,
                songTitle = "AI DJ Crossfade Transition",
                rawResponse = MusicGenerationResponse(
                    status = "success",
                    songTitle = "AI DJ Transition",
                    audioUrl = sampleUrl,
                    message = "AI DJ transition generated successfully."
                )
            )
        }

        val json = JSONObject().apply {
            put("track_a", request.trackAUrl)
            put("track_b", request.trackBUrl)
            put("crossfade_seconds", request.crossfadeDurationSeconds)
        }

        val httpRequest = Request.Builder()
            .url(ApiConfig.AI_DJ_ENDPOINT)
            .post(json.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("AI DJ service failed with code ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty response")
            val contentType = body.contentType()?.toString()?.lowercase().orEmpty()

            if (contentType.contains("application/json")) {
                val jsonStr = body.string()
                val jsonObj = JSONObject(jsonStr)
                val transitionUrl = if (jsonObj.has("transition_audio_url")) jsonObj.getString("transition_audio_url") else null
                if (transitionUrl.isNullOrBlank()) {
                    throw IOException("JSON response did not contain transition_audio_url")
                }
                val file = downloadAudioFile(context, transitionUrl)
                MusicGenerationResult(
                    audioFile = file,
                    songTitle = "AI DJ Transition"
                )
            } else {
                val file = saveStreamToFile(context, body)
                MusicGenerationResult(
                    audioFile = file,
                    songTitle = "AI DJ Transition"
                )
            }
        }
    }

    private fun downloadAudioFile(context: Context, audioUrl: String): File {
        val downloadRequest = Request.Builder().url(audioUrl).get().build()
        client.newCall(downloadRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to download audio from server: HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty audio stream from server.")
            return saveStreamToFile(context, body)
        }
    }

    private fun saveStreamToFile(context: Context, responseBody: ResponseBody): File {
        val outputDirectory = File(context.cacheDir, "generated_audio")
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }

        val outputFile = File(
            outputDirectory,
            "MoodMix_${System.currentTimeMillis()}.wav"
        )

        responseBody.byteStream().use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        if (!outputFile.exists() || (outputFile.length() == 0L)) {
            throw IOException("The generated audio file is empty.")
        }

        return outputFile
    }

    private fun createDummyWavFile(context: Context): File {
        val outputDirectory = File(context.cacheDir, "generated_audio")
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }
        val outputFile = File(outputDirectory, "MoodMix_mock_${System.currentTimeMillis()}.wav")
        outputFile.writeBytes(ByteArray(1024))
        return outputFile
    }
}
