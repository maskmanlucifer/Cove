package app.cove.companion.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cove.companion.core.appViewModel
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.navigation.Nav
import app.cove.companion.navigation.Routes

/** Categories with budgets and progress; long-press a row to drag it into a new order. */
@Composable
fun MoneyCategoriesScreen(nav: Nav) {
    val vm = appViewModel { CategoriesViewModel(it) }
    val s by vm.state.collectAsState()
    val c = Cove.colors
    CoveScreen {
        Column(Modifier.fillMaxSize().coveTopInset()) {
            MoneyTopBar(s.month, "Done", nav.back, nav.back, actionStrong = true)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CoveText("Categories", style = CoveType.Title)
                    CoveText(subtitle(s), style = MoneyType.Sub, color = c.muted)
                }
                RowsCard(vertical = 4.dp) {
                    DragList(
                        items = s.items,
                        key = { it.category.id },
                        onReorder = { ids -> vm.reorder(ids) },
                    ) { item, first -> CategoryRow(item, first, onClick = { nav.go(Routes.moneyCategoryDetail(item.category.id)) }) }
                    RowDivider()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).pressable({ nav.go(Routes.moneyCategory()) }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CoveIcon(CoveIcons.Plus, c.muted, size = 18.dp)
                        CoveText("New category", style = MoneyType.Row, color = c.muted)
                    }
                }
                CoveText("Anything without a category goes to Other.", style = MoneyType.Note, color = c.muted)
            }
        }
        MoneyDock(nav, Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 24.dp))
    }
}

private fun subtitle(s: CategoriesState): String {
    val count = s.items.count { it.category.kind == "spending" }
    val words = listOf("no categories", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
    val n = words.getOrElse(count) { count.toString() }
    return if (s.budgetTotal > 0) "${MoneyMath.wholeRupees(s.budgetTotal)} a month across $n. Drag to reorder."
    else "Add a monthly budget to see progress. Drag to reorder."
}

@Composable
private fun CategoryRow(item: CategoryMonth, first: Boolean, onClick: () -> Unit) {
    val c = Cove.colors
    val cat = item.category
    if (!first) RowDivider()
    Column(Modifier.fillMaxWidth().pressable(onClick).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            CoveText(cat.name, style = CoveType.BodyMedium)
            val amount = MoneyMath.wholeRupees(item.spent)
            if (cat.kind == "income") CoveText(amount, style = MoneyType.Sub)
            else CoveText(amount, if (item.budget > 0) " of ${MoneyMath.wholeRupees(item.budget)}" else "", style = MoneyType.Sub, secondaryColor = c.muted)
        }
        if (cat.kind == "spending" && item.budget > 0) {
            BudgetBar(MoneyMath.progress(item.spent, item.budget), item.over)
            MoneyMath.overNote(item.spent, item.budget)?.let { CoveText(it, style = MoneyType.Small, color = c.muted) }
        }
    }
}
