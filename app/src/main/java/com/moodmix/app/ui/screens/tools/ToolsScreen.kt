package com.moodmix.app.ui.screens.tools

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moodmix.app.playback.PlayerController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.moodmix.app.audio.AudioAnalyzer
import com.moodmix.app.audio.SongAnalysis
import com.moodmix.app.data.theme.RegionalThemePreferences
import com.moodmix.app.domain.model.AppRegion
import com.moodmix.app.domain.model.RegionalThemeState
import com.moodmix.app.lyrics.LyricsAnalysis
import com.moodmix.app.lyrics.LyricsAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class ActiveTool {
    NONE,
    SONG_ANALYSIS,
    LYRICS_INTELLIGENCE,
    AI_DJ
}

enum class TransitionStyle {
    SMOOTH,
    LINEAR,
    QUICK_CUT
}

data class TargetLanguageOption(val displayName: String, val code: String)

val targetLanguageOptions = listOf(
    TargetLanguageOption("English", TranslateLanguage.ENGLISH),
    TargetLanguageOption("Hindi", TranslateLanguage.HINDI),
    TargetLanguageOption("Spanish", TranslateLanguage.SPANISH),
    TargetLanguageOption("French", TranslateLanguage.FRENCH),
    TargetLanguageOption("German", TranslateLanguage.GERMAN),
    TargetLanguageOption("Japanese", TranslateLanguage.JAPANESE),
    TargetLanguageOption("Korean", TranslateLanguage.KOREAN),
    TargetLanguageOption("Arabic", TranslateLanguage.ARABIC),
    TargetLanguageOption("Portuguese", TranslateLanguage.PORTUGUESE),
    TargetLanguageOption("Bengali", TranslateLanguage.BENGALI),
    TargetLanguageOption("Tamil", TranslateLanguage.TAMIL),
    TargetLanguageOption("Telugu", TranslateLanguage.TELUGU)
)

