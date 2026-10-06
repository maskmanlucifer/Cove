package app.cove.companion.feature.training

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.cove.companion.feature.training.plan.PlanDayScreen
import app.cove.companion.feature.training.progress.ProgressScreen
import app.cove.companion.navigation.Nav
import app.cove.companion.navigation.Routes

/** Routes of the Training area: the page, a weekday's plan and the progress screen. */
fun NavGraphBuilder.trainingGraph(nav: Nav) {
    composable(Routes.Training) { TrainingScreen(nav) }
    composable(Routes.TrainingPlanDay, listOf(navArgument("weekday") { type = NavType.IntType })) {
        PlanDayScreen((it.arguments?.getInt("weekday") ?: 1).coerceIn(1, 7), nav)
    }
    composable(Routes.TrainingProgress) { ProgressScreen(nav) }
}
