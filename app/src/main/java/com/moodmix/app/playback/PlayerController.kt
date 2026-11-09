package com.moodmix.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.net.toUri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.moodmix.app.data.repository.MusicRepository
import com.moodmix.app.domain.model.PlayableTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object PlayerController {

    private var appContext: Context? = null
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var positionPollJob: Job? = null
    private var lastRecordedTrackId: String? = null

    private val _currentTrack = MutableStateFlow<PlayableTrack?>(null)
    val currentTrack: StateFlow<PlayableTrack?> = _currentTrack.asStateFlow()

    private val _currentTrackId = MutableStateFlow<String?>(null)
    val currentTrackId: StateFlow<String?> = _currentTrackId.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _queue = MutableStateFlow<List<PlayableTrack>>(emptyList())
    val queue: StateFlow<List<PlayableTrack>> = _queue.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private val trackMap = mutableMapOf<String, PlayableTrack>()

    fun init(context: Context) {
        if (mediaController != null || controllerFuture != null) return
        appContext = context.applicationContext

        val sessionToken = SessionToken(
            context.applicationContext,
            ComponentName(context.applicationContext, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context.applicationContext, sessionToken).buildAsync()
        controllerFuture = future

        future.addListener({
            try {
                val controller = future.get()
                mediaController = controller
                attachPlayerListener(controller)
            } catch (_: Exception) {
            }
        }, MoreExecutors.directExecutor())
    }

    private fun attachPlayerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) {
                    _playbackError.value = null
                    startPositionPolling()
                } else {
                    stopPositionPolling()
                    _position.value = controller.currentPosition.coerceAtLeast(0L)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    _duration.value = controller.duration.coerceAtLeast(0L)
                    _playbackError.value = null
                } else if (playbackState == Player.STATE_ENDED) {
                    _isPlaying.value = false
                    stopPositionPolling()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _playbackError.value = error.message ?: "Playback failed"
                _isPlaying.value = false
                _isBuffering.value = false
                stopPositionPolling()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) {
                    val track = trackMap[mediaItem.mediaId] ?: mediaItemToTrack(mediaItem)
                    _currentTrack.value = track
                    _currentTrackId.value = track.id
                    _currentIndex.value = controller.currentMediaItemIndex.coerceAtLeast(0)
                    _duration.value = controller.duration.coerceAtLeast(0L)
                    _position.value = controller.currentPosition.coerceAtLeast(0L)
                    _playbackError.value = null

                    if (lastRecordedTrackId != track.id) {
                        lastRecordedTrackId = track.id
                        appContext?.let { ctx ->
                            scope.launch {
                                MusicRepository.getInstance(ctx).recordPlayedTrack(track)
                            }
                        }
                    }
                } else {
                    _currentTrack.value = null
                    _currentTrackId.value = null
                    _currentIndex.value = 0
                    _position.value = 0L
                    _duration.value = 0L
                    lastRecordedTrackId = null
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffle.value = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }
        })

        _isPlaying.value = controller.isPlaying
        _shuffle.value = controller.shuffleModeEnabled
        _repeatMode.value = controller.repeatMode
        _currentIndex.value = controller.currentMediaItemIndex.coerceAtLeast(0)
        if (controller.currentMediaItem != null) {
            val track = trackMap[controller.currentMediaItem!!.mediaId] ?: mediaItemToTrack(controller.currentMediaItem!!)
            _currentTrack.value = track
            _currentTrackId.value = track.id
        }
    }

    private fun startPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = scope.launch {
            while (isActive && _isPlaying.value) {
                mediaController?.let { controller ->
                    _position.value = controller.currentPosition.coerceAtLeast(0L)
                    if (controller.duration > 0) {
                        _duration.value = controller.duration
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = null
    }

    fun play(track: PlayableTrack, newQueue: List<PlayableTrack> = listOf(track)) {
        val controller = mediaController ?: return
        val validQueue = newQueue.filter { it.audioUri.isNotBlank() }
        if (validQueue.isEmpty()) return

        _queue.value = validQueue
        validQueue.forEach { trackMap[it.id] = it }

        val mediaItems = validQueue.map { trackToMediaItem(it) }
        val startIndex = validQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()

        val activeTrack = validQueue.getOrNull(startIndex) ?: track
        _currentTrack.value = activeTrack
        _currentTrackId.value = activeTrack.id
        _currentIndex.value = startIndex
        _playbackError.value = null
    }

    fun closePlayback() {
        val controller = mediaController
        if (controller != null) {
            controller.stop()
            controller.clearMediaItems()
        }
        stopPositionPolling()
        _currentTrack.value = null
        _currentTrackId.value = null
        _currentIndex.value = 0
        _position.value = 0L
        _duration.value = 0L
        _queue.value = emptyList()
        _isPlaying.value = false
        _isBuffering.value = false
        _playbackError.value = null
        trackMap.clear()
        lastRecordedTrackId = null
    }

    fun skipToQueueItem(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekTo(index, 0L)
            _currentIndex.value = index
            val item = controller.getMediaItemAt(index)
            val track = trackMap[item.mediaId] ?: mediaItemToTrack(item)
            _currentTrack.value = track
            _currentTrackId.value = track.id
            if (!controller.isPlaying) {
                controller.play()
            }
        }
    }

    fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.removeMediaItem(index)
            _queue.update { currentList ->
                currentList.filterIndexed { i, _ -> i != index }
            }
            val newIndex = controller.currentMediaItemIndex.coerceAtLeast(0)
            _currentIndex.value = newIndex
            val currentItem = controller.currentMediaItem
            if (currentItem != null) {
                val activeTrack = trackMap[currentItem.mediaId] ?: mediaItemToTrack(currentItem)
                _currentTrack.value = activeTrack
                _currentTrackId.value = activeTrack.id
            } else {
                _currentTrack.value = null
                _currentTrackId.value = null
            }
        }
    }

    fun addToQueue(track: PlayableTrack) {
        val controller = mediaController ?: return
        if (track.audioUri.isBlank()) return
        trackMap[track.id] = track
        _queue.update { it + track }
        controller.addMediaItem(trackToMediaItem(track))
    }

    fun togglePlay() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun pause() {
        mediaController?.pause()
    }

    fun next() {
        mediaController?.seekToNextMediaItem()
    }

    fun previous() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _position.value = positionMs
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        val newShuffle = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = newShuffle
        _shuffle.value = newShuffle
    }

    fun cycleRepeat() {
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = nextMode
        _repeatMode.value = nextMode
    }

    private fun trackToMediaItem(track: PlayableTrack): MediaItem {
        val extras = Bundle().apply {
            putBoolean("isGenerated", track.isGenerated)
            putBoolean("isPreview", track.isPreview)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setArtworkUri(track.artworkUrl?.toUri())
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.audioUri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun mediaItemToTrack(mediaItem: MediaItem): PlayableTrack {
        val metadata = mediaItem.mediaMetadata
        val extras = metadata.extras
        return PlayableTrack(
            id = mediaItem.mediaId,
            title = metadata.title?.toString() ?: "Unknown Track",
            artist = metadata.artist?.toString() ?: "MoodMix AI",
            artworkUrl = metadata.artworkUri?.toString(),
            audioUri = mediaItem.localConfiguration?.uri?.toString() ?: "",
            isGenerated = extras?.getBoolean("isGenerated") ?: false,
            isPreview = extras?.getBoolean("isPreview") ?: false
        )
    }
}
