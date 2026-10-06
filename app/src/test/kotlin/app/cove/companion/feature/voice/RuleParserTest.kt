package app.cove.companion.feature.voice

import app.cove.companion.core.Clock
import app.cove.companion.core.toEpochMillis
import app.cove.companion.ai.provider.rules.RuleParser
import app.cove.companion.ai.model.TodoDraft
import app.cove.companion.ai.model.VoiceIntent
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleParserTest {
    /** Tuesday 6 October 2026, 10:35. */
    private val now = LocalDateTime.of(2026, 10, 6, 10, 35)
    private val parser = RuleParser(Clock { now.toEpochMillis() })

    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, day, h, m).toEpochMillis()

    private val habits = listOf("Read 10 pages", "Walk", "Stretch")

    private val cases: List<Pair<String, List<VoiceIntent>>> = listOf(
        "set an alarm for six thirty tomorrow" to listOf(VoiceIntent.SetAlarm(390)),
        "set an alarm for 6:30" to listOf(VoiceIntent.SetAlarm(390)),
        "wake me up at 7" to listOf(VoiceIntent.SetAlarm(420)),
        "set an alarm for 6 pm" to listOf(VoiceIntent.SetAlarm(18 * 60)),
        "alarm at seven in the evening" to listOf(VoiceIntent.SetAlarm(19 * 60)),
        "Hey Cove, set an alarm for quarter to seven" to listOf(VoiceIntent.SetAlarm(6 * 60 + 45)),
        "set an alarm for half past five" to listOf(VoiceIntent.SetAlarm(5 * 60 + 30)),
        "set an alarm for weekdays at 6:45 am called gym" to listOf(VoiceIntent.SetAlarm(405, "Gym", 0b0011111)),
        "change my alarm to seven thirty" to listOf(VoiceIntent.ChangeAlarm(450)),
        "move the bedtime alarm to 10 pm" to listOf(VoiceIntent.ChangeAlarm(22 * 60, "bedtime")),
        "remind me to call mum at 6" to listOf(VoiceIntent.AddReminder("Call mum", at(6, 18))),
        "remind me to buy milk tomorrow at 9" to listOf(VoiceIntent.AddReminder("Buy milk", at(7, 9))),
        "remind me at 4 pm" to listOf(VoiceIntent.AddReminder("Reminder", at(6, 16))),
        "remind me in 20 minutes to stretch" to listOf(VoiceIntent.AddReminder("Stretch", at(6, 10, 55))),
        "remind me to take the pills" to listOf(VoiceIntent.AddReminder("Take the pills", null)),
        "add milk, batteries and fix the bathroom tap" to listOf(
            VoiceIntent.AddTodos(
                listOf(TodoDraft("Milk", "Shopping"), TodoDraft("Batteries", "Shopping"), TodoDraft("Fix the bathroom tap", "Home")),
            ),
        ),
        "buy eggs and bread" to listOf(VoiceIntent.AddTodos(listOf(TodoDraft("Eggs", "Shopping"), TodoDraft("Bread", "Shopping")))),
        "add milk to my shopping list" to listOf(VoiceIntent.AddTodos(listOf(TodoDraft("Milk", "Shopping")))),
        "I need to call the dentist tomorrow" to listOf(VoiceIntent.AddTodos(listOf(TodoDraft("Call the dentist", "Personal", at(7, 9))))),
        "spent 340 on lunch at Café Ivy" to listOf(VoiceIntent.LogExpense(34000, "Food", null, "Lunch · Café Ivy")),
        "log 240 lunch" to listOf(VoiceIntent.LogExpense(24000, "Food", null, "Lunch")),
        "spent three hundred forty rupees on auto using cash" to listOf(VoiceIntent.LogExpense(34000, "Transport", "Cash", "Auto")),
        "paid ₹1,250 for the electricity bill via UPI" to listOf(VoiceIntent.LogExpense(125000, "Home", "UPI", "The electricity bill")),
        "spent 2k on a movie" to listOf(VoiceIntent.LogExpense(200000, "Fun", null, "A movie")),
        "received 5000 salary" to listOf(VoiceIntent.LogExpense(500000, null, null, "Salary", received = true)),
        "undo that" to listOf(VoiceIntent.UndoLast),
        "never mind" to listOf(VoiceIntent.UndoLast),
        "what's next" to listOf(VoiceIntent.QueryNext),
        "journal: today was a good day and I felt calm" to listOf(VoiceIntent.JournalNote("Today was a good day and I felt calm")),
        "I did my walk" to listOf(VoiceIntent.LogHabit("Walk")),
        "mark stretch as done" to listOf(VoiceIntent.LogHabit("Stretch")),
        "set an alarm for 6:30 and remind me to call mum at 6" to listOf(
            VoiceIntent.SetAlarm(390), VoiceIntent.AddReminder("Call mum", at(6, 18)),
        ),
        "add milk and then set an alarm for 7" to listOf(
            VoiceIntent.AddTodos(listOf(TodoDraft("Milk", "Shopping"))), VoiceIntent.SetAlarm(420),
        ),
    )

    @Test
    fun understandsCommonPhrasings() {
        val failures = cases.mapNotNull { (text, expected) ->
            parser.parse(text, habits).takeIf { it != expected }?.let { "$text\n  expected $expected\n  actual   $it" }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun ignoresWhatItCannotUse() {
        listOf("", "blah blah", "set an alarm", "remind", "add").forEach { assertEquals(it, emptyList<VoiceIntent>(), parser.parse(it, habits)) }
    }

    @Test
    fun guessesWhenOnlyATimeWasHeard() {
        val guesses = parser.guesses("…mind me… the… at four…")
        assertEquals(listOf(VoiceIntent.AddReminder("Reminder", at(6, 16)), VoiceIntent.SetAlarm(240)), guesses)
        assertTrue(parser.guesses("mumble").isEmpty())
    }

    @Test
    fun expenseAtAnEarlierTimeToday() {
        val e = parser.parse("spent 340 on lunch at 8:15 am").single() as VoiceIntent.LogExpense
        assertEquals(at(6, 8, 15), e.at)
    }
}
