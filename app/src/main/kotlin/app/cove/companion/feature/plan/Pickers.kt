package app.cove.companion.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.pressable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/** Month grid (Monday first) that highlights [selected] and reports a tapped day. */
@Composable
fun DatePanel(selected: LocalDate, onPick: (LocalDate) -> Unit) {
    val c = Cove.colors
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            CoveText(month.format(monthTitle), Modifier.weight(1f), style = CoveType.BodyMedium)
            Box(Modifier.size(44.dp).pressable({ month = month.minusMonths(1) }, role = Role.Button).semantics { contentDescription = "Previous month" }, contentAlignment = Alignment.Center) {
                CoveIcon(CoveIcons.ChevronLeft, c.ink, size = 20.dp)
            }
            Box(Modifier.size(44.dp).pressable({ month = month.plusMonths(1) }, role = Role.Button).semantics { contentDescription = "Next month" }, contentAlignment = Alignment.Center) {
                CoveIcon(CoveIcons.ChevronRight, c.ink, size = 20.dp)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach {
                Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.Center) {
                    CoveText(it.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), style = CoveType.Meta, color = c.tail)
                }
            }
        }
        val lead = month.atDay(1).dayOfWeek.value - 1
        val weeks = (lead + month.lengthOfMonth() + 6) / 7
        repeat(weeks) { w ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { d ->
                    val day = w * 7 + d - lead + 1
                    Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            val on = date == selected
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .background(if (on) c.ink else Color.Transparent, CoveShapes.Circle)
                                    .pressable({ onPick(date) }),
                                contentAlignment = Alignment.Center,
                            ) {
                                CoveText(day.toString(), style = CoveType.Body, color = if (on) c.onInk else c.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Hour / minute / am-pm steppers for a time given as minutes since midnight. Minutes move in steps of 5. */
@Composable
fun TimePanel(minutes: Int, onChange: (Int) -> Unit) {
    val c = Cove.colors
    val h24 = minutes / 60
    val m = minutes % 60
    val h12 = if (h24 % 12 == 0) 12 else h24 % 12
    val pm = h24 >= 12
    fun set(hour24: Int, minute: Int) = onChange(Math.floorMod(hour24, 24) * 60 + Math.floorMod(minute, 60))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stepper(h12.toString(), onUp = { set(h24 + 1, m) }, onDown = { set(h24 - 1, m) })
        CoveText(":", style = CoveType.Hero, color = c.tail)
        Stepper(m.toString().padStart(2, '0'), onUp = { set(h24, (m / 5 + 1) * 5) }, onDown = { set(h24, ((m + 4) / 5 - 1) * 5) })
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("am" to false, "pm" to true).forEach { (label, isPm) ->
                val on = pm == isPm
                Box(
                    Modifier
                        .size(56.dp, 40.dp)
                        .background(if (on) c.ink else c.canvas, CoveShapes.Pill)
                        .pressable({ if (!on) set(if (isPm) h24 + 12 else h24 - 12, m) }),
                    contentAlignment = Alignment.Center,
                ) {
                    CoveText(label, style = CoveType.Button.copy(fontWeight = FontWeight.Medium), color = if (on) c.onInk else c.muted)
                }
            }
        }
    }
}

@Composable
private fun Stepper(value: String, onUp: () -> Unit, onDown: () -> Unit) {
    val c = Cove.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp, 44.dp).pressable(onUp, role = Role.Button).semantics { contentDescription = "Increase" }, contentAlignment = Alignment.Center) {
            CoveIcon(PlanIcons.ChevronUp, c.muted, size = 24.dp)
        }
        Box(Modifier.width(72.dp).height(56.dp), contentAlignment = Alignment.Center) {
            CoveText(value, style = CoveType.Hero)
        }
        Box(Modifier.size(56.dp, 44.dp).pressable(onDown, role = Role.Button).semantics { contentDescription = "Decrease" }, contentAlignment = Alignment.Center) {
            CoveIcon(CoveIcons.ChevronDown, c.muted, size = 24.dp)
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)

/** "Thu, 8 Oct". */
fun LocalDate.dayLabel(): String = format(dayFormat)
