package com.moodmix.app.ui.screens.generate

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodmix.app.network.AceStepApi
import com.moodmix.app.network.MusicGenApi
import com.moodmix.app.network.MusicGenerationRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

@Immutable
data class GenerateUiState(
    val prompt: String = "",
    val duration: Float = 15f,
    val generateLyrics: Boolean = false,
    val isGenerating: Boolean = false,
    val generationStatusText: String = "Connecting to server...",
    val isDownloading: Boolean = false,
    val generatedFile: File? = null,
    val generatedPrompt: String = "",
    val generatedLyrics: String? = null,
    val errorMessage: String? = null,
    val isPlaying: Boolean = false,
    val playbackPosition: Long = 0L,
    val playbackDuration: Long = 0L
)

class GenerateViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GenerateUiState())
    val uiState: StateFlow<GenerateUiState> = _uiState.asStateFlow()

    fun initPrompt(initialPrompt: String) {
        if (initialPrompt.isNotBlank() && _uiState.value.prompt.isBlank()) {
            _uiState.update { it.copy(prompt = initialPrompt.take(400)) }
        }
    }

    fun onPromptChanged(newPrompt: String) {
        _uiState.update { it.copy(prompt = newPrompt.take(400)) }
    }

    fun onDurationChanged(newDuration: Float) {
        _uiState.update { it.copy(duration = newDuration) }
    }

    fun onGenerateLyricsToggled(enabled: Boolean) {
        _uiState.update { it.copy(generateLyrics = enabled) }
    }

    fun updatePlaybackState(isPlaying: Boolean, position: Long, duration: Long) {
        _uiState.update {
            it.copy(
                isPlaying = isPlaying,
                playbackPosition = position,
                playbackDuration = duration
            )
        }
    }

    fun generateMusic(context: Context, onSuccess: (File) -> Unit = {}) {
        val currentPrompt = _uiState.value.prompt.trim()
        if (currentPrompt.isBlank() || _uiState.value.isGenerating) return

        val durationSeconds = _uiState.value.duration.toInt().coerceIn(5, 30)

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    errorMessage = null,
                    generatedFile = null,
                    generatedPrompt = "",
                    generatedLyrics = null,
                    generationStatusText = "Connecting to server..."
                )
            }

            try {
                // Primary Generation: ACE-Step 1.5 Turbo
                val generatedFile = try {
                    AceStepApi.generateTrack(
                        context = context,
                        prompt = currentPrompt,
                        durationSeconds = durationSeconds,
                        generateLyrics = _uiState.value.generateLyrics,
                        onStatusUpdate = { statusMsg ->
                            _uiState.update { it.copy(generationStatusText = statusMsg) }
                        }
                    )
                } catch (aceStepException: Exception) {
                    // Fallback Generation: Secondary Server
                    _uiState.update { it.copy(generationStatusText = "Connecting to secondary server...") }
                    val maxNewTokens = (durationSeconds * 17.0f).toInt()
                    val request = MusicGenerationRequest(
                        prompt = currentPrompt,
                        maxNewTokens = maxNewTokens,
                        generateLyrics = _uiState.value.generateLyrics
                    )
                    val result = MusicGenApi.generateWithDetails(
                        context = context,
                        request = request,
                        onStatusUpdate = { statusMsg ->
                            _uiState.update { it.copy(generationStatusText = statusMsg) }
                        }
                    )
                    result.audioFile
                }

                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        generatedFile = generatedFile,
                        generatedPrompt = currentPrompt,
                        generatedLyrics = if (_uiState.value.generateLyrics) "Lyrics prompt attached: $currentPrompt" else null
                    )
                }

                onSuccess(generatedFile)

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = e.message ?: "Music generation failed."
                    )
                }
            }
        }
    }
}
