package app.cove.companion.ai

import app.cove.companion.ai.model.Availability
import app.cove.companion.ai.model.Capability
import app.cove.companion.ai.model.Location
import app.cove.companion.ai.model.ProviderRef

/** One provider's state for one capability. [availability] ignores app state; [active] says it would be used now. */
data class ProviderStatus(val ref: ProviderRef, val availability: Availability, val active: Boolean)

/** Providers of one [capability] in routing order. */
data class CapabilityStatus(val capability: Capability, val providers: List<ProviderStatus>) {
    /** The provider that would answer a request right now, or null when none can. */
    val active: ProviderRef? get() = providers.firstOrNull { it.active }?.ref
}

/** What AI can do on this phone, for settings screens. Contains no content. */
data class AiStatus(val capabilities: List<CapabilityStatus>) {
    fun of(capability: Capability): CapabilityStatus = capabilities.first { it.capability == capability }

    /** Language-model providers (intent parsing stands for all text capabilities) at [location]. */
    private fun states(location: Location) = of(Capability.Intent).providers.filter { it.ref.location == location }

    /** True when a language model on the phone could answer now (app state aside). */
    val onDeviceReady: Boolean get() = states(Location.Native).any { it.availability == Availability.Available }

    /** True when a cloud language model is configured. */
    val cloudReady: Boolean get() = states(Location.Cloud).any { it.availability == Availability.Available }

    /** "available", or the first reason the on-device model cannot run, e.g. "Gemini Nano unavailable on this device". */
    val onDevice: String get() = summarize(states(Location.Native), "available")

    /** "key set", or what the cloud still needs, e.g. "no API key". */
    val cloud: String get() = summarize(states(Location.Cloud), "key set")

    /** "On-device AI: available · Cloud AI: key set". */
    val summary: String get() = "On-device AI: $onDevice · Cloud AI: $cloud"

    private fun summarize(list: List<ProviderStatus>, ok: String): String {
        if (list.any { it.availability == Availability.Available }) return ok
        return when (val a = list.firstOrNull()?.availability) {
            is Availability.Unavailable -> a.reason
            is Availability.NeedsConfig -> "no ${a.what}"
            Availability.NeedsForeground -> "only while Cove is open"
            else -> "not available"
        }
    }
}
