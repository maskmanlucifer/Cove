package app.cove.companion.navigation


import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.cove.companion.feature.alarms.AlarmEditScreen
import app.cove.companion.feature.alarms.AlarmsScreen
import app.cove.companion.feature.brief.BriefScreen
import app.cove.companion.feature.habits.HabitNewScreen
import app.cove.companion.feature.habits.HabitsScreen
import app.cove.companion.feature.journal.JournalEditScreen
import app.cove.companion.feature.money.ExpenseEditScreen
import app.cove.companion.feature.money.MoneyCategoriesScreen
import app.cove.companion.feature.money.MoneyCategoryDetailScreen
import app.cove.companion.feature.money.MoneyCategoryEditScreen
import app.cove.companion.feature.onboarding.AlarmPermissionScreen
import app.cove.companion.feature.onboarding.MicPermissionScreen
import app.cove.companion.feature.onboarding.WakeTimeScreen
import app.cove.companion.feature.onboarding.WelcomeScreen
import app.cove.companion.feature.sync.SyncConflictScreen
import app.cove.companion.feature.today.OneThingScreen
import app.cove.companion.feature.voice.VoiceScreen

private val fadeIn = fadeIn(tween(250))
private val fadeOut = fadeOut(tween(200))

/** App-wide navigation graph. Screens receive [Nav] and route ids, never the controller. */
@Composable
fun CoveNavHost(start: String) {
    val controller = rememberNavController()
    val nav = remember(controller) {
        Nav(
            go = { controller.navigate(it) },
            back = { controller.popBackStack() },
            home = {
                controller.navigate(Routes.Main) { popUpTo(0) { inclusive = true } }
            },
        )
    }
    fun id(e: NavBackStackEntry) = e.arguments?.getString("id") ?: "new"
    val idArg = listOf(navArgument("id") { type = NavType.StringType })

    NavHost(
        controller, start,
        enterTransition = { fadeIn },
        exitTransition = { fadeOut },
        popEnterTransition = { fadeIn },
        popExitTransition = { fadeOut },
    ) {
        composable(Routes.Welcome) { WelcomeScreen(nav) }
        composable(Routes.WakeTime) { WakeTimeScreen(nav) }
        composable(Routes.MicPermission) { MicPermissionScreen(nav) }
        composable(Routes.AlarmPermission) { AlarmPermissionScreen(nav) }

        composable(Routes.Main) { MainScreen(nav) }
        composable(
            Routes.Voice,
            enterTransition = { slideInVertically(tween(280)) { it / 8 } + fadeIn },
            exitTransition = { ExitTransition.None },
            popExitTransition = { slideOutVertically(tween(220)) { it / 8 } + fadeOut },
        ) { VoiceScreen(nav) }
        composable(Routes.Brief) { BriefScreen(nav) }
        composable(Routes.OneThing) { OneThingScreen(nav) }

        composable(Routes.Alarms) { AlarmsScreen(nav) }
        composable(Routes.AlarmEdit, idArg) { AlarmEditScreen(id(it), nav) }

        composable(Routes.ExpenseEdit, idArg) { ExpenseEditScreen(id(it), nav) }
        composable(Routes.MoneyCategories) { MoneyCategoriesScreen(nav) }
        composable(Routes.MoneyCategory, idArg) { MoneyCategoryEditScreen(id(it), nav) }
        composable(Routes.MoneyCategoryDetail, idArg) { MoneyCategoryDetailScreen(id(it), nav) }

        composable(Routes.Habits) { HabitsScreen(nav) }
        composable(Routes.HabitNew) { HabitNewScreen(nav) }

        composable(Routes.JournalEdit, idArg) { JournalEditScreen(id(it), nav) }
        composable(Routes.SyncConflict) { SyncConflictScreen(nav) }
    }
}

