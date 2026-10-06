package app.cove.companion.feature.journal

import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.newId
import app.cove.companion.core.toLocalDate
import app.cove.companion.data.local.entity.JournalEntryEntity
import app.cove.companion.data.local.entity.JournalMediaEntity
import app.cove.companion.data.media.PlaybackState
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Autosave progress shown at the top of the editor. */
enum class SaveStatus { Idle, Saving, Saved }

data class JournalEditState(
    val loaded: Boolean = false,
    val day: LocalDate = LocalDate.now(),
    val mood: String? = null,
    val media: List<JournalMediaEntity> = emptyList(),
    val status: SaveStatus = SaveStatus.Idle,
    /** True once the entry exists in the database, so "Delete entry" makes sense. */
    val persisted: Boolean = false,
    val recordingMs: Long? = null,
    val playback: PlaybackState = PlaybackState(),
)

/** Edits one journal entry: debounced autosave, attachments, voice recording and playback. */
class JournalEditViewModel(private val c: AppContainer, private val routeId: String) : ViewModel() {
    val title = TextFieldState()
    val body = TextFieldState()

    private val _state = MutableStateFlow(JournalEditState())
    val state: StateFlow<JournalEditState> = _state.asStateFlow()

    private lateinit var entry: JournalEntryEntity
    private var persisted = false
    private var loading = true
    private var recordJob: Job? = null
    private var playJob: Job? = null
    private var recordingId: String? = null
    private val recorder = c.voiceRecorder()
    private val player = c.voicePlayer()

    /** File the camera app is writing; set between launching it and getting the result. */
    var cameraTarget: Pair<File, Uri>? = null

    init {
        viewModelScope.launch {
            val existing = if (routeId == "new" || newEntryDay(routeId) != null) null else c.journal.get(routeId)
            entry = existing ?: c.journal.newEntry(newEntryDay(routeId) ?: c.clock.now().toLocalDate())
            persisted = existing != null
            title.setTextAndPlaceCursorAtEnd(entry.title)
            body.setTextAndPlaceCursorAtEnd(entry.body)
            loading = false
            _state.update { it.copy(loaded = true, day = LocalDate.ofEpochDay(entry.day), mood = entry.mood, persisted = persisted, status = if (persisted) SaveStatus.Saved else SaveStatus.Idle) }
            launch { c.journal.media(entry.id).collect { list -> _state.update { it.copy(media = list) } } }
            launch { player.state.collect { p -> _state.update { it.copy(playback = p) } } }
        }
        val edits = snapshotFlow { title.text.toString() to body.text.toString() }.filter { !loading && it != entry.title to entry.body }
        viewModelScope.launch { edits.collect { _state.update { it.copy(status = SaveStatus.Saving) } } }
        viewModelScope.launch { edits.debounce(AUTOSAVE_MS).collect { save() } }
    }

    fun setMood(mood: String?) {
        _state.update { it.copy(mood = mood, status = SaveStatus.Saving) }
        viewModelScope.launch { save() }
    }

    /** Writes the draft now. A blank entry with no attachments is not stored. */
    suspend fun save() {
        if (loading) return
        val s = _state.value
        val blank = title.text.isBlank() && body.text.isBlank() && s.media.isEmpty()
        if (blank && !persisted) return
        entry = entry.copy(title = title.text.toString(), body = body.text.toString(), mood = s.mood)
        c.journal.save(entry)
        persisted = true
        _state.update { it.copy(status = SaveStatus.Saved, persisted = true) }
        c.searchIndexer.indexText(entry)
    }

    /** Saves and, while the app is on screen, lets the on-device model add its insights. */
    suspend fun finish() {
        save()
        if (persisted) c.appScope.launch { c.searchIndexer.enrich(entry) }
    }

    /** Soft-deletes the entry and its attachments. */
    suspend fun delete() {
        if (!persisted) return
        _state.value.media.forEach { c.journalMedia.remove(it) }
        c.journal.delete(entry.id)
        c.journalSearch.remove(entry.id)
        persisted = false
    }

    fun addPhoto(uri: Uri) {
        viewModelScope.launch {
            ensurePersisted()
            c.journalMedia.addPhoto(entry.id, uri)
            cameraTarget?.first?.delete()
            cameraTarget = null
            save()
        }
    }

    fun newCameraUri(): Uri = c.journalFiles.newCameraTarget().also { cameraTarget = it }.second

    fun removeMedia(media: JournalMediaEntity) {
        if (player.state.value.id == media.id) player.stop()
        viewModelScope.launch {
            c.journalMedia.remove(media)
            save()
        }
    }

    /** Starts a voice note; false when the microphone could not be opened. */
    fun startRecording(): Boolean {
        val id = newId()
        if (!recorder.start(c.journalFiles.voice(id))) return false
        recordingId = id
        player.stop()
        _state.update { it.copy(recordingMs = 0) }
        recordJob = viewModelScope.launch {
            while (recorder.isRecording) {
                recorder.tick()
                _state.update { it.copy(recordingMs = recorder.elapsedMs.value) }
                delay(200)
            }
        }
        return true
    }

    fun stopRecording() {
        recordJob?.cancel()
        val id = recordingId ?: return
        recordingId = null
        val recording = recorder.stop()
        _state.update { it.copy(recordingMs = null) }
        if (recording == null) return
        viewModelScope.launch {
            ensurePersisted()
            c.journalMedia.addVoice(entry.id, id, recording)
            save()
        }
    }

    /** Plays or pauses the voice note [media]. */
    fun togglePlayback(media: JournalMediaEntity) {
        if (recorder.isRecording) return
        player.toggle(media.id, media.localPath)
        playJob?.cancel()
        playJob = viewModelScope.launch {
            while (player.state.value.id != null) {
                player.tick()
                delay(250)
            }
        }
    }

    private suspend fun ensurePersisted() {
        if (!persisted) {
            entry = entry.copy(title = title.text.toString(), body = body.text.toString(), mood = _state.value.mood)
            c.journal.save(entry)
            persisted = true
            _state.update { it.copy(persisted = true) }
        }
    }

    override fun onCleared() {
        if (recorder.isRecording) recorder.cancel()
        player.stop()
    }

    private companion object {
        const val AUTOSAVE_MS = 700L
    }
}
