package app.cove.companion.resilience

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Process-wide safety net. Installed first thing in `CoveApp.onCreate`: records a [CrashNote] for every uncaught
 * exception, then lets the previous handler end the process as usual so Android's own reporting still works.
 */
object CrashHandler {
    private lateinit var store: CrashStore
    private var version = ""
    private var now: () -> Long = System::currentTimeMillis

    /** Where notes live: a plain file in `no_backup`, readable with no database, key or network. */
    fun storeFor(context: Context): CrashStore = CrashStore(File(context.noBackupFilesDir, "crash-notes.txt"))

    /** Installs the handler; [versionName] is stored in each note. */
    fun install(context: Context, versionName: String) {
        store = storeFor(context)
        version = versionName
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record("uncaught", error, thread.name, fatal = true)
            previous?.uncaughtException(thread, error)
        }
    }

    /** Records a handled failure of a background component (not counted as a crash). */
    fun report(where: String, error: Throwable) = record(where, error, Thread.currentThread().name, fatal = false)

    /** Handler for coroutine scopes: reports and swallows, so a failing background job cannot kill the process. */
    fun coroutineHandler(where: String) = CoroutineExceptionHandler { _, error -> report(where, error) }

    /** Runs [block], reporting any exception under [where] instead of throwing. Cancellation is rethrown. */
    inline fun <T> guarded(where: String, block: () -> T): T? = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (t: Throwable) {
        report(where, t)
        null
    }

    private fun record(where: String, error: Throwable, thread: String, fatal: Boolean) {
        if (!::store.isInitialized) return
        runCatching { store.add(CrashNote.of(error, where, thread, version, now(), fatal)) }
    }
}
