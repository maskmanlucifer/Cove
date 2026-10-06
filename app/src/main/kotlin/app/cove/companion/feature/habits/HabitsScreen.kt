package app.cove.companion.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.core.appViewModel
import app.cove.companion.design.Cove
import app.cove.companion.design.hueFor
import app.cove.companion.design.components.bloom
import app.cove.companion.design.components.easeIn
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveDock
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.EmptyState
import app.cove.companion.design.illustrations.Scene
import app.cove.companion.design.components.DockClearance
import app.cove.companion.design.components.Tab
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.navigation.DebugLaunch
import app.cove.companion.navigation.Nav
import app.cove.companion.navigation.Routes

/** Habits: each habit as a row with its last 7 days; tap today's dot to tick it. Nothing ever resets. */
@Composable
fun HabitsScreen(nav: Nav) {
    val vm = appViewModel { HabitsViewModel(it) }
    val s by vm.state.collectAsState()
    val c = Cove.colors
    CoveScreen {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .coveTopInset()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = DockClearance),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp, 44.dp).pressable(nav.back, role = Role.Button).semantics { contentDescription = "Back" },
                        contentAlignment = Alignment.CenterStart,
                    ) { CoveIcon(CoveIcons.ChevronLeft, c.muted, size = 22.dp) }
                    CoveText("Habits", style = CoveType.Title, modifier = Modifier.semantics { heading() })
                }
                Box(
                    Modifier.size(44.dp).background(c.card, CoveShapes.Circle).pressable({ nav.go(Routes.HabitNew) }, role = Role.Button)
                        .semantics { contentDescription = "Add habit" },
                    contentAlignment = Alignment.Center,
                ) { CoveIcon(CoveIcons.Plus, c.ink, size = 18.dp) }
            }
            CoveText(
                "No streaks to break. Just how often, lately.",
                style = CoveType.Body.copy(fontSize = 15.sp, lineHeight = 22.sp),
                color = c.muted,
            )
            if (s.loaded && s.rows.isEmpty()) {
                EmptyState(Scene.Habits, "No habits yet.", "Start with one small thing. Tap the plus when you are ready.")
            }
            s.rows.forEach { row ->
                HabitCard(row, onOpen = { nav.go(Routes.habitEdit(row.id)) }, onToggleToday = { vm.toggleToday(row.id) })
            }
        }
        CoveDock(
            Tab.Me,
            onSelect = { tab ->
                if (tab == Tab.Me) nav.back() else {
                    DebugLaunch.tab = tab.name
                    nav.home()
                }
            },
            onVoice = { nav.go(Routes.Voice) },
        )
    }
}

@Composable
private fun HabitCard(row: HabitRow, onOpen: () -> Unit, onToggleToday: () -> Unit) {
    val c = Cove.colors
    val tint = c.hueFor(row.id).tint
    Row(
        Modifier.fillMaxWidth().easeIn().background(tint, RoundedCorner24).padding(start = 20.dp, top = 18.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f).pressable(onOpen, onClickLabel = "Edit habit", role = Role.Button), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CoveText(row.name, style = CoveType.BodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            CoveText(row.summary, style = CoveType.Meta, color = c.muted)
        }
        Row(Modifier.height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            row.days.forEachIndexed { i, on ->
                val today = i == row.days.lastIndex
                if (today) {
                    Box(
                        Modifier.width(32.dp).fillMaxHeight().pressable(onToggleToday, role = Role.Checkbox)
                            .semantics {
                                contentDescription = "${row.name} today"
                                stateDescription = if (on) "Done" else "Not done"
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Dot(on, today = true, surface = tint)
                    }
                } else {
                    Box(Modifier.semantics { contentDescription = "${row.name}, ${row.days.lastIndex - i} days ago: ${if (on) "done" else "not done"}" }) { Dot(on, today = false, surface = tint) }
                }
            }
        }
    }
}

/** 12 dp dot: leaf green when done, a ring when not; today's dot gets a faint outer ring. */
@Composable
private fun Dot(on: Boolean, today: Boolean, surface: Color) {
    val c = Cove.colors
    val ring = c.ring
    Box(
        Modifier
            .size(12.dp)
            .let { if (today) it.bloom(on, c.accent) else it }
            .let { m ->
                if (!today) m else m.drawBehind {
                    drawCircle(surface, radius = 8.dp.toPx(), center = Offset(size.width / 2, size.height / 2), style = Stroke(2.dp.toPx()))
                }
            }
            .background(if (on) c.accent else Color.Transparent, CoveShapes.Circle)
            .let { if (on) it else it.border(1.5.dp, ring, CoveShapes.Circle) },
    )
}

private val RoundedCorner24 = RoundedCornerShape(24.dp)
