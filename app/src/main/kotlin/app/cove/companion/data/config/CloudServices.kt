package app.cove.companion.data.config

import app.cove.companion.data.auth.AuthRepository
import app.cove.companion.data.auth.GoogleSignIn
import app.cove.companion.data.drive.DriveKit
import app.cove.companion.data.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

/**
 * One generation of everything that depends on the Supabase and Google credentials. Built by the
 * [ServiceProvider] in `AppContainer`; when those credentials change it is [close]d and a fresh one replaces it.
 * All background work of this generation runs in [scope], so closing it stops that work.
 */
class CloudServices(
    val auth: AuthRepository,
    val sync: SyncManager,
    val drive: DriveKit,
    val signIn: GoogleSignIn,
    private val scope: CoroutineScope,
) {
    private var started = false

    /** Starts the schedules and collectors of sync and Drive once; safe to call again. */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        sync.start()
        drive.start()
    }

    /** Cancels this generation's background work. */
    fun close() = scope.cancel()
}
