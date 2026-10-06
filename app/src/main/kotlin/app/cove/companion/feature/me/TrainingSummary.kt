package app.cove.companion.feature.me

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One-line value shown on the Me > Body > Training row, e.g. "Push · 7 pm". */
interface TrainingSummary {
    /** Current row value. */
    val value: StateFlow<String>

    companion object {
        /** Shown until the training feature has a plan. */
        const val NOT_SET_UP = "Not set up"
    }
}

/**
 * Where the Me screen reads the Training row from. The training feature replaces [current] at start-up
 * with a summary of the next session.
 */
object TrainingSummaries {
    /** Always "Not set up". */
    val Default: TrainingSummary = object : TrainingSummary {
        override val value: StateFlow<String> = MutableStateFlow(TrainingSummary.NOT_SET_UP)
    }

    /** The summary the Me screen shows. */
    @Volatile
    var current: TrainingSummary = Default
}
