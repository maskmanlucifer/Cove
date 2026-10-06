package app.cove.companion.feature.training

import app.cove.companion.AppContainer
import app.cove.companion.core.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The training tables as a [TrainingSnapshot] for "today" on the app clock, re-emitted on every change. */
fun AppContainer.trainingSnapshots(): Flow<TrainingSnapshot> =
    training.tables.map { TrainingSnapshot(it, clock.now().toLocalDate()) }
