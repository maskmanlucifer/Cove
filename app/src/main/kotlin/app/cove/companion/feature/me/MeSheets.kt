package app.cove.companion.feature.me

import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.TextScales
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.PillButton
import app.cove.companion.data.local.entity.SettingsEntity
import app.cove.companion.data.media.PhotoQuality
import app.cove.companion.feature.onboarding.WakeWheel
import app.cove.companion.design.components.Segmented
import app.cove.companion.data.sms.CaptureMode
import app.cove.companion.feature.money.live.PaymentsModePicker
import app.cove.companion.feature.money.live.PaymentsPermissionGuide
import app.cove.companion.feature.money.live.PaymentsPrivacyNote
import app.cove.companion.feature.money.live.rememberPaymentsAccess
import app.cove.companion.feature.plan.PlanSheet
import app.cove.companion.feature.security.LockAfterSheet
import app.cove.companion.feature.security.LockUnavailableSheet
import app.cove.companion.feature.plan.TitleField

/** Which Me sheet is open. */
enum class MeSheet { Name, Wake, Brief, OneThing, Spoken, Nudges, Motion, Look, Privacy, PhotoQuality, Backup, Restore, LockAfter, LockUnavailable, VoiceCheck, ForgetMessages, ClearData, PaymentsFromMessages }

/** Hosts whichever sheet [sheet] names, reading and writing through [vm]. */
@Composable
fun MeSheets(sheet: MeSheet?, s: SettingsEntity, vm: MeViewModel, briefPlay: () -> Unit = {}, openConnect: () -> Unit = {}, onOpen: (MeSheet) -> Unit = {}, onDismiss: () -> Unit) {
    when (sheet) {
        null -> Unit
        MeSheet.Name -> NameSheet(s.displayName, { n -> vm.update { it.copy(displayName = n.trim()) } }, onDismiss)
        MeSheet.Wake -> WakeSheet(s.wakeMinutes, vm::setWake, onDismiss)
        MeSheet.Brief -> PlanSheet(onDismiss, gap = 16) { close ->
            SheetHeading("Morning brief")
            OnOffSegment(s.briefOn) { on -> vm.update { it.copy(briefOn = on) } }
            SheetCaption("A short spoken summary of your day, ready when you wake.")
            PillButton("Play today’s brief", { close(); briefPlay() }, Modifier.fillMaxWidth(), height = 52.dp)
        }
        MeSheet.OneThing -> PlanSheet(onDismiss, gap = 16) {
            SheetHeading("One-thing mode")
            OnOffSegment(s.oneThingMode) { on -> vm.update { it.copy(oneThingMode = on, oneThingUntil = 0) } }
            SheetCaption("Today shows only the next thing to do, so you can focus. Turn it off here any time.")
        }
        MeSheet.VoiceCheck -> app.cove.companion.feature.voice.VoiceCheckSheet(onDismiss)
        MeSheet.Spoken -> PlanSheet(onDismiss, gap = 16) {
            SheetHeading("Spoken replies")
            OnOffSegment(s.spokenReplies) { on -> vm.update { it.copy(spokenReplies = on) } }
            SheetCaption("Cove answers out loud after you speak to it.")
        }
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
        MeSheet.Privacy -> PrivacySheet(onDismiss) { onDismiss(); onOpen(MeSheet.ClearData) }
        MeSheet.PaymentsFromMessages -> PaymentsSheet(vm, onDismiss)
        MeSheet.ForgetMessages -> PlanSheet(onDismiss, gap = 16) { close ->
            val context = LocalContext.current
            SheetHeading("Forget imported-message history")
            SheetCaption("Cove will no longer remember which messages it has already looked at. Your expenses stay exactly as they are. Next time, Cove checks for repeats against your expenses.")
            PillButton("Forget history", { vm.forgetImportedMessages(); Toast.makeText(context, "Message history forgotten", Toast.LENGTH_SHORT).show(); close() }, Modifier.fillMaxWidth(), height = 52.dp)
            SheetCaption("Cove also remembers how you tagged payees, so the next payment to the same shop or QR is tagged like last time. You can forget that on its own: your expenses and their tags stay as they are.")
            PillButton("Forget learned payees", { vm.forgetPayees(); Toast.makeText(context, "Learned payees forgotten", Toast.LENGTH_SHORT).show(); close() }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, height = 52.dp)
            PillButton("Cancel", close, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, height = 52.dp)
        }
        MeSheet.ClearData -> app.cove.companion.feature.datacontrols.ClearDataSheet(onDismiss)
        MeSheet.LockAfter -> LockAfterSheet(s.lockAfter, { v -> vm.update { it.copy(lockAfter = v.key) } }, onDismiss)
        MeSheet.LockUnavailable -> LockUnavailableSheet(onDismiss)
        MeSheet.PhotoQuality -> PlanSheet(onDismiss, gap = 16) {
            SheetHeading("Photo quality")
            Segmented(
                PhotoQuality.all.map(PhotoQuality::label),
                PhotoQuality.all.indexOf(s.photoQuality).coerceAtLeast(0),
                { i -> vm.update { it.copy(photoQuality = PhotoQuality.all[i]) } },
                Modifier.fillMaxWidth(), fillWidth = true,
            )
            SheetCaption(photoQualityHelp(s.photoQuality))
        }
        MeSheet.Backup, MeSheet.Restore -> BackupSheet(sheet == MeSheet.Restore, vm, onDismiss, openConnect)
    }
}

