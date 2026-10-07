package app.cove.companion.navigation


import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.cove.companion.design.Cove
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.cove.companion.design.LocalReduceMotion
import app.cove.companion.design.ReducedMotionMillis
import app.cove.companion.feature.alarms.AlarmEditScreen
import app.cove.companion.feature.alarms.AlarmsScreen
import app.cove.companion.feature.brief.BriefScreen
import app.cove.companion.feature.habits.HabitNewScreen
import app.cove.companion.feature.habits.HabitsScreen
import app.cove.companion.feature.journal.JournalEditScreen
import app.cove.companion.BuildConfig
import app.cove.companion.feature.money.ExpenseEditScreen
import app.cove.companion.feature.money.MoneyLoggedDebugScreen
import app.cove.companion.feature.money.MoneyReviewScreen
import app.cove.companion.feature.money.imports.MoneyImportScreen
import app.cove.companion.feature.money.MoneyCategoriesScreen
import app.cove.companion.feature.money.MoneyCategoryDetailScreen
import app.cove.companion.feature.money.MoneyCategoryEditScreen
import app.cove.companion.feature.onboarding.AlarmPermissionScreen
import app.cove.companion.feature.onboarding.MicPermissionScreen
import app.cove.companion.feature.onboarding.WakeTimeScreen
import app.cove.companion.feature.onboarding.WelcomeScreen
import app.cove.companion.feature.sync.SyncConflictScreen
import app.cove.companion.feature.training.trainingGraph
import app.cove.companion.feature.connect.ConnectScreen
import app.cove.companion.feature.today.OneThingScreen
import app.cove.companion.feature.voice.VoiceScreen


/**
 * Pops one screen, but only from a settled screen (a second tap during the exit animation is ignored) and never the
 * last one: with nothing to pop, a screen that is not Main falls back to Main instead of leaving an empty stack.
 */
private fun safeBack(controller: NavHostController) {
    val current = controller.currentBackStackEntry ?: return
    if (current.lifecycle.currentState != Lifecycle.State.RESUMED) return
    if (controller.previousBackStackEntry != null) {
        controller.popBackStack()
    } else if (current.destination.route != Routes.Main) {
        controller.navigate(Routes.Main) { popUpTo(0) { inclusive = true } }
    }
}

