package app.cove.companion.feature.training

import app.cove.companion.data.local.entity.ExerciseEntity
import app.cove.companion.data.local.entity.PlanDayEntity
import app.cove.companion.feature.training.engine.ExerciseKind
import app.cove.companion.feature.training.engine.WeightUnit
import kotlin.math.roundToInt

/** A lift of the default programme: its settings and a first weight in kilograms (0 for body weight). */
data class DefaultLift(
    val name: String,
    val group: String,
    val kind: ExerciseKind,
    val incrementKg: Double,
    val repMin: Int,
    val repMax: Int,
    val sets: Int,
    val startKg: Double,
    /** Shown in the setup screen where the user may change the starting weight. */
    val main: Boolean = false,
)

/** The Push / Pull / Legs programme Cove starts with (see `docs/TRAINING.md`). */
object DefaultProgramme {
    val push = listOf(
        DefaultLift("Bench press", "push", ExerciseKind.Weighted, 2.5, 8, 8, 3, 40.0, main = true),
        DefaultLift("Overhead press", "push", ExerciseKind.Weighted, 2.5, 8, 8, 3, 25.0, main = true),
        DefaultLift("Incline dumbbell", "push", ExerciseKind.Weighted, 2.0, 10, 12, 3, 12.0),
        DefaultLift("Triceps pushdown", "push", ExerciseKind.Weighted, 2.5, 10, 12, 3, 20.0),
    )
    val pull = listOf(
        DefaultLift("Barbell row", "pull", ExerciseKind.Weighted, 2.5, 8, 8, 3, 40.0, main = true),
        DefaultLift("Lat pulldown", "pull", ExerciseKind.Weighted, 2.5, 10, 12, 3, 35.0),
        DefaultLift("Biceps curl", "pull", ExerciseKind.Weighted, 1.0, 10, 12, 3, 8.0),
    )
    val legs = listOf(
        DefaultLift("Squat", "legs", ExerciseKind.Weighted, 2.5, 8, 8, 3, 50.0, main = true),
        DefaultLift("Romanian deadlift", "legs", ExerciseKind.Weighted, 2.5, 8, 8, 3, 50.0, main = true),
        DefaultLift("Leg press", "legs", ExerciseKind.Weighted, 5.0, 10, 12, 3, 80.0),
        DefaultLift("Calf raise", "legs", ExerciseKind.Weighted, 2.5, 12, 15, 3, 30.0),
    )

    /** Accessories offered by "+ Dips" and the exercise picker that are not in a day by default. */
    val extras = listOf(
        DefaultLift("Dips", "push", ExerciseKind.Bodyweight, 0.0, 10, 10, 3, 0.0),
        DefaultLift("Pull-ups", "pull", ExerciseKind.Bodyweight, 0.0, 6, 6, 3, 0.0),
        DefaultLift("Lunges", "legs", ExerciseKind.Weighted, 2.0, 10, 12, 3, 10.0),
        DefaultLift("Deadlift", "legs", ExerciseKind.Weighted, 2.5, 5, 5, 3, 60.0),
    )

    /** Order of the programme's days. */
    val dayTypes = listOf("Push", "Pull", "Legs")

    val all: List<DefaultLift> get() = push + pull + legs

    /** Lifts of each day type. */
    fun lifts(dayType: String): List<DefaultLift> = when (dayType.lowercase()) {
        "push" -> push
        "pull" -> pull
        "legs" -> legs
        else -> emptyList()
    }

    /** The lift called [name] (built-in or extra), ignoring case. */
    fun find(name: String): DefaultLift? = (all + extras).firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Weight step in kilograms for [kg]-valued increment in [unit]: kilograms as is, pounds in 2.5 lb steps (at least 5 lb for barbells). */
    fun incrementFor(incrementKg: Double, unit: WeightUnit): Double {
        if (unit == WeightUnit.Kg || incrementKg <= 0) return incrementKg
        val lb = unit.fromKg(incrementKg)
        val step = maxOf(2.5, (lb / 2.5).roundToInt() * 2.5)
        return unit.toKg(if (incrementKg >= 2.0) maxOf(5.0, step) else step)
    }

    /** A first weight in [unit] for a lift whose default is [kg]: kilograms to the nearest 2.5, pounds to the nearest 5. */
    fun startIn(kg: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.Kg -> (kg / 2.5).roundToInt() * 2.5
        WeightUnit.Lb -> (unit.fromKg(kg) / 5).roundToInt() * 5.0
    }

    /** Builds the lift rows for [lifts]; ids come from [idOf]. */
    fun exerciseRows(lifts: List<DefaultLift>, unit: WeightUnit, idOf: (DefaultLift) -> String, firstSort: Int = 0): List<ExerciseEntity> =
        lifts.mapIndexed { i, l ->
            ExerciseEntity(
                idOf(l), l.name, l.group, l.kind.key, incrementFor(l.incrementKg, unit), l.repMin, l.repMax, l.sets, firstSort + i,
            )
        }

    /** Plan day rows for the three days from the lift ids of each. */
    fun dayRows(planId: String, idsOf: (String) -> List<String>, idOf: (String) -> String): List<PlanDayEntity> =
        dayTypes.mapIndexed { i, type -> PlanDayEntity(idOf(type), planId, type, idsOf(type).joinToString(","), i) }
}
