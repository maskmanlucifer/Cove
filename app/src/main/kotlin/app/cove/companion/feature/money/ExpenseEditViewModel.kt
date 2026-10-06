package app.cove.companion.feature.money

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cove.companion.AppContainer
import app.cove.companion.core.Undo
import app.cove.companion.core.newId
import app.cove.companion.core.toLocalDate
import app.cove.companion.data.categorize.ExpenseCategorizer
import app.cove.companion.data.categorize.PayeeLearning
import app.cove.companion.data.sms.PayeeKey
import app.cove.companion.data.local.entity.CategoryMemoryEntity
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

/** The payee of an imported expense as the edit screen shows it; [remembered] is "Gym · Health" when Cove has learned it. */
data class PayeeInfo(val name: String, val handle: String?, val remembered: String?)

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
    /** Shown for a moment when a digit was refused because the amount is at its largest. */
    val limitHint: String? = null,
    /** Set for expenses imported from messages that carry a payee identity. */
    val payee: PayeeInfo? = null,
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

    /** Id a new expense is saved under; stable across taps and process death, so saving twice only rewrites one row. */
    private val newExpenseId: String = saved.get<String>(SAVED_ID) ?: newId().also { saved[SAVED_ID] = it }
    private val limitHint = MutableStateFlow(false)
    private var limitJob: Job? = null

    private val existingId = id.takeUnless { it == "new" || it.startsWith("new@") }
    private var existing: ExpenseEntity? = null
    private val loaded = MutableStateFlow<ExpenseEntity?>(null)

    private val payeeInfo: StateFlow<PayeeInfo?> = combine(loaded, c.money.payeeMemory, c.money.categories) { e, memory, cats ->
        val key = e?.payeeKey ?: return@combine null
        val m = memory[key]
        val cat = m?.let { mem -> cats.firstOrNull { it.id == mem.categoryId }?.name }
        PayeeInfo(
            name = m?.displayName?.ifBlank { null } ?: e.note,
            handle = PayeeKey.handleOf(key),
            remembered = if (m != null && cat != null) "${m.label ?: m.displayName.ifBlank { e.note }} · $cat" else null,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
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

    val state: StateFlow<ExpenseEditState> = combine(form, c.money.categories, c.money.memory, limitHint, payeeInfo) { f, all, memory, limit, payee ->
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
            limitHint = if (limit) AmountInput.LIMIT_HINT else null,
            payee = payee,
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
                    loaded.value = e
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

    fun key(ch: Char) {
        if (AmountInput.wouldOverflow(form.value.amount, ch)) showLimit()
        form.update { it.copy(amount = AmountInput.push(it.amount, ch)) }
    }

    private fun showLimit() {
        limitHint.value = true
        limitJob?.cancel()
        limitJob = viewModelScope.launch {
            delay(2500)
            limitHint.value = false
        }
    }

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
            id = existing?.id ?: newExpenseId,
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
        teachPayee(entity)
        return alert
    }

    /**
     * Editing the category or note of an expense that has a payee is an explicit choice: remember it for the payee and
     * offer to tag the earlier payments to the same payee. A note left as the generated name stores no label.
     */
    private suspend fun teachPayee(e: ExpenseEntity) {
        val before = existing ?: return
        val key = before.payeeKey ?: return
        val category = e.categoryId ?: return
        if (e.kind != "spent" || (category == before.categoryId && e.note == before.note)) return
        val generated = c.money.payee(key)?.displayName?.ifBlank { null } ?: before.note
        val label = PayeeLearning.labelFor(e.note, generated)
        c.money.teachPayee(key, category, label, generated)
        val changes = c.money.retroChanges(mapOf(key to (category to label)), setOf(e.id))
        if (changes.isNotEmpty()) RetroTag.post(RetroOffer(changes, 1))
    }

    /** "Forget" on the remembered line: Cove stops pre-tagging this payee; the expense itself is not touched. */
    fun forgetPayee() {
        val key = existing?.payeeKey ?: return
        viewModelScope.launch { c.money.forgetPayee(key) }
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

    /** Soft-deletes the expense and offers "Expense deleted · Undo" on the screen the user returns to. */
    suspend fun delete() {
        val eid = existingId ?: return
        c.money.delete(eid)
        Undo.center.post(MONEY_UNDO, "Expense deleted") { c.money.restore(eid) }
    }
}

private const val SAVED_ID = "expense-id"
private const val SAVED_RECEIVED = "received"
private const val SAVED_AMOUNT = "amount"
private const val SAVED_CATEGORY = "category"
private const val SAVED_TOUCHED = "touched"
private const val SAVED_NOTE = "note"
private const val SAVED_WHEN = "when"
private const val SAVED_PAID = "paid"
