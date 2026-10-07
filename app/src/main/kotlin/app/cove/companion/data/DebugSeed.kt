package app.cove.companion.data

import app.cove.companion.AppContainer
import app.cove.companion.core.newId
import app.cove.companion.core.toEpochMillis
import app.cove.companion.core.toLocalDate
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
import app.cove.companion.data.local.entity.JournalEntryEntity
import app.cove.companion.data.local.entity.TodoCategoryEntity
import app.cove.companion.data.local.entity.SyncConflictEntity
import app.cove.companion.data.local.entity.TodoEntity
import app.cove.companion.data.sync.RoomSyncStore
import app.cove.companion.data.sync.SyncTables
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Debug-only sample data matching the design frames, used to compare screens pixel by pixel. */
object DebugSeed {
    suspend fun load(c: AppContainer, dark: Boolean, evening: Boolean, plan: String? = null, moneyLogged: Boolean = false) {
        c.settings.update {
            it.copy(displayName = "Maya", onboarded = true, theme = if (dark) "dark" else "light", wakeMinutes = 6 * 60 + 30, spokenReplies = false)
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
        if (!evening) { // evening Today (frame 10) lists only the three done items
            c.todos.add("Dish soap", shopping.id)
            c.todos.add("Birthday card for Ana", shopping.id)
        }
        if (evening) open.forEach { c.todos.setDone(it.id, true) }

        c.plan.saveEvent(EventEntity(newId(), "Coffee with Jo", at(11), at(11, 45), "Café Ivy", "bring her book back"))
        c.plan.saveAlarm(AlarmEntity("alarm-wake", "Wake up", 6 * 60 + 30, 0b0011111))
        c.plan.saveAlarm(AlarmEntity("alarm-weekend", "Weekends", 8 * 60, 0b1100000))
        c.plan.saveAlarm(AlarmEntity("alarm-nap", "Nap", 14 * 60 + 15, 0, enabled = false))
        c.plan.saveAlarm(AlarmEntity("alarm-bed", "Bedtime", 22 * 60 + 30, 0b1111111, kind = "bedtime"))

        seedHabits(c, day, evening)
        seedJournal(c, day)

        seedMoney(c, day, moneyLogged)
        seedPlan(c, day, plan)
        seedTraining(c, day)
    }

    /**
     * Categories and this month's spending. Default matches frame 07 (Food ₹7,000, total ₹18,420.50);
     * [logged] matches frames 25/34/36 (Food ₹7,340 of ₹9,000, with today's and yesterday's rows).
     */
    /**
     * Debug (`--ez journalBlocks true`): journal entries in August 2026 that show every block layout. Open one with
     * `--es route journal/blocks-<name>`: `text` (the frame 08 entry), `mid` (text, photo, text), `start` (photo, photo,
     * text), `mixed` (text, photo, photo, text, voice, text), `legacy` (text plus two attachments, no markers),
     * `missing` (a marker whose media does not exist) and `long` (30 blocks). Photos are coloured gradients.
     */
    suspend fun seedJournalBlocks(c: AppContainer) {
        val month = LocalDate.of(2026, 8, 1)
        var hue = 0f
        suspend fun photo(entry: String, id: String): String {
            val file = java.io.File(c.journalFiles.photoBase(id).path + ".webp")
            val w = 900
            val h = if (id.hashCode() % 3 == 0) 1200 else 600
            val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val paint = android.graphics.Paint().apply {
                shader = android.graphics.LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    android.graphics.Color.HSVToColor(floatArrayOf(hue % 360, 0.45f, 0.95f)),
                    android.graphics.Color.HSVToColor(floatArrayOf((hue + 50) % 360, 0.55f, 0.65f)),
                    android.graphics.Shader.TileMode.CLAMP,
                )
            }
            hue += 67f
            android.graphics.Canvas(bitmap).drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSY, 80, it) }
            val thumb = c.journalFiles.thumb(id)
            app.cove.companion.data.media.ThumbnailMaker.make(file, thumb)
            c.journal.saveMedia(app.cove.companion.data.local.entity.JournalMediaEntity(id, entry, "photo", file.path, thumb.path, bytes = file.length()))
            return id
        }
        suspend fun voice(entry: String, id: String, seconds: Long): String {
            val file = c.journalFiles.voice(id).also { it.writeBytes(ByteArray(64)) }
            c.journal.saveMedia(app.cove.companion.data.local.entity.JournalMediaEntity(id, entry, "voice", file.path, durationMs = seconds * 1000, bytes = 64))
            return id
        }
        fun m(id: String) = app.cove.companion.feature.journal.blocks.JournalBodyCodec.marker(id)
        suspend fun entry(name: String, dayOfMonth: Int, title: String, mood: String, body: suspend (String) -> String) {
            val id = "blocks-$name"
            val date = month.withDayOfMonth(dayOfMonth)
            c.journal.save(JournalEntryEntity(id, date.toEpochDay(), title, body(id), mood, createdAt = LocalDateTime.of(date, LocalTime.of(21, 0)).toEpochMillis()))
        }
        entry("text", 3, "A slow Sunday", "calm") {
            "Slept in without the alarm. Made coffee and sat by the window for a while before doing anything at all.\n\nWalked to the market later. Bought too many tomatoes"
        }
        entry("mid", 5, "Market morning", "good") { e ->
            "Early walk to the market, the light was lovely.\n${m(photo(e, "bm-mid-1"))}\nCame home with far too many tomatoes."
        }
        entry("start", 7, "Garden shots", "calm") { e ->
            "${m(photo(e, "bm-start-1"))}\n${m(photo(e, "bm-start-2"))}\nThe garden after the rain."
        }
        entry("mixed", 9, "Saturday in pieces", "good") { e ->
            "Morning first.\n${m(photo(e, "bm-mixed-1"))}\n${m(photo(e, "bm-mixed-2"))}\nThen a note to myself.\n${m(voice(e, "bm-mixed-v1", 42))}\nAnd the walk home.\n${m(voice(e, "bm-mixed-v2", 8))}"
        }
        entry("legacy", 11, "Before blocks", "calm") { e ->
            photo(e, "bm-legacy-1"); photo(e, "bm-legacy-2"); voice(e, "bm-legacy-v1", 15)
            "An older entry: text first, then its attachments below, exactly as it was written."
        }
        entry("missing", 13, "Not synced yet", "tired") {
            "A photo that has not arrived on this phone yet.\n${m("bm-gone")}\nThe text around it is safe."
        }
        entry("long", 15, "A long day", "good") { e ->
            val lines = ArrayList<String>()
            for (i in 0 until 15) {
                lines += "Paragraph ${i + 1}. " + "Some more words to fill the line and wrap. ".repeat(3)
                lines += m(if (i % 5 == 4) voice(e, "bm-long-v$i", 20L + i) else photo(e, "bm-long-$i"))
            }
            lines.joinToString("\n") + "\nThe end."
        }
    }

    /** Debug: [n] to-dos, events, expenses, journal entries and alarms to check that long lists scroll smoothly. */
    suspend fun seedBulk(c: AppContainer, n: Int) {
        val day = c.clock.now().let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
        val cat = c.todos.categories.first().firstOrNull()?.id
        repeat(n) { i ->
            c.todos.add("Bulk to-do ${i + 1}", cat, if (i % 3 == 0) LocalDateTime.of(day, LocalTime.of(7 + i % 15, i % 60)).toEpochMillis() else null)
            c.plan.saveEvent(EventEntity(newId(), "Bulk event ${i + 1}", LocalDateTime.of(day, LocalTime.of(i % 24, (i * 7) % 60)).toEpochMillis(), null))
            c.money.save(ExpenseEntity(newId(), (100 + i) * 100L, categoryId = "cat-food", note = "Bulk expense ${i + 1}", spentAt = LocalDateTime.of(day.minusDays((i % 20).toLong()), LocalTime.of(9, i % 60)).toEpochMillis()))
            val date = day.minusDays(i.toLong() + 40)
            c.journal.save(JournalEntryEntity("bulk-journal-$i", date.toEpochDay(), "Bulk entry ${i + 1}", "Body of entry ${i + 1}.", "calm", createdAt = LocalDateTime.of(date, LocalTime.of(21, 0)).toEpochMillis()))
        }
        repeat(minOf(n, 60)) { i -> c.plan.saveAlarm(AlarmEntity("bulk-alarm-$i", "Bulk alarm ${i + 1}", (i * 17) % 1440, 0b0011111, enabled = false)) }
    }

    /** A weekly plan with today as Push day and four weeks of history (see `docs/TRAINING.md`). */
    suspend fun seedTraining(c: AppContainer, day: LocalDate) = seedTrainingData(c, day)

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

    /** Frame 24: Read, Walk, Vitamins (not on Today), Stretch with the last six days of history; today as on the Today frame. */
    private suspend fun seedHabits(c: AppContainer, day: LocalDate, evening: Boolean) {
        val past = listOf(
            "Read 10 pages" to "111101", "Walk" to "101011", "Vitamins" to "111111", "Stretch" to "010001",
        )
        val habits = past.mapIndexed { i, (n, _) -> HabitEntity(newId(), n, sort = i, showOnToday = n != "Vitamins") }
        habits.forEach { c.habits.save(it) }
        habits.forEachIndexed { i, h -> past[i].second.forEachIndexed { d, ch -> if (ch == '1') c.habits.toggle(h.id, day.minusDays(6L - d)) } }
        if (!evening) habits.filter { it.name == "Read 10 pages" || it.name == "Walk" }.forEach { c.habits.toggle(it.id, day) }
    }

    /** Frames 08 and 26: 14 September entries (27th "Walk in the park", 25th "Finally finished the book") and "A slow Sunday" on 4 October. Entries after [day] are skipped. */
    private suspend fun seedJournal(c: AppContainer, day: LocalDate) {
        val sept = listOf(2, 4, 5, 7, 9, 10, 12, 14, 15, 18, 20, 23, 25, 27)
        val titles = mapOf(27 to ("Walk in the park" to "calm"), 25 to ("Finally finished the book" to "good"))
        fun save(date: LocalDate, title: String, mood: String, body: String) = JournalEntryEntity(
            "seed-journal-$date", date.toEpochDay(), title, body, mood, createdAt = LocalDateTime.of(date, LocalTime.of(21, 0)).toEpochMillis(),
        )
        sept.map { LocalDate.of(2026, 9, it) }.filter { it <= day }.forEach {
            val (t, m) = titles[it.dayOfMonth] ?: ("A quiet day" to "calm")
            c.journal.save(save(it, t, m, "Notes from the ${it.dayOfMonth}th."))
        }
        val sunday = LocalDate.of(2026, 10, 4)
        if (sunday <= day) {
            c.journal.save(
                save(
                    sunday, "A slow Sunday", "calm",
                    "Slept in without the alarm. Made coffee and sat by the window for a while before doing anything at all.\n\nWalked to the market later. Bought too many tomatoes",
                ),
            )
        }
    }

    /**
     * Frame 31: "Call mum" due 6:00 pm here (edited 8:12 am) against 7:30 pm on a tablet (edited 7:50 am).
     * Needs [load] to have created the to-do.
     */
    suspend fun seedConflict(c: AppContainer) {
        val todo = c.todos.todos.first().firstOrNull { it.title == "Call mum" } ?: return
        val table = SyncTables.find("todos") ?: return
        val day = c.clock.now().toLocalDate()
        fun at(h: Int, m: Int) = LocalDateTime.of(day, LocalTime.of(h, m)).toEpochMillis()
        val local = RoomSyncStore(c.database).read(table, listOf(todo.id)).getValue(todo.id)
        val localJson = JsonObject(local + ("updated_at" to JsonPrimitive(at(8, 12))))
        val remote = JsonObject(
            local + mapOf(
                "due_at" to JsonPrimitive(at(19, 30)), "updated_at" to JsonPrimitive(at(7, 50)),
                "device_id" to JsonPrimitive("tablet"), "device_name" to JsonPrimitive("Tablet"),
            ),
        )
        c.database.sync().saveConflict(
            SyncConflictEntity("todos", todo.id, localJson.toString(), remote.toString(), "Tablet", at(8, 12), at(7, 50), at(8, 12)),
        )
    }

    /**
     * Switches Drive to the folder-backed fake, adds a journal entry with a generated pending photo and, when [run],
     * uploads it and writes a backup (look under `files/drive-fake/Cove`).
     */
    suspend fun seedDrive(c: AppContainer, run: Boolean) {
        c.driveKit.useFake()
        val entry = c.journal.newEntry(c.clock.now().toLocalDate()).copy(title = "Drive test", body = "A seeded photo waiting to upload.")
        c.journal.save(entry)
        val id = newId()
        val file = java.io.File(c.journalFiles.photoBase(id).path + ".webp")
        val bitmap = android.graphics.Bitmap.createBitmap(600, 400, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(120, 160, 140))
        }
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSY, 80, it) }
        val thumb = c.journalFiles.thumb(id)
        app.cove.companion.data.media.ThumbnailMaker.make(file, thumb)
        c.journal.saveMedia(
            app.cove.companion.data.local.entity.JournalMediaEntity(id, entry.id, "photo", file.path, thumb.path, bytes = file.length()),
        )
        if (run) {
            c.driveKit.uploader().run()
            c.driveKit.backUpNow()
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

    /** `--ez reviewSeed true`: five recent expenses in Other (with notes the rules know) and a few misfiled ones for Review. */
    suspend fun seedReview(c: AppContainer) {
        val day = c.clock.now().toLocalDate()
        fun at(d: Long, h: Int) = LocalDateTime.of(day.minusDays(d), LocalTime.of(h, 0)).toEpochMillis()
        val other = "cat-other"
        suspend fun add(cat: String, note: String, paise: Long, daysAgo: Long, h: Int = 12) =
            c.money.save(ExpenseEntity(newId(), paise, categoryId = cat, note = note, spentAt = at(daysAgo, h)))
        add(other, "Blinkit", 45_000, 1)
        add(other, "uber to office", 21_000, 2)
        add(other, "Netflix", 64_900, 3)
        add(other, "Pharmacy", 38_000, 4)
        add(other, "Birthday cake for Ana", 80_000, 5)
        add("cat-fun", "Swiggy dinner", 52_000, 2, 20)
        add("cat-food", "Uber to airport", 90_000, 6, 7)
    }
}
