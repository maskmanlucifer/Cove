package app.cove.companion.feature.onboarding

import app.cove.companion.navigation.Routes

/** Onboarding screens in order; the step indicator counts only the screens after Welcome. */
enum class OnboardingStep(val route: String) {
    Welcome(Routes.Welcome),
    WakeTime(Routes.WakeTime),
    Mic(Routes.MicPermission),
    Alarms(Routes.AlarmPermission),
    ;

    /** The step after this one, or null when onboarding is finished. */
    val next: OnboardingStep? get() = entries.getOrNull(ordinal + 1)

    /** 1-based position among the indicator steps (0 for Welcome). */
    val position: Int get() = ordinal

    companion object {
        /** Number of steps shown in the indicator. */
        val indicatorCount = entries.size - 1
    }
}

/** Step size of the wake-time wheel, in minutes. */
const val WakeStepMinutes = 15

private const val DayMinutes = 24 * 60

/** Moves [minutes] by [steps] wheel positions, wrapping around midnight. */
fun stepWake(minutes: Int, steps: Int): Int = Math.floorMod(minutes + steps * WakeStepMinutes, DayMinutes)

/** Snaps an arbitrary minute-of-day to the wheel grid. */
fun snapWake(minutes: Int): Int = Math.floorMod(Math.round(minutes / WakeStepMinutes.toFloat()) * WakeStepMinutes, DayMinutes)

/** Weekdays and weekends: bits 0..6 of [app.cove.companion.data.local.entity.AlarmEntity.daysMask]. */
const val EveryDayMask = 0b1111111