val sourceLanguageOptions = listOf(
    TargetLanguageOption("Auto Detect", "und"),
    TargetLanguageOption("English", TranslateLanguage.ENGLISH),
    TargetLanguageOption("Hindi", TranslateLanguage.HINDI),
    TargetLanguageOption("Spanish", TranslateLanguage.SPANISH),
    TargetLanguageOption("French", TranslateLanguage.FRENCH),
    TargetLanguageOption("German", TranslateLanguage.GERMAN),
    TargetLanguageOption("Japanese", TranslateLanguage.JAPANESE),
    TargetLanguageOption("Korean", TranslateLanguage.KOREAN),
    TargetLanguageOption("Arabic", TranslateLanguage.ARABIC),
    TargetLanguageOption("Portuguese", TranslateLanguage.PORTUGUESE),
    TargetLanguageOption("Bengali", TranslateLanguage.BENGALI),
    TargetLanguageOption("Tamil", TranslateLanguage.TAMIL),
    TargetLanguageOption("Telugu", TranslateLanguage.TELUGU)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onNavigateToGenerate: (String) -> Unit = {},
    onReplayTutorial: () -> Unit = {},
    viewModel: ToolsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()

    val regionalThemePreferences = remember { RegionalThemePreferences.getInstance(context) }
    val regionalThemeState by regionalThemePreferences.regionalThemeState.collectAsStateWithLifecycle(initialValue = RegionalThemeState())

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
                description = "Configure transition styles and audition DJ crossfades.",
                onClick = { activeTool = ActiveTool.AI_DJ }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Regional Themes Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🌐 Regional Theme & Occasions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Switch(
                            checked = regionalThemeState.isAutoThemeEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    regionalThemePreferences.setAutoThemeEnabled(enabled)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Region / Occasion Selection:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    var isDropdownExpanded by remember { mutableStateOf(false) }

                    Box {
                        OutlinedButton(
                            onClick = { isDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = when (regionalThemeState.selectedRegion) {
                                    AppRegion.AUTOMATIC -> "Automatic (Device Locale: ${regionalThemeState.activeRegionCode})"
                                    AppRegion.INDIA -> "Manual: India (Diwali Supported)"
                                    AppRegion.UNITED_STATES -> "Manual: United States"
                                    AppRegion.GERMANY -> "Manual: Germany"
                                    AppRegion.JAPAN -> "Manual: Japan"
                                    AppRegion.STANDARD_DARK -> "Manual: Standard Dark Theme (Themes Off)"
                                }
                            )
                        }

                        DropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false }
                        ) {
                            AppRegion.entries.forEach { region ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (region) {
                                                AppRegion.AUTOMATIC -> "Automatic (Device Locale: ${regionalThemeState.activeRegionCode})"
                                                AppRegion.INDIA -> "Manual: India (Diwali Festival)"
                                                AppRegion.UNITED_STATES -> "Manual: United States"
                                                AppRegion.GERMANY -> "Manual: Germany"
                                                AppRegion.JAPAN -> "Manual: Japan"
                                                AppRegion.STANDARD_DARK -> "Manual: Standard Dark Theme (Themes Off)"
                                            }
                                        )
                                    },
                                    onClick = {
                                        scope.launch {
                                            regionalThemePreferences.setSelectedRegion(region)
                                        }
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (regionalThemeState.isFestiveActive) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "✨ Active Occasion: ${regionalThemeState.activeOccasion?.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ToolCard(
                icon = "🎓",
                title = "Replay Interactive Tutorial",
                description = "Revisit the 5-destination guided walkthrough of MoodMix.",
                onClick = onReplayTutorial
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
                ActiveTool.SONG_ANALYSIS -> SongAnalysisWorkspace(
                    context = context,
                    onClose = { activeTool = ActiveTool.NONE }
                )

                ActiveTool.LYRICS_INTELLIGENCE -> LyricsIntelligenceWorkspace(
                    viewModel = viewModel,
                    lyricsState = lyricsState,
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
private fun SongAnalysisWorkspace(
    context: Context,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var fileSize by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisProgress by remember { mutableFloatStateOf(0f) }
    var analysisResult by remember { mutableStateOf<SongAnalysis?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedAudioUri = uri
        analysisResult = null
        errorMessage = null
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

                    Button(
                        onClick = {
                            val uri = selectedAudioUri ?: return@Button
                            scope.launch {
                                isAnalyzing = true
                                analysisProgress = 0f
                                errorMessage = null
                                analysisResult = null
                                try {
                                    val result = AudioAnalyzer.analyze(context, uri) { progress ->
                                        analysisProgress = progress
                                    }
                                    analysisResult = result
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Song analysis failed."
                                } finally {
                                    isAnalyzing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isAnalyzing,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (isAnalyzing) "Analyzing On-Device DSP..." else "Analyze Audio File")
                    }

                    if (isAnalyzing) {
                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { analysisProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Processing audio DSP... ${(analysisProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (analysisResult != null) {
                        val result = analysisResult!!
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "On-Device DSP Analysis Breakdown:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("• Duration: ${formatSecToMinSec(result.durationSec)}")

                        val bpmText = if (result.bpm != null) {
                            "${result.bpm} BPM (Confidence: ${(result.bpmConfidence * 100).toInt()}%)"
                        } else {
                            "Uncertain / Insufficient audio data"
                        }
                        Text("• Estimated Tempo: $bpmText")

                        val keyText = if (result.key != null) {
                            "${result.key} (Confidence: ${(result.keyConfidence * 100).toInt()}%)"
                        } else {
                            "Uncertain / Insufficient chroma data"
                        }
                        Text("• Key Signature: $keyText")

                        Text("• Energy Level: ${(result.energy * 100).toInt()}%")
                        Text("• Approx. Loudness: %.1f dBFS".format(result.loudnessDb))
                        Text("• Acoustic Vibe (Heuristic): ${result.mood}")

                        if (result.sections.isNotEmpty()) {
                            Text("• Energy Segments: ${result.sections.size} detected")
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Energy Curve & Energy Segment Boundaries:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        EnergyCurveChart(
                            energyCurve = result.energyCurve,
                            sections = result.sections,
                            durationSec = result.durationSec,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        )
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
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
private fun EnergyCurveChart(
    energyCurve: List<Float>,
    sections: List<Pair<Float, Float>>,
    durationSec: Float,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    val sectionLineColor = MaterialTheme.colorScheme.error

    Canvas(modifier = modifier) {
        if (energyCurve.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height
        val numBars = energyCurve.size
        val barWidth = width / numBars

        for (i in energyCurve.indices) {
            val barHeight = energyCurve[i] * height
            val x = i * barWidth
            val y = height - barHeight
            drawRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(max(1f, barWidth * 0.75f), barHeight)
            )
        }

        if (durationSec > 0f) {
            for (sec in sections) {
                if (sec.first > 0f) {
                    val boundaryX = (sec.first / durationSec) * width
                    drawLine(
                        color = sectionLineColor,
                        start = Offset(boundaryX, 0f),
                        end = Offset(boundaryX, height),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    )
                }
            }
        }
    }
}

private fun formatSecToMinSec(seconds: Float): String {
    val totalSec = seconds.toInt().coerceAtLeast(0)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return "%d:%02d".format(mins, secs)
}

@Composable
private fun LyricsIntelligenceWorkspace(
    viewModel: ToolsViewModel,
    lyricsState: LyricsIntelligenceUiState,
    onClose: () -> Unit
) {
    var isSourceDropdownExpanded by remember { mutableStateOf(false) }
    var isTargetDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "💬 Lyrics Intelligence",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = lyricsState.inputLyrics,
            onValueChange = { viewModel.onLyricsInputChanged(it) },
            placeholder = { Text("Paste or type song lyrics here...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            enabled = !lyricsState.isAnalyzing && !lyricsState.isTranslating,
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = { viewModel.analyzeLyrics() },
            modifier = Modifier.fillMaxWidth(),
            enabled = lyricsState.inputLyrics.isNotBlank() && !lyricsState.isAnalyzing,
            shape = RoundedCornerShape(14.dp)
        ) {
            if (lyricsState.isAnalyzing) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Text("Analyzing Structure & Sentiment...")
                }
            } else {
                Text("Analyze Structure & Sentiment")
            }
        }

        if (lyricsState.analysisError != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = lyricsState.analysisError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (lyricsState.analysisResult != null) {
            val analysis = lyricsState.analysisResult
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
                        text = "📊 On-Device Lyrics Analysis:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("• Lines: ${analysis.lineCount} | Words: ${analysis.wordCount} (Unique: ${analysis.uniqueWordCount})")
                    Text("• Avg Syllables per Line: ${"%.1f".format(analysis.avgSyllablesPerLine)}")
                    Text("• Emotional Sentiment: ${analysis.sentiment} (Score: ${"%.2f".format(analysis.sentimentScore)})")

                    if (analysis.topRepeatedWords.isNotEmpty()) {
                        Text("• Top Keywords: ${analysis.topRepeatedWords.joinToString(", ") { "${it.first} (${it.second}x)" }}")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Summary:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = analysis.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Estimated Rhyme Patterns (Heuristic):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    for (sec in analysis.sections) {
                        if (!sec.header.isNullOrBlank()) {
                            Text(
                                text = sec.header,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        for ((line, letter) in sec.lineRhymes) {
                            Text(
                                text = "[$letter]  $line",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // On-Device ML Kit Translation Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🌐 On-Device Translation (ML Kit)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                val detectedLabel = if (lyricsState.detectedLanguageCode == "und") "Unidentified / Unknown" else {
                    sourceLanguageOptions.firstOrNull { it.code.lowercase() == lyricsState.detectedLanguageCode.lowercase() }?.displayName ?: lyricsState.detectedLanguageCode
                }
                Text(
                    text = "Detected Language: $detectedLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lyricsState.detectedLanguageCode == "und") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (lyricsState.detectedLanguageCode == "und" && !lyricsState.isSourceManuallySelected) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Source language could not be identified automatically. Please select the source language manually below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Source Language Selector
                Text(
                    text = "Select Source Language:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Column {
                    OutlinedButton(
                        onClick = { isSourceDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Source: ${lyricsState.selectedSourceLang.displayName}")
                    }

                    DropdownMenu(
                        expanded = isSourceDropdownExpanded,
                        onDismissRequest = { isSourceDropdownExpanded = false }
                    ) {
                        sourceLanguageOptions.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang.displayName) },
                                onClick = {
                                    viewModel.onSourceLanguageSelected(lang)
                                    isSourceDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Target Language Selector
                Text(
                    text = "Select Target Language:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Column {
                    OutlinedButton(
                        onClick = { isTargetDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Target: ${lyricsState.selectedTargetLang.displayName}")
                    }

                    DropdownMenu(
                        expanded = isTargetDropdownExpanded,
                        onDismissRequest = { isTargetDropdownExpanded = false }
                    ) {
                        targetLanguageOptions.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang.displayName) },
                                onClick = {
                                    viewModel.onTargetLanguageSelected(lang)
                                    isTargetDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val effectiveSourceCode = if (lyricsState.selectedSourceLang.code != "und") lyricsState.selectedSourceLang.code else lyricsState.detectedLanguageCode

                Button(
                    onClick = { viewModel.translateLyrics() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = lyricsState.inputLyrics.isNotBlank() && !lyricsState.isTranslating && effectiveSourceCode != "und",
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (lyricsState.isTranslating) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Text(lyricsState.translationStatusText)
                        }
                    } else {
                        Text("Translate Lyrics")
                    }
                }

                if (lyricsState.translatedLyricsText != null) {
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Translated Lyrics (${lyricsState.selectedTargetLang.displayName}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    SelectionContainer {
                        Text(
                            text = lyricsState.translatedLyricsText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (lyricsState.translationError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = lyricsState.translationError,
                        color = MaterialTheme.colorScheme.error,
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

internal suspend fun identifyLyricsLanguage(text: String): String = suspendCancellableCoroutine { cont ->
    if (text.isBlank()) {
        cont.resume("und")
        return@suspendCancellableCoroutine
    }
    try {
        val languageIdentifier = LanguageIdentification.getClient()
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                if (cont.isActive) {
                    cont.resume(if (languageCode.isNullOrBlank() || languageCode == "und") "und" else languageCode)
                }
            }
            .addOnFailureListener {
                if (cont.isActive) {
                    cont.resume("und")
                }
            }
    } catch (_: Exception) {
        if (cont.isActive) {
            cont.resume("und")
        }
    }
}

internal suspend fun translateLyricsOnDevice(
    lyricsText: String,
    sourceLangCode: String,
    targetLangCode: String,
    onStatusUpdate: (String) -> Unit
): String = withContext(Dispatchers.IO) {

    if (sourceLangCode == "und" || sourceLangCode.isBlank()) {
        throw IllegalArgumentException("Source language is unidentified. Please select the source language manually before translating.")
    }

    if (sourceLangCode.lowercase() == targetLangCode.lowercase()) {
        return@withContext "Source and target languages are identical ($sourceLangCode). No translation required."
    }

    onStatusUpdate("Downloading language model (one-time requirement)...")

    val options = TranslatorOptions.Builder()
        .setSourceLanguage(sourceLangCode)
        .setTargetLanguage(targetLangCode)
        .build()

    val translator = Translation.getClient(options)

    try {
        suspendCancellableCoroutine { cont ->
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    if (cont.isActive) cont.resume(Unit)
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }

        onStatusUpdate("Translating line by line...")

        val lines = lyricsText.lines()
        val translatedLines = mutableListOf<String>()
        var untranslatedCount = 0

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank()) {
                translatedLines.add("")
                continue
            }

            if (trimmedLine.startsWith("[") && trimmedLine.endsWith("]")) {
                translatedLines.add(line)
                continue
            }

            try {
                val translatedLine = suspendCancellableCoroutine<String> { cont ->
                    translator.translate(line)
                        .addOnSuccessListener { result ->
                            if (cont.isActive) cont.resume(result)
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume("[Untranslated] $line")
                        }
                }
                if (translatedLine.startsWith("[Untranslated]")) {
                    untranslatedCount++
                }
                translatedLines.add(translatedLine)
            } catch (_: Exception) {
                untranslatedCount++
                translatedLines.add("[Untranslated] $line")
            }
        }

        val resultText = translatedLines.joinToString("\n")
        if (untranslatedCount > 0) {
            "$resultText\n\n[Notice: $untranslatedCount line(s) failed translation and are marked with [Untranslated]]"
        } else {
            resultText
        }

    } finally {
        translator.close()
    }
}

@Composable
private fun AiDjWorkspace(
    context: Context,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var trackAUri by remember { mutableStateOf<Uri?>(null) }
    var trackAName by remember { mutableStateOf<String?>(null) }
    var trackBUri by remember { mutableStateOf<Uri?>(null) }
    var trackBName by remember { mutableStateOf<String?>(null) }

    var selectedStyle by remember { mutableStateOf(TransitionStyle.SMOOTH) }
    var crossfadeSeconds by remember { mutableFloatStateOf(4f) }

    var matchTempo by remember { mutableStateOf(false) }
    var isAnalyzingTempo by remember { mutableStateOf(false) }
    var tempoStatusText by remember { mutableStateOf<String?>(null) }
    var calculatedSpeedB by remember { mutableFloatStateOf(1.0f) }

    var isAuditioning by remember { mutableStateOf(false) }
    var auditionStatusText by remember { mutableStateOf("") }
    var auditionProgress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var auditionJob by remember { mutableStateOf<Job?>(null) }

    val audioPickerLauncherA = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        trackAUri = uri
        if (uri != null) {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    trackAName = cursor.getString(nameIndex)
                }
            }
        }
    }

    val audioPickerLauncherB = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        trackBUri = uri
        if (uri != null) {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    trackBName = cursor.getString(nameIndex)
                }
            }
        }
    }

    val playerA = remember(context) { ExoPlayer.Builder(context).build() }
    val playerB = remember(context) { ExoPlayer.Builder(context).build() }

    DisposableEffect(context) {
        onDispose {
            auditionJob?.cancel()
            try {
                playerA.stop()
                playerA.release()
                playerB.stop()
                playerB.release()
            } catch (_: Exception) {}
        }
    }

    fun stopAudition() {
        auditionJob?.cancel()
        auditionJob = null
        try {
            playerA.stop()
            playerB.stop()
        } catch (_: Exception) {}
        isAuditioning = false
    }

    fun triggerTempoAnalysis() {
        val uriA = trackAUri
        val uriB = trackBUri
        if (matchTempo && uriA != null && uriB != null) {
            scope.launch {
                isAnalyzingTempo = true
                tempoStatusText = "Analyzing BPMs on-device..."
                try {
                    val analysisA = AudioAnalyzer.analyze(context, uriA) {}
                    val analysisB = AudioAnalyzer.analyze(context, uriB) {}
                    val bpmA = analysisA.bpm
                    val bpmB = analysisB.bpm

                    if (bpmA != null && bpmB != null && analysisA.bpmConfidence >= 0.35f && analysisB.bpmConfidence >= 0.35f && bpmB > 0) {
                        val ratio = (bpmA.toFloat() / bpmB.toFloat()).coerceIn(0.92f, 1.08f)
                        calculatedSpeedB = ratio
                        tempoStatusText = "Track A: $bpmA BPM | Track B: $bpmB BPM -> Speed B: ${"%.2f".format(ratio)}x"
                    } else {
                        calculatedSpeedB = 1.0f
                        tempoStatusText = "BPM confidence too low for tempo sync."
                    }
                } catch (e: Exception) {
                    calculatedSpeedB = 1.0f
                    tempoStatusText = "Tempo matching failed: ${e.message}"
                } finally {
                    isAnalyzingTempo = false
                }
            }
        } else {
            calculatedSpeedB = 1.0f
            tempoStatusText = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "✨ AI DJ & Smart Transitions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Track Selection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Select Tracks to Crossfade:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Track A Picker
                Button(
                    onClick = { audioPickerLauncherA.launch("audio/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isAuditioning
                ) {
                    Text(if (trackAName != null) "Track A: $trackAName" else "Pick Track A")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Track B Picker
                Button(
                    onClick = { audioPickerLauncherB.launch("audio/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isAuditioning
                ) {
                    Text(if (trackBName != null) "Track B: $trackBName" else "Pick Track B")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Transition Style FilterChips
        Text(
            text = "Transition Style:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedStyle == TransitionStyle.SMOOTH,
                onClick = { selectedStyle = TransitionStyle.SMOOTH },
                label = { Text("Smooth") },
                enabled = !isAuditioning
            )

            FilterChip(
                selected = selectedStyle == TransitionStyle.LINEAR,
                onClick = { selectedStyle = TransitionStyle.LINEAR },
                label = { Text("Linear") },
                enabled = !isAuditioning
            )

            FilterChip(
                selected = selectedStyle == TransitionStyle.QUICK_CUT,
                onClick = { selectedStyle = TransitionStyle.QUICK_CUT },
                label = { Text("Quick Cut") },
                enabled = !isAuditioning
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Crossfade Duration Slider
        Text(
            text = if (selectedStyle == TransitionStyle.QUICK_CUT) "Crossfade Duration: 0.5s (Quick Cut)" else "Crossfade Duration: ${crossfadeSeconds.toInt()}s",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Slider(
            value = crossfadeSeconds,
            onValueChange = { crossfadeSeconds = it },
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isAuditioning && selectedStyle != TransitionStyle.QUICK_CUT
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Match Tempo Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Match Tempo (BPM Sync)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Adjusts Track B playback speed to match Track A",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = matchTempo,
                onCheckedChange = { checked ->
                    matchTempo = checked
                    triggerTempoAnalysis()
                },
                enabled = !isAuditioning
            )
        }

        if (tempoStatusText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tempoStatusText!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Audition Button / Controls
        if (isAuditioning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    LinearProgressIndicator(
                        progress = { auditionProgress },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = auditionStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { stopAudition() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Stop Audition ⏹")
                    }
                }
            }
        } else {
            Button(
                onClick = {
                    val uriA = trackAUri ?: return@Button
                    val uriB = trackBUri ?: return@Button

                    PlayerController.pause()

                    auditionJob?.cancel()
                    auditionJob = scope.launch {
                        isAuditioning = true
                        errorMessage = null
                        try {
                            val effectiveCrossfadeSec = if (selectedStyle == TransitionStyle.QUICK_CUT) 0.5f else crossfadeSeconds
                            val crossfadeMs = (effectiveCrossfadeSec * 1000).toLong()

                            playerA.stop()
                            playerB.stop()

                            playerA.setMediaItem(MediaItem.fromUri(uriA))
                            playerB.setMediaItem(MediaItem.fromUri(uriB))

                            if (matchTempo && calculatedSpeedB != 1.0f) {
                                playerB.playbackParameters = PlaybackParameters(calculatedSpeedB)
                            } else {
                                playerB.playbackParameters = PlaybackParameters(1.0f)
                            }

                            playerA.prepare()
                            playerB.prepare()

                            // Wait for duration
                            while (playerA.duration <= 0L && isActive) {
                                delay(100)
                            }

                            val durationA = playerA.duration.coerceAtLeast(10000L)
                            val preTransitionLeadMs = 8000L
                            val transitionStartMs = max(0L, durationA - crossfadeMs)
                            val playStartMs = max(0L, transitionStartMs - preTransitionLeadMs)

                            playerA.volume = 1.0f
                            playerB.volume = 0.0f

                            playerA.seekTo(playStartMs)
                            playerA.play()

                            auditionStatusText = "Auditioning Track A lead-in..."

                            while (isActive && playerA.isPlaying && playerA.currentPosition < transitionStartMs) {
                                val pos = playerA.currentPosition
                                val elapsed = pos - playStartMs
                                val totalLead = transitionStartMs - playStartMs
                                auditionProgress = if (totalLead > 0) (elapsed.toFloat() / totalLead * 0.4f).coerceIn(0f, 0.4f) else 0f
                                delay(100)
                            }

                            auditionStatusText = "Crossfading (${selectedStyle.name.lowercase().replace('_', ' ')}) ..."
                            playerB.seekTo(0L)
                            playerB.play()

                            val rampStartTime = System.currentTimeMillis()
                            while (isActive) {
                                val elapsedRamp = System.currentTimeMillis() - rampStartTime
                                if (elapsedRamp >= crossfadeMs) break

                                val t = (elapsedRamp.toDouble() / crossfadeMs).coerceIn(0.0, 1.0)
                                val (volA, volB) = when (selectedStyle) {
                                    TransitionStyle.SMOOTH -> {
                                        val angle = t * (Math.PI / 2.0)
                                        cos(angle).toFloat() to sin(angle).toFloat()
                                    }
                                    TransitionStyle.LINEAR -> {
                                        (1.0 - t).toFloat() to t.toFloat()
                                    }
                                    TransitionStyle.QUICK_CUT -> {
                                        if (t < 0.5) 1.0f to 0.0f else 0.0f to 1.0f
                                    }
                                }

                                playerA.volume = volA.coerceIn(0f, 1f)
                                playerB.volume = volB.coerceIn(0f, 1f)

                                auditionProgress = (0.4f + (elapsedRamp.toFloat() / crossfadeMs) * 0.4f).coerceIn(0.4f, 0.8f)
                                delay(50)
                            }

                            playerA.pause()
                            playerA.volume = 1.0f
                            playerB.volume = 1.0f

                            auditionStatusText = "Playing Track B outflow..."
                            val postTransitionEndTime = System.currentTimeMillis() + 5000L
                            while (isActive && System.currentTimeMillis() < postTransitionEndTime) {
                                val rem = postTransitionEndTime - System.currentTimeMillis()
                                auditionProgress = (0.8f + (1f - rem.toFloat() / 5000f) * 0.2f).coerceIn(0.8f, 1.0f)
                                delay(100)
                            }

                            playerB.pause()
                            auditionStatusText = "Transition audition complete"
                            auditionProgress = 1.0f

                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Audition failed."
                        } finally {
                            isAuditioning = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = trackAUri != null && trackBUri != null && !isAuditioning,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Audition Transition ▶")
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = {
                stopAudition()
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
