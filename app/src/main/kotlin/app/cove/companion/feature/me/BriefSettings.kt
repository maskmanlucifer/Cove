package app.cove.companion.feature.me

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.cove.companion.container
import app.cove.companion.core.PermissionStep
import app.cove.companion.core.Permissions
import app.cove.companion.core.permissionStep
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.PillButton
import app.cove.companion.feature.brief.BriefPrefs
import app.cove.companion.feature.plan.PlanSheet
import app.cove.companion.feature.plan.TitleField
import kotlinx.coroutines.launch

/** Value shown on the calendar row for the current permission state. */
internal fun calendarRowValue(granted: Boolean) = if (granted) "Allowed" else "Not allowed"

/** Caption under the city field after a lookup attempt. */
internal fun cityLookupMessage(found: String?, attempted: Boolean) = when {
    found != null -> "Weather for $found."
    attempted -> "Couldn’t find that place. Check the spelling and your connection."
    else -> "Used for the weather in your morning brief when your location isn’t available."
}

private tailrec fun Context.activity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** The two brief rows (default city, calendar access) with their sheets; drop inside the "Day" group after "Morning brief". */
@Composable
fun BriefSettingsRows() {
    val context = LocalContext.current
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var granted by remember { mutableStateOf(Permissions.calendarGranted(context)) }
    var city by remember { mutableStateOf("") }
    val tick = remember { mutableIntStateOf(0) }
    LaunchedEffect(tick.intValue) { city = BriefPrefs(context).defaultPlace().name }

    RowDivider()
    SettingsRow("Brief city", value = city, onClick = { sheet = CITY })
    RowDivider()
    SettingsRow("Calendar in brief", value = calendarRowValue(granted), onClick = { granted = Permissions.calendarGranted(context); sheet = CALENDAR })

    when (sheet) {
        CITY -> CitySheet(city, { sheet = null; tick.intValue++ })
        CALENDAR -> CalendarSheet(granted, { granted = it }, { sheet = null })
    }
}

@Composable
private fun CitySheet(current: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf(current) }
    var found by remember { mutableStateOf<String?>(null) }
    var attempted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    PlanSheet(onDismiss, gap = 16) { close ->
        SheetHeading("Brief city")
        TitleField(text, { text = it; attempted = false; found = null }, "City", onDone = {}, autofocus = true)
        SheetCaption(cityLookupMessage(found, attempted))
        PillButton(if (busy) "Looking up…" else "Save", {
            if (text.isBlank() || busy) return@PillButton
            busy = true
            scope.launch {
                val place = context.container.geocoder.find(text)
                if (place != null) {
                    BriefPrefs(context).setDefaultPlace(place)
                    found = place.name
                    close()
                } else attempted = true
                busy = false
            }
        }, Modifier.fillMaxWidth(), height = 52.dp)
    }
}

@Composable
private fun CalendarSheet(granted: Boolean, onResult: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var deniedBefore by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        onResult(ok)
        if (!ok) deniedBefore = true
    }
    val canAsk = context.activity()?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR) ?: true
    val step = permissionStep(granted, deniedBefore, canAsk)
    PlanSheet(onDismiss, gap = 16) { close ->
        SheetHeading("Calendar in your brief")
        SheetCaption(
            when (step) {
                PermissionStep.Granted -> "Your morning brief mentions today’s events. Cove only reads your calendar on this phone and never changes it."
                PermissionStep.Ask -> "Allow Cove to read your calendar so your morning brief can mention today’s events. It stays on this phone and nothing is changed. The brief works without it."
                PermissionStep.OpenSettings -> "Calendar access is off. You can switch it on in your phone’s settings; the brief works without it."
            },
        )
        when (step) {
            PermissionStep.Granted -> PillButton("Done", close, Modifier.fillMaxWidth(), height = 52.dp)
            PermissionStep.Ask -> {
                PillButton("Allow calendar", { launcher.launch(Manifest.permission.READ_CALENDAR) }, Modifier.fillMaxWidth(), height = 52.dp)
                PillButton("Not now", close, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, height = 52.dp)
            }
            PermissionStep.OpenSettings -> {
                PillButton("Open settings", { context.startActivity(Permissions.appSettings(context)); close() }, Modifier.fillMaxWidth(), height = 52.dp)
                PillButton("Not now", close, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary, height = 52.dp)
            }
        }
    }
}

private const val CITY = "city"
private const val CALENDAR = "calendar"
