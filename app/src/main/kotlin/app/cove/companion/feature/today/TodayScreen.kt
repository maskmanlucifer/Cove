package app.cove.companion.feature.today

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.cove.companion.container
import app.cove.companion.data.local.entity.EventEntity
import app.cove.companion.feature.plan.EventEditSheet
import app.cove.companion.feature.plan.PlanUndoBar
import app.cove.companion.feature.voice.TtsSpeaker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.cove.companion.core.DayPhase
import app.cove.companion.core.appViewModel
import app.cove.companion.core.clockText
import app.cove.companion.core.inText
import app.cove.companion.core.longLabel
import app.cove.companion.core.rupees
import app.cove.companion.core.shortTime
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.BalancedText
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CheckCircle
import app.cove.companion.design.components.Chip
import app.cove.companion.design.components.CoveCard
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.DockClearance
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.OfflineNotice
import app.cove.companion.design.components.rememberIsOffline
import app.cove.companion.feature.suggest.SuggestionCard
import app.cove.companion.feature.suggest.SuggestionViewModel
import app.cove.companion.feature.suggest.WhySheet
import androidx.compose.ui.unit.sp
import app.cove.companion.feature.nudges.ReminderPlanner
import app.cove.companion.feature.permissions.PermissionGuides
import app.cove.companion.feature.permissions.PermissionIssue
import app.cove.companion.feature.permissions.PermissionNeeds
import app.cove.companion.feature.permissions.rememberPermissionIssues
import app.cove.companion.navigation.Nav
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine

private val numberWords = listOf("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")

/** Today tab: greeting, the single next item, a few to-dos and two quick numbers. */
@Composable
fun TodayScreen(nav: Nav) {
    val vm = appViewModel { TodayViewModel(it) }
    val state by vm.state.collectAsState()
    val evening = state.phase == DayPhase.Evening
    val c = Cove.colors
    val context = LocalContext.current
    val suggest = appViewModel { SuggestionViewModel(it) }
    val sug by suggest.state.collectAsState()
    val offline = rememberIsOffline()
    val suggestion = sug.decision?.takeIf { !offline }
    val remindersExist by produceState(false) {
        val c = context.container
        combine(c.todos.todos, c.plan.upcomingEvents(30)) { todos, events -> ReminderPlanner.plan(todos, events, c.clock.now()).isNotEmpty() }
            .catch { emit(false) }.collect { value = it }
    }
    val permissionIssues = rememberPermissionIssues(PermissionNeeds(reminders = remindersExist))
        .filter { it == PermissionIssue.Notifications }
    DisposableEffect(Unit) { onDispose { vm.markNewSeen() } }
    if (state.oneThingMode) {
        OneThingContent(nav)
        return
    }

    val scope = rememberCoroutineScope()
    val container = context.container
    val speaker = remember { TtsSpeaker(context) { container.settings.settings.first().spokenReplies } }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    var editing by remember { mutableStateOf<EventEntity?>(null) }
    var undo by remember { mutableStateOf<EventEntity?>(null) }
    val actions = NextActions(
        move = { editing = it },
        startWindDown = { scope.launch { vm.startWindDown().let { speaker.speak(windDownLine(it)) } } },
        later = vm::snoozeCard,
    )
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .coveTopInset()
            .padding(start = 24.dp, end = 24.dp, top = if (evening || suggestion != null) 20.dp else 12.dp, bottom = DockClearance),
        verticalArrangement = Arrangement.spacedBy(if (suggestion != null || offline) 24.dp else if (evening) 28.dp else 20.dp),
    ) {
        if (offline) OfflineNotice()
        PermissionGuides(permissionIssues, alarmsInUse = false)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!offline) CoveText(state.date.longLabel(), style = CoveType.Meta, color = c.muted)
            BalancedText(greeting(state), suggestion?.let { " ${it.title}" } ?: headline(state).let { h -> if (offline) h.replace(Regex(", nothing before .*\\.$"), ".") else h }, CoveType.Title)
        }
        if (suggestion != null) SuggestionCard(suggestion, sug.detail, suggest)
        else if (!offline) state.next?.let { NextCard(it, actions) }
        if (offline) OfflineTodos(state.todos, sug.pendingTodoIds, vm::toggle)
        else Column {
            state.todos.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CheckCircle(row.done, onToggle = { vm.toggle(row.id, !row.done) })
                    CoveText(
                        row.title,
                        Modifier.weight(1f),
                        color = if (row.done) c.tail else c.ink,
                    )
                    if (row.isNew) CoveText("New", style = CoveType.MetaMedium, color = c.saved)
                    else if (!row.done) row.time?.let { CoveText(shortTime(it), style = CoveType.Meta, color = c.muted) }
                }
            }
        }
        if (suggestion != null) {
            CoveText("One suggestion at a time. Ignored ones disappear at noon.", style = CoveType.Meta, color = c.muted)
        } else if (offline) {
            CoveText(
                "Voice, alarms and your journal work offline. Only weather and the brief's one thing to read wait for a connection.",
                style = CoveType.Meta.copy(lineHeight = 21.sp), color = c.muted,
            )
        } else if (evening) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CoveText("How was today?", style = CoveType.Meta, color = c.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Calm", "Good", "Tired", "Low").forEach { Chip(it, onClick = { vm.setMood(it.lowercase()) }) }
                }
            }
        } else {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Stat("Spent today") { CoveText(rupees(state.spentTodayPaise), style = CoveType.Value) }
                Stat("Habits") {
                    CoveText("${state.habitsDone}", " of ${state.habitsTotal}", style = CoveType.Value)
                }
            }
        }
    }
    undo?.let { gone ->
        LaunchedEffect(gone.id) {
            delay(6000)
            undo = null
        }
        PlanUndoBar("Deleted “${gone.title}”", { vm.restoreEvent(gone); undo = null }, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 112.dp))
    }
    }
    editing?.let { event ->
        EventEditSheet(
            event, container.clock.now(),
            onSave = vm::saveEvent,
            onDelete = { vm.deleteEvent(it); undo = it },
            onDismiss = { editing = null },
        )
    }
    if (suggestion != null && sug.whyOpen) WhySheet(sug.detail, suggest)
}

