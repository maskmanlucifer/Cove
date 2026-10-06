package app.cove.companion.feature.brief

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.BuildConfig
import app.cove.companion.core.toLocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Loads today's brief (generating it on demand) into the app-scoped [BriefPlayer] and starts reading. */
class BriefViewModel(private val c: AppContainer) : ViewModel() {
    val player: BriefPlayer = c.briefPlayer

    init {
        viewModelScope.launch {
            val brief = c.assistant.brief(c.clock.now().toLocalDate()).first() ?: c.briefGenerator.generate()
            player.load(brief)
            val frozen = BriefDebug.frozen
            if (BuildConfig.DEBUG && frozen != null) player.freeze(1, frozen.first, frozen.second) else player.play()
        }
    }

    /** Stops speaking when the screen is left. */
    override fun onCleared() = player.stop()
}

/** Debug-only: `--es briefAt 51/124` shows the player mid-brief (elapsed/total seconds) without speaking. */
object BriefDebug {
    @Volatile
    var frozen: Pair<Int, Int>? = null
}
