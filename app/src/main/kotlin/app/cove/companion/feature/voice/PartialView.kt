package app.cove.companion.feature.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.cove.companion.core.toLocalDate
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.BalancedText
import app.cove.companion.design.components.coveTopInset

/**
 * Frame 19: only part of it was understood; offer best guesses, or ask to try again. Silence and typed text that
 * matched nothing get their own, accurate wording.
 */
@Composable
fun PartialView(s: VoiceState, nowMillis: Long, vm: VoiceViewModel, onClose: () -> Unit) {
    val today = nowMillis.toLocalDate()
    val guesses = s.guesses
    val heard = s.transcript.isNotBlank()
    val (head, tail) = when {
        !heard -> "I didn’t hear anything." to " Try again or type it."
        guesses.isNotEmpty() && !s.heardByVoice -> "I’m not sure what you meant." to " Was it one of these?"
        guesses.isNotEmpty() -> "I caught part of that." to " Was it one of these?"
        s.heardByVoice -> "I only caught part of that." to " Try again?"
        else -> "I’m not sure what to do with that." to " Try again?"
    }
    Box(Modifier.fillMaxSize()) {
        ResultFrame(
            s,
            headline = { BalancedText(head, tail, CoveType.Title) },
            body = {
                if (guesses.isNotEmpty()) {
                    RowsCard(
                        guesses.map { g ->
                            @Composable {
                                CardRow(describe(g, today), onClick = { vm.chooseGuess(g) }) {
                                    CoveIcon(CoveIcons.ChevronRight, Cove.colors.tail, size = 16.dp)
                                }
                            }
                        },
                    )
                }
            },
            actions = {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Hint(
                        when {
                            !heard -> "Holding the phone closer can help."
                            s.heardByVoice -> "It’s a bit noisy here. Holding the phone closer helps."
                            else -> "Try something like “remind me at 4 pm”."
                        },
                    )
                    ActionPair(if (s.heardByVoice) "Say it again" else "Try again", vm::listen, "Type it", 120, vm::typeInstead)
                }
            },
        )
        CloseButton(onClose, Modifier.align(Alignment.TopEnd).coveTopInset().padding(top = 20.dp, end = 24.dp))
    }
}
