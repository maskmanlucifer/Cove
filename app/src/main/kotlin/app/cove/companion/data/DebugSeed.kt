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
    suspend fun load(c: AppContainer, dark: Boolean, evening: Boolean) {
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

        val cats = listOf("Food" to 9000, "Home" to 8000, "Transport" to 5000, "Fun" to 2000, "Other" to 6000)
            .mapIndexed { i, (n, b) -> ExpenseCategoryEntity(newId(), n, budgetPaise = b * 100L, sort = i) }
        cats.forEach { c.money.saveCategory(it) }
        suspend fun add(cat: String, paise: Long, d: LocalDate, hour: Int = 9) =
            c.money.save(ExpenseEntity(newId(), paise, categoryId = cats.first { it.name == cat }.id, note = cat, spentAt = at(hour, 0, d)))
        add("Food", 34_000, day)
        add("Transport", 50_000, day)
        val earlier = day.withDayOfMonth(1)
        add("Food", 666_000, earlier)
        add("Home", 442_000, earlier.plusDays(1))
        add("Transport", 245_000, earlier.plusDays(2))
        add("Fun", 221_000, earlier.plusDays(3))
        add("Other", 184_050, earlier.plusDays(4))
    }
}
