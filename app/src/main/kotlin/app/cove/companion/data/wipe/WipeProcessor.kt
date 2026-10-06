package app.cove.companion.data.wipe

import java.io.File

/** Where a wipe works; all plain folders so the processor runs (and is tested) without Android. */
data class WipeDirs(
    val databases: File,
    val files: File,
    val noBackup: File,
    val cache: File,
    val prefs: File,
    /** Outside every wiped folder: holds the `pending_wipe` marker and the notice. */
    val root: File,
) {
    /** The marker that says a wipe was requested and may be unfinished. */
    val marker: File get() = File(root, "pending_wipe.json")

    /** Message shown once on the next launch. */
    val notice: File get() = File(root, "wipe_notice.txt")
}

/** Removes every Android Keystore key Cove created. */
fun interface KeyEraser {
    /** Deletes all keys; throws if the Keystore cannot be reached. */
    fun eraseAll()
}

/** What [WipeProcessor.processIfPending] did. */
sealed interface WipeOutcome {
    data object NothingPending : WipeOutcome
    data object Done : WipeOutcome

    /** Some files could not be removed; the marker stays so the next start tries again. */
    data class Partial(val failed: List<String>) : WipeOutcome
}

/**
 * Performs the on-disk part of "Clear all data" at the start of a process, before anything opens the database.
 * Every step is idempotent and the marker is removed last, so a kill at any point is repaired by the next start.
 */
class WipeProcessor(
    private val dirs: WipeDirs,
    private val keys: KeyEraser,
    private val plan: WipePlan = WipePlan(),
    private val retries: Int = 3,
    private val pause: (Long) -> Unit = Thread::sleep,
) {
    /** True when a wipe was requested and is not finished. */
    fun isPending(): Boolean = dirs.marker.isFile

    /** Runs the wipe when the marker exists. Never throws. */
    fun processIfPending(): WipeOutcome {
        if (!isPending()) return WipeOutcome.NothingPending
        val message = PendingWipe.read(dirs.marker)?.notice ?: PendingWipe.DEFAULT_NOTICE
        val failed = mutableListOf<String>()
        fun step(area: WipeArea, block: () -> Boolean) {
            if (!block()) failed += area.label
        }
        step(WipeArea.Database) { dirs.databases.listFiles().orEmpty().filter { plan.isDatabaseFile(it.name) }.all(::delete) }
        step(WipeArea.Files) { emptied(dirs.files) }
        step(WipeArea.NoBackup) { dirs.noBackup.listFiles().orEmpty().filter { plan.removesNoBackup(it.name) }.all(::delete) }
        step(WipeArea.Cache) { emptied(dirs.cache) }
        step(WipeArea.Preferences) { dirs.prefs.listFiles().orEmpty().filter { plan.removesPrefs(it.name) }.all(::delete) }
        step(WipeArea.Keystore) { runCatching { keys.eraseAll() }.isSuccess }
        val partial = failed.isNotEmpty()
        runCatching {
            dirs.notice.writeText(
                if (partial) "$message\nSome files could not be removed (${failed.joinToString()}). Cove will try again next time it opens."
                else message,
            )
        }
        if (partial) return WipeOutcome.Partial(failed)
        dirs.marker.delete()
        return WipeOutcome.Done
    }

    private fun emptied(dir: File): Boolean = dir.listFiles().orEmpty().all(::delete)

    /** Deletes [file] (recursively), retrying briefly for files that are momentarily locked; a missing file counts as deleted. */
    private fun delete(file: File): Boolean {
        repeat(retries) { attempt ->
            if (!file.exists() || file.deleteRecursively()) return true
            if (attempt < retries - 1) pause(150)
        }
        return !file.exists()
    }
}
