package app.cove.companion.data

import app.cove.companion.AppContainer
import app.cove.companion.core.newId
import app.cove.companion.core.toEpochMillis
import app.cove.companion.data.local.entity.SetLogEntity
import app.cove.companion.data.local.entity.WorkoutSessionEntity
import app.cove.companion.feature.training.DefaultProgramme
import app.cove.companion.feature.training.engine.WeightUnit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Debug-only training data matching frames 41 to 48: Bench 55 to 62.5, Overhead press 32.5 to 37.5, Squat 70 to 80,
 * eleven sessions in four weeks (two, three, three, three per week), body weight 69.0 to 68.4, and today's Push session
 * (done when [todayDone], otherwise still to do as in frame 41).
 */
internal suspend fun seedTrainingData(c: AppContainer, day: LocalDate, todayDone: Boolean) {
    if (c.training.hasPlan()) return
    val push3 = DefaultProgramme.push.take(3)
    val ids = DefaultProgramme.create(
        c.training, WeightUnit.Kg, setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
        starts = mapOf("Bench press" to 55.0, "Overhead press" to 32.5, "Squat" to 70.0, "Barbell row" to 40.0),
        restSeconds = 120, pushLifts = push3,
    )
    val bench = ids.getValue("Bench press")
    val ohp = ids.getValue("Overhead press")
    val incline = ids.getValue("Incline dumbbell")
    val squat = ids.getValue("Squat")
    val row = ids.getValue("Barbell row")
    val dips = c.training.saveExercise(DefaultProgramme.exerciseRows(listOf(DefaultProgramme.find("Dips")!!), WeightUnit.Kg, { newId() }, 20).first())

    fun time(offset: Int, h: Int, m: Int) = LocalDateTime.of(day.plusDays(offset.toLong()), LocalTime.of(h, m)).toEpochMillis()

    suspend fun session(offset: Int, type: String, lifts: List<Pair<String, Triple<Double, List<Int>, Boolean>>>, startH: Int = 19, startM: Int = 0, minutes: Int = 48) {
        val start = time(offset, startH, startM)
        val s = c.training.saveSession(
            WorkoutSessionEntity(newId(), type, start, start, start + minutes * 60_000L, exerciseIds = lifts.joinToString(",") { it.first }),
        )
        var t = start
        for ((exId, spec) in lifts) {
            spec.second.forEachIndexed { i, reps ->
                t += 150_000
                c.training.saveSet(SetLogEntity(newId(), s.id, exId, i + 1, spec.first, reps, t, "manual"))
            }
        }
    }
    fun l(id: String, kg: Double, vararg reps: Int) = id to Triple(kg, reps.toList(), false)

    session(-26, "Push", listOf(l(bench, 55.0, 8, 8, 8), l(ohp, 32.5, 8, 8, 8), l(incline, 14.0, 10, 10, 10)))
    session(-24, "Legs", listOf(l(squat, 70.0, 8, 8, 8)))
    session(-19, "Push", listOf(l(bench, 57.5, 8, 8, 8), l(ohp, 32.5, 8, 8, 7), l(incline, 14.0, 12, 12, 12)))
    session(-17, "Legs", listOf(l(squat, 75.0, 8, 8, 8)))
    session(-15, "Pull", listOf(l(row, 40.0, 8, 8, 8)))
    session(-12, "Pull", listOf(l(row, 42.5, 8, 8, 8)))
    session(-10, "Legs", listOf(l(squat, 80.0, 8, 8, 8)))
    session(-8, "Push", listOf(l(bench, 60.0, 8, 7, 7), l(ohp, 32.5, 8, 8, 8)))
    session(-5, "Push", listOf(l(bench, 60.0, 8, 8, 8), l(ohp, 35.0, 8, 8, 8), l(incline, 16.0, 10, 10, 9)))
    session(-3, "Pull", listOf(l(row, 45.0, 8, 8, 8)))
    if (todayDone) {
        session(0, "Push", listOf(l(bench, 62.5, 8, 8, 6), l(ohp, 37.5, 8, 8, 8), l(incline, 16.0, 10, 10, 10), l(dips.id, 0.0, 10, 10, 10)), startH = 9, startM = 47)
    }
    val weights = listOf(-29 to 69.0, -24 to 69.1, -20 to 68.8, -15 to 68.9, -11 to 68.7, -8 to 68.6, -4 to 68.6, 0 to 68.4)
    weights.forEach { (offset, kg) -> c.training.saveBodyWeight(day.plusDays(offset.toLong()).toEpochDay(), kg) }
}