@Composable
private fun Stat(label: String, value: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CoveText(label, style = CoveType.Meta, color = Cove.colors.muted)
        value()
    }
}

/** What the Next card's buttons do: open the event editor, start the wind-down, or hide the card for a while. */
private class NextActions(val move: (EventEntity) -> Unit, val startWindDown: () -> Unit, val later: () -> Unit)

@Composable
private fun NextCard(next: NextItem, actions: NextActions) {
    val c = Cove.colors
    val context = LocalContext.current
    val time = clockText(next.minutes)
    CoveCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CoveText("Next", style = CoveType.Meta, color = c.muted)
                CoveText(inText(next.inMinutes), style = CoveType.Meta, color = c.muted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CoveText(time.digits, time.suffix, style = CoveType.Hero)
                CoveText(next.title, style = CoveType.Heading)
                next.detail?.let { CoveText(it, style = CoveType.Button.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = c.muted) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (next.windDown) {
                    PillButton("Start now", actions.startWindDown)
                    PillButton("Later", actions.later, kind = ButtonKind.Secondary, horizontalPadding = 16.dp)
                } else {
                    PillButton("Directions", {
                        next.event?.place?.let {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(it))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    })
                    PillButton("Move", { next.event?.let(actions.move) }, kind = ButtonKind.Secondary, container = c.canvas)
                }
            }
        }
    }
}

private fun greeting(s: TodayState): String {
    val word = when (s.phase) {
        DayPhase.Morning -> "Morning"
        DayPhase.Afternoon -> "Afternoon"
        DayPhase.Evening -> "Evening"
    }
    return if (s.name.isBlank()) "$word." else "$word, ${s.name}."
}

private fun headline(s: TodayState): String {
    if (s.phase == DayPhase.Evening && s.doneCount > 0) {
        return " ${numberWords.getOrElse(s.doneCount) { s.doneCount.toString() }} done. That’s enough."
    }
    val count = s.todos.size
    val things = if (count == 1) "thing" else "things"
    val first = s.next?.event?.let { clockText(s.next.minutes) }
    val tail = if (first != null && s.next.minutes >= 180) ", nothing before ${hourWord(s.next.minutes / 60)}" else ""
    return " ${numberWords.getOrElse(count) { count.toString() }} $things today$tail."
}

private fun hourWord(h24: Int): String {
    val words = listOf("twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven")
    return words[h24 % 12]
}
