package app.cove.companion.feature.journal

import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.Undo
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
import kotlinx.coroutines.flow.first
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
    /** Short status per voice note id while it downloads from Drive ("Loading…") or when it cannot be fetched. */
    val voiceHints: Map<String, String> = emptyMap(),
    /** A short calm note about something that did not work ("Couldn't use that photo"); clears itself. */
    val notice: String? = null,
)

/** Area name of the journal's Undo offers. */
internal const val JOURNAL_UNDO = "journal"

/** Edits one journal entry: debounced autosave, attachments, voice recording and playback. */
class JournalEditViewModel(private val c: AppContainer, private val routeId: String) : ViewModel() {
    val title = TextFieldState()
    val body = TextFieldState()

    private val _state = MutableStateFlow(JournalEditState())
    val state: StateFlow<JournalEditState> = _state.asStateFlow()

    private lateinit var entry: JournalEntryEntity
    private var persisted = false
    private var loading = true

    /** Set once the entry is deleted, so neither autosave nor leaving the screen can bring it back. */
    private var deleted = false
    private var noticeJob: Job? = null
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
        if (loading || deleted) return
        val s = _state.value
        val blank = title.text.isBlank() && body.text.isBlank() && s.media.isEmpty()
        if (blank && !persisted) return
        entry = entry.copy(title = title.text.toString(), body = body.text.toString(), mood = s.mood)
        c.journal.save(entry)
        persisted = true
        _state.update { it.copy(status = SaveStatus.Saved, persisted = true) }
        c.searchIndexer.indexText(entry)
    }

    /**
     * Saves and, while the app is on screen, lets the on-device model add its insights. A recording in progress is
     * kept as a voice note. A mood picked with nothing written joins the day's entry (or becomes "Feeling calm"),
     * never an untitled one.
     *
     * @return true when a recording was stopped and attached.
     */
    suspend fun finish(): Boolean {
        if (deleted) return false
        val keptRecording = stopRecordingNow()
        save()
        if (!persisted) saveMoodOnly()
        if (persisted) c.appScope.launch { c.searchIndexer.enrich(entry) }
        return keptRecording
    }

    private suspend fun saveMoodOnly() {
        val mood = _state.value.mood ?: return
        if (title.text.isNotBlank() || body.text.isNotBlank()) return
        val sameDay = c.journal.entries.first().firstOrNull { it.day == entry.day }
        if (sameDay != null) {
            c.journal.save(sameDay.copy(mood = mood))
        } else {
            entry = entry.copy(title = "Feeling $mood", mood = mood)
            c.journal.save(entry)
            persisted = true
        }
    }

    /** Soft-deletes the entry and its attachments and offers "Entry deleted · Undo" where the user lands. */
    suspend fun delete() {
        if (deleted) return
        deleted = true
        loading = true
        if (!persisted) return
        val snapshot = entry.copy(title = title.text.toString(), body = body.text.toString(), mood = _state.value.mood)
        val media = _state.value.media
        media.forEach { c.journalMedia.softRemove(it) }
        c.journal.delete(snapshot.id)
        c.journalSearch.remove(snapshot.id)
        persisted = false
        Undo.center.post(JOURNAL_UNDO, "Entry deleted", onExpire = { media.forEach { c.journalMedia.deleteFiles(it) } }) {
            c.journal.save(snapshot)
            media.forEach { c.journalMedia.restore(it) }
            c.searchIndexer.indexText(snapshot)
        }
    }

    private fun notice(text: String) {
        _state.update { it.copy(notice = text) }
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch {
            delay(NOTICE_MS)
            _state.update { it.copy(notice = null) }
        }
    }

    fun addPhoto(uri: Uri) {
        viewModelScope.launch {
            if (deleted) return@launch
            ensurePersisted()
            val added = c.journalMedia.addPhoto(entry.id, uri)
            cameraTarget?.first?.delete()
            cameraTarget = null
            if (added == null) notice("Couldn’t use that photo")
            save()
        }
    }

    /** Called when this phone has no camera app to take the picture. */
    fun cameraUnavailable() {
        cameraTarget?.first?.delete()
        cameraTarget = null
        notice("No camera app found. Try choosing a photo from your library.")
    }

    fun newCameraUri(): Uri = c.journalFiles.newCameraTarget().also { cameraTarget = it }.second

    fun removeMedia(media: JournalMediaEntity) {
        if (player.state.value.id == media.id) player.stop()
        viewModelScope.launch {
            c.journalMedia.softRemove(media)
            save()
            val what = if (media.kind == "photo") "Photo removed" else "Voice note removed"
            Undo.center.post(JOURNAL_UNDO, what, onExpire = { c.journalMedia.deleteFiles(media) }) { c.journalMedia.restore(media) }
        }
    }

    /** Starts a voice note; false when the microphone could not be opened. */
    fun startRecording(): Boolean {
        val id = newId()
        if (!recorder.start(c.journalFiles.voice(id))) {
            notice("The microphone is busy right now. Try again in a moment.")
            return false
        }
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
        viewModelScope.launch { stopRecordingNow() }
    }

    /** Stops recording and attaches the note; false when nothing was recording. */
    private suspend fun stopRecordingNow(): Boolean {
        recordJob?.cancel()
        val id = recordingId ?: return false
        recordingId = null
        val recording = recorder.stop()
        _state.update { it.copy(recordingMs = null) }
        if (recording == null || deleted) return false
        ensurePersisted()
        c.journalMedia.addVoice(entry.id, id, recording)
        save()
        return true
    }

    /** Plays or pauses the voice note [media]. */
    fun togglePlayback(media: JournalMediaEntity) {
        if (recorder.isRecording) return
        if (player.state.value.id == media.id) {
            player.stop()
            return
        }
        viewModelScope.launch {
            hint(media.id, "Loading…")
            val file = c.driveKit.fetcher.file(media)
            if (file == null) {
                hint(media.id, "Not available offline")
                delay(3000)
                hint(media.id, null)
                return@launch
            }
            hint(media.id, null)
            player.toggle(media.id, file.path)
            playJob?.cancel()
            playJob = launch {
                while (player.state.value.id != null) {
                    player.tick()
                    delay(250)
                }
            }
        }
    }

    private fun hint(id: String, text: String?) =
        _state.update { it.copy(voiceHints = if (text == null) it.voiceHints - id else it.voiceHints + (id to text)) }

    private suspend fun ensurePersisted() {
        if (!persisted && !deleted) {
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
        const val NOTICE_MS = 4000L
    }
}
