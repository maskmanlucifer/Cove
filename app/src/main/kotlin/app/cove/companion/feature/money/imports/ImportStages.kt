package app.cove.companion.feature.money.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cove.companion.core.PermissionStep
import app.cove.companion.data.sms.CaptureMode
import app.cove.companion.data.sms.ImportRange
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.AccentButton
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CheckCircle
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.illustrations.Illustration
import app.cove.companion.design.illustrations.Scene
import app.cove.companion.design.components.GuideBanner
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.graphicsLayerAlpha
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.money.BudgetBar
import app.cove.companion.feature.money.MoneyType
import app.cove.companion.feature.money.RowDivider
import app.cove.companion.feature.money.RowsCard
import app.cove.companion.feature.money.live.PaymentTexts
import app.cove.companion.feature.money.live.PaymentsModePicker
import app.cove.companion.feature.money.live.PaymentsPrivacyNote
import java.text.NumberFormat
import java.util.Locale

private val indian: NumberFormat get() = NumberFormat.getIntegerInstance(Locale("en", "IN"))

/** Plain number with Indian digit grouping, for counts such as "12,345 messages". */
internal fun count(n: Int): String = indian.format(n)

private val BigButton = MoneyType.Row.copy(fontWeight = FontWeight.Medium)

/** Heading, the privacy promise and the one allowed way forward (ask, open Settings) with "Paste a message" always at hand. */
@Composable
internal fun IntroStage(message: String?, step: PermissionStep, deniedBefore: Boolean, onAllow: () -> Unit, onSettings: () -> Unit, onPaste: () -> Unit) {
    val c = Cove.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Illustration(Scene.Messages, Modifier.align(Alignment.CenterHorizontally).height(120.dp))
        CoveText("Find your spending in your messages", style = CoveType.Section)
        CoveText(
            "Cove reads bank and UPI messages on this phone to find your spending. Messages are read here and never leave your phone.",
            style = MoneyType.Quote, color = c.muted,
        )
        RowsCard {
            Fact("Only bank and UPI messages are looked at.")
            RowDivider()
            Fact("Message text is not saved, backed up or sent anywhere. Only payments you choose become expenses.")
            RowDivider()
            Fact("You check everything before it is added, and can undo it.")
        }
        message?.let { CoveText(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MoneyType.Note, color = c.muted) }
        if (step == PermissionStep.OpenSettings) {
            GuideBanner(PaymentTexts.GUIDE_TITLE, PaymentTexts.GUIDE_BODY, "Open settings", onSettings)
        } else {
            PillButton(if (deniedBefore) "Try allowing again" else "Allow reading messages", onAllow, Modifier.fillMaxWidth(), height = 56.dp, textStyle = BigButton)
        }
        AccentButton("Paste a message instead", onPaste, Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun Fact(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 8.dp).size(6.dp).background(Cove.colors.accent, CoveShapes.Circle))
        CoveText(text, Modifier.weight(1f), style = MoneyType.Note)
    }
}

/** Range choice with an estimate of how many messages will be looked at. */
@Composable
internal fun RangeStage(s: ImportState, onRange: (ImportRange) -> Unit, onFind: () -> Unit, onPaste: () -> Unit, payments: @Composable () -> Unit) {
    val c = Cove.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        CoveText("How far back should Cove look?", style = CoveType.Section)
        RowsCard {
            ImportRange.entries.forEachIndexed { i, r ->
                if (i > 0) RowDivider()
                val hint = if (r == ImportRange.SinceLast && !s.hasHistory) "Nothing imported yet: looks back 30 days" else null
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).pressable({ onRange(r) }).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CheckCircle(s.range == r, null, size = 24)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        CoveText(r.label, style = MoneyType.Row)
                        hint?.let { CoveText(it, style = MoneyType.Small, color = c.muted) }
                    }
                }
            }
        }
        CoveText(
            s.estimate?.let { "About ${count(it)} message${if (it == 1) "" else "s"} to look through" } ?: "Counting messages…",
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MoneyType.Note, color = c.muted,
        )
        s.message?.let { CoveText(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MoneyType.Note, color = c.muted) }
        PillButton("Find transactions", onFind, Modifier.fillMaxWidth(), height = 56.dp, textStyle = BigButton)
        AccentButton("Paste a message instead", onPaste, Modifier.align(Alignment.CenterHorizontally))
        payments()
    }
}

