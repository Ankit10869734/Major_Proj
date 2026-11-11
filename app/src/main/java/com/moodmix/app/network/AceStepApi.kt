package com.moodmix.app.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * ACE-Step 1.5 Turbo Music Generation Network Client.
 * Connects to Kaggle/ngrok ACE-Step server to release tasks, poll status, download WAV, and manage 7-day cache storage.
 */
object AceStepApi {

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Cleans cacheDir/moodmix_generated/ deleting files older than 7 days (7 * 24 * 60 * 60 * 1000L ms).
     */
    fun cleanExpired7DayCache(context: Context) {
        try {
            val cacheFolder = File(context.cacheDir, "moodmix_generated")
            if (cacheFolder.exists() && cacheFolder.isDirectory) {
                val now = System.currentTimeMillis()
                val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
                cacheFolder.listFiles()?.forEach { file ->
                    if (file.isFile && (now - file.lastModified() > sevenDaysMs)) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Executes music generation via ACE-Step 1.5 Turbo.
     * 1. Submits release_task POST with exact prompt and duration slider seconds.
     * 2. Polls query_result POST asynchronously.
     * 3. Downloads generated WAV file to persistent generated_music folder.
     */
    suspend fun generateTrack(
        context: Context,
        prompt: String,
        durationSeconds: Int,
        onStatusUpdate: ((String) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        cleanExpired7DayCache(context)

        onStatusUpdate?.invoke("Submitting ACE-Step task...")

        val cleanPrompt = prompt.trim().take(400)

        val releaseJson = JSONObject().apply {
            put("prompt", cleanPrompt)
            put("lyrics", "")
            put("thinking", true)
            put("audio_duration", durationSeconds.coerceIn(5, 30))
            put("audio_format", "wav")
            put("model", "acestep-v15-turbo")
            put("inference_steps", 8)
            put("batch_size", 1)
        }

        val releaseRequest = Request.Builder()
            .url(ApiConfig.ACESTEP_RELEASE_TASK_ENDPOINT)
            .header("Content-Type", "application/json")
            .header("ngrok-skip-browser-warning", "true")
            .post(releaseJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val taskId: String = okHttpClient.newCall(releaseRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("ACE-Step release_task returned HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Empty response from ACE-Step release_task")
            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: throw IOException("Missing data object in ACE-Step release_task response")
            val id = data.optString("task_id", "")
            if (id.isBlank()) throw IOException("ACE-Step did not return a valid task_id")
            id
        }

        onStatusUpdate?.invoke("Task queued (ID: ${taskId.take(8)}...)")

        // Step 2: Poll query_result
        val audioFilePath = pollQueryResult(taskId, onStatusUpdate)

        // Step 3: Download WAV file
        onStatusUpdate?.invoke("Downloading generated WAV...")
        val fullAudioUrl = if (audioFilePath.startsWith("http://") || audioFilePath.startsWith("https://")) {
            audioFilePath
        } else {
            val base = ApiConfig.ACESTEP_BASE_URL.removeSuffix("/")
            val path = if (audioFilePath.startsWith("/")) audioFilePath else "/$audioFilePath"
            "$base$path"
        }

        val downloadedFile = downloadWavToCache(context, fullAudioUrl, taskId)
        onStatusUpdate?.invoke("Generation complete!")
        downloadedFile
    }

    private suspend fun pollQueryResult(
        taskId: String,
        onStatusUpdate: ((String) -> Unit)?
    ): String = withContext(Dispatchers.IO) {
        val queryJson = JSONObject().apply {
            put("task_id_list", JSONArray().apply { put(taskId) })
        }

        val pollRequest = Request.Builder()
            .url(ApiConfig.ACESTEP_QUERY_RESULT_ENDPOINT)
            .header("Content-Type", "application/json")
            .header("ngrok-skip-browser-warning", "true")
            .post(queryJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        var pollAttempts = 0
        val maxAttempts = 120 // Up to 10 minutes polling (every 5 seconds)

        while (pollAttempts < maxAttempts) {
            pollAttempts++
            delay(5000)

            try {
                val response = okHttpClient.newCall(pollRequest.newBuilder().build()).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) return@use

                    val body = resp.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val dataArray = json.optJSONArray("data")
                    if (dataArray != null && dataArray.length() > 0) {
                        val firstItem = dataArray.getJSONObject(0)
                        val status = firstItem.optInt("status", 0)

                        when (status) {
                            0 -> {
                                val progress = firstItem.optDouble("progress", 0.0)
                                val progPct = (progress * 100).toInt()
                                onStatusUpdate?.invoke("Synthesizing audio ($progPct%)...")
                            }
                            1 -> {
                                val resultStr = firstItem.optString("result", "")
                                val filePath = extractFilePathFromResult(resultStr)
                                if (filePath.isNotBlank()) {
                                    return@withContext filePath
                                } else {
                                    throw IOException("ACE-Step task succeeded but returned no audio file path")
                                }
                            }
                            2 -> {
                                throw IOException("ACE-Step audio generation task failed (Status 2)")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is IOException && e.message?.contains("failed") == true) {
                    throw e
                }
                onStatusUpdate?.invoke("Waiting for backend worker... ($pollAttempts)")
            }
        }

        throw IOException("ACE-Step music generation polling timed out after 10 minutes.")
    }

    private fun extractFilePathFromResult(resultStr: String): String {
        return try {
            val trimmed = resultStr.trim()
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                if (arr.length() > 0) {
                    arr.getJSONObject(0).optString("file", "")
                } else ""
            } else if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                obj.optString("file", "")
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun downloadWavToCache(context: Context, audioUrl: String, taskId: String): File {
        val request = Request.Builder()
            .url(audioUrl)
            .header("ngrok-skip-browser-warning", "true")
            .get()
            .build()

        val response = okHttpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Failed to download generated audio: HTTP ${resp.code}")
            }

            val body = resp.body ?: throw IOException("Empty audio stream returned from server")
            val cacheFolder = File(context.cacheDir, "moodmix_generated")
            if (!cacheFolder.exists()) {
                cacheFolder.mkdirs()
            }

            val persistentFolder = File(context.filesDir, "generated_music")
            if (!persistentFolder.exists()) {
                persistentFolder.mkdirs()
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "MoodMix_${timeStamp}_${taskId.take(6)}.wav"
            val cacheFile = File(cacheFolder, fileName)
            val persistentFile = File(persistentFolder, fileName)

            body.byteStream().use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (!cacheFile.exists() || (cacheFile.length() == 0L)) {
                throw IOException("Downloaded WAV file is empty.")
            }

            try {
                cacheFile.copyTo(persistentFile, overwrite = true)
            } catch (_: Exception) {}

            return persistentFile
        }
    }
}
