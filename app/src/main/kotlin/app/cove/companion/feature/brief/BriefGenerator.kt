package app.cove.companion.feature.brief

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import app.cove.companion.AppContainer
import app.cove.companion.core.Clock
import app.cove.companion.core.startOfDayMillis
import app.cove.companion.core.toLocalDate
import app.cove.companion.core.toLocalDateTime
import app.cove.companion.data.ai.AiGateway
import app.cove.companion.data.local.entity.BriefEntity
import app.cove.companion.feature.money.MoneyMath
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Gathers the day's facts (weather, events, to-dos, calendar, money, habits) and builds the script from templates.
 * Journal content is never read. The gateway only adds an optional short intro and "one thing to read".
 */
class BriefGenerator(
    private val c: AppContainer,
    private val context: Context,
    private val weather: WeatherClient,
    private val prefs: BriefPrefs,
    private val calendar: CalendarSource,
    private val gateway: AiGateway,
    private val clock: Clock,
) {
    /** Today's cached brief, generated first when none exists. */
    suspend fun today(): BriefEntity =
        c.assistant.brief(clock.now().toLocalDate()).first() ?: generate()

    /** Builds and stores the brief for today. */
    suspend fun generate(): BriefEntity {
        val day = clock.now().toLocalDate()
        val facts = collect(day)
        val segments = BriefTemplates.build(facts, day.dayOfYear)
        val brief = BriefEntity(day.toEpochDay(), BriefCodec.encode(segments), clock.now(), BriefTiming.totalSeconds(segments))
        c.assistant.saveBrief(brief)
        return brief
    }

    /** Today's facts with the optional generated lines applied. */
    suspend fun collect(day: LocalDate): BriefFacts {
        val settings = c.settings.settings.first()
        val start = day.startOfDayMillis()
        val end = day.plusDays(1).startOfDayMillis() - 1
        val roomItems = c.plan.eventsOn(day).first().map {
            val t = it.startAt.toLocalDateTime()
            DayItem(t.hour * 60 + t.minute, it.title, it.place)
        }
        val items = (roomItems + calendar.eventsOn(day)).distinctBy { it.minutes to it.title }
        val todos = c.todos.todos.first()
            .filter { !it.done && (it.dueAt == null || it.dueAt in start..end) }
            .sortedBy { it.dueAt ?: Long.MAX_VALUE }.map { it.title }
        val shown = c.habits.habits.first().filter { it.showOnToday }
        val logs = c.habits.logs(day, day).first()
        val cats = c.money.categories.first().filter { it.kind == "spending" }
        val monthStart = day.withDayOfMonth(1).startOfDayMillis()
        val spent = MoneyMath.spentOf(c.money.expenses(monthStart, end).first())
        val budget = cats.sumOf { it.budgetPaise }
        val forecast = weather.forecast(place())
        val base = BriefFacts(
            name = settings.displayName,
            weather = forecast,
            items = items,
            todoTitles = todos,
            moneyLeftPaise = if (budget > 0) budget - spent else null,
            habitsTotal = shown.size,
            habitsDone = shown.count { h -> logs.any { it.habitId == h.id } },
        )
        val generic = buildMap {
            put("part_of_day", "morning")
            forecast?.let { put("weather", "${it.description}, ${it.tempC} degrees") }
            put("events_today", items.size.toString())
            put("todos_open", todos.size.toString())
        }
        return base.copy(
            intro = gateway.briefLine("intro", generic + ("name" to settings.displayName)),
            thought = gateway.briefLine("thought", generic),
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun place(): Place {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val last = runCatching { lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time } }.getOrNull()
            if (last != null) return Place("here", last.latitude, last.longitude)
        }
        return prefs.defaultPlace()
    }
}
