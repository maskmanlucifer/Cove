package app.cove.companion.ai.model

/** Where a provider runs; decides what data it may see (see `AiPolicy`). */
enum class Location {
    /** The phone's own AI: ML Kit Prompt API (Gemini Nano), ML Kit GenAI speech, the Android recognizer. */
    Native,

    /** A remote service reached over the network with the user's own credentials. */
    Cloud,

    /** Deterministic code on the phone: regex parsers and typed input. */
    Rules,
    ;

    /** True when nothing leaves the phone. */
    val isLocal: Boolean get() = this != Cloud
}

/** What a request asks for; one provider list per capability in `AiRouter`. */
enum class Capability(
    /** Nano and on-device models only run while the app is the top foreground app (PLAN 6a). */
    val needsForeground: Boolean,
) {
    Intent(true), Brief(true), Speech(false), Caption(true), Summary(true), Embedding(true), Category(true), Advice(true)
}

/** How private the payload is. [Journal] content is processed on the phone only, always. */
enum class Sensitivity { Everyday, Journal }

/** Which provider produced a result; safe to log (never contains content). */
data class ProviderRef(val id: String, val location: Location) {
    override fun toString() = "$id/${location.name.lowercase()}"
}

/** Whether a provider could serve a request right now, ignoring who is asking ([Sensitivity]) and app state. */
sealed interface Availability {
    data object Available : Availability

    /** The provider cannot work here: no model, unsupported device, service missing. */
    data class Unavailable(val reason: String) : Availability

    /** Works only while the app is on screen. */
    data object NeedsForeground : Availability

    /** Needs [what] set up first, such as an API key. */
    data class NeedsConfig(val what: String) : Availability
}

/** Why a provider or the whole chain did not produce a result. */
sealed interface AiError {
    /** Short plain-language reason, safe to show or log. */
    val message: String

    data class Unavailable(val reason: String) : AiError { override val message get() = reason }
    data object NeedsForeground : AiError { override val message get() = "Only runs while Cove is open" }
    data class NeedsConfig(val what: String) : AiError { override val message get() = "Needs $what" }

    /** The model is busy (ML Kit BUSY); worth exactly one retry after a short pause. */
    data object Busy : AiError { override val message get() = "The model is busy" }

    /** The per-app quota is used up (battery quota, HTTP 429); retrying now cannot help. */
    data object RateLimited : AiError { override val message get() = "Rate limit reached" }
    data object Timeout : AiError { override val message get() = "Took too long" }
    data object InvalidOutput : AiError { override val message get() = "The answer could not be read" }
    data object Offline : AiError { override val message get() = "No internet connection" }

    /** Policy refused: journal content is never sent to the cloud, or the payload is not allowed off the phone. */
    data object PrivacyBlocked : AiError { override val message get() = "Blocked to keep this on the phone" }
}

/** A typed answer with provenance, or the reason there is none. */
sealed interface AiResult<out T> {
    /** [value] came from [source]. */
    data class Ok<T>(val value: T, val source: ProviderRef) : AiResult<T>

    /** Nothing answered. [tried] are the providers that were actually called, in order. */
    data class Failed(val error: AiError, val tried: List<ProviderRef> = emptyList()) : AiResult<Nothing>

    /** The value, or null on failure. */
    fun valueOrNull(): T? = (this as? Ok<T>)?.value
}
