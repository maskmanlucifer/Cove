package app.cove.companion.feature.me

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cove.companion.BuildConfig
import app.cove.companion.core.appViewModel
import app.cove.companion.data.media.PhotoQuality
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveIcon
import app.cove.companion.design.CoveIcons
import app.cove.companion.design.CoveShapes
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.DockClearance
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.security.AuthAvailability
import app.cove.companion.feature.security.Authenticator
import app.cove.companion.feature.security.SecurityGroup
import app.cove.companion.feature.security.findFragmentActivity
import app.cove.companion.feature.permissions.PermissionGuides
import app.cove.companion.feature.permissions.PermissionNeeds
import app.cove.companion.feature.permissions.rememberPermissionIssues
import app.cove.companion.navigation.Nav
import app.cove.companion.navigation.Routes

/** Me tab: profile, day and calm settings, and links to alarms, habits, look and privacy. */
@Composable
fun MeScreen(nav: Nav) {
    val vm = appViewModel { MeViewModel(it) }
    val s by vm.settings.collectAsState()
    val alarms by vm.alarms.collectAsState()
    var sheet by rememberSaveable { mutableStateOf<MeSheet?>(null) }
    val sync by vm.sync.collectAsState()
    val conflictTitle by vm.conflictTitle.collectAsState()
    val backupLabel by vm.backupLabel.collectAsState()
    val settings = s ?: return
    var advanced by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .coveTopInset()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = DockClearance),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Header(settings.displayName) { sheet = MeSheet.Name }
        PermissionGuides(
            rememberPermissionIssues(
                PermissionNeeds(alarms = alarms.isNotEmpty(), brief = settings.briefOn, voice = false),
            ),
            alarmsInUse = alarms.isNotEmpty(),
        )
        sync.problem?.let { SyncProblemBanner(it, onRetry = vm::retrySync, onOpenConnect = { nav.go(Routes.Connect) }) }
        conflictTitle?.let { ConflictBanner(it) { nav.go(Routes.SyncConflict) } }
        SettingsGroup("Day") {
            SettingsRow("Alarms", value = alarmSummary(alarms.map { it.minutes }), onClick = { nav.go(Routes.Alarms) })
            RowDivider()
            SettingsRow("Habits", onClick = { nav.go(Routes.Habits) })
            RowDivider()
            SettingsRow("Morning brief", value = onOff(settings.briefOn), onClick = { sheet = MeSheet.Brief })
            RowDivider()
            SettingsRow("Nudges", value = nudgeLabel(settings.nudgeMode), onClick = { sheet = MeSheet.Nudges })
        }
        SettingsGroup("Body") {
            val training by TrainingSummaries.current.value.collectAsState()
            SettingsRow("Training", value = training, onClick = { nav.go(Routes.Training) })
        }
        SettingsGroup("Calm") {
            SettingsRow("One-thing mode", value = onOff(settings.oneThingMode), onClick = { sheet = MeSheet.OneThing })
            RowDivider()
            SettingsRow("Look and text size", value = lookSummary(settings.theme, settings.textScale), onClick = { sheet = MeSheet.Look })
        }
        SettingsGroup("Your data") {
            SettingsRow("Connect services", value = sync.label, onClick = { nav.go(Routes.Connect) })
            RowDivider()
            SettingsRow("Back up now", value = backupLabel, onClick = { vm.resetBackup(); sheet = MeSheet.Backup })
            RowDivider()
            SettingsRow("Privacy and data", value = "Yours", onClick = { sheet = MeSheet.Privacy })
        }
        SecurityGroup(
            settings,
            onLockToggle = { on ->
                val host = context.findFragmentActivity()
                when {
                    !on -> vm.update { it.copy(biometricLock = false) }
                    host == null || Authenticator.availability(context) == AuthAvailability.None -> sheet = MeSheet.LockUnavailable
                    else -> Authenticator.prompt(host, "Turn on app lock", null) { ok ->
                        if (ok) vm.update { it.copy(biometricLock = true) }
                    }
                }
            },
            onLockAfter = { sheet = MeSheet.LockAfter },
            onHideInRecents = { v -> vm.update { it.copy(hideInRecents = v) } },
        )
        SettingsGroup("Advanced") {
            SettingsRow("Advanced settings", value = if (advanced) "Hide" else "Show", onClick = { advanced = !advanced })
            if (advanced) {
                RowDivider()
                SettingsRow("Wake-up time", value = clockLabel(settings.wakeMinutes), onClick = { sheet = MeSheet.Wake })
                RowDivider()
                SettingsRow("Spoken replies", value = onOff(settings.spokenReplies), onClick = { sheet = MeSheet.Spoken })
                RowDivider()
                SettingsRow("Reduce motion", value = settings.reduceMotion.replaceFirstChar { it.uppercase() }, onClick = { sheet = MeSheet.Motion })
                BriefSettingsRows()
                RowDivider()
                SettingsRow("Photo quality", value = PhotoQuality.label(settings.photoQuality), onClick = { vm.resetBackup(); sheet = MeSheet.PhotoQuality })
                RowDivider()
                SettingsRow("Upload photos on Wi-Fi only", checked = settings.uploadOnWifiOnly, onCheck = { v -> vm.update { it.copy(uploadOnWifiOnly = v) } })
                RowDivider()
                SettingsRow("Restore from backup", onClick = { vm.resetBackup(); sheet = MeSheet.Restore })
                RowDivider()
                SettingsRow("Version", value = BuildConfig.VERSION_NAME)
            }
        }
    }
    MeSheets(sheet, settings, vm, briefPlay = { nav.go(Routes.Brief) }, openConnect = { nav.go(Routes.Connect) }) { sheet = null }
}

@Composable
private fun Header(name: String, onEdit: () -> Unit) {
    val c = Cove.colors
    Row(
        Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp).pressable(onEdit),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(CoveShapes.Circle).background(if (c.isDark) c.wellStrong else Color(0xFFE4E1DB)),
            contentAlignment = Alignment.Center,
        ) {
            if (name.isBlank()) CoveIcon(CoveIcons.Me, c.muted, size = 24.dp)
            else CoveText(name.first().uppercase(), style = CoveType.Value.copy(fontSize = 20.sp, lineHeight = 27.sp), color = c.muted)
        }
        Column {
            if (name.isBlank()) CoveText("Add your name", style = CoveType.Section.copy(lineHeight = 32.sp), color = c.placeholder)
            else CoveText(name, style = CoveType.Section.copy(lineHeight = 32.sp))
            CoveText("Your data lives in your own space", style = CoveType.Meta, color = c.muted)
        }
    }
}
