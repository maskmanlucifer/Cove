package app.cove.companion.feature.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.core.appViewModel
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.ButtonKind
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.PillButton
import app.cove.companion.design.components.CoveText
import androidx.compose.foundation.layout.height
import app.cove.companion.design.illustrations.Illustration
import app.cove.companion.design.illustrations.Scene
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.me.RowDivider
import app.cove.companion.feature.me.SettingsGroup
import app.cove.companion.feature.me.SettingsRow
import app.cove.companion.navigation.DebugLaunch
import app.cove.companion.navigation.Nav
import app.cove.companion.navigation.Routes

/** "Connect services": paste your own accounts once; everything stays encrypted on this phone. */
@Composable
fun ConnectScreen(nav: Nav, onboarding: Boolean = false) {
    val vm = appViewModel { ConnectViewModel(it) }
    val ui by vm.ui.collectAsState()
    var sheet by rememberSaveable { mutableStateOf(DebugLaunch.sheet?.let { s -> ConnectSheet.entries.firstOrNull { it.name.equals(s, true) } }) }
    CoveScreen {
        Column(Modifier.fillMaxSize().coveTopInset()) {
            Box(Modifier.padding(horizontal = 16.dp).heightIn(min = 56.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.size(44.dp).pressable(nav.back, role = Role.Button).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) {
                    CoveIcon(CoveIcons.ChevronLeft, Cove.colors.muted, size = 22.dp)
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoveText("Connect services", style = CoveType.Title, modifier = Modifier.semantics { heading() })
                    CoveText(
                        "Cove works fully on its own. Add your own accounts to sync, back up photos and understand trickier commands. " +
                            "Everything you paste is stored encrypted on this phone only.",
                        style = CoveType.Meta.copy(lineHeight = 21.sp), color = Cove.colors.muted,
                    )
                }
                if (ServiceId.entries.none { ui.status(it) == ServiceStatus.Connected }) {
                    Illustration(Scene.Synced, Modifier.align(Alignment.CenterHorizontally).height(120.dp))
                }
                SettingsGroup("Quick start") {
                    SettingsRow("Paste setup code", value = "Fills everything", onClick = { sheet = ConnectSheet.Code })
                    RowDivider()
                    SettingsRow("My setup code", value = "Copy or save", onClick = { sheet = ConnectSheet.Share })
                }
                SettingsGroup("Services") {
                    ServiceRow(ServiceId.Supabase, ui.status(ServiceId.Supabase)) { sheet = ConnectSheet.Supabase }
                    RowDivider()
                    ServiceRow(ServiceId.Google, ui.status(ServiceId.Google)) { sheet = ConnectSheet.Google }
                    RowDivider()
                    ServiceRow(ServiceId.Drive, ui.status(ServiceId.Drive)) { sheet = ConnectSheet.Drive }
                    RowDivider()
                    ServiceRow(ServiceId.Gemini, ui.status(ServiceId.Gemini)) { sheet = ConnectSheet.Gemini }
                }
            }
            if (onboarding) OnboardingActions(ui.signedIn, onContinue = { nav.go(Routes.WakeTime) }, onSkip = { vm.skipSetup { nav.home() } })
        }
        ConnectSheets(sheet, ui, vm) { sheet = null }
    }
}

/** Way forward when Connect was opened from "I already use Cove": skip the questions once signed in, or continue to them. */
@Composable
private fun OnboardingActions(signedIn: Boolean, onContinue: () -> Unit, onSkip: () -> Unit) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (signedIn) {
            CoveText(
                "You’re signed in. Your things will arrive in a moment, so you can skip the setup questions.",
                style = CoveType.Meta, color = Cove.colors.muted,
            )
            PillButton("Skip the setup questions", onSkip, Modifier.fillMaxWidth(), height = 52.dp)
        }
        PillButton("Continue", onContinue, Modifier.fillMaxWidth(), kind = if (signedIn) ButtonKind.Secondary else ButtonKind.Primary, height = 52.dp)
    }
}

/** A service with a one-line purpose, its status dot and a chevron. */
@Composable
private fun ServiceRow(service: ServiceId, status: ServiceStatus, onClick: () -> Unit) {
    val c = Cove.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).pressable(onClick, role = Role.Button).semantics(mergeDescendants = true) {}.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CoveText(service.title, style = CoveType.Body.copy(fontSize = 16.sp))
            CoveText(service.blurb, style = CoveType.Meta.copy(fontSize = 13.sp, lineHeight = 18.sp), color = c.muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(CoveShapes.Circle).background(dotColor(status)))
            CoveText(status.label, style = CoveType.Meta, color = if (status == ServiceStatus.NeedsAttention) c.alert else c.muted, maxLines = 2)
            CoveIcon(CoveIcons.ChevronRight, c.tail, size = 14.dp)
        }
    }
}

@Composable
private fun dotColor(status: ServiceStatus): Color {
    val c = Cove.colors
    return when (status) {
        ServiceStatus.Connected -> c.saved
        ServiceStatus.NeedsAttention -> c.alert
        ServiceStatus.Saved -> c.muted
        ServiceStatus.NotSet -> c.placeholder
    }
}
