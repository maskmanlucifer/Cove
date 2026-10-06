package app.cove.companion.feature.money

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.newId
import app.cove.companion.core.toLocalDate
import app.cove.companion.data.categorize.ExpenseCategorizer
import app.cove.companion.data.local.entity.CategoryMemoryEntity
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ways of paying offered in the "Paid with" sheet. */
val PaymentMethods = listOf("UPI", "Card", "Cash", "Bank transfer")

data class ExpenseEditState(
    val isNew: Boolean = true,
    val received: Boolean = false,
    /** Digits typed on the keypad, see [AmountInput]. */
    val amount: String = "",
    val categoryId: String? = null,
    val note: String = "",
    val whenMillis: Long = 0,
    val paidWith: String = "UPI",
    /** Categories matching Spent / Received, in the user's order. */
    val categories: List<ExpenseCategoryEntity> = emptyList(),
) {
    val paise: Long get() = AmountInput.toPaise(amount)
}

/**
 * Form state for the Add expense screen. [id] is `new`, `new@<categoryId>` to preselect a category,
 * or the id of an expense being edited.
 */
class ExpenseEditViewModel(private val c: AppContainer, private val id: String, private val saved: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private data class Form(
        val received: Boolean = false,
        val amount: String = "",
        val categoryId: String? = null,
        val categoryTouched: Boolean = false,
        val note: String = "",
        val whenMillis: Long = 0,
        val paidWith: String = "UPI",
    )

    private val existingId = id.takeUnless { it == "new" || it.startsWith("new@") }
    private var existing: ExpenseEntity? = null
    private val form = MutableStateFlow(
        if (existingId == null && saved.contains(SAVED_AMOUNT)) {
            Form(
                received = saved[SAVED_RECEIVED] ?: false,
                amount = saved[SAVED_AMOUNT] ?: "",
                categoryId = saved[SAVED_CATEGORY],
                categoryTouched = saved[SAVED_TOUCHED] ?: false,
                note = saved[SAVED_NOTE] ?: "",
                whenMillis = saved[SAVED_WHEN] ?: c.clock.now(),
                paidWith = saved[SAVED_PAID] ?: "UPI",
            )
        } else {
            Form(
                whenMillis = c.clock.now(),
                categoryId = id.substringAfter("new@", "").ifEmpty { null },
                categoryTouched = id.startsWith("new@"),
            )
        },
    )

    val state: StateFlow<ExpenseEditState> = combine(form, c.money.categories, c.money.memory) { f, all, memory ->
        val wanted = if (f.received) "income" else "spending"
        val cats = all.filter { it.kind == wanted }
        ExpenseEditState(
            isNew = existingId == null,
            received = f.received,
            amount = f.amount,
            categoryId = if (f.categoryTouched) f.categoryId else suggestedId(f, cats, memory),
            note = f.note,
            whenMillis = f.whenMillis,
            paidWith = f.paidWith,
            categories = cats,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ExpenseEditState(whenMillis = form.value.whenMillis))

    /** Until the user picks a chip: the categorizer's guess for the typed note, else Other, else the first chip. */
    private fun suggestedId(f: Form, cats: List<ExpenseCategoryEntity>, memory: Map<String, CategoryMemoryEntity>): String? {
        if (f.received || f.note.isBlank()) return cats.firstOrNull()?.id
        return ExpenseCategorizer.suggest(f.note, cats, memory).categoryId
            ?: (ExpenseCategorizer.fallback(cats) ?: cats.firstOrNull())?.id
    }

    init {
        if (existingId == null) {
            viewModelScope.launch {
                form.collect {
                    saved[SAVED_RECEIVED] = it.received
                    saved[SAVED_AMOUNT] = it.amount
                    saved[SAVED_CATEGORY] = it.categoryId
                    saved[SAVED_TOUCHED] = it.categoryTouched
                    saved[SAVED_NOTE] = it.note
                    saved[SAVED_WHEN] = it.whenMillis
                    saved[SAVED_PAID] = it.paidWith
                }
            }
        }
        existingId?.let { eid ->
            viewModelScope.launch {
                c.money.expense(eid)?.let { e ->
                    existing = e
                    form.value = Form(
                        received = e.kind == "received",
                        amount = AmountInput.fromPaise(e.amountPaise),
                        categoryId = e.categoryId,
                        categoryTouched = true,
                        note = e.note,
                        whenMillis = e.spentAt,
                        paidWith = e.paidWith,
                    )
                }
            }
        }
    }

    fun key(ch: Char) = form.update { it.copy(amount = AmountInput.push(it.amount, ch)) }

    fun back() = form.update { it.copy(amount = AmountInput.back(it.amount)) }

    fun clear() = form.update { it.copy(amount = "") }

    fun setReceived(received: Boolean) = form.update {
        if (it.received == received) it else it.copy(received = received, categoryId = null, categoryTouched = false)
    }

    fun setCategory(categoryId: String) = form.update { it.copy(categoryId = categoryId, categoryTouched = true) }

    fun setNote(note: String) = form.update { it.copy(note = note) }

    fun setWhen(millis: Long) = form.update { it.copy(whenMillis = millis) }

    fun setPaidWith(method: String) = form.update { it.copy(paidWith = method) }

    /** Saves the expense and returns a gentle 80% heads-up when this spend crosses the line, else null. */
    suspend fun save(): String? {
        val s = state.value
        if (s.paise <= 0) return null
        val entity = ExpenseEntity(
            id = existing?.id ?: newId(),
            amountPaise = s.paise,
            kind = if (s.received) "received" else "spent",
            categoryId = s.categoryId,
            note = s.note.trim(),
            paidWith = s.paidWith,
            spentAt = s.whenMillis,
            source = existing?.source ?: "manual",
        )
        val alert = alertFor(entity)
        c.money.save(entity)
        val picked = if (existing == null) form.value.categoryTouched else s.categoryId != existing?.categoryId
        if (picked && !s.received && s.categoryId != null) c.money.teach(entity.note, s.categoryId, existing?.categoryId)
        return alert
    }

    private suspend fun alertFor(e: ExpenseEntity): String? {
        if (e.kind != "spent" || e.categoryId == null) return null
        val day = e.spentAt.toLocalDate()
        val range = ledgerRange(day)
        val all = c.money.expenses(range.first, range.last).first()
        val cat = c.money.categories.first().firstOrNull { it.id == e.categoryId } ?: return null
        val before = categoryMonths(listOf(cat), all.filter { it.id != e.id }, day).single()
        val after = before.spent + e.amountPaise
        return if (MoneyMath.crossedAlert(before.spent, after, before.budget, cat.alertAt80)) {
            MoneyMath.alertMessage(cat.name, after, before.budget)
        } else null
    }

    fun delete() {
        existingId?.let { eid ->
            viewModelScope.launch {
                c.money.delete(eid)
                MoneyUndo.deleted.value = eid
            }
        }
    }
}

private const val SAVED_RECEIVED = "received"
private const val SAVED_AMOUNT = "amount"
private const val SAVED_CATEGORY = "category"
private const val SAVED_TOUCHED = "touched"
private const val SAVED_NOTE = "note"
private const val SAVED_WHEN = "when"
private const val SAVED_PAID = "paid"
