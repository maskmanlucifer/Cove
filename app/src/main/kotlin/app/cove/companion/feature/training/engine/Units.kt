package app.cove.companion.feature.training.engine

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Weight unit shown to the user; everything stored and computed is in kilograms. */
enum class WeightUnit(val key: String, val label: String, private val perKg: Double) {
    Kg("kg", "kg", 1.0),
    Lb("lb", "lb", 2.2046226218);

    /** [kg] expressed in this unit. */
    fun fromKg(kg: Double): Double = kg * perKg

    /** [value] given in this unit, in kilograms. */
    fun toKg(value: Double): Double = value / perKg

    /** Smallest step the weight stepper moves by: 2.5 kg or 5 lb. */
    val stepperStep: Double get() = if (this == Kg) 2.5 else 5.0

    companion object {
        /** The unit stored as [key]; kilograms for anything unknown. */
        fun of(key: String?): WeightUnit = entries.firstOrNull { it.key == key } ?: Kg
    }
}

/** Number and weight formatting shared by every training screen. */
object WeightFormat {
    /** "62.5", "40", "137.8": [kg] in [unit] to at most one decimal, no trailing zero. */
    fun number(kg: Double, unit: WeightUnit = WeightUnit.Kg): String = trim(unit.fromKg(kg))

    /** "62.5 kg". */
    fun withUnit(kg: Double, unit: WeightUnit = WeightUnit.Kg): String = number(kg, unit) + " " + unit.label

    /** [value] to at most one decimal without a trailing ".0". */
    fun trim(value: Double): String {
        val tenths = (value * 10).roundToLong()
        val whole = tenths / 10
        val frac = abs(tenths % 10)
        return if (frac == 0L) whole.toString() else String.format(Locale.ENGLISH, "%s%d.%d", if (tenths < 0 && whole == 0L) "-" else "", whole, frac)
    }

    /** "62.5 × 8" style pair for a set. */
    fun set(kg: Double, reps: Int, unit: WeightUnit = WeightUnit.Kg, withUnit: Boolean = true): String =
        (if (withUnit) withUnit(kg, unit) else number(kg, unit)) + " × " + reps

    /** The number typed in [text] (comma or point as decimal separator), or null when it is not a usable weight. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it < 1000 }
}
