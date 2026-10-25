package com.moodmix.app.ui.screens.generate

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.moodmix.app.network.MusicGenApi
import com.moodmix.app.network.MusicGenerationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun GenerateScreen(
    initialPrompt: String = ""
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var prompt by remember(initialPrompt) {
        mutableStateOf(initialPrompt)
    }

    var duration by remember {
        mutableFloatStateOf(15f)
    }

    var generateLyrics by remember {
        mutableStateOf(false)
    }

    var isGenerating by remember {
        mutableStateOf(false)
    }

    var isDownloading by remember {
        mutableStateOf(false)
    }

    var generatedFile by remember {
        mutableStateOf<File?>(null)
    }

    var generatedPrompt by remember {
        mutableStateOf("")
    }

    var generatedLyrics by remember {
        mutableStateOf<String?>(null)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var isPlaying by remember {
        mutableStateOf(false)
    }

    var playbackPosition by remember {
        mutableLongStateOf(0L)
    }

    var playbackDuration by remember {
        mutableLongStateOf(0L)
    }

    val player = remember(context) {
        ExoPlayer.Builder(context).build()
    }

    /*
     * Listen for playback state changes.
     */
    DisposableEffect(player) {

        val listener = object : Player.Listener {

            override fun onIsPlayingChanged(
                playing: Boolean
            ) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(
                playbackState: Int
            ) {
                when (playbackState) {

                    Player.STATE_READY -> {
                        playbackDuration =
                            player.duration.coerceAtLeast(0L)
                    }

                    Player.STATE_ENDED -> {
                        isPlaying = false
                        playbackPosition = playbackDuration
                    }
                }
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    /*
     * Keep the playback progress updated.
     */
    LaunchedEffect(generatedFile) {

        while (isActive) {

            playbackPosition = player.currentPosition

            if (player.duration > 0) {
                playbackDuration = player.duration
            }

            delay(250)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
            .padding(
                horizontal = 20.dp,
                vertical = 24.dp
            )
    ) {

        Text(
            text = "Create your vibe",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Tell MoodMix what you want to hear.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {

            Column(
                modifier = Modifier.padding(18.dp)
            ) {

                Text(
                    text = "Describe your music",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = prompt,
                    onValueChange = {
                        prompt = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "e.g. peaceful music for studying at night"
                        )
                    },
                    minLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isGenerating
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Duration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${duration.toInt()} seconds",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Slider(
                    value = duration,
                    onValueChange = {
                        duration = it
                    },
                    valueRange = 5f..30f,
                    steps = 24,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isGenerating
                )

                Spacer(modifier = Modifier.height(16.dp))

                /*
                 * Toggle switch for lyrics generation option.
                 */
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Generate Lyrics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Create matching song lyrics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = generateLyrics,
                        onCheckedChange = { generateLyrics = it },
                        enabled = !isGenerating
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {

                        val cleanPrompt = prompt.trim()
                        val selectedDuration = duration.toInt()
                        val maxNewTokens = durationToTokens(selectedDuration)

                        scope.launch {

                            isGenerating = true
                            errorMessage = null
                            generatedFile = null
                            generatedLyrics = null

                            /*
                             * Stop the previous audio while a
                             * new generation is happening.
                             */
                            player.pause()

                            try {

                                val result = MusicGenApi.generateWithDetails(
                                    context = context,
                                    request = MusicGenerationRequest(
                                        prompt = cleanPrompt,
                                        maxNewTokens = maxNewTokens,
                                        generateLyrics = generateLyrics
                                    )
                                )

                                generatedFile = result.audioFile
                                generatedPrompt = cleanPrompt
                                generatedLyrics = result.lyrics

                                playbackPosition = 0L
                                playbackDuration = 0L

                                /*
                                 * Load the generated audio into ExoPlayer and play it.
                                 */
                                player.setMediaItem(
                                    MediaItem.fromUri(result.audioFile.toUri())
                                )

                                player.prepare()
                                player.play()

                            } catch (exception: Exception) {

                                errorMessage =
                                    exception.message
                                        ?: "Music generation failed."

                            } finally {

                                isGenerating = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = prompt.isNotBlank() && !isGenerating
                ) {

                    if (isGenerating) {

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.height(20.dp)
                            )

                            Text("Generating music...")
                        }

                    } else {

                        Text("Generate Music")
                    }
                }
            }
        }

        /*
         * Error message.
         */
        if (errorMessage != null) {

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {

                Text(
                    text = errorMessage!!,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        /*
         * Generated audio & lyrics display section.
         */
        if (generatedFile != null) {

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {

                Column(
                    modifier = Modifier.padding(18.dp)
                ) {

                    Text(
                        text = "Your music is ready",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = generatedPrompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    /*
                     * Playback progress.
                     */
                    if (playbackDuration > 0L) {

                        Slider(
                            value = playbackPosition
                                .toFloat()
                                .coerceIn(
                                    0f,
                                    playbackDuration.toFloat()
                                ),
                            onValueChange = { newPosition ->

                                player.seekTo(newPosition.toLong())
                                playbackPosition = newPosition.toLong()
                            },
                            valueRange = 0f..playbackDuration.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {

                            Text(
                                text = formatTime(playbackPosition),
                                style = MaterialTheme.typography.bodySmall
                            )

                            Text(
                                text = formatTime(playbackDuration),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    /*
                     * Play / Pause.
                     */
                    Button(
                        onClick = {

                            if (player.isPlaying) {
                                player.pause()
                            } else {

                                if (player.playbackState == Player.STATE_ENDED) {
                                    player.seekTo(0)
                                }

                                player.play()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Text(
                            if (isPlaying) {
                                "Pause"
                            } else {
                                "Play"
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    /*
                     * Download + Share.
                     */
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        Button(
                            onClick = {

                                val file = generatedFile ?: return@Button

                                scope.launch {

                                    isDownloading = true

                                    try {

                                        withContext(Dispatchers.IO) {
                                            saveAudioToMusic(context, file)
                                        }

                                        Toast.makeText(
                                            context,
                                            "Saved to Music/MoodMix",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                    } catch (exception: Exception) {

                                        Toast.makeText(
                                            context,
                                            exception.message ?: "Download failed.",
                                            Toast.LENGTH_LONG
                                        ).show()

                                    } finally {

                                        isDownloading = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isDownloading
                        ) {

                            Text(
                                if (isDownloading) {
                                    "Saving..."
                                } else {
                                    "Download"
                                }
                            )
                        }

                        Button(
                            onClick = {

                                val file = generatedFile ?: return@Button

                                shareAudio(context, file)
                            },
                            modifier = Modifier.weight(1f)
                        ) {

                            Text("Share")
                        }
                    }

                    /*
                     * Generated Lyrics display container.
                     */
                    if (!generatedLyrics.isNullOrBlank()) {

                        Spacer(modifier = Modifier.height(20.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {

                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {

                                Text(
                                    text = "📝 Generated Lyrics",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                SelectionContainer {
                                    Text(
                                        text = generatedLyrics!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Quick moods",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            MoodButton(
                text = "😌 Calm",
                modifier = Modifier.weight(1f)
            ) {
                prompt = appendOrSetPrompt(prompt, "Calm music for relaxing")
            }

            MoodButton(
                text = "🔥 Energy",
                modifier = Modifier.weight(1f)
            ) {
                prompt = appendOrSetPrompt(prompt, "High energy music for a workout")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            MoodButton(
                text = "🎯 Focus",
                modifier = Modifier.weight(1f)
            ) {
                prompt = appendOrSetPrompt(prompt, "Focused instrumental music for studying")
            }

            MoodButton(
                text = "🌙 Night",
                modifier = Modifier.weight(1f)
            ) {
                prompt = appendOrSetPrompt(prompt, "Chill late night music")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun appendOrSetPrompt(currentPrompt: String, moodText: String): String {
    val clean = currentPrompt.trim()
    return if (clean.isEmpty()) {
        moodText
    } else if (clean.contains(moodText, ignoreCase = true)) {
        clean
    } else {
        "$clean, $moodText"
    }
}

private fun durationToTokens(durationSeconds: Int): Int {
    return (durationSeconds * 50).coerceAtMost(1503)
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private fun saveAudioToMusic(context: Context, sourceFile: File) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, sourceFile.name)
        put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
        put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/MoodMix")
        put(MediaStore.Audio.Media.IS_PENDING, 1)
    }

    val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IllegalStateException("Could not create the music file.")

    try {
        resolver.openOutputStream(uri).use { output ->
            if (output == null) {
                throw IllegalStateException("Could not open the downloaded file.")
            }
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        }

        val publishValues = ContentValues().apply {
            put(MediaStore.Audio.Media.IS_PENDING, 0)
        }

        resolver.update(uri, publishValues, null, null)

    } catch (exception: Exception) {
        resolver.delete(uri, null, null)
        throw exception
    }
}

private fun shareAudio(context: Context, file: File) {
    val contentUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/wav"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("MoodMix audio", contentUri)
    }

    context.startActivity(
        Intent.createChooser(shareIntent, "Share MoodMix audio")
    )
}

@Composable
private fun MoodButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(text)
    }
}
