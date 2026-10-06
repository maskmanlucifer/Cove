package app.cove.companion.feature.money

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.cove.companion.AppContainer
import app.cove.companion.container
import app.cove.companion.core.Undo
import app.cove.companion.data.categorize.PayeeLogic
import app.cove.companion.data.categorize.RetroChange
import app.cove.companion.design.components.TopUndoBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** "Tag 3 earlier payments to this payee too?": the changes Apply would make and how many payees they cover. */
class RetroOffer(val changes: List<RetroChange>, val payees: Int) {
    val text: String get() = PayeeLogic.offerText(changes.size, payees)
}

/**
 * Holds the one retro-tag offer that outlives the screen that made it (an expense edit closes before Money shows it).
 * It goes away by itself after [WINDOW_MS]; "Not now" drops it, Apply updates the earlier payments and offers Undo.
 */
object RetroTag {
    /** Long enough to read and decide, short enough not to nag. */
    const val WINDOW_MS = 15_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _offer = MutableStateFlow<RetroOffer?>(null)
    val offer: StateFlow<RetroOffer?> = _offer.asStateFlow()
    private var timer: Job? = null

    /** Shows [offer] when it has anything to change. */
    fun post(offer: RetroOffer) {
        if (offer.changes.isEmpty()) return
        timer?.cancel()
        _offer.value = offer
        timer = scope.launch {
            delay(WINDOW_MS)
            if (_offer.value === offer) _offer.value = null
        }
    }

    /** Drops the offer ("Not now"). */
    fun dismiss() {
        timer?.cancel()
        _offer.value = null
    }

    /** Applies the offer (no teaching) and offers Undo through [Undo] in the Money area. */
    fun apply(c: AppContainer) {
        val o = _offer.value ?: return
        dismiss()
        scope.launch {
            c.money.applyRetro(o.changes)
            val n = o.changes.size
            Undo.center.post(MONEY_UNDO, "Tagged $n earlier payment${if (n == 1) "" else "s"}") { c.money.undoRetro(o.changes) }
        }
    }
}

/** The retro-tag prompt as a top bar with Apply and Not now; place it with `Modifier.align(Alignment.TopCenter)`. */
@Composable
fun RetroTagBar(modifier: Modifier = Modifier, belowHeader: Boolean = true) {
    val c = LocalContext.current.container
    val offer by RetroTag.offer.collectAsState()
    val held = remember { mutableStateOf<RetroOffer?>(null) }
    if (offer != null) held.value = offer
    val shown = held.value ?: return
    TopUndoBar(
        shown.text, { RetroTag.apply(c) }, modifier, action = "Apply", belowHeader = belowHeader, visible = offer != null,
        secondaryAction = "Not now", onSecondary = RetroTag::dismiss,
    )
}
