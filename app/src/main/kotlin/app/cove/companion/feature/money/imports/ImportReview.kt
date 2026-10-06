package app.cove.companion.feature.money.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cove.companion.container
import app.cove.companion.core.rupees
import app.cove.companion.core.rupeesSpoken
import app.cove.companion.core.toLocalDate
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.data.sms.ImportRange
import app.cove.companion.data.sms.PayeeKey
import app.cove.companion.design.components.AccentButton
import app.cove.companion.design.components.CheckCircle
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.cardRow
import app.cove.companion.design.components.graphicsLayerAlpha
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.money.MoneyMath
import app.cove.companion.feature.money.MoneyType
import app.cove.companion.feature.money.RowDivider

/** The review list: New (checked) and Possible duplicates (unchecked), with the one-shot "Import N" button. */
@Composable
internal fun ReviewStage(s: ImportState, vm: ImportViewModel, importing: Boolean) {
    val c = Cove.colors
    val today = LocalContext.current.container.clock.now().toLocalDate()
    val fresh = s.newRows
    val dups = s.dupRows
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp),
        ) {
            item(key = "head") {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CoveText(
                        if (s.rows.isEmpty()) "No new transactions found." else "${fresh.size} new" + if (dups.isNotEmpty()) " · ${dups.size} possible duplicate${if (dups.size == 1) "" else "s"}" else "",
                        style = CoveType.Section,
                    )
                    if (s.rows.isEmpty()) EmptyHint(s, vm) else {
                        val anyOff = fresh.any { !it.checked }
                        Box(Modifier.heightIn(min = 44.dp).pressable(vm::selectAllOrNone), contentAlignment = Alignment.CenterStart) {
                            CoveText(if (anyOff) "Select all new" else "Select none", style = MoneyType.Sub.copy(fontWeight = FontWeight.Medium), color = c.accent)
                        }
                    }
                    s.message?.let { CoveText(it, style = MoneyType.Note, color = c.alert) }
                }
            }
            section("New", fresh, today, s.categories, vm)
            section("Possible duplicates", dups, today, s.categories, vm, note = "Unchecked, because Cove already has something that looks the same.")
        }
        if (s.rows.isNotEmpty()) {
            val n = s.checkedCount
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.canvas.copy(alpha = 0.94f)).padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 28.dp)) {
                PillButton(
                    if (importing) "Importing…" else if (n == 0) "Nothing selected" else "Import $n",
                    { if (n > 0 && !importing) vm.import() },
                    Modifier.fillMaxWidth().graphicsLayerAlpha(if (n == 0 || importing) 0.35f else 1f),
                    height = 56.dp, textStyle = MoneyType.Row.copy(fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyHint(s: ImportState, vm: ImportViewModel) {
    val c = Cove.colors
    val where = if (s.fromPaste) "in what you pasted" else when (s.range) {
        ImportRange.SinceLast -> "since your last import"
        ImportRange.Last30 -> "from the last 30 days"
        ImportRange.ThisMonth -> "from this month"
        ImportRange.All -> "in your inbox"
    }
    CoveText("Cove looked at ${count(s.scanned)} message${if (s.scanned == 1) "" else "s"} $where.", style = MoneyType.Note, color = c.muted)
    CoveText(
        if (s.fromPaste) "Try pasting the whole bank message, including the amount and the word debited or credited."
        else "Nothing new to add. Try a longer range, or paste a message if one is missing.",
        style = MoneyType.Note, color = c.muted,
    )
    if (s.duplicatesDropped > 0) CoveText("${s.duplicatesDropped} repeated or already handled.", style = MoneyType.Note, color = c.muted)
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!s.fromPaste) AccentButton("Change range", { vm.backToStart(true) })
        AccentButton("Paste a message", vm::openPaste)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String, rows: List<ImportRow>, today: java.time.LocalDate, cats: List<ExpenseCategoryEntity>, vm: ImportViewModel, note: String? = null,
) {
    if (rows.isEmpty()) return
    item(key = "h-$title") {
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CoveText("$title · ${rows.size}", style = CoveType.MetaMedium, color = Cove.colors.muted)
            note?.let { CoveText(it, style = MoneyType.Small, color = Cove.colors.muted) }
        }
    }
    items(rows.size, key = { rows[it].id }) { i ->
        val row = rows[i]
        Column(Modifier.cardRow(Cove.colors.card, i, rows.size, 24.dp, horizontal = 20.dp)) {
            if (i > 0) RowDivider()
            ImportRowItem(row, today, cats, vm)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportRowItem(row: ImportRow, today: java.time.LocalDate, cats: List<ExpenseCategoryEntity>, vm: ImportViewModel) {
    val c = Cove.colors
    val tx = row.item.candidate.tx
    var picking by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val stacked = LocalDensity.current.fontScale > 1.3f
    val note = row.note
    val handle = if (tx.merchant == null) PayeeKey.handleOf(tx.payeeKey) else null
    val account = listOfNotNull(tx.bank, tx.last4?.let { "··$it" }).joinToString(" ").ifBlank { null }
    val meta = listOfNotNull(MoneyMath.dayLabel(tx.at.toLocalDate(), today), account, tx.paidWith).joinToString(" · ")
    val spoken = "$note, ${rupeesSpoken(tx.amountPaise)}, ${if (row.kind == "received") "received" else "spent"}, ${if (row.checked) "will be imported" else "skipped"}"
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().pressable({ vm.toggle(row.id) }).semantics(mergeDescendants = true) { contentDescription = spoken },
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CheckCircle(row.checked, null, Modifier.padding(top = 2.dp), size = 24)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CoveText(note, style = CoveType.Body, maxLines = 2)
                if (stacked) AmountText(tx.amountPaise, row.kind)
                CoveText(meta, style = MoneyType.Small, color = c.muted)
                handle?.let { CoveText(it, style = MoneyType.Small, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                row.item.match?.let { m ->
                    CoveText("Possible duplicate: already added \"${m.note.ifBlank { "an expense" }} ${rupees(m.amountPaise)}\"", style = MoneyType.Small, color = c.alert)
                }
            }
            if (!stacked) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) { AmountText(tx.amountPaise, row.kind) }
        }
        FlowRow(Modifier.fillMaxWidth().padding(start = 38.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (row.kind == "spent") {
                val name = cats.firstOrNull { it.id == row.categoryId }?.name
                Pill(name ?: "Pick a category", selected = false, accent = name != null) { picking = !picking }
            }
            KindToggle(row.kind) { vm.setKind(row.id, it) }
            if (row.kind == "spent") Pill("Rename", selected = renaming) { renaming = !renaming }
        }
        if (row.recalled && row.kind == "spent" && !row.picked) {
            CoveText("Learned from your earlier payment", Modifier.padding(start = 38.dp), style = MoneyType.Small, color = c.accent)
        }
        if (renaming) LabelField(row.note, { vm.setLabel(row.id, it) }, Modifier.padding(start = 38.dp))
        if (picking && row.kind == "spent") {
            Row(Modifier.fillMaxWidth().padding(start = 38.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEach { cat ->
                    Pill(cat.name, selected = cat.id == row.categoryId) { vm.pickCategory(row.id, cat.id); picking = false }
                }
            }
        }
    }
}

/** One-line field for the note an imported payment is saved with; Cove remembers it for this payee once the row is imported. */
@Composable
private fun LabelField(text: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Cove.colors
    var value by remember { mutableStateOf(text) }
    Box(modifier.fillMaxWidth().heightIn(min = 44.dp).background(c.well, CoveShapes.Pill).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        BasicTextField(
            value, { value = it; onChange(it) }, Modifier.fillMaxWidth(), singleLine = true,
            textStyle = CoveType.Body.copy(color = c.ink), cursorBrush = SolidColor(c.ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
    }
}

/** Amount in ink (never red), with a green "Received" label for income. */
@Composable
private fun AmountText(paise: Long, kind: String) {
    CoveText(rupees(paise), style = CoveType.Body)
    if (kind == "received") CoveText("Received", style = MoneyType.Small, color = Cove.colors.saved)
}

@Composable
private fun KindToggle(kind: String, onPick: (String) -> Unit) {
    val c = Cove.colors
    Row(Modifier.heightIn(min = 36.dp).background(c.well, CoveShapes.Pill).padding(2.dp)) {
        listOf("spent" to "Spent", "received" to "Received").forEach { (k, label) ->
            Box(
                Modifier.heightIn(min = 32.dp).background(if (kind == k) c.card else androidx.compose.ui.graphics.Color.Transparent, CoveShapes.Pill)
                    .pressable({ onPick(k) }).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { CoveText(label, style = MoneyType.Small.copy(fontWeight = if (kind == k) FontWeight.Medium else FontWeight.Normal), color = if (kind == k) c.ink else c.muted) }
        }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, accent: Boolean = false, onClick: () -> Unit) {
    val c = Cove.colors
    val bg = if (selected) c.accent else if (accent) c.accentSoft else c.well
    val fg = if (selected) c.onAccent else if (accent) c.accent else c.ink
    Box(
        Modifier.heightIn(min = 36.dp).background(bg, CoveShapes.Pill).pressable(onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { CoveText(text, style = CoveType.Meta.copy(fontWeight = if (selected || accent) FontWeight.Medium else FontWeight.Normal), color = fg) }
}
