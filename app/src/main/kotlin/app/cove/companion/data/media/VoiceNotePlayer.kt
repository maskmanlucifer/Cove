package app.cove.companion.data.media

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What is playing: the media row [id] and how far in. */
data class PlaybackState(val id: String? = null, val positionMs: Long = 0)

/** Plays one voice note at a time with [MediaPlayer]; tapping the playing row pauses it. */
class VoiceNotePlayer {
    private var player: MediaPlayer? = null
    private val _state = MutableStateFlow(PlaybackState())

    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** Starts [path] for row [id], or stops it when it is already playing. */
    fun toggle(id: String, path: String) {
        if (_state.value.id == id) {
            stop()
            return
        }
        stop()
        val p = MediaPlayer()
        try {
            p.setDataSource(path)
            p.setOnCompletionListener { stop() }
            p.prepare()
            p.start()
            player = p
            _state.value = PlaybackState(id, 0)
        } catch (e: Exception) {
            p.release()
        }
    }

    /** Refreshes the playback position; call while playing. */
    fun tick() {
        val p = player ?: return
        _state.value = _state.value.copy(positionMs = p.currentPosition.toLong())
    }

    fun stop() {
        player?.release()
        player = null
        _state.value = PlaybackState()
    }
}
