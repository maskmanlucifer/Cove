package app.cove.companion.feature.training

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.cove.companion.feature.training.screens.BodyWeightScreen
import app.cove.companion.feature.training.screens.LiftHistoryScreen
import app.cove.companion.feature.training.screens.PlanEditScreen
import app.cove.companion.feature.training.screens.ProgressScreen
import app.cove.companion.feature.training.screens.RestScreen
import app.cove.companion.feature.training.screens.SessionDoneScreen
import app.cove.companion.feature.training.screens.SessionScreen
import app.cove.companion.navigation.Nav
import app.cove.companion.navigation.Routes

/** Routes of the Training area; [TrainingScreen] is the home and the first-run setup. */
fun NavGraphBuilder.trainingGraph(nav: Nav) {
    composable(Routes.Training) { TrainingScreen(nav) }
    composable(Routes.TrainingSetup) { TrainingScreen(nav) }
    composable(Routes.TrainingPlan) { PlanEditScreen(nav) }
    composable(Routes.TrainingSession) { SessionScreen(nav) }
    composable(Routes.TrainingRest) { RestScreen(nav) }
    composable(Routes.TrainingSummary, listOf(navArgument("id") { type = NavType.StringType })) {
        SessionDoneScreen(it.arguments?.getString("id").orEmpty(), nav)
    }
    composable(Routes.TrainingLift, listOf(navArgument("exerciseId") { type = NavType.StringType })) {
        LiftHistoryScreen(it.arguments?.getString("exerciseId").orEmpty(), nav)
    }
    composable(Routes.TrainingWeight) { BodyWeightScreen(nav) }
    composable(Routes.TrainingProgress) { ProgressScreen(nav) }
}
