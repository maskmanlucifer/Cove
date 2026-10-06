package app.cove.companion.feature.money

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.cove.companion.core.rupeesSpoken
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cove.companion.core.appViewModel
import app.cove.companion.core.rupees
import app.cove.companion.core.toLocalDate
import app.cove.companion.data.categorize.ReviewRow
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.container
import app.cove.companion.navigation.Nav
import androidx.compose.ui.platform.LocalContext

/** Review: expenses filed under Other (or nothing) with a suggested category each; nothing changes until the user taps. */
@Composable
fun MoneyReviewScreen(nav: Nav) {
    val vm = appViewModel { ReviewViewModel(it) }
    val s by vm.state.collectAsState()
    val today = LocalContext.current.container.clock.now().toLocalDate()
    val c = Cove.colors
    val cross = s.mode == ReviewMode.CrossCheck
    val open = s.review.open
    CoveScreen {
        Column(Modifier.fillMaxSize().coveTopInset()) {
            MoneyTopBar(if (cross) "Cross-check" else "Review", "Done", nav.back, nav.back, actionStrong = true)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cross) SmallAction("Back to Other", enabled = !s.busy, onClick = vm::showUnfiled)
                    else SmallAction("Check with AI", enabled = !s.busy, onClick = vm::checkWithAi)
                    SmallAction("Cross-check this month", enabled = !s.busy, onClick = vm::crossCheck)
                }
                if (s.busy) CoveText("Checking with AI…", Modifier.padding(horizontal = 4.dp), style = MoneyType.Note, color = c.muted)
                s.message?.let { CoveText(it, Modifier.padding(horizontal = 4.dp), style = MoneyType.Note, color = c.muted) }
                s.provenance?.let { CoveText("Answered by: $it", Modifier.padding(horizontal = 4.dp), style = MoneyType.Small, color = c.tail) }
                if (s.loaded && open.isEmpty() && !s.busy && (cross || s.mode == ReviewMode.Unfiled)) {
                    CoveText(if (cross) "AI agrees with how you filed everything." else "Everything is filed.", Modifier.padding(horizontal = 4.dp), style = MoneyType.Note, color = c.muted)
                }
                if (open.isNotEmpty()) {
                    RowsCard(radius = 24.dp, vertical = 0.dp) {
                        open.forEachIndexed { i, row ->
                            if (i > 0) RowDivider()
                            ReviewItem(row, s.categories, cross, today, vm)
                        }
                    }
                }
            }
        }
        if (!cross && s.review.acceptAllChanges().isNotEmpty()) {
            PillButton(
                "Accept all", vm::acceptAll,
                Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = 40.dp).fillMaxWidth(),
                height = 56.dp, textStyle = MoneyType.Row.copy(fontWeight = FontWeight.Medium),
            )
        }
        if (s.undoCount > 0) UndoBar("Filed ${s.undoCount}", vm::undoLast, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 108.dp))
    }
}

@Composable
private fun SmallAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Cove.colors
    Box(
        Modifier.height(40.dp).background(c.card, CoveShapes.Pill).pressable(onClick, enabled = enabled).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { CoveText(text, style = CoveType.Button.copy(fontWeight = FontWeight.Normal), color = if (enabled) c.ink else c.tail) }
}

@Composable
private fun ReviewItem(row: ReviewRow, categories: List<ExpenseCategoryEntity>, cross: Boolean, today: java.time.LocalDate, vm: ReviewViewModel) {
    val c = Cove.colors
    var picking by remember { mutableStateOf(false) }
    val e = row.expense
    val target = categories.firstOrNull { it.id == row.targetId }
    val current = categories.firstOrNull { it.id == e.categoryId }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CoveText(e.note.ifBlank { "No note" }, style = CoveType.Body)
                CoveText(MoneyMath.dayLabel(e.spentAt.toLocalDate(), today), style = MoneyType.Small, color = c.muted)
            }
            CoveText(rupees(e.amountPaise), Modifier.semantics { contentDescription = rupeesSpoken(e.amountPaise) }, style = CoveType.Body)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipButton(target?.name ?: "Pick a category", selected = target != null) { picking = !picking }
            val note = if (cross) "Now ${current?.name ?: "Other"} · AI" else row.reasonLabel
            if (note != null) CoveText(note, Modifier.weight(1f), style = MoneyType.Small, color = c.muted)
            else Box(Modifier.weight(1f))
            if (target != null) TextAction("Accept", strong = true) { vm.accept(e.id) }
            TextAction("Skip", strong = false) { vm.skip(e.id) }
        }
        if (picking) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.filterNot { it.name.trim().equals("Other", true) }.forEach { cat ->
                    ChipButton(cat.name, selected = cat.id == row.targetId) {
                        vm.pick(e.id, cat.id)
                        picking = false
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipButton(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Cove.colors
    Box(
        Modifier.height(36.dp).background(if (selected) c.ink else c.well, CoveShapes.Pill).pressable(onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        CoveText(text, style = CoveType.Meta.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal), color = if (selected) c.onInk else c.ink)
    }
}

@Composable
private fun TextAction(text: String, strong: Boolean, onClick: () -> Unit) {
    val c = Cove.colors
    Box(Modifier.height(36.dp).pressable(onClick).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        CoveText(text, style = CoveType.Meta.copy(fontWeight = if (strong) FontWeight.Medium else FontWeight.Normal), color = if (strong) c.ink else c.muted)
    }
}

@Composable
private fun UndoBar(text: String, onUndo: () -> Unit, modifier: Modifier) {
    val c = Cove.colors
    Row(modifier.fillMaxWidth().height(56.dp).background(c.ink, CoveShapes.Pill).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CoveText(text, Modifier.weight(1f), style = MoneyType.Sub, color = c.onInk)
        Box(
            Modifier.height(40.dp).background(c.onInk.copy(alpha = 0.12f), CoveShapes.Pill).pressable(onUndo).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) { CoveText("Undo", style = CoveType.Button.copy(fontWeight = FontWeight.Medium), color = c.onInk) }
    }
}