/** The one-time offer to turn on "Payments from messages": three choices, one sentence each, and the privacy promise. */
@Composable
internal fun OfferStage(mode: CaptureMode, onMode: (CaptureMode) -> Unit, onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Illustration(Scene.Messages, Modifier.align(Alignment.CenterHorizontally).height(120.dp))
        CoveText("Catch payments as they arrive?", style = CoveType.Section)
        CoveText("Cove can notice a payment the moment its message arrives, so you do not have to come looking.", style = MoneyType.Quote, color = Cove.colors.muted)
        PaymentsModePicker(mode, onMode)
        PaymentsPrivacyNote()
        PillButton("Continue", onContinue, Modifier.fillMaxWidth(), height = 56.dp, textStyle = BigButton)
    }
}

/** "Payments from messages" on the range step: the mode picker, the privacy promise and any permission guide. */
@Composable
internal fun PaymentsSection(mode: CaptureMode, onMode: (CaptureMode) -> Unit, guide: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CoveText("Payments from messages", style = CoveType.Section)
        PaymentsModePicker(mode, onMode)
        guide()
        PaymentsPrivacyNote()
    }
}

/** Progress while the scan runs; Cancel is always available. */
@Composable
internal fun ScanningStage(s: ImportState, onCancel: () -> Unit) {
    val c = Cove.colors
    val p = s.progress
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Illustration(Scene.Messages, Modifier.align(Alignment.CenterHorizontally).height(120.dp))
        CoveText("Looking through your messages", style = CoveType.Section)
        val total = p?.total
        BudgetBar(if (p == null || total == null || total == 0) 0.02f else (p.scanned.toFloat() / total).coerceIn(0.02f, 1f), over = false)
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CoveText(
                if (p == null) "Starting…" else "Checked ${count(p.scanned)}" + (total?.let { " of about ${count(it)}" } ?: ""),
                style = MoneyType.Row,
            )
            CoveText("Found ${p?.found ?: 0} so far", style = MoneyType.Note, color = c.muted)
        }
        CoveText("This stays on your phone. You can leave this page; nothing is added until you say so.", style = MoneyType.Note, color = c.muted)
        PillButton("Cancel", onCancel, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, height = 56.dp, textStyle = BigButton)
    }
}

/** Text box for messages the user pastes; works without the SMS permission. */
@Composable
internal fun PasteStage(message: String?, granted: Boolean, onFind: (String) -> Unit, onBack: () -> Unit) {
    val c = Cove.colors
    var text by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        CoveText("Paste a message", style = CoveType.Section)
        CoveText("Paste one or many bank or UPI messages. They are read on this phone only.", style = MoneyType.Note, color = c.muted)
        Box(Modifier.fillMaxWidth().heightIn(min = 180.dp).background(c.card, CoveShapes.Card).padding(20.dp)) {
            BasicTextField(
                text, { text = it }, Modifier.fillMaxWidth(),
                textStyle = MoneyType.Row.copy(color = c.ink), cursorBrush = SolidColor(c.ink),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) CoveText("Rs.450.00 debited from A/c XX1234 …", style = MoneyType.Row, color = c.placeholder)
                        inner()
                    }
                },
            )
        }
        message?.let { CoveText(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MoneyType.Note, color = c.muted) }
        PillButton("Find transactions", { onFind(text) }, Modifier.fillMaxWidth().graphicsLayerAlpha(if (text.isBlank()) 0.35f else 1f), height = 56.dp, textStyle = BigButton)
        AccentButton(if (granted) "Look through my messages instead" else "Back", onBack, Modifier.align(Alignment.CenterHorizontally))
    }
}

/** Summary after the import; the Undo bar of the Money area shows at the bottom. */
@Composable
internal fun DoneStage(s: ImportState, onDone: () -> Unit) {
    val c = Cove.colors
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Illustration(if (s.added > 0) Scene.Synced else Scene.Cleared, Modifier.align(Alignment.CenterHorizontally).height(120.dp))
        CoveText(s.summaryText.orEmpty(), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = CoveType.Section)
        CoveText(
            if (s.undone) "Your expenses are as they were. You can look again any time."
            else if (s.added > 0) "They are in your expenses now. Your messages stayed on your phone."
            else "Everything stays as it was. You can look again any time.",
            style = MoneyType.Quote, color = c.muted,
        )
        PillButton("Done", onDone, Modifier.fillMaxWidth(), height = 56.dp, textStyle = BigButton)
    }
}
