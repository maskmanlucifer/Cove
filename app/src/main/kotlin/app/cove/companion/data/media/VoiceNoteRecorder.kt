package app.cove.companion.data.media

import android.content.Context
import android.media.MediaRecorder
import android.os.SystemClock
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A finished recording. */
data class VoiceRecording(val file: File, val durationMs: Long)

/**
 * Records a voice note as Opus in an OGG container, 16 kbps mono (about 120 KB a minute), and exposes the live
 * duration. The caller must hold RECORD_AUDIO before [start]. Duration is wall time (not the app [app.cove.companion.core.Clock]).
 */
class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var startedAt = 0L
    private var target: File? = null

    private val _elapsedMs = MutableStateFlow(0L)

    /** Milliseconds recorded so far; call [tick] to refresh while recording. */
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    val isRecording: Boolean get() = recorder != null

    /** Starts recording into [file]. Returns false if the microphone could not be opened. */
    fun start(file: File): Boolean {
        if (recorder != null) return false
        val r = MediaRecorder(context)
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(16_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            target = file
            startedAt = SystemClock.elapsedRealtime()
            _elapsedMs.value = 0
            true
        } catch (e: Exception) {
            r.release()
            file.delete()
            false
        }
    }

    /** Refreshes [elapsedMs]. */
    fun tick() {
        if (recorder != null) _elapsedMs.value = SystemClock.elapsedRealtime() - startedAt
    }

    /** Stops and returns the recording, or null if nothing usable was captured (for example under a second). */
    fun stop(): VoiceRecording? {
        val r = recorder ?: return null
        val file = target
        val duration = SystemClock.elapsedRealtime() - startedAt
        recorder = null
        target = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        _elapsedMs.value = 0
        if (!ok || file == null) {
            file?.delete()
            return null
        }
        return VoiceRecording(file, duration)
    }

    /** Throws the current recording away. */
    fun cancel() {
        val file = target
        stop()
        file?.delete()
    }
}
