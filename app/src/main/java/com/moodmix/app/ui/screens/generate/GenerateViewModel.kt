package com.moodmix.app.ui.screens.generate

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodmix.app.network.AceStepApi
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
    val isGenerating: Boolean = false,
    val generationStatusText: String = "Connecting to ACE-Step server...",
    val isDownloading: Boolean = false,
    val generatedFile: File? = null,
    val generatedPrompt: String = "",
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
                    generationStatusText = "Connecting to ACE-Step 1.5 Turbo server..."
                )
            }

            try {
                val generatedFile = AceStepApi.generateTrack(
                    context = context,
                    prompt = currentPrompt,
                    durationSeconds = durationSeconds,
                    onStatusUpdate = { statusMsg ->
                        _uiState.update { it.copy(generationStatusText = statusMsg) }
                    }
                )

                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        generatedFile = generatedFile,
                        generatedPrompt = currentPrompt
                    )
                }

                onSuccess(generatedFile)

            } catch (e: Exception) {
                val friendlyError = if (e.message?.contains("404") == true) {
                    "ACE-Step server endpoint not found (HTTP 404). Please verify ngrok tunnel status."
                } else if (e.message?.contains("connect") == true || e.message?.contains("timeout") == true) {
                    "Could not connect to ACE-Step server. Please check your internet connection or server status."
                } else {
                    e.message ?: "ACE-Step music generation failed."
                }

                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = friendlyError
                    )
                }
            }
        }
    }
}
