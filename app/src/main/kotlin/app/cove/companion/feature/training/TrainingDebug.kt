package app.cove.companion.feature.training

import android.content.Intent
import app.cove.companion.AppContainer
import app.cove.companion.BuildConfig
import app.cove.companion.core.toLocalDate
import app.cove.companion.feature.training.engine.RestState
import app.cove.companion.feature.training.rest.RestStore
import kotlinx.coroutines.flow.first

/** Debug-only extras for the Training screens (`MainActivity.handleDebugIntent`); ignored in release builds. */
object TrainingDebug {
    suspend fun handle(context: android.content.Context, c: AppContainer, intent: Intent) {
        if (!BuildConfig.DEBUG) return
        if (intent.getBooleanExtra("trainingStart", false)) {
            val snap = TrainingSnapshot(c.training.tables.first(), c.clock.now().toLocalDate())
            if (snap.hasPlan && snap.active == null) {
                val h = HomeModel.build(snap)
                c.training.begin(h.dayType, h.exerciseIds, snap.plannedToday)
            }
        }
        if (intent.getBooleanExtra("trainingProgress", false)) {
            // Frame 42: Bench done, Overhead press two sets in.
            val snap = TrainingSnapshot(c.training.tables.first(), c.clock.now().toLocalDate())
            val s = snap.active ?: return
            val ids = s.exerciseIds.split(',')
            if (c.training.setsOf(s.id).isEmpty() && ids.size >= 2) {
                repeat(3) { c.training.logSet(s.id, ids[0], 62.5, 8) }
                c.training.logSet(s.id, ids[1], 37.5, 8)
                c.training.logSet(s.id, ids[1], 37.5, 7)
            }
        }
        if (intent.hasExtra("restLeft")) {
            val left = intent.getIntExtra("restLeft", 84)
            RestStore(context).set(RestState(c.clock.now() + left * 1000L, "set 3, 37.5 kg × 8", "Push · 2 of 4"))
        }
    }
}
