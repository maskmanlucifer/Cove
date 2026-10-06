package app.cove.companion.resilience

import androidx.work.ListenableWorker.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Runs a worker body so that no exception escapes: it is noted under [where] and the work is retried until
 * [runAttemptCount] reaches [maxRetries], then ends with [Result.failure] (periodic work still runs next time).
 */
suspend fun guardedWork(where: String, runAttemptCount: Int, maxRetries: Int = 3, block: suspend () -> Result): Result = try {
    block()
} catch (t: Throwable) {
    if (t is CancellationException && t !is TimeoutCancellationException) throw t
    CrashHandler.report(where, t)
    if (runAttemptCount < maxRetries) Result.retry() else Result.failure()
}
