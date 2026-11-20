package com.moodmix.app.ui.screens.search

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.moodmix.app.network.SongRecognitionResult
import com.moodmix.app.network.SongRecognizer
import com.moodmix.app.ui.components.MoodChip
import com.moodmix.app.ui.theme.MintPrimary
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

private enum class VoiceMode { SPEAK, IDENTIFY }
private enum class VoicePhase { IDLE, LISTENING, PROCESSING, ERROR }

/**
 * YouTube-style voice sheet.
 *  - SPEAK: listens, shows live partial text, and AUTO-SUBMITS the final result as a search.
 *  - IDENTIFY: records ~10s of the music playing nearby and asks a fingerprinting service which song it is.
 * Either way [onQuery] is called with the text to search for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSearchSheet(
    onDismiss: () -> Unit,
    onQuery: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val latestOnQuery by rememberUpdatedState(onQuery)

    var mode by remember { mutableStateOf(VoiceMode.SPEAK) }
    var phase by remember { mutableStateOf(VoicePhase.IDLE) }
    var partialText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var level by remember { mutableStateOf(0f) }
    var secondsLeft by remember { mutableStateOf(0) }
    var identifyJob by remember { mutableStateOf<Job?>(null) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    val speechAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val recognizer = remember { if (speechAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null }
    DisposableEffect(recognizer) {
        onDispose {
            recognizer?.cancel()
            recognizer?.destroy()
        }
    }

    val listener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { phase = VoicePhase.LISTENING }
            override fun onBeginningOfSpeech() { phase = VoicePhase.LISTENING }
            override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { phase = VoicePhase.PROCESSING; level = 0f }

            override fun onError(error: Int) {
                level = 0f
                phase = VoicePhase.ERROR
                message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Tap the mic and try again."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Check your internet connection and try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy. Try again in a moment."
                    else -> "Something went wrong. Tap the mic to retry."
                }
            }

            override fun onResults(results: Bundle?) {
                level = 0f
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty().trim()
                if (text.isNotBlank()) {
                    partialText = text
                    phase = VoicePhase.PROCESSING
                    latestOnQuery(text) // auto-execute the search
                } else {
                    phase = VoicePhase.ERROR
                    message = "Didn't catch that. Tap the mic and try again."
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isNotBlank()) partialText = text
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun stopAll() {
        identifyJob?.cancel()
        identifyJob = null
        recognizer?.cancel()
        level = 0f
    }

    fun startSpeech() {
        val sr = recognizer ?: return
        message = null
        partialText = ""
        phase = VoicePhase.LISTENING
        try {
            sr.setRecognitionListener(listener)
            sr.cancel()
            sr.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }
            )
        } catch (e: Exception) {
            phase = VoicePhase.ERROR
            message = e.message ?: "Could not start speech recognition"
        }
    }

    fun startIdentify() {
        recognizer?.cancel()
        identifyJob?.cancel()
        message = null
        partialText = ""
        phase = VoicePhase.LISTENING
        identifyJob = scope.launch {
            val outcome = SongRecognizer.identify(
                context = context,
                onTick = { secondsLeft = it },
                onUploading = { phase = VoicePhase.PROCESSING }
            )
            when (outcome) {
                is SongRecognitionResult.Match -> latestOnQuery("${outcome.title} ${outcome.artist}".trim())
                SongRecognitionResult.NoMatch -> {
                    phase = VoicePhase.ERROR
                    message = "Couldn't recognise that song. Try again closer to the speaker."
                }
                SongRecognitionResult.NotConfigured -> {
                    phase = VoicePhase.ERROR
                    message = "Song identification needs an AudD API token (see SongRecognizer.kt)."
                }
                is SongRecognitionResult.Failure -> {
                    phase = VoicePhase.ERROR
                    message = outcome.message
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Auto-start whenever the sheet opens, the mode changes, or permission is granted.
    LaunchedEffect(mode, hasPermission) {
        stopAll()
        partialText = ""
        message = null
        phase = VoicePhase.IDLE
        if (!hasPermission) return@LaunchedEffect
        when (mode) {
            VoiceMode.SPEAK -> if (speechAvailable) {
                startSpeech()
            } else {
                phase = VoicePhase.ERROR
                message = "Speech recognition isn't available here. Try \"Identify song\"."
            }
            VoiceMode.IDENTIFY -> startIdentify()
        }
    }

    fun onMicTap() {
        when {
            !hasPermission -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            phase == VoicePhase.LISTENING && mode == VoiceMode.SPEAK -> recognizer?.stopListening()
            phase == VoicePhase.LISTENING || phase == VoicePhase.PROCESSING -> {
                stopAll()
                phase = VoicePhase.IDLE
            }
            mode == VoiceMode.SPEAK -> startSpeech()
            else -> startIdentify()
        }
    }

    val headline = when {
        !hasPermission -> "Microphone access needed"
        phase == VoicePhase.LISTENING && mode == VoiceMode.SPEAK -> partialText.ifBlank { "Listening…" }
        phase == VoicePhase.LISTENING -> "Listening to the music… ${secondsLeft}s"
        phase == VoicePhase.PROCESSING -> if (mode == VoiceMode.SPEAK) "Searching…" else "Identifying song…"
        message != null -> message.orEmpty()
        else -> "Tap the mic"
    }
    val hint = if (mode == VoiceMode.SPEAK) {
        "Say a song, artist or album name"
    } else {
        "Hold your phone close to the speaker"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0B0B0F),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            PulsingMic(
                active = phase == VoicePhase.LISTENING,
                level = level,
                identify = mode == VoiceMode.IDENTIFY,
                onClick = { onMicTap() }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoodChip(text = "🎤 Speak", selected = mode == VoiceMode.SPEAK, onClick = { mode = VoiceMode.SPEAK })
                MoodChip(text = "🎵 Identify song", selected = mode == VoiceMode.IDENTIFY, onClick = { mode = VoiceMode.IDENTIFY })
            }
        }
    }
}

@Composable
private fun PulsingMic(
    active: Boolean,
    level: Float,
    identify: Boolean,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "micPulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )
    val smoothLevel by animateFloatAsState(targetValue = level, label = "level")

    Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        if (active) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        val s = 1f + pulse + smoothLevel * 0.6f
                        scaleX = s
                        scaleY = s
                        alpha = (1f - pulse) * 0.35f
                    }
                    .background(MintPrimary, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        val s = 1.1f + smoothLevel * 0.8f
                        scaleX = s
                        scaleY = s
                        alpha = 0.2f
                    }
                    .background(MintPrimary, CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MintPrimary)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (identify) Icons.Default.GraphicEq else Icons.Default.Mic,
                contentDescription = if (identify) "Identify song" else "Voice search",
                tint = Color.Black,
                modifier = Modifier.size(42.dp)
            )
        }
    }
}
