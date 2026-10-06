package app.cove.companion.data

import app.cove.companion.AppContainer
import java.time.LocalDate

/** `--ez noTraining true` keeps the seed from planning any workout, to see the empty Training page. */
object DebugTraining {
    @Volatile var skip = false
}

/**
 * Debug-only training data: a weekly plan with today as a Push day (Bench press, Overhead press, Incline dumbbell
 * press), Legs two days later and Pull four days later, four weeks of history so that today's Bench press and Incline
 * dumbbell suggest more weight and Overhead press holds, and body weight from 69.0 to 68.4 kg (nothing yet today).
 */
internal suspend fun seedTrainingData(c: AppContainer, day: LocalDate) {
    if (DebugTraining.skip || c.training.plan().isNotEmpty()) return
    fun weekday(offset: Long) = day.plusDays(offset).dayOfWeek.value
    val repo = c.training
    repo.addExercise(weekday(0), "Bench press", 60.0, 3, 8)
    repo.addExercise(weekday(0), "Overhead press", 35.0, 3, 8)
    repo.addExercise(weekday(0), "Incline dumbbell press", 16.0, 3, 10)
    repo.addExercise(weekday(2), "Squat", 80.0, 3, 8)
    repo.addExercise(weekday(2), "Romanian deadlift", 70.0, 3, 8)
    repo.addExercise(weekday(4), "Barbell row", 45.0, 3, 8)
    repo.addExercise(weekday(4), "Biceps curl", 12.0, 3, 12)

    suspend fun log(offset: Long, name: String, kg: Double, sets: Int, reps: Int, done: List<Int>) =
        repo.logExercise(day.plusDays(offset).toEpochDay(), name, kg, sets, reps, done)
    log(-28, "Bench press", 55.0, 3, 8, listOf(8, 8, 8))
    log(-21, "Bench press", 57.5, 3, 8, listOf(8, 8, 8))
    log(-14, "Bench press", 60.0, 3, 8, listOf(8, 8, 7))
    log(-7, "Bench press", 60.0, 3, 8, listOf(8, 8, 8))
    log(-21, "Overhead press", 32.5, 3, 8, listOf(8, 8, 8))
    log(-14, "Overhead press", 35.0, 3, 8, listOf(8, 8, 8))
    log(-7, "Overhead press", 35.0, 3, 8, listOf(8, 8, 7))
    log(-14, "Incline dumbbell press", 14.0, 3, 10, listOf(10, 10, 10))
    log(-7, "Incline dumbbell press", 16.0, 3, 10, listOf(10, 10, 10))
    log(-26, "Squat", 70.0, 3, 8, listOf(8, 8, 8))
    log(-19, "Squat", 75.0, 3, 8, listOf(8, 8, 8))
    log(-12, "Squat", 80.0, 3, 8, listOf(8, 8, 8))
    log(-24, "Barbell row", 40.0, 3, 8, listOf(8, 8, 8))
    log(-17, "Barbell row", 42.5, 3, 8, listOf(8, 8, 8))
    log(-10, "Barbell row", 45.0, 3, 8, listOf(8, 8, 8))

    val weights = listOf(-29 to 69.0, -24 to 69.1, -20 to 68.8, -15 to 68.9, -11 to 68.7, -8 to 68.6, -4 to 68.6, -1 to 68.4)
    weights.forEach { (offset, kg) -> repo.saveBodyWeight(day.plusDays(offset.toLong()).toEpochDay(), kg) }
}
