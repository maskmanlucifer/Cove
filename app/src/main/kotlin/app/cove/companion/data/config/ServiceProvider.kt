package app.cove.companion.data.config

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Holds one generation of a service built from [Credentials] and rebuilds it, lazily, when the part of the
 * credentials it depends on ([key]) changes. The previous generation is passed to [retire] so it can cancel its
 * scope and in-flight work. A blank credential just yields a disabled instance from [build]; nothing throws.
 */
class ServiceProvider<T : Any>(
    private val credentials: StateFlow<Credentials>,
    private val key: (Credentials) -> Any,
    private val build: (Credentials) -> T,
    private val retire: (T) -> Unit = {},
) {
    private var current: T? = null
    private var currentKey: Any? = null

    /** The instance for the current credentials, built on first use and after every relevant change. */
    @Synchronized
    fun get(): T {
        val creds = credentials.value
        val k = key(creds)
        current?.takeIf { k == currentKey }?.let { return it }
        current?.let(retire)
        return build(creds).also {
            current = it
            currentKey = k
        }
    }

    /** Emits the matching instance now and again whenever a relevant credential changes. */
    fun changes(): Flow<T> = credentials.map(key).distinctUntilChanged().map { get() }
}
