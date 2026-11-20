package com.moodmix.app.network

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Paste your AudD token here (https://dashboard.audd.io). Leave blank to disable song identification.
 * Better: move it into BuildConfig / local.properties so it isn't committed to git.
 */
object SongRecognitionConfig {
    const val AUDD_API_TOKEN: String = "1543b25001afe3ea646bb715200fc4ba"
}

sealed interface SongRecognitionResult {
    data class Match(val title: String, val artist: String, val album: String?) : SongRecognitionResult
    object NoMatch : SongRecognitionResult
    object NotConfigured : SongRecognitionResult
    data class Failure(val message: String) : SongRecognitionResult
}

/**
 * Shazam-style "what song is this?" - records a short clip from the mic and sends it to AudD's
 * audio-fingerprinting API. Plain speech recognition can't do this; it only understands words.
 */
object SongRecognizer {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun identify(
        context: Context,
        listenSeconds: Int = 10,
        onTick: (secondsLeft: Int) -> Unit = {},
        onUploading: () -> Unit = {}
    ): SongRecognitionResult {
        if (SongRecognitionConfig.AUDD_API_TOKEN.isBlank()) return SongRecognitionResult.NotConfigured

        val file = File(context.cacheDir, "song_id_${System.currentTimeMillis()}.m4a")
        try {
            val recorded = record(context, file, listenSeconds, onTick)
            if (!recorded) return SongRecognitionResult.Failure("Couldn't record from the microphone. Is another app using it?")
            onUploading()
            return withContext(Dispatchers.IO) { upload(file) }
        } finally {
            file.delete()
        }
    }

    private suspend fun record(
        context: Context,
        file: File,
        seconds: Int,
        onTick: (Int) -> Unit
    ): Boolean {
        val recorder = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44100)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
        } catch (e: Exception) {
            runCatching { recorder.release() }
            return false
        }

        try {
            for (s in seconds downTo 1) {
                onTick(s)
                delay(1000)
            }
            return try {
                recorder.stop()
                true
            } catch (e: RuntimeException) {
                false
            }
        } finally {
            runCatching { recorder.release() }
        }
    }

    private fun upload(file: File): SongRecognitionResult {
        return try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("api_token", SongRecognitionConfig.AUDD_API_TOKEN)
                .addFormDataPart("return", "apple_music")
                .addFormDataPart("file", file.name, file.asRequestBody("audio/mp4".toMediaType()))
                .build()

            val request = Request.Builder()
                .url("https://api.audd.io/")
                .post(body)
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return SongRecognitionResult.Failure("Recognition service error (${resp.code})")
                }
                val json = JSONObject(resp.body?.string().orEmpty())
                if (json.optString("status") != "success") {
                    val msg = json.optJSONObject("error")?.optString("error_message").orEmpty()
                    return SongRecognitionResult.Failure(msg.ifBlank { "Recognition failed" })
                }
                val result = json.optJSONObject("result") ?: return SongRecognitionResult.NoMatch
                val title = result.optString("title")
                val artist = result.optString("artist")
                if (title.isBlank()) return SongRecognitionResult.NoMatch
                SongRecognitionResult.Match(
                    title = title,
                    artist = artist,
                    album = result.optString("album").ifBlank { null }
                )
            }
        } catch (e: Exception) {
            SongRecognitionResult.Failure("Network error. Check your connection and try again.")
        }
    }
}