/** App-wide navigation graph. Screens receive [Nav] and route ids, never the controller. */
@Composable
fun CoveNavHost(start: String, voiceRequest: Int = 0, briefRequest: Int = 0, paymentsRequest: Int = 0) {
    val reduce = LocalReduceMotion.current
    val density = LocalDensity.current
    val slide = with(density) { NavMotion.SLIDE_DP.dp.roundToPx() }
    val rise = with(density) { NavMotion.RISE_DP.dp.roundToPx() }
    val enterMs = if (reduce) ReducedMotionMillis else NavMotion.ENTER_MS
    val exitMs = if (reduce) ReducedMotionMillis else NavMotion.EXIT_MS
    val fadeSpecIn = tween<Float>(enterMs, easing = NavMotion.Ease)
    val fadeSpecOut = tween<Float>(exitMs, easing = NavMotion.Ease)
    val slideSpecIn = tween<IntOffset>(enterMs, easing = NavMotion.Ease)
    val slideSpecOut = tween<IntOffset>(exitMs, easing = NavMotion.Ease)
    // Forward: new screen fades in while sliding in from the right; the old one only fades. Back is the mirror.
    val fadeIn = fadeIn(fadeSpecIn)
    val fadeOut = fadeOut(fadeSpecOut)
    val forwardIn = if (reduce) fadeIn else fadeIn + slideInHorizontally(slideSpecIn) { slide }
    val backIn = fadeIn
    val backOut = if (reduce) fadeOut else fadeOut + slideOutHorizontally(slideSpecOut) { slide }
    val controller = rememberNavController()
    val nav = remember(controller) {
        val gate = TapGate()
        Nav(
            go = { if (gate.allow(it)) controller.navigate(it) },
            back = { safeBack(controller) },
            home = {
                if (gate.allow(Routes.Main)) controller.navigate(Routes.Main) { popUpTo(0) { inclusive = true } }
            },
            goReplacing = { route, upTo -> if (gate.allow(route)) controller.navigate(route) { popUpTo(upTo) { inclusive = false } } },
        )
    }
    fun id(e: NavBackStackEntry) = e.arguments?.getString("id") ?: "new"
    val idArg = listOf(navArgument("id") { type = NavType.StringType })

    NavHost(
        controller, start,
        modifier = Modifier.background(Cove.colors.canvas),
        enterTransition = { forwardIn },
        exitTransition = { fadeOut },
        popEnterTransition = { backIn },
        popExitTransition = { backOut },
    ) {
        composable(Routes.Welcome) { WelcomeScreen(nav) }
        composable(Routes.WakeTime) { WakeTimeScreen(nav) }
        composable(Routes.MicPermission) { MicPermissionScreen(nav) }
        composable(Routes.AlarmPermission) { AlarmPermissionScreen(nav) }

        composable(Routes.Main) { MainScreen(nav) }
        composable(
            Routes.Voice,
            enterTransition = { if (reduce) fadeIn else slideInVertically(tween(enterMs, easing = NavMotion.Ease)) { rise } + fadeIn },
            exitTransition = { ExitTransition.None },
            popExitTransition = { if (reduce) fadeOut else slideOutVertically(tween(exitMs, easing = NavMotion.Ease)) { rise } + fadeOut },
        ) { VoiceScreen(nav) }
        composable(Routes.Brief) { BriefScreen(nav) }
        composable(Routes.OneThing) { OneThingScreen(nav) }

        composable(Routes.Alarms) { AlarmsScreen(nav) }
        composable(Routes.AlarmEdit, idArg) { AlarmEditScreen(id(it), nav) }

        composable(Routes.ExpenseEdit, idArg) { ExpenseEditScreen(id(it), nav) }
        composable(Routes.MoneyCategories) { MoneyCategoriesScreen(nav) }
        composable(Routes.MoneyCategory, idArg) { MoneyCategoryEditScreen(id(it), nav) }
        composable(Routes.MoneyReview) { MoneyReviewScreen(nav) }
        composable(Routes.MoneyImport) { MoneyImportScreen(nav) }
        composable(Routes.MoneyImportPending) { MoneyImportScreen(nav, pendingOnly = true) }
        composable(Routes.MoneyCategoryDetail, idArg) { MoneyCategoryDetailScreen(id(it), nav) }

        composable(Routes.Habits) { HabitsScreen(nav) }
        composable(Routes.HabitNew) { HabitNewScreen(nav) }
        composable(Routes.HabitEdit, idArg) { HabitNewScreen(nav, id(it)) }

        trainingGraph(nav)
        composable(Routes.JournalEdit, idArg) { JournalEditScreen(id(it), nav) }
        composable(Routes.SyncConflict) { SyncConflictScreen(nav) }
        composable(Routes.Connect) { ConnectScreen(nav) }
        composable(Routes.ConnectOnboarding) { ConnectScreen(nav, onboarding = true) }
        if (BuildConfig.DEBUG) composable("debug/money-logged") { MoneyLoggedDebugScreen() }
        if (BuildConfig.DEBUG) composable("debug/illustrations") { app.cove.companion.design.illustrations.IllustrationGallery() }
    }
    LaunchedEffect(voiceRequest) { if (voiceRequest > 0 && start == Routes.Main) nav.go(Routes.Voice) }
    LaunchedEffect(briefRequest) { if (briefRequest > 0 && start == Routes.Main) nav.go(Routes.Brief) }
    LaunchedEffect(paymentsRequest) { if (paymentsRequest > 0 && start == Routes.Main) nav.go(Routes.MoneyImportPending) }
}

