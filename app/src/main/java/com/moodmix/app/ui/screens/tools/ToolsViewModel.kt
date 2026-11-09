package com.moodmix.app.ui.screens.tools

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodmix.app.lyrics.LyricsAnalysis
import com.moodmix.app.lyrics.LyricsAnalyzer
import com.moodmix.app.network.QwenApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class LyricsIntelligenceUiState(
    val inputLyrics: String = "",
    val analysisResult: LyricsAnalysis? = null,
    val isAnalyzing: Boolean = false,
    val analysisError: String? = null,
    val detectedLanguageCode: String = "und",
    val selectedSourceLang: TargetLanguageOption = sourceLanguageOptions[0],
    val isSourceManuallySelected: Boolean = false,
    val selectedTargetLang: TargetLanguageOption = targetLanguageOptions[0],
    val isTranslating: Boolean = false,
    val translationStatusText: String = "",
    val translatedLyricsText: String? = null,
    val translationError: String? = null
)

class ToolsViewModel : ViewModel() {

    private val _lyricsState = MutableStateFlow(LyricsIntelligenceUiState())
    val lyricsState: StateFlow<LyricsIntelligenceUiState> = _lyricsState.asStateFlow()

    private var langIdJob: Job? = null

    fun onLyricsInputChanged(newText: String) {
        _lyricsState.update {
            it.copy(
                inputLyrics = newText,
                analysisResult = null,
                translatedLyricsText = null,
                translationError = null,
                analysisError = null,
                detectedLanguageCode = "und",
                selectedSourceLang = if (!it.isSourceManuallySelected) sourceLanguageOptions[0] else it.selectedSourceLang
            )
        }

        langIdJob?.cancel()
        if (newText.isNotBlank()) {
            langIdJob = viewModelScope.launch {
                delay(300)
                val code = identifyLyricsLanguage(newText)
                if (isActive && _lyricsState.value.inputLyrics == newText) {
                    _lyricsState.update { current ->
                        val matchingSource = if (!current.isSourceManuallySelected && code != "und") {
                            sourceLanguageOptions.firstOrNull { opt -> opt.code.lowercase() == code.lowercase() } ?: sourceLanguageOptions[0]
                        } else current.selectedSourceLang

                        current.copy(
                            detectedLanguageCode = code,
                            selectedSourceLang = matchingSource
                        )
                    }
                }
            }
        }
    }

    fun onSourceLanguageSelected(option: TargetLanguageOption) {
        _lyricsState.update {
            it.copy(
                selectedSourceLang = option,
                isSourceManuallySelected = (option.code != "und")
            )
        }
    }

    fun onTargetLanguageSelected(option: TargetLanguageOption) {
        _lyricsState.update {
            it.copy(selectedTargetLang = option)
        }
    }

    fun analyzeLyrics() {
        val lyrics = _lyricsState.value.inputLyrics
        if (lyrics.isBlank() || _lyricsState.value.isAnalyzing) return

        viewModelScope.launch {
            _lyricsState.update { it.copy(isAnalyzing = true, analysisError = null) }
            try {
                val result = try {
                    QwenApi.analyzeLyrics(lyrics)
                } catch (_: Exception) {
                    withContext(Dispatchers.Default) {
                        LyricsAnalyzer.analyze(lyrics)
                    }
                }
                _lyricsState.update { it.copy(analysisResult = result, isAnalyzing = false) }
            } catch (e: Exception) {
                _lyricsState.update { it.copy(analysisError = e.message ?: "Analysis failed.", isAnalyzing = false) }
            }
        }
    }

    fun translateLyrics() {
        val state = _lyricsState.value
        val lyrics = state.inputLyrics
        val effectiveSource = if (state.selectedSourceLang.code != "und") state.selectedSourceLang.code else state.detectedLanguageCode

        if (lyrics.isBlank() || state.isTranslating || effectiveSource == "und") return

        viewModelScope.launch {
            _lyricsState.update {
                it.copy(
                    isTranslating = true,
                    translationError = null,
                    translatedLyricsText = null,
                    translationStatusText = "Downloading language model..."
                )
            }
            try {
                val translated = translateLyricsOnDevice(
                    lyricsText = lyrics,
                    sourceLangCode = effectiveSource,
                    targetLangCode = state.selectedTargetLang.code,
                    onStatusUpdate = { status ->
                        _lyricsState.update { current -> current.copy(translationStatusText = status) }
                    }
                )
                _lyricsState.update { it.copy(translatedLyricsText = translated, isTranslating = false) }
            } catch (e: Exception) {
                _lyricsState.update { it.copy(translationError = e.message ?: "Translation failed.", isTranslating = false) }
            }
        }
    }
}