/** "Payments from messages": the mode, what it needs, and the privacy promise. */
@Composable
private fun PaymentsSheet(vm: MeViewModel, onDismiss: () -> Unit) {
    val mode by vm.captureMode.collectAsState()
    val access = rememberPaymentsAccess()
    PlanSheet(onDismiss, gap = 16) {
        SheetHeading("Payments from messages")
        SheetCaption("Cove can notice a payment the moment its message arrives. It needs “Receive text messages” and “Read text messages”.")
        PaymentsModePicker(mode, { m -> vm.setCaptureMode(m); if (m != CaptureMode.Off && !access.granted) access.askPermissions() })
        PaymentsPermissionGuide(mode, access)
        PaymentsPrivacyNote()
    }
}

@Composable
private fun OnOffSegment(on: Boolean, onChange: (Boolean) -> Unit) =
    Segmented(listOf("On", "Off"), if (on) 0 else 1, { onChange(it == 0) }, Modifier.fillMaxWidth(), fillWidth = true)

/** Name editor: one line, at most [NAME_MAX] characters, saved only with Save. */
@Composable
private fun NameSheet(name: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(name) }
    var saved by remember { mutableStateOf(false) }
    PlanSheet(onDismiss, gap = 16) { close ->
        val save = {
            if (!saved) {
                saved = true
                onSave(normalizeName(text))
                close()
            }
        }
        SheetHeading("Your name")
        TitleField(text, { text = capName(it) }, "Your name", onDone = save, autofocus = true)
        if (text.codePointCount(0, text.length) >= NAME_MAX - 10) SheetCaption("${text.codePointCount(0, text.length)} of $NAME_MAX")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Cancel", close, Modifier.weight(1f), kind = ButtonKind.Secondary, height = 52.dp)
            PillButton("Save", save, Modifier.weight(1f), height = 52.dp)
        }
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

/** One group of the Privacy sheet: a heading and titled paragraphs. */
private class PrivacyGroup(val heading: String, val blocks: List<Pair<String, String>>)

private val privacyGroups = listOf(
    PrivacyGroup(
        "What stays on this phone",
        listOf(
            "Your entries" to "Alarms, to-dos, habits, money and journal are saved on this phone first, in an encrypted database. Cove works with no signal.",
            "Your messages" to "Money can read bank and UPI messages on this phone to find your spending. The text is read here only: never sent, synced or backed up. Only the payments you choose become expenses.",
            "Your journal" to "Journal text, photos and voice notes are never sent to an AI service. Mood and pattern summaries are made on this phone.",
        ),
    ),
    PrivacyGroup(
        "What can leave, and when",
        listOf(
            "Sync and backup (only if you set them up)" to "Changes are copied to your own Supabase database, and photos, voice notes and backups go to a Cove folder in your own Google Drive.",
            "Speech" to "Your phone turns speech into text. On phones without an offline speech model, Android may send the audio to Google to do this.",
            "Gemini (only if you add a key)" to "Short non-journal commands (up to 160 characters), the facts for your morning brief, and expense notes with amounts removed (when you ask Review to sort them) go from this phone straight to Google with your own key. Without a key, your Supabase project may relay the same text.",
        ),
    ),
    PrivacyGroup(
        "Clearing your data",
        listOf(
            "Clear all data" to "Erases everything Cove stored on this phone, including your saved connections and sign-in, and makes Cove behave like a fresh install. Copies in your own Supabase and Google Drive stay unless you also choose to delete them there. Updates and reinstalls over the same app keep your connections.",
        ),
    ),
)

@Composable
private fun PrivacySheet(onDismiss: () -> Unit, onClear: () -> Unit) {
    PlanSheet(onDismiss, gap = 16) {
        SheetHeading("Privacy and data")
        Column(
            Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            privacyGroups.forEach { group ->
                CoveText(group.heading, style = CoveType.Heading)
                group.blocks.forEach { (title, body) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CoveText(title, style = CoveType.BodyMedium)
                        CoveText(body, style = CoveType.Meta.copy(lineHeight = androidx.compose.ui.unit.TextUnit(21f, androidx.compose.ui.unit.TextUnitType.Sp)), color = Cove.colors.muted)
                    }
                }
            }
            PillButton("Clear all data…", onClear, Modifier.fillMaxWidth(), kind = app.cove.companion.design.components.ButtonKind.Secondary, height = 52.dp)
        }
    }
}
