package app.cove.companion.feature.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.toLocalDate
import app.cove.companion.data.categorize.ReviewLogic
import app.cove.companion.data.sms.CaptureMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.format.TextStyle
import java.util.Locale

/** A line in the Money tab's category card. [id] is null for spending with no category ("Other"). */
data class MoneyRow(val id: String?, val name: String, val spent: Long, val budget: Long)

data class MoneyState(
    val month: String = "",
    val spent: Long = 0,
    /** Total budget minus spend; null when no budgets are set. */
    val left: Long? = null,
    val daysToGo: Int = 0,
    val bars: List<DayBar> = emptyList(),
    val rows: List<MoneyRow> = emptyList(),
    val empty: Boolean = true,
    /** "5 in Other · Review" when enough recent expenses are unfiled; null otherwise. */
    val reviewBanner: String? = null,
)

/** Month overview for the Money tab: spend so far, daily strip and the busiest categories (the rest fold into Other). */
class MoneyViewModel(c: AppContainer) : ViewModel() {
    /** Payments found in new messages that wait for the user ("Payments from messages"). */
    val pending: StateFlow<Int> = c.smsImport.pendingCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** This device's "Payments from messages" mode. */
    val captureMode: StateFlow<CaptureMode> = c.smsCapturePrefs.mode

    init {
        c.smsCatchUp.runIfDue()
    }

    private val today = c.clock.now().toLocalDate()
    private val range = ledgerRange(today)

    private val now = c.clock.now()

    val state: StateFlow<MoneyState> = combine(
        c.money.categories, c.money.expenses(range.first, range.last),
        c.money.expenses(now - ReviewLogic.UNFILED_DAYS * 24L * 60 * 60 * 1000, now),
    ) { categories, all, recent ->
        val month = thisMonth(all, today)
        val spending = categories.filter { it.kind == "spending" }
        val perCategory = categoryMonths(spending, all, today)
        val spent = MoneyMath.spentOf(month)
        val budget = perCategory.sumOf { it.budget }
        val rows = MoneyMath.collapseRows(moneyRows(perCategory, month))
        MoneyState(
            month = today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            spent = spent,
            left = if (budget > 0) budget - spent else null,
            daysToGo = MoneyMath.daysToGo(today),
            bars = MoneyMath.dailyBars(month, today),
            rows = rows,
            empty = month.none { it.kind == "spent" },
            reviewBanner = ReviewLogic.bannerText(ReviewLogic.unfiled(recent, categories, now).size),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MoneyState())
}
