package app.cove.companion.feature.money

import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import app.cove.companion.core.rupeesSpoken
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.unit.sp
import app.cove.companion.core.appViewModel
import app.cove.companion.core.rupees
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.EmptyState
import app.cove.companion.design.illustrations.Scene
import app.cove.companion.design.components.EmptyAction
import app.cove.companion.design.components.FitText
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.navigation.Nav
import app.cove.companion.navigation.Routes

private val Spent = CoveType.Figure.copy(fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-2).sp)
private val OfBudget = CoveType.Figure.copy(fontSize = 28.sp, lineHeight = 37.8.sp, letterSpacing = (-0.5).sp)

/** One category's month: spent of budget, what is left, and its transactions grouped by day. */
@Composable
fun MoneyCategoryDetailScreen(id: String, nav: Nav) {
    val vm = appViewModel(key = "category-$id") { CategoryDetailViewModel(it, id) }
    val s by vm.state.collectAsState()
    val c = Cove.colors
    val cat = s.category
    val owner = LocalLifecycleOwner.current
    // A category deleted from its Edit sheet leaves this page empty: close it once it is the screen on top.
    LaunchedEffect(s.loaded, cat == null) {
        if (s.loaded && cat == null) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { nav.back() }
    }
    CoveScreen {
        Column(Modifier.fillMaxSize().coveTopInset()) {
            MoneyTopBar(
                label = if (cat == null) "" else "${cat.name} · ${s.month}",
                action = "Edit",
                onBack = nav.back,
                onAction = { nav.go(Routes.moneyCategory(id)) },
                actionStrong = false,
            )
            if (cat != null) {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 176.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item { Hero(s) }
                    if (s.groups.isEmpty()) {
                        item { EmptyState(Scene.Money, "Nothing here yet.", "No spending in this category this month.", compact = true) }
                    }
                    items(s.groups, key = { it.label }) { group ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CoveText(group.label, Modifier.padding(start = 4.dp), style = CoveType.Meta, color = c.muted)
                            RowsCard(radius = 24.dp, vertical = 0.dp) {
                                group.items.forEachIndexed { i, e ->
                                    if (i > 0) RowDivider()
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 64.dp).pressable({ nav.go(Routes.expenseEdit(e.id)) }),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            CoveText(e.note.ifBlank { cat.name }, style = CoveType.Body)
                                            CoveText(MoneyMath.methodLine(e), style = MoneyType.Small, color = c.muted)
                                        }
                                        CoveText((if (e.kind == "received") "+" else "") + rupees(e.amountPaise), Modifier.semantics { contentDescription = (if (e.kind == "received") "received " else "") + rupeesSpoken(e.amountPaise) }, style = CoveType.Body)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 112.dp)) {
            val shadow = Color(0x1F141420)
            Row(
                Modifier
                    .height(52.dp)
                    .shadow(24.dp, CoveShapes.Pill, ambientColor = shadow, spotColor = shadow)
                    .background(c.ink, CoveShapes.Pill)
                    .pressable({ nav.go(Routes.expenseEdit("new@$id")) })
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CoveIcon(CoveIcons.Plus, c.onInk, size = 18.dp)
                CoveText("Add", style = CoveType.Button, color = c.onInk)
            }
        }
        RetroTagBar(Modifier.align(Alignment.TopCenter))
        MoneyUndoBar(Modifier.align(Alignment.TopCenter))
        MoneyDock(nav)
    }
}

@Composable
private fun Hero(s: CategoryDetailState) {
    val c = Cove.colors
    val spent = if (s.spent >= MoneyMath.CRORE_PAISE) MoneyMath.compactRupees(s.spent) else MoneyMath.wholeRupees(s.spent)
    val spoken = "${MoneyMath.spokenRupees(s.spent)} spent" + if (s.budget > 0) " of ${MoneyMath.spokenRupees(s.budget)}" else ""
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(bottom = 7.dp).semantics(mergeDescendants = true) { contentDescription = spoken }, verticalAlignment = Alignment.Bottom) {
            FitText(spent, Modifier.weight(1f, fill = false), style = Spent, minScale = 0.5f)
            if (s.budget > 0) {
                val budget = if (s.budget >= MoneyMath.CRORE_PAISE) MoneyMath.compactRupees(s.budget) else MoneyMath.wholeRupees(s.budget)
                CoveText(" of ${budget.removePrefix("₹")}", style = OfBudget, color = c.tail, maxLines = 1)
            }
        }
        if (s.budget > 0) {
            BudgetBar(MoneyMath.progress(s.spent, s.budget), MoneyMath.isOver(s.spent, s.budget))
            CoveText(heroLine(s), style = MoneyType.Sub, color = c.muted)
        } else {
            CoveText("No budget set · tap Edit to add one", style = MoneyType.Sub, color = c.muted)
        }
    }
}

private fun heroLine(s: CategoryDetailState): String =
    MoneyMath.overNote(s.spent, s.budget) ?: buildString {
        append("${MoneyMath.wholeRupees(s.left)} left")
        MoneyMath.perDay(s.left, s.daysToGo)?.let { append(" · about ${MoneyMath.wholeRupees(it)} a day") }
    }
