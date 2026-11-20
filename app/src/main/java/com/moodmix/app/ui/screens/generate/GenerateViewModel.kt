package com.moodmix.app.ui.screens.generate

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moodmix.app.data.repository.MusicRepository
import com.moodmix.app.data.storage.ChatMessage
import com.moodmix.app.data.storage.ChatSession
import com.moodmix.app.data.storage.ChatStorage
import com.moodmix.app.data.storage.GeneratedFiles
import com.moodmix.app.data.storage.MessageStatus
import com.moodmix.app.network.AceStepApi
import com.moodmix.app.playback.PlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

@Immutable
data class GenerateUiState(
    val sessions: List<ChatSession> = emptyList(),
    val currentId: String = "",
    val input: String = "",
    val duration: Float = 60f,
    val isGenerating: Boolean = false,
    val generatingMessageId: String? = null,
    val statusText: String = ""
) {
    val current: ChatSession
        get() = sessions.firstOrNull { it.id == currentId } ?: ChatSession(id = currentId)

    val isGeneratingHere: Boolean
        get() = generatingMessageId != null && current.messages.any { it.id == generatingMessageId }
}

class GenerateViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = ChatStorage(application)
    private val saveDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var job: Job? = null
    private var lastInitialPrompt = ""

    private val _ui = MutableStateFlow(GenerateUiState())
    val uiState: StateFlow<GenerateUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        val fresh = ChatSession()
        _ui.value = GenerateUiState(sessions = listOf(fresh), currentId = fresh.id)
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = storage.loadAll().map { repairInterrupted(it) }
            _ui.update { it.copy(sessions = it.sessions + loaded) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        saveDispatcher.close()
    }

    // ---------- input ----------

    fun initPrompt(prompt: String) {
        if (prompt.isBlank() || prompt == lastInitialPrompt) return
        lastInitialPrompt = prompt
        _ui.update { it.copy(input = prompt.take(400)) }
    }

    fun onInputChanged(text: String) = _ui.update { it.copy(input = text.take(400)) }

    fun onDurationChanged(value: Float) = _ui.update { it.copy(duration = value) }

    // ---------- generate / stop ----------

    fun send() {
        val s = _ui.value
        val prompt = s.input.trim()
        if (prompt.isBlank() || s.isGenerating) return
        val seconds = s.duration.toInt().coerceIn(30, 120)
        val chatId = s.currentId

        val userMsg = ChatMessage(isUser = true, text = prompt, durationSeconds = seconds)
        val aiMsg = ChatMessage(
            isUser = false, text = prompt, durationSeconds = seconds,
            status = MessageStatus.GENERATING
        )

        updateChat(chatId) { c ->
            c.copy(
                title = if (c.messages.isEmpty()) prompt.take(40) else c.title,
                updatedAt = System.currentTimeMillis(),
                messages = c.messages + userMsg + aiMsg
            )
        }
        _ui.update {
            it.copy(input = "", isGenerating = true, generatingMessageId = aiMsg.id, statusText = "Sending your idea…")
        }

        job = viewModelScope.launch {
            try {
                val file = AceStepApi.generateTrack(
                    context = getApplication<Application>(),
                    prompt = prompt,
                    durationSeconds = seconds,
                    onStatusUpdate = { msg -> _ui.update { it.copy(statusText = msg) } }
                )
                mutateMessage(aiMsg.id) { it.copy(status = MessageStatus.DONE, audioFileName = file.name) }
                endGeneration(aiMsg.id)
                                viewModelScope.launch(saveDispatcher) {
                    MusicRepository.getInstance(getApplication<Application>()).notifyLibraryUpdated()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutateMessage(aiMsg.id) { it.copy(status = MessageStatus.ERROR, errorText = friendly(e)) }
                endGeneration(aiMsg.id)
            }
        }
    }

    /** Stops WAITING for the track. The Kaggle GPU still finishes the task it already started. */
    fun stop() {
        val msgId = _ui.value.generatingMessageId ?: return
        job?.cancel()
        job = null
        mutateMessage(msgId) { it.copy(status = MessageStatus.CANCELLED) }
        endGeneration(msgId)
    }

    private fun friendly(e: Exception): String {
        val m = e.message.orEmpty()
        return when {
            m.contains("404") ->
                "Can't reach the music server (404). Check that the Kaggle notebook and ngrok tunnel are running."
            e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.SocketTimeoutException ->
                "Couldn't connect to the music server. Check your internet and that the Kaggle notebook is running."
            m.isNotBlank() -> m
            else -> "Music generation failed."
        }
    }

    // ---------- chats ----------

    fun newChat() {
        _ui.update { st ->
            val empty = st.sessions.firstOrNull { it.messages.isEmpty() }
            if (empty != null) st.copy(currentId = empty.id)
            else {
                val fresh = ChatSession()
                st.copy(sessions = listOf(fresh) + st.sessions, currentId = fresh.id)
            }
        }
    }

    fun selectChat(id: String) {
        _ui.update { st ->
            if (st.sessions.none { it.id == id }) st
            else st.copy(
                currentId = id,
                sessions = st.sessions.filter { it.messages.isNotEmpty() || it.id == id }
            )
        }
    }

    fun deleteChat(id: String) {
        val st = _ui.value
        val chat = st.sessions.firstOrNull { it.id == id } ?: return
        val app = getApplication<Application>()

        val gen = st.generatingMessageId
        if (gen != null && chat.messages.any { it.id == gen }) {
            job?.cancel(); job = null
            endGeneration(gen)
        }

        val names = chat.messages.mapNotNull { it.audioFileName }
        val playing = PlayerController.currentTrack.value?.id
        if (playing != null && playing in names) PlayerController.closePlayback()

        _ui.update { s ->
            val remaining = s.sessions.filter { it.id != id }
            if (s.currentId == id) {
                val fresh = ChatSession()
                s.copy(sessions = listOf(fresh) + remaining, currentId = fresh.id)
            } else s.copy(sessions = remaining)
        }

        viewModelScope.launch(Dispatchers.IO) {
            chat.messages.forEach { m ->
                m.audioFileName?.let { GeneratedFiles.delete(app, it) }
                m.exportedUri?.let {
                    try { app.contentResolver.delete(Uri.parse(it), null, null) } catch (_: Exception) {}
                }
            }
            storage.delete(id)
            MusicRepository.getInstance(app).notifyLibraryUpdated()
        }
    }

    // ---------- export to Music/MoodMix ----------

    fun export(message: ChatMessage) {
        val name = message.audioFileName ?: return
        if (message.exportedUri != null) return
        val app = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = GeneratedFiles.find(app, name)
                if (file == null) {
                    _events.tryEmit("Audio file not found.")
                    return@launch
                }
                val title = message.text
                    .replace(Regex("[^\\p{L}\\p{N} _-]"), "")
                    .trim().take(40).ifBlank { "MoodMix track" }

                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, "$title.wav")
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/MoodMix")
                }
                val resolver = app.contentResolver
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Could not create MediaStore entry")
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: throw IllegalStateException("Could not open output stream")

                mutateMessage(message.id) { it.copy(exportedUri = uri.toString()) }
                _events.tryEmit("Saved to Music/MoodMix")
            } catch (e: Exception) {
                _events.tryEmit("Export failed: ${e.message}")
            }
        }
    }

    // ---------- helpers ----------

    private fun repairInterrupted(chat: ChatSession): ChatSession {
        if (chat.messages.none { it.status == MessageStatus.GENERATING }) return chat
        val fixed = chat.copy(messages = chat.messages.map {
            if (it.status == MessageStatus.GENERATING)
                it.copy(status = MessageStatus.ERROR, errorText = "Generation was interrupted because the app was closed.")
            else it
        })
        storage.save(fixed)
        return fixed
    }

    private fun saveAsync(chat: ChatSession) {
        if (chat.messages.isEmpty()) return
        viewModelScope.launch(saveDispatcher) { storage.save(chat) }
    }

    private fun updateChat(chatId: String, transform: (ChatSession) -> ChatSession) {
        var changed: ChatSession? = null
        _ui.update { s ->
            changed = null
            s.copy(sessions = s.sessions.map { c ->
                if (c.id == chatId) transform(c).also { changed = it } else c
            })
        }
        changed?.let { saveAsync(it) }
    }

    private fun mutateMessage(messageId: String, transform: (ChatMessage) -> ChatMessage) {
        val chat = _ui.value.sessions.firstOrNull { c -> c.messages.any { it.id == messageId } } ?: return
        updateChat(chat.id) { c ->
            c.copy(
                updatedAt = System.currentTimeMillis(),
                messages = c.messages.map { m -> if (m.id == messageId) transform(m) else m }
            )
        }
    }

    private fun endGeneration(messageId: String) {
        _ui.update {
            if (it.generatingMessageId == messageId)
                it.copy(isGenerating = false, generatingMessageId = null, statusText = "")
            else it
        }
    }
}