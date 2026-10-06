package app.cove.companion.feature.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cove.companion.core.appViewModel
import app.cove.companion.core.rupees
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.DockClearance
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.navigation.Nav
import app.cove.companion.navigation.Routes

/** Money tab: spent so far this month, the daily strip and the busiest categories. */
@Composable
fun MoneyScreen(nav: Nav) {
    val vm = appViewModel { MoneyViewModel(it) }
    val s by vm.state.collectAsState()
    val c = Cove.colors
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .coveTopInset()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = DockClearance),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                CoveText("Money", style = CoveType.Title)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.height(44.dp).pressable({ nav.go(Routes.MoneyCategories) }).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                        CoveText("Categories", style = CoveType.Meta, color = c.muted)
                    }
                    RoundIconButton(CoveIcons.Plus, { nav.go(Routes.expenseEdit()) })
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CoveText("Spent so far · ${s.month}", style = CoveType.Meta, color = c.muted)
                val figure = rupees(s.spent, decimals = true)
                CoveText(figure.substringBefore('.'), "." + figure.substringAfter('.'), style = CoveType.Figure)
                CoveText(summaryLine(s), style = MoneyType.Sub, color = c.muted)
            }
            DailyBars(s.bars)
            if (s.empty) {
                CoveText("Nothing spent yet this month. Tap + or just say it.", style = MoneyType.Note, color = c.muted)
            } else {
                CategoryCard(s.rows, nav)
            }
            s.reviewBanner?.let { ReviewLink(it) { nav.go(Routes.MoneyReview) } }
        }
        MoneyUndoBar(Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 112.dp))
    }
}

private fun summaryLine(s: MoneyState): String {
    val days = if (s.daysToGo == 1) "1 day to go" else "${s.daysToGo} days to go"
    return s.left?.let { "${MoneyMath.wholeRupees(it.coerceAtLeast(0))} left · $days" } ?: days
}

/** One bar per day: ink for days with spending, grey for quiet days, faint dots for days to come. */
@Composable
private fun DailyBars(bars: List<DayBar>) {
    val c = Cove.colors
    val quiet = if (c.isDark) c.tail else androidx.compose.ui.graphics.Color(0xFFC8CACE)
    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEach { bar ->
            val (color, h) = when (bar.kind) {
                BarKind.Spent -> c.ink to (6 + 28 * bar.fraction).dp
                BarKind.Quiet -> quiet to 6.dp
                BarKind.Future -> c.wellStrong to 6.dp
            }
            Box(Modifier.weight(1f).height(h).background(color, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun CategoryCard(rows: List<MoneyRow>, nav: Nav) {
    RowsCard {
        rows.forEachIndexed { i, row ->
            if (i > 0) RowDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .pressable({ row.id?.let { nav.go(Routes.moneyCategoryDetail(it)) } }, enabled = row.id != null),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CoveText(row.name, style = MoneyType.Row)
                Row(verticalAlignment = Alignment.Bottom) {
                    CoveText(MoneyMath.wholeRupees(row.spent), style = MoneyType.Row)
                    MoneyMath.overInline(row.spent, row.budget)?.let { CoveText(it, style = MoneyType.Small, color = Cove.colors.tail) }
                }
            }
        }
    }
}

/** Quiet link to the Review screen, shown only when several recent expenses sit in Other. */
@Composable
private fun ReviewLink(text: String, onClick: () -> Unit) {
    val c = Cove.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).pressable(onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CoveText(text, style = MoneyType.Sub, color = c.muted)
        CoveIcon(CoveIcons.ChevronRight, c.tail, size = 14.dp)
    }
}
