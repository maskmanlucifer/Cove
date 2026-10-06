package app.cove.companion.feature.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cove.companion.ai.speech.SpeechErrors
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.BalancedText
import app.cove.companion.design.components.coveTopInset

/**
 * Listening did not work: says why in plain words and offers the right next step ([speechGuidance]). The error code
 * is shown small so a screenshot is enough for a bug report.
 */
@Composable
fun TroubleView(s: VoiceState, guidance: Guidance, onAction: (FixAction) -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        ResultFrame(
            s,
            headline = { BalancedText(guidance.head, guidance.tail, CoveType.Title) },
            body = {},
            actions = {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Hint(guidance.hint)
                    if (s.troubleCode != 0) Hint("Code ${s.troubleCode}: ${SpeechErrors.describe(s.troubleCode)}")
                    ActionPair(guidance.primary.label, { onAction(guidance.primary) }, guidance.secondary.label, 148) { onAction(guidance.secondary) }
                }
            },
        )
        CloseButton(onClose, Modifier.align(Alignment.TopEnd).coveTopInset().padding(top = 20.dp, end = 24.dp))
    }
}
