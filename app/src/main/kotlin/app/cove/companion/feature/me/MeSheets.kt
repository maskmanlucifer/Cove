package app.cove.companion.feature.me

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.TextScales
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.PillButton
import app.cove.companion.data.local.entity.SettingsEntity
import app.cove.companion.feature.onboarding.WakeWheel
import app.cove.companion.design.components.Segmented
import app.cove.companion.feature.plan.PlanSheet
import app.cove.companion.feature.plan.TitleField

/** Which Me sheet is open. */
enum class MeSheet { Name, Wake, Nudges, Motion, Look, Privacy }

/** Hosts whichever sheet [sheet] names, reading and writing through [vm]. */
@Composable
fun MeSheets(sheet: MeSheet?, s: SettingsEntity, vm: MeViewModel, onDismiss: () -> Unit) {
    when (sheet) {
        null -> Unit
        MeSheet.Name -> NameSheet(s.displayName, { n -> vm.update { it.copy(displayName = n.trim()) } }, onDismiss)
        MeSheet.Wake -> WakeSheet(s.wakeMinutes, vm::setWake, onDismiss)
        MeSheet.Nudges -> PlanSheet(onDismiss, gap = 16) {
            SheetHeading("Nudges")
            Segmented(
                listOf("As they come", "Bundled", "Brief only"),
                NudgeModes.indexOf(s.nudgeMode).coerceAtLeast(0),
                { i -> vm.update { it.copy(nudgeMode = NudgeModes[i]) } },
                Modifier.fillMaxWidth(), fillWidth = true,
            )
            SheetCaption(nudgeHelp(s.nudgeMode))
        }
        MeSheet.Motion -> PlanSheet(onDismiss, gap = 16) {
            SheetHeading("Reduce motion")
            Segmented(
                listOf("System", "On", "Off"),
                MotionModes.indexOf(s.reduceMotion).coerceAtLeast(0),
                { i -> vm.update { it.copy(reduceMotion = MotionModes[i]) } },
                Modifier.fillMaxWidth(), fillWidth = true,
            )
            SheetCaption("When on, screens fade for a moment instead of sliding. System follows your phone’s animation setting.")
        }
        MeSheet.Look -> PlanSheet(onDismiss, gap = 16) {
            SheetHeading("Look and text size")
            SheetCaption("Appearance")
            Segmented(
                listOf("System", "Light", "Dark"),
                ThemeModes.indexOf(s.theme).coerceAtLeast(0),
                { i -> vm.update { it.copy(theme = ThemeModes[i]) } },
                Modifier.fillMaxWidth(), fillWidth = true,
            )
            SheetCaption("Text size")
            Segmented(
                TextScales.options.map { it.first },
                TextScales.indexOf(s.textScale),
                { i -> vm.update { it.copy(textScale = TextScales.options[i].second) } },
                Modifier.fillMaxWidth(), fillWidth = true,
            )
        }
        MeSheet.Privacy -> PrivacySheet(onDismiss)
    }
}

@Composable
private fun NameSheet(name: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(name) }
    val latest = rememberUpdatedState(text)
    DisposableEffect(Unit) { onDispose { onSave(latest.value) } }
    PlanSheet(onDismiss, gap = 16) { close ->
        SheetCaption("Your name")
        TitleField(text, { text = it }, "Your name", onDone = close, autofocus = true)
        PillButton("Done", close, Modifier.fillMaxWidth(), height = 52.dp)
    }
}

@Composable
private fun WakeSheet(minutes: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf(minutes) }
    PlanSheet(onDismiss, gap = 16) { close ->
        SheetHeading("Wake-up time")
        WakeWheel(picked, { picked = it }, Modifier.fillMaxWidth().height(300.dp))
        SheetCaption("Updates your “Wake up” alarm too.")
        PillButton("Save", { onSave(picked); close() }, Modifier.fillMaxWidth(), height = 52.dp)
    }
}

private val privacyBlocks = listOf(
    "On this phone" to "Alarms, to-dos, habits, money and journal entries are saved on this phone first, so Cove works with no signal.",
    "Your own space" to "When you sign in, changes are copied to your own database (Supabase) and photos and voice notes go to your own Google Drive in a folder called Cove. Only you can read them.",
    "Your voice" to "Speech is turned into text on this phone whenever it can. If it can’t, a short voice command (never a journal note) may be sent to be transcribed by Google’s Gemini through your own database. Journal voice notes always stay on this phone.",
    "Journal insights" to "Mood and pattern summaries of your journal are made on this phone only. Nothing is sent anywhere to produce them.",
)

@Composable
private fun PrivacySheet(onDismiss: () -> Unit) {
    PlanSheet(onDismiss, gap = 16) {
        SheetHeading("Privacy and data")
        Column(
            Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            privacyBlocks.forEach { (title, body) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CoveText(title, style = CoveType.BodyMedium)
                    CoveText(body, style = CoveType.Meta.copy(lineHeight = androidx.compose.ui.unit.TextUnit(21f, androidx.compose.ui.unit.TextUnitType.Sp)), color = Cove.colors.muted)
                }
            }
        }
    }
}
