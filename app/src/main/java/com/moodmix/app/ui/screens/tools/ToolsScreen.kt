package com.moodmix.app.ui.screens.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ActiveTool {
    NONE,
    VOICE_SEARCH,
    SONG_ANALYSIS,
    LYRICS_INTELLIGENCE,
    AI_DJ
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onNavigateToGenerate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var activeTool by remember { mutableStateOf(ActiveTool.NONE) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {

            Text(
                text = "AI Tools",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Explore MoodMix's music intelligence features",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            ToolCard(
                icon = "🎤",
                title = "Voice Search & Recognition",
                description = "Speak a music prompt or search query using your microphone.",
                onClick = { activeTool = ActiveTool.VOICE_SEARCH }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ToolCard(
                icon = "🎵",
                title = "Song Analysis & Breakdown",
                description = "Pick an audio file from storage to inspect its structure and attributes.",
                onClick = { activeTool = ActiveTool.SONG_ANALYSIS }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ToolCard(
                icon = "💬",
                title = "Lyrics Intelligence",
                description = "Analyze, translate, and explore rhyme patterns in lyrics.",
                onClick = { activeTool = ActiveTool.LYRICS_INTELLIGENCE }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ToolCard(
                icon = "✨",
                title = "AI DJ & Smart Transitions",
                description = "Configure transition styles and audition sample DJ crossfades.",
                onClick = { activeTool = ActiveTool.AI_DJ }
            )
        }
    }

    /*
     * Modal Bottom Sheets for Active Tool Workspaces
     */
    if (activeTool != ActiveTool.NONE) {
        ModalBottomSheet(
            onDismissRequest = { activeTool = ActiveTool.NONE },
            sheetState = sheetState
        ) {
            when (activeTool) {
                ActiveTool.VOICE_SEARCH -> VoiceSearchWorkspace(
                    context = context,
                    onNavigateToGenerate = { prompt ->
                        activeTool = ActiveTool.NONE
                        onNavigateToGenerate(prompt)
                    },
                    onClose = { activeTool = ActiveTool.NONE }
                )

                ActiveTool.SONG_ANALYSIS -> SongAnalysisWorkspace(
                    context = context,
                    onClose = { activeTool = ActiveTool.NONE }
                )

                ActiveTool.LYRICS_INTELLIGENCE -> LyricsIntelligenceWorkspace(
                    onClose = { activeTool = ActiveTool.NONE }
                )

                ActiveTool.AI_DJ -> AiDjWorkspace(
                    context = context,
                    onClose = { activeTool = ActiveTool.NONE }
                )

                ActiveTool.NONE -> {}
            }
        }
    }
}

@Composable
private fun ToolCard(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Text(
                text = icon,
                style = MaterialTheme.typography.headlineSmall
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VoiceSearchWorkspace(
    context: Context,
    onNavigateToGenerate: (String) -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isListening by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var recognizedPrompt by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "🎤 Voice Search & Recognition",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Demonstration Workspace (Backend STT Integration Pending)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (!hasPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Microphone Permission Required",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Grant audio recording permissions to test voice input handling.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text("Grant Permission")
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isListening) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Listening... Speak your prompt",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Button(
                            onClick = {
                                scope.launch {
                                    isListening = true
                                    recognizedPrompt = null
                                    delay(2500)
                                    isListening = false
                                    recognizedPrompt = "Chill lo-fi study music for late night"
                                }
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Tap to Speak 🎤")
                        }
                    }

                    if (recognizedPrompt != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Simulated Recognition Output:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "\"${recognizedPrompt}\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { onNavigateToGenerate(recognizedPrompt!!) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Use in Generate Screen →")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Close")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SongAnalysisWorkspace(
    context: Context,
    onClose: () -> Unit
) {
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var fileSize by remember { mutableStateOf<String?>(null) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedAudioUri = uri
        if (uri != null) {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    fileName = if (nameIndex != -1) cursor.getString(nameIndex) else "Audio File"
                    val bytes = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                    fileSize = "${bytes / 1024} KB"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "🎵 Song Analysis & Breakdown",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Demonstration Workspace (Backend Musicology Model Pending)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { audioPickerLauncher.launch("audio/*") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(if (selectedAudioUri == null) "Select Audio File from Device" else "Change Audio File")
        }

        if (selectedAudioUri != null) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Selected File:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = fileName ?: "Unknown Track",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Size: ${fileSize ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Sample Analysis Breakdown (Simulated Result):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("• Estimated Tempo: 120 BPM")
                    Text("• Key Signature: C Major")
                    Text("• Energy Level: High (84%)")
                    Text("• Primary Mood: Energetic & Upbeat")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Close")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LyricsIntelligenceWorkspace(
    onClose: () -> Unit
) {
    var inputLyrics by remember { mutableStateOf("") }
    var analysisResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "💬 Lyrics Intelligence",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Demonstration Workspace (Backend Lyrics NLP Pending)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = inputLyrics,
            onValueChange = { inputLyrics = it },
            placeholder = { Text("Paste or type song lyrics here...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = {
                analysisResult = """
                    • Sentiment: Highly Positive & Uplifting
                    • Rhyme Scheme: AABB AA
                    • Key Keywords: Lights, Rhythm, Sky, Night
                    • Theme: Freedom & Musical Energy
                """.trimIndent()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = inputLyrics.isNotBlank(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Analyze Lyrics Structure")
        }

        if (analysisResult != null) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sample Intelligence Result (Demo Output):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = analysisResult!!,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Close")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AiDjWorkspace(
    context: Context,
    onClose: () -> Unit
) {
    var crossfadeDuration by remember { mutableFloatStateOf(4f) }
    var isPlayingDemo by remember { mutableStateOf(false) }

    val player = remember(context) { ExoPlayer.Builder(context).build() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlayingDemo = playing
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "✨ AI DJ & Smart Transitions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Demonstration Workspace (Backend AI DJ Engine Pending)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Crossfade Duration: ${crossfadeDuration.toInt()}s",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Slider(
            value = crossfadeDuration,
            onValueChange = { crossfadeDuration = it },
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    val sampleUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
                    player.setMediaItem(MediaItem.fromUri(sampleUrl))
                    player.prepare()
                    player.play()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(if (isPlayingDemo) "Pause DJ Transition Preview" else "▶ Test AI DJ Transition (Demo Audio)")
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = {
                player.pause()
                onClose()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Close")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
