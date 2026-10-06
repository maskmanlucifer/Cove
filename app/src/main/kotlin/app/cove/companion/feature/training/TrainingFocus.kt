package app.cove.companion.feature.training

/** The lift on screen during a workout, so a spoken "thirty-seven five for eight" needs no lift name. */
object TrainingFocus {
    @Volatile
    var exercise: String? = null
}
