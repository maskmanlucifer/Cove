package app.cove.companion.feature.training.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cove.companion.core.OneShot
import app.cove.companion.core.appViewModel
import app.cove.companion.data.local.entity.SetLogEntity
import app.cove.companion.design.Cove
import app.cove.companion.design.CoveType
import app.cove.companion.design.components.CoveScreen
import app.cove.companion.design.components.CoveSheet
import app.cove.companion.design.components.CoveText
import app.cove.companion.design.components.UndoHost
import app.cove.companion.design.components.coveTopInset
import app.cove.companion.design.components.pressable
import app.cove.companion.feature.training.SessionViewModel
import app.cove.companion.feature.training.TrainingFocus
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.ui.CloseCircle
import app.cove.companion.feature.training.ui.NumberEntrySheet
import app.cove.companion.feature.training.ui.PrimaryButton
import app.cove.companion.feature.training.ui.SecondaryButton
import app.cove.companion.feature.training.ui.SetEditSheet
import app.cove.companion.feature.training.ui.StepperLine
import app.cove.companion.feature.training.ui.TextAction
import app.cove.companion.feature.training.ui.TrainingTopBar
import app.cove.companion.feature.training.ui.TrainingType
import app.cove.companion.navigation.Nav
import app.cove.companion.navigation.Routes
import kotlinx.coroutines.launch

private enum class Typing { None, Weight, Reps }

/** Frame 42: log one set with steppers (or by voice), see the earlier sets, finish early or skip a lift. */
@Composable
fun SessionScreen(nav: Nav) {
    val ctx = LocalContext.current
    val vm = appViewModel { SessionViewModel(it, ctx) }
    val u by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val guard = remember { OneShot() }
    var typing by remember { mutableStateOf(Typing.None) }
    var finishSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SetLogEntity?>(null) }
    val c = Cove.colors
    val large = LocalDensity.current.fontScale > 1.25f
    var leaving by remember { mutableStateOf(false) }
    fun summary(id: String) { leaving = true; nav.goReplacing(Routes.trainingSummary(id), Routes.Training) }

    // Kept while the Voice screen is open on top (this screen leaves composition then); cleared on leaving the workout.
    LaunchedEffect(u.exercise?.name) { TrainingFocus.exercise = u.exercise?.name }
    LaunchedEffect(u.loaded, u.session, u.allDone, leaving) {
        if (leaving) Unit
        else if (u.loaded && u.session == null) nav.back()
        else if (u.allDone && u.session != null) vm.finish()?.let { summary(it) }
    }

    CoveScreen {
        Column(Modifier.fillMaxSize().coveTopInset()) {
            TrainingTopBar(u.header, { CloseCircle({ TrainingFocus.exercise = null; nav.back() }, "Leave workout, it keeps going") }, { TextAction("Finish", { finishSheet = true }) })
            val e = u.exercise
            if (e != null) {
                val body: @Composable () -> Unit = {
                    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CoveText(e.name, style = CoveType.Title)
                            CoveText(
                                "Set ${u.setNo} of ${u.plannedSets}" + (u.lastTime?.let { " · $it" } ?: ""),
                                style = TrainingType.Sub, color = c.muted,
                            )
                        }
                        Column {
                            u.previous.forEachIndexed { i, s ->
                                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.well))
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 56.dp).pressable({ editing = s }, onClickLabel = "Edit set ${s.setNo}", role = Role.Button),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    CoveText("Set ${s.setNo}", Modifier.weight(1f), style = TrainingType.Row, color = c.tail)
                                    CoveText(
                                        if (u.bodyweight) "${s.reps} reps" else WeightFormat.set(s.weightKg, s.reps, u.unit),
                                        style = TrainingType.Row, color = c.tail,
                                    )
                                }
                            }
                        }
                    }
                }
                val steppers: @Composable () -> Unit = {
                    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                        if (!u.bodyweight) StepperLine(WeightFormat.number(u.weightKg, u.unit), u.unit.label, "weight", { vm.stepWeight(-1) }, { vm.stepWeight(1) }, { typing = Typing.Weight })
                        StepperLine(u.reps.toString(), "reps", "reps", { vm.stepReps(-1) }, { vm.stepReps(1) }, { typing = Typing.Reps })
                    }
                }
                val bottom: @Composable () -> Unit = {
                    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 40.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Box(Modifier.fillMaxWidth().height(19.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.fillMaxWidth().requiredHeight(48.dp).pressable({ nav.go(Routes.Voice) }, role = Role.Button), contentAlignment = Alignment.Center) {
                                CoveText(u.hint, style = CoveType.Meta, color = c.tail, textAlign = TextAlign.Center)
                            }
                        }
                        PrimaryButton("Log set", {
                            guard.launch(scope) {
                                when (val next = vm.log()) {
                                    SessionViewModel.Next.Rest -> nav.go(Routes.TrainingRest)
                                    is SessionViewModel.Next.Done -> summary(next.sessionId)
                                    SessionViewModel.Next.Stay -> Unit
                                }
                                false
                            }
                        }, Modifier.fillMaxWidth(), enabled = !guard.busy)
                    }
                }
                if (large) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) { body(); steppers() }
                } else {
                    Column(Modifier.weight(1f)) {
                        Spacer(Modifier.height(12.dp))
                        body()
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) { steppers() }
                    }
                }
                bottom()
            }
        }
        UndoHost("training", Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = 120.dp))

        NumberEntrySheet(
            typing == Typing.Weight, "Weight, ${u.unit.label}", WeightFormat.number(u.weightKg, u.unit), u.unit.label, true,
            { typing = Typing.None }, { v -> vm.setWeight(u.unit.toKg(v)); typing = Typing.None },
        )
        NumberEntrySheet(
            typing == Typing.Reps, "Reps", u.reps.toString(), "reps", false,
            { typing = Typing.None }, { v -> vm.setReps(v.toInt()); typing = Typing.None },
        )
        val s = editing
        SetEditSheet(
            s != null, s?.id.orEmpty(), "Set ${s?.setNo ?: ""}", s?.weightKg ?: 0.0, s?.reps ?: 8, u.unit, u.bodyweight, { editing = null },
            { w, r -> s?.let { scope.launch { vm.updateSet(it, w, r) } }; editing = null },
            { s?.let { scope.launch { vm.deleteSet(it) } }; editing = null },
        )
        CoveSheet(finishSheet, { finishSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CoveText("Finish now?", style = CoveType.Meta, color = c.muted)
                CoveText("Whatever you logged is saved, and Cove plans next time from it.", style = TrainingType.Sub, color = c.muted)
                PrimaryButton("Finish workout", {
                    finishSheet = false
                    scope.launch { val id = vm.finish(); if (id != null) summary(id) else { leaving = true; nav.back() } }
                }, Modifier.fillMaxWidth())
                u.exercise?.let { ex ->
                    SecondaryButton("Skip ${ex.name}", {
                        finishSheet = false
                        scope.launch { if (vm.skipExercise()) vm.finish()?.let { summary(it) } ?: run { leaving = true; nav.back() } }
                    }, Modifier.fillMaxWidth())
                }
                SecondaryButton("Keep going", { finishSheet = false }, Modifier.fillMaxWidth())
            }
        }
    }
}
