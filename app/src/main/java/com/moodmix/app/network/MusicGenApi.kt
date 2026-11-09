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
 * Production Service for communicating with the MoodMix backend music & lyrics generation server.
 * Deployed backend URL: https://moodmix-backend.onrender.com
 */
object MusicGenApi {

    /*
     * OkHttpClient configured using central settings from [ApiConfig].
     * Includes extended timeouts to handle Render free instance cold starts and Daydream model generation.
     */
    private val client = OkHttpClient.Builder()
        .connectTimeout(ApiConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(ApiConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(ApiConfig.READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        .callTimeout(ApiConfig.CALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Health check endpoint to verify backend connectivity (e.g. Render server readiness).
     */
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = Request.Builder()
                .url(ApiConfig.HEALTH_ENDPOINT)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

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
     * Production generation method returning [MusicGenerationResult]
     * which contains the local audio [File], generated lyrics text (if any), and metadata.
     * Communicates with backend endpoints and provides real-time status updates.
     */
    suspend fun generateWithDetails(
        context: Context,
        request: MusicGenerationRequest,
        onStatusUpdate: ((String) -> Unit)? = null
    ): MusicGenerationResult = withContext(Dispatchers.IO) {

        onStatusUpdate?.invoke("Connecting to server...")

        val cleanPrompt = request.prompt.trim().take(400)

        val json = JSONObject().apply {
            put("prompt", cleanPrompt)
            put("max_new_tokens", request.maxNewTokens)
            put("generate_lyrics", request.generateLyrics)
            if (!request.genre.isNullOrBlank()) {
                put("genre", request.genre)
            }
            if (request.bpm != null) {
                put("bpm", request.bpm.coerceIn(60, 200))
            }
        }

        onStatusUpdate?.invoke("Submitting request...")

        val requestBody = json.toString().toRequestBody(jsonMediaType)

        val httpRequest = Request.Builder()
            .url(ApiConfig.GENERATE_ENDPOINT)
            .post(requestBody)
            .build()

        client.newCall(httpRequest).execute().use { response ->

            if (!response.isSuccessful) {
                val serverMessage = response.body?.string().orEmpty()
                val friendlyError = when (response.code) {
                    400 -> "HTTP 400 Bad Request: Invalid prompt or parameters."
                    401 -> "HTTP 401 Unauthorized: Daydream API key rejected by backend server."
                    402 -> "HTTP 402 Payment Required: Insufficient Daydream credits on backend."
                    429 -> "HTTP 429 Rate Limit Exceeded: Too many generation requests sent."
                    500 -> "HTTP 500 Internal Server Error: Backend server error."
                    502 -> "HTTP 502 Bad Gateway: Daydream upstream model failed."
                    503 -> "HTTP 503 Service Unavailable: Daydream temporarily unavailable."
                    504 -> "HTTP 504 Gateway Timeout: Daydream generation request timed out."
                    else -> "Server returned HTTP ${response.code}" + if (serverMessage.isNotBlank()) ": $serverMessage" else ""
                }
                throw IOException(friendlyError)
            }

            val responseBody = response.body
                ?: throw IOException("Server returned an empty response.")

            val contentType = responseBody.contentType()?.toString()?.lowercase().orEmpty()

            if (contentType.contains("application/json")) {
                val jsonStr = responseBody.string()
                val jsonObject = JSONObject(jsonStr)

                val status = jsonObject.optString("status", "queued")
                val songId = if (jsonObject.has("song_id")) jsonObject.optString("song_id")
                             else if (jsonObject.has("id")) jsonObject.optString("id") else null
                val songTitle = if (jsonObject.has("song_title")) jsonObject.optString("song_title")
                                else if (jsonObject.has("title")) jsonObject.optString("title") else null
                val lyrics = if (jsonObject.has("lyrics")) jsonObject.optString("lyrics")
                             else if (jsonObject.has("lyrics_text")) jsonObject.optString("lyrics_text") else null
                val audioUrl = parseAudioUrlFromResponse(jsonObject)
                val message = if (jsonObject.has("message")) jsonObject.optString("message") else null

                val parsedResponse = MusicGenerationResponse(
                    status = status,
                    songId = songId,
                    songTitle = songTitle,
                    audioUrl = audioUrl,
                    lyrics = lyrics,
                    message = message,
                )

                if (!audioUrl.isNullOrBlank()) {
                    onStatusUpdate?.invoke("Downloading audio...")
                    val fullAudioUrl = if (audioUrl.startsWith("http://") || audioUrl.startsWith("https://")) {
                        audioUrl
                    } else {
                        "${ApiConfig.BASE_URL.removeSuffix("/")}/${audioUrl.removePrefix("/")}"
                    }
                    val outputFile = downloadAudioFile(context, fullAudioUrl)
                    onStatusUpdate?.invoke("Completed")
                    MusicGenerationResult(
                        audioFile = outputFile,
                        lyrics = lyrics,
                        songTitle = songTitle,
                        rawResponse = parsedResponse,
                    )
                } else if (!songId.isNullOrBlank()) {
                    // Poll status endpoint for asynchronous generation
                    pollDaydreamStatus(context, songId, onStatusUpdate, lyrics, songTitle, parsedResponse)
                } else {
                    throw IOException("Server returned JSON response without an audio_url or song_id.")
                }

            } else {
                // Direct binary audio stream (WAV/MP3)
                onStatusUpdate?.invoke("Downloading audio...")
                val outputFile = saveStreamToFile(context, responseBody)
                onStatusUpdate?.invoke("Completed")
                MusicGenerationResult(
                    audioFile = outputFile,
                )
            }
        }
    }

    /**
     * Poll backend status endpoint `GET /status/{song_id}` until completed or failed.
     */
    private suspend fun pollDaydreamStatus(
        context: Context,
        songId: String,
        onStatusUpdate: ((String) -> Unit)?,
        initialLyrics: String?,
        initialTitle: String?,
        initialResponse: MusicGenerationResponse
    ): MusicGenerationResult = withContext(Dispatchers.IO) {
        val statusUrl = "${ApiConfig.STATUS_ENDPOINT}/$songId"

        for (attempt in 1..150) { // Poll for up to 5 minutes (every 2s)
            val request = Request.Builder().url(statusUrl).get().build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val serverMessage = response.body?.string().orEmpty()
                    val friendlyError = when (response.code) {
                        400 -> "HTTP 400 Bad Request: Invalid status request."
                        401 -> "HTTP 401 Unauthorized: Daydream API key rejected."
                        402 -> "HTTP 402 Payment Required: Daydream API credits exhausted."
                        429 -> "HTTP 429 Rate Limit Exceeded: Too many status requests."
                        500 -> "HTTP 500 Internal Server Error: Status check failed."
                        502 -> "HTTP 502 Bad Gateway: Upstream status failure."
                        503 -> "HTTP 503 Service Unavailable: Daydream temporarily unavailable."
                        504 -> "HTTP 504 Gateway Timeout: Status check timed out."
                        else -> "Status check returned HTTP ${response.code}: $serverMessage"
                    }
                    throw IOException(friendlyError)
                }

                val body = response.body ?: throw IOException("Empty status response from server.")
                val jsonStr = body.string()
                val jsonObject = JSONObject(jsonStr)

                val status = jsonObject.optString("status", "queued").lowercase()
                val audioUrl = parseAudioUrlFromResponse(jsonObject)
                val lyrics = if (jsonObject.has("lyrics")) jsonObject.optString("lyrics") else initialLyrics
                val songTitle = if (jsonObject.has("song_title")) jsonObject.optString("song_title") else initialTitle

                when (status) {
                    "queued" -> onStatusUpdate?.invoke("Waiting in queue...")
                    "processing", "generating", "in_progress" -> onStatusUpdate?.invoke("Generating music...")
                    "completed", "success" -> {
                        if (!audioUrl.isNullOrBlank()) {
                            onStatusUpdate?.invoke("Downloading audio...")
                            val fullAudioUrl = if (audioUrl.startsWith("http://") || audioUrl.startsWith("https://")) {
                                audioUrl
                            } else {
                                "${ApiConfig.BASE_URL.removeSuffix("/")}/${audioUrl.removePrefix("/")}"
                            }
                            val file = downloadAudioFile(context, fullAudioUrl)
                            onStatusUpdate?.invoke("Completed")
                            return@withContext MusicGenerationResult(
                                audioFile = file,
                                lyrics = lyrics,
                                songTitle = songTitle,
                                rawResponse = initialResponse.copy(status = "completed", audioUrl = audioUrl)
                            )
                        } else {
                            throw IOException("Generation status completed but no audio URL was returned.")
                        }
                    }
                    "failed", "error" -> {
                        val errorMsg = jsonObject.optString("error", jsonObject.optString("message", "Generation failed on backend model."))
                        throw IOException("Generation Failed: $errorMsg")
                    }
                }
            }

            delay(2000)
        }

        throw IOException("Generation status polling timed out after 5 minutes.")
    }

    private fun parseAudioUrlFromResponse(jsonObject: JSONObject): String? {
        if (jsonObject.has("mp3_url") && !jsonObject.isNull("mp3_url")) return jsonObject.optString("mp3_url")
        if (jsonObject.has("wav_url") && !jsonObject.isNull("wav_url")) return jsonObject.optString("wav_url")
        if (jsonObject.has("audio_url") && !jsonObject.isNull("audio_url")) return jsonObject.optString("audio_url")
        if (jsonObject.has("audioUrl") && !jsonObject.isNull("audioUrl")) return jsonObject.optString("audioUrl")
        
        // Handle nested takes array if Daydream returns takes structure
        if (jsonObject.has("takes")) {
            val takesArray = jsonObject.optJSONArray("takes")
            if (takesArray != null && takesArray.length() > 0) {
                val firstTake = takesArray.optJSONObject(0)
                if (firstTake != null) {
                    if (firstTake.has("mp3_url")) return firstTake.optString("mp3_url")
                    if (firstTake.has("wav_url")) return firstTake.optString("wav_url")
                    if (firstTake.has("audio_url")) return firstTake.optString("audio_url")
                }
            }
        }
        return null
    }

    /**
     * Fetch song recommendations from backend `POST /recommendations`
     */
    suspend fun fetchRecommendations(
        genre: String? = null
    ): RecommendationsResponse = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            if (!genre.isNullOrBlank()) put("genre", genre)
        }
        val httpRequest = Request.Builder()
            .url(ApiConfig.RECOMMENDATIONS_ENDPOINT)
            .post(json.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Backend recommendations service returned HTTP ${response.code}")
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
     * Voice Search Speech-to-Text via backend `POST /voice-search` (Multipart upload)
     */
    suspend fun performVoiceSearch(
        audioFile: File
    ): VoiceSearchResponse = withContext(Dispatchers.IO) {
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
                throw IOException("Voice search service returned HTTP ${response.code}")
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
     * Song Analysis & Breakdown via backend `POST /analyze-song` (Multipart upload)
     */
    suspend fun analyzeSong(
        audioFile: File
    ): SongAnalysisResponse = withContext(Dispatchers.IO) {
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
                throw IOException("Song analysis service returned HTTP ${response.code}")
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
     * Lyrics Intelligence via backend `POST /lyrics-intelligence`
     */
    suspend fun analyzeLyrics(
        request: LyricsIntelligenceRequest
    ): LyricsIntelligenceResponse = withContext(Dispatchers.IO) {
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
                throw IOException("Lyrics intelligence service returned HTTP ${response.code}")
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
     * AI DJ & Smart Transitions via backend `POST /ai-dj`
     */
    suspend fun generateAiDjTransition(
        context: Context,
        request: AiDjRequest
    ): MusicGenerationResult = withContext(Dispatchers.IO) {
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
                throw IOException("AI DJ service returned HTTP ${response.code}")
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

        val contentTypeStr = responseBody.contentType()?.toString()?.lowercase().orEmpty()
        val isMp3 = contentTypeStr.contains("mpeg") || contentTypeStr.contains("mp3")
        val extension = if (isMp3) "mp3" else "wav"

        val outputFile = File(
            outputDirectory,
            "MoodMix_${System.currentTimeMillis()}.$extension"
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
}
