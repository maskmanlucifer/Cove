package app.cove.companion.data

import app.cove.companion.AppContainer
import app.cove.companion.core.newId
import app.cove.companion.core.toEpochMillis
import app.cove.companion.data.local.entity.AlarmEntity
import app.cove.companion.data.local.entity.BriefEntity
import app.cove.companion.data.local.entity.DecisionEntity
import app.cove.companion.feature.brief.BriefCodec
import app.cove.companion.feature.brief.BriefSegment
import app.cove.companion.feature.suggest.SuggestDebug
import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.data.local.entity.ExpenseCategoryEntity
import app.cove.companion.data.local.entity.ExpenseEntity
import app.cove.companion.data.local.entity.HabitEntity
import app.cove.companion.data.local.entity.TodoCategoryEntity
import app.cove.companion.data.local.entity.TodoEntity
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Debug-only sample data matching the design frames, used to compare screens pixel by pixel. */
object DebugSeed {
    suspend fun load(c: AppContainer, dark: Boolean, evening: Boolean, plan: String? = null, moneyLogged: Boolean = false) {
        c.settings.update {
            it.copy(displayName = "Maya", onboarded = true, theme = if (dark) "dark" else "light", wakeMinutes = 6 * 60 + 30)
        }
        seedBrief(c)
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
        seedPlan(c, day, plan)
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

    /**
     * Plan frames: a daily Vitamins event and the 2-4 pm Deep work block for the schedule, and for
     * [variant] `todos` | `empty` | `drag` a replacement set of to-dos matching frames 05, 23 and 21.
     */
    private suspend fun seedPlan(c: AppContainer, day: LocalDate, variant: String?) {
        fun at(h: Int, m: Int = 0, d: LocalDate = day) = LocalDateTime.of(d, LocalTime.of(h, m)).toEpochMillis()
        c.plan.saveEvent(EventEntity(newId(), "Vitamins", at(8), null, repeat = "daily"))
        c.plan.saveEvent(EventEntity(newId(), "Deep work", at(14), at(16), notes = "notifications held"))
        if (variant == null) return

        c.todos.todos.first().forEach { c.todos.delete(it.id) }
        c.todos.categories.first().forEach { c.todos.saveCategory(it.copy(sort = if (it.name == "Home") 1 else if (it.name == "Shopping") 0 else it.sort)) }
        val cats = c.todos.categories.first().associate { it.name to it.id }
        val saturday = day.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
        suspend fun todo(cat: String, title: String, sort: Int, done: Boolean = false, due: Long? = null, daysAgo: Long = 0) {
            val entity = TodoEntity(newId(), cats.getValue(cat), title, due, remind = due != null, done = done, sort = sort)
            c.todos.save(if (done) entity.copy(doneAt = at(9, 0, day.minusDays(daysAgo))) else entity)
        }
        when (variant) {
            "drag" -> {
                todo("Shopping", "Milk", 0, done = true)
                listOf("Batteries", "Light bulbs", "Birthday card for Ana", "Coffee beans", "Dish soap").forEachIndexed { i, t -> todo("Shopping", t, i + 1) }
                todo("Shopping", "Tea", 9, done = true, daysAgo = 2)
                todo("Shopping", "Bin bags", 10, done = true, daysAgo = 3)
                todo("Home", "Water the plants", 0)
                todo("Home", "Fix the shelf", 1)
            }
            else -> {
                if (variant == "todos") {
                    todo("Shopping", "Milk", 0)
                    todo("Shopping", "Batteries", 1)
                    todo("Shopping", "Dish soap", 2, done = true)
                    todo("Shopping", "Birthday card for Ana", 3, due = at(10, 0, saturday))
                } else {
                    listOf("Milk", "Batteries", "Dish soap", "Birthday card for Ana", "Coffee beans").forEachIndexed { i, t -> todo("Shopping", t, i) }
                }
                todo("Home", "Water the plants", 0)
                todo("Home", "Fix the shelf", 1)
                todo("Errands", "Return the parcel", 0)
            }
        }
    }

    /**
     * Frame 16's cached brief, and for the debug late-night trigger (`--es suggest late-night`) the 7:00 "Run" alarm
     * that frame 17's suggestion moves.
     */
    suspend fun seedBrief(c: AppContainer) {
        val day = c.clock.now().let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
        val segments = listOf(
            BriefSegment("Weather · mild, 24°", "Good morning, Maya. It is mild and clear, 24 degrees now, up to 27 later."),
            BriefSegment("Your day", "Coffee with Jo at eleven. Leave by 10:45, it’s a short walk."),
            BriefSegment("Money · ₹11,580 left", "You have ₹11,580 left this month. No rush."),
            BriefSegment("One thing to read", "A slow start is still a start. Today only needs a few things from you."),
        )
        c.assistant.saveBrief(BriefEntity(day.toEpochDay(), BriefCodec.encode(segments), c.clock.now(), 124))
        if (SuggestDebug.lastUse != null && c.database.alarms().get("seed-run") == null) {
            c.plan.saveAlarm(AlarmEntity("seed-run", "Run", 7 * 60, 0b1111111, kind = "custom"))
            listOf(3L, 6L).forEach { ago ->
                c.assistant.saveDecision(
                    DecisionEntity("seed-past-$ago", "late_night_shift", "Late night?", "", "[]", "confirmed", c.clock.now() - ago * 86_400_000L),
                )
            }
        }
    }
}
