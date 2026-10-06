package app.cove.companion.data.media

/** Stored `photoQuality` setting values and the WebP quality each one encodes with (PLAN 6c). */
object PhotoQuality {
    const val BALANCED = "balanced"
    const val HIGH = "high"

    /** Values in the order of the Me selector. */
    val all = listOf(BALANCED, HIGH)

    /** WebP quality for a stored [value]; unknown values fall back to Balanced. */
    fun webp(value: String): Int = if (value == HIGH) 90 else QUALITY_BALANCED

    /** "Balanced" / "High" for display. */
    fun label(value: String): String = if (value == HIGH) "High" else "Balanced"
}
