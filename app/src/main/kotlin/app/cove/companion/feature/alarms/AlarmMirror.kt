package app.cove.companion.feature.alarms

import android.content.Context
import app.cove.companion.data.local.entity.AlarmEntity
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The minimum needed to ring an alarm without the database: time, days, label, kind, sound, snooze.
 * Nothing else about the user is stored here.
 */
@Serializable
data class MirrorAlarm(
    val id: String,
    val label: String,
    val minutes: Int,
    val daysMask: Int,
    val kind: String,
    val sound: String,
    val gentleRise: Boolean,
    val snoozeMinutes: Int,
    val enabled: Boolean,
) {
    /** An [AlarmEntity] carrying the same ring-relevant fields. */
    fun toEntity() = AlarmEntity(id, label, minutes, daysMask, kind, sound, gentleRise, snoozeMinutes, enabled)

    companion object {
        /** Mirror of [alarm]. */
        fun of(alarm: AlarmEntity) = MirrorAlarm(
            alarm.id, alarm.label, alarm.minutes, alarm.daysMask, alarm.kind, alarm.sound,
            alarm.gentleRise, alarm.snoozeMinutes, alarm.enabled,
        )
    }
}

/**
 * Plain-file copy of the enabled alarms, kept in sync by [AlarmScheduler]. When the database cannot be opened
 * (corrupt file, lost key) the receiver still rings from this. The file is unencrypted on purpose: it must be
 * readable with no key; it lives in app-private `no_backup` storage.
 */
class AlarmMirror(private val file: File) {
    /** Replaces the mirror with [alarms] (enabled, not deleted); written atomically. Never throws. */
    @Synchronized
    fun write(alarms: List<AlarmEntity>) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(encode(alarms.filter { it.enabled && it.deletedAt == null }.map(MirrorAlarm::of)))
            check(tmp.renameTo(file)) { "mirror rename failed" }
        }
    }

    /** Every mirrored alarm; empty when the file is missing or unreadable. */
    @Synchronized
    fun read(): List<MirrorAlarm> = runCatching { if (file.isFile) decode(file.readText()) else emptyList() }.getOrDefault(emptyList())

    /** The mirrored alarm [id], if any. */
    fun find(id: String): MirrorAlarm? = read().firstOrNull { it.id == id }

    /** Ids of all mirrored alarms. */
    fun ids(): List<String> = read().map { it.id }

    /** Drops [id] from the mirror, for a one-time alarm that fired while the database was unreadable. */
    @Synchronized
    fun remove(id: String) {
        runCatching { file.writeText(encode(read().filter { it.id != id })) }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Serialises [alarms]. */
        fun encode(alarms: List<MirrorAlarm>): String = json.encodeToString(alarms)

        /** Reads what [encode] wrote; unknown fields are ignored so newer files stay readable. */
        fun decode(text: String): List<MirrorAlarm> = json.decodeFromString(text)

        /** The mirror file of this app. */
        fun forContext(context: Context) = AlarmMirror(File(context.noBackupFilesDir, "alarm-mirror.json"))
    }
}
