package com.moodmix.app.network

import android.content.Context
import kotlinx.coroutines.CancellationException
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
 * ACE-Step 1.5 Turbo client (Kaggle + ngrok).
 */
object AceStepApi {

    private const val POLL_INTERVAL_MS = 4_000L
    private const val MAX_WAIT_MS = 20 * 60 * 1000L
    private const val MAX_CONSECUTIVE_POLL_ERRORS = 15

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // A 180 s WAV is about 35 MB, so give downloads more time.
    private val downloadClient: OkHttpClient = okHttpClient.newBuilder()
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /** Errors that must stop polling immediately (not retried). */
    private class FatalGenerationException(message: String) : IOException(message)

    fun cleanExpired7DayCache(context: Context) {
        try {
            val cacheFolder = File(context.cacheDir, "moodmix_generated")
            if (cacheFolder.exists() && cacheFolder.isDirectory) {
                val now = System.currentTimeMillis()
                val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
                cacheFolder.listFiles()?.forEach { file ->
                    if (file.isFile && (now - file.lastModified() > sevenDaysMs)) file.delete()
                }
            }
        } catch (_: Exception) {
        }
    }

    /** User idea + style tags the model responds to well. */
    private fun buildCaption(userPrompt: String): String {
        val idea = userPrompt.trim().take(400)
        return "$idea. Instrumental, rich full arrangement, polished studio recording, high quality."
    }

    suspend fun generateTrack(
        context: Context,
        prompt: String,
        durationSeconds: Int,
        onStatusUpdate: ((String) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        cleanExpired7DayCache(context)
        onStatusUpdate?.invoke("Sending your idea to the studio…")

        val releaseJson = JSONObject().apply {
            put("prompt", buildCaption(prompt))
            put("lyrics", "[Instrumental]")
            put("thinking", true)
            put("audio_duration", durationSeconds.coerceIn(30, 120))
            put("audio_format", "wav")
            put("model", "acestep-v15-turbo")
            put("inference_steps", 8)
            put("batch_size", 1)
            put("use_random_seed", true)
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
            val body = response.body?.string() ?: throw IOException("Empty response from ACE-Step")
            val data = JSONObject(body).optJSONObject("data")
                ?: throw IOException("Missing data object in ACE-Step response")
            val id = data.optString("task_id", "")
            if (id.isBlank()) throw IOException("ACE-Step did not return a task id")
            id
        }

        val audioFilePath = pollQueryResult(taskId, onStatusUpdate)

        onStatusUpdate?.invoke("Downloading your track…")
        val fullAudioUrl = if (audioFilePath.startsWith("http://") || audioFilePath.startsWith("https://")) {
            audioFilePath
        } else {
            val base = ApiConfig.ACESTEP_BASE_URL.removeSuffix("/")
            val path = if (audioFilePath.startsWith("/")) audioFilePath else "/$audioFilePath"
            "$base$path"
        }

        val file = downloadWavToCache(context, fullAudioUrl, taskId)
        onStatusUpdate?.invoke("Done!")
        file
    }

    private fun clock(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    private suspend fun pollQueryResult(
        taskId: String,
        onStatusUpdate: ((String) -> Unit)?
    ): String {
        val queryBody = JSONObject().put("task_id_list", JSONArray().put(taskId)).toString()
        val startedAt = System.currentTimeMillis()
        var consecutiveErrors = 0

        while (true) {
            delay(POLL_INTERVAL_MS) // cancellation point
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed > MAX_WAIT_MS) {
                throw IOException("The server took longer than 20 minutes. Try a shorter track.")
            }

            try {
                val request = Request.Builder()
                    .url(ApiConfig.ACESTEP_QUERY_RESULT_ENDPOINT)
                    .header("Content-Type", "application/json")
                    .header("ngrok-skip-browser-warning", "true")
                    .post(queryBody.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val item: JSONObject? = okHttpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    JSONObject(resp.body?.string().orEmpty()).optJSONArray("data")?.optJSONObject(0)
                }
                consecutiveErrors = 0

                if (item == null) {
                    onStatusUpdate?.invoke("Waiting in queue… ${clock(elapsed)}")
                    continue
                }

                when (item.optInt("status", 0)) {
                    1 -> {
                        val path = extractFilePathFromResult(item.optString("result", ""))
                        if (path.isBlank()) throw FatalGenerationException("The server finished but returned no audio file.")
                        return path
                    }
                    2 -> throw FatalGenerationException("The server could not generate this track. Try a different description.")
                    else -> {
                        val extra = item.optString("progress_text", "").take(60)
                        onStatusUpdate?.invoke(
                            buildString {
                                append("Composing… ").append(clock(elapsed))
                                if (extra.isNotBlank()) append(" · ").append(extra)
                            }
                        )
                    }
                }
            } catch (e: FatalGenerationException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                consecutiveErrors++
                if (consecutiveErrors >= MAX_CONSECUTIVE_POLL_ERRORS) {
                    throw IOException("Lost connection to the music server.")
                }
                onStatusUpdate?.invoke("Reconnecting… ${clock(elapsed)}")
            }
        }
    }

    private fun extractFilePathFromResult(resultStr: String): String {
        return try {
            val trimmed = resultStr.trim()
            when {
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    if (arr.length() > 0) arr.getJSONObject(0).optString("file", "") else ""
                }
                trimmed.startsWith("{") -> JSONObject(trimmed).optString("file", "")
                else -> ""
            }
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

        return downloadClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Failed to download audio: HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Empty audio stream from server")

            val cacheFolder = File(context.cacheDir, "moodmix_generated").apply { mkdirs() }
            val persistentFolder = File(context.filesDir, "generated_music").apply { mkdirs() }

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "MoodMix_${stamp}_${taskId.take(6)}.wav"
            val cacheFile = File(cacheFolder, fileName)
            val persistentFile = File(persistentFolder, fileName)

            body.byteStream().use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (cacheFile.length() == 0L) throw IOException("Downloaded WAV file is empty.")

            try {
                cacheFile.copyTo(persistentFile, overwrite = true)
            } catch (_: Exception) {
                cacheFile
            }
        }
    }
}