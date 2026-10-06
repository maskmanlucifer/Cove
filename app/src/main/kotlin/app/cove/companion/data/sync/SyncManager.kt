package app.cove.companion.data.sync

import android.content.Context
import android.os.Build
import app.cove.companion.core.Clock
import app.cove.companion.data.auth.AuthRepository
import app.cove.companion.data.auth.AuthState
import app.cove.companion.data.local.CoveDatabase
import app.cove.companion.data.local.entity.SyncConflictEntity
import io.ktor.client.HttpClient
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * App-level owner of sync: builds the [SyncEngine], schedules work while signed in, and triggers a debounced
 * expedited sync after local writes and when the app comes to the foreground.
 */
@OptIn(FlowPreview::class)
class SyncManager(
    private val context: Context,
    private val db: CoveDatabase,
    val auth: AuthRepository,
    baseUrl: String,
    anonKey: String,
    client: HttpClient,
    clock: Clock,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("cove_sync", Context.MODE_PRIVATE)
    private val engine = SyncEngine(
        store = RoomSyncStore(db),
        remote = SupabaseRemote(baseUrl, anonKey, auth, client),
        deviceId = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).apply() },
        deviceName = Build.MODEL ?: "Phone",
        now = clock::now,
    )

    /** True when a backend is configured. */
    val enabled: Boolean get() = auth.enabled
    val status: StateFlow<SyncStatus> = engine.status
    val authState: StateFlow<AuthState> = auth.state
    val conflicts: Flow<List<SyncConflictEntity>> = db.sync().observeConflicts()

    /**
     * Begins reacting to sign-in, local writes and (via [onForeground]) app opens. Call once. When no backend is
     * configured it instead cancels any work an earlier configuration scheduled.
     */
    fun start() {
        if (!enabled) {
            SyncScheduler.cancelAll(context)
            return
        }
        engine.restore(prefs.getLong("lastSyncAt", 0))
        scope.launch {
            auth.state.collectLatest { state ->
                if (state is AuthState.SignedIn) {
                    SyncScheduler.schedulePeriodic(context)
                    SyncScheduler.requestNow(context)
                } else {
                    SyncScheduler.cancelAll(context)
                }
            }
        }
        scope.launch {
            db.sync().observePendingCount().debounce(DEBOUNCE_MS).filter { it > 0 && auth.state.value is AuthState.SignedIn }
                .collect { SyncScheduler.requestNow(context) }
        }
        scope.launch {
            engine.status.collect { if (it is SyncStatus.UpToDate) prefs.edit().putLong("lastSyncAt", it.at).apply() }
        }
    }

    /** Called when the app returns to the foreground. */
    fun onForeground() {
        if (enabled && auth.state.value is AuthState.SignedIn) SyncScheduler.requestNow(context)
    }

    /**
     * Runs a sync now; true when nothing needs retrying. The run belongs to [scope], so replacing the credentials
     * (which cancels the scope) stops it mid-way; a stopped run reports true because the new setup starts afresh.
     */
    suspend fun syncNow(): Boolean {
        if (auth.state.value !is AuthState.SignedIn || !scope.isActive) return true
        val run = scope.async { engine.sync() }
        return try {
            run.await()
        } catch (e: SyncAuthException) {
            false
        } catch (e: CancellationException) {
            if (currentCoroutineContext().isActive) true else {
                run.cancel()
                throw e
            }
        }
    }

    /** Queues a sync and returns immediately. */
    fun requestSync() {
        if (enabled && auth.state.value is AuthState.SignedIn) SyncScheduler.requestNow(context)
    }

    private companion object {
        const val DEBOUNCE_MS = 2_000L
    }
}
