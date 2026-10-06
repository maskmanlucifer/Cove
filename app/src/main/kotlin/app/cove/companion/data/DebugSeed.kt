package app.cove.companion.data

import app.cove.companion.AppContainer
import app.cove.companion.core.newId
import app.cove.companion.core.toEpochMillis
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import app.cove.companion.data.local.entity.HabitEntity
import app.cove.companion.data.local.entity.TodoCategoryEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Debug-only sample data matching the design frames, used to compare screens pixel by pixel. */
object DebugSeed {
    suspend fun load(c: AppContainer, dark: Boolean, evening: Boolean, moneyLogged: Boolean = false) {
        c.settings.update {
            it.copy(displayName = "Maya", onboarded = true, theme = if (dark) "dark" else "light", wakeMinutes = 6 * 60 + 30)
        }
        if (c.database.todos().categoryCount() > 0) return
        val day = c.clock.now().let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
        fun at(h: Int, m: Int = 0, d: LocalDate = day) = LocalDateTime.of(d, LocalTime.of(h, m)).toEpochMillis()

        val home = TodoCategoryEntity(newId(), "Home", 0)
        val shopping = TodoCategoryEntity(newId(), "Shopping", 1)
        val personal = TodoCategoryEntity(newId(), "Personal", 2)
        val errands = TodoCategoryEntity(newId(), "Errands", 3)
        listOf(home, shopping, personal, errands).forEach { c.todos.saveCategory(it) }
        val open = listOf(
            c.todos.add("Reply to Priya", personal.id, at(13)),
            c.todos.add("Water the plants", home.id),
            c.todos.add("Call mum", personal.id, at(18)),
        )
        c.todos.add("Dish soap", shopping.id)
        c.todos.add("Birthday card for Ana", shopping.id)
        if (evening) open.forEach { c.todos.setDone(it.id, true) }

        c.plan.saveEvent(EventEntity(newId(), "Coffee with Jo", at(11), at(11, 45), "Café Ivy", "bring her book back"))
        c.plan.saveAlarm(AlarmEntity(newId(), "Wake up", 6 * 60 + 30, 0b0011111))
        c.plan.saveAlarm(AlarmEntity(newId(), "Bedtime", 22 * 60 + 30, 0b1111111, kind = "bedtime"))

        val habits = listOf("Read 10 pages", "Walk", "Stretch").mapIndexed { i, n -> HabitEntity(newId(), n, sort = i) }
        habits.forEach { c.habits.save(it) }
        if (!evening) habits.take(2).forEach { c.habits.toggle(it.id, day) }

        seedMoney(c, day, moneyLogged)
    }

    /**
     * Categories and this month's spending. Default matches frame 07 (Food ₹7,000, total ₹18,420.50);
     * [logged] matches frames 25/34/36 (Food ₹7,340 of ₹9,000, with today's and yesterday's rows).
     */
    private suspend fun seedMoney(c: AppContainer, day: LocalDate, logged: Boolean) {
        fun at(h: Int, m: Int, d: LocalDate) = LocalDateTime.of(d, LocalTime.of(h, m)).toEpochMillis()
        val cats = listOf("Food" to 9000, "Home" to 8000, "Transport" to 5000, "Fun" to 2000, "Other" to 6000)
            .mapIndexed { i, (n, b) -> ExpenseCategoryEntity("cat-" + n.lowercase(), n, budgetPaise = b * 100L, sort = i) }
        cats.forEach { c.money.saveCategory(it) }
        suspend fun add(
            cat: String, paise: Long, d: LocalDate, h: Int = 9, m: Int = 0, note: String = cat,
            paidWith: String = "UPI", source: String = "manual", id: String = newId(),
        ) = c.money.save(
            ExpenseEntity(id, paise, categoryId = cats.first { it.name == cat }.id, note = note, paidWith = paidWith, spentAt = at(h, m, d), source = source),
        )
        val first = day.withDayOfMonth(1)
        add("Food", if (logged) 545_000 else 666_000, first, note = "Weekly shop")
        add("Home", 442_000, first.plusDays(1), paidWith = "Card")
        add("Transport", 245_000, first.plusDays(2))
        add("Fun", 221_000, first.plusDays(3), paidWith = "Card")
        add("Other", 184_050, first.plusDays(4))
        add("Transport", 50_000, day, 8, 15, note = "Cab")
        if (logged) {
            add("Food", 34_000, day, 13, 12, note = "Lunch · Café Ivy", source = "voice")
            add("Food", 25_000, day, 18, 40, note = "Groceries", id = "seed-groceries")
            add("Food", 112_000, day.minusDays(1), 20, 30, note = "Dinner with Jo", paidWith = "Card")
            add("Food", 18_000, day.minusDays(1), 9, 5, note = "Coffee")
        } else {
            add("Food", 34_000, day, 13, 12, note = "Lunch")
        }
    }
}
