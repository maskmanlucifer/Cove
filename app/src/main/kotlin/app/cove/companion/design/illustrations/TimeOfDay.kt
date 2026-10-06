package app.cove.companion.design.illustrations

/** The time-of-day scene for a 24-hour [hour]: morning 5-11, afternoon 12-16, evening 17-20, night 21-4. */
fun sceneForHour(hour: Int): Scene = when (hour.mod(24)) {
    in 5..11 -> Scene.Morning
    in 12..16 -> Scene.Afternoon
    in 17..20 -> Scene.Evening
    else -> Scene.Night
}

/** How much of the Today scene to show. */
enum class BannerMode(val heightDp: Int) {
    /** Nothing planned: the scene becomes a full empty state with a line and an action. */
    Empty(160),

    /** Little content: a full banner fills the dead space under the list. */
    Full(160),

    /** A moderate page: a thin strip that does not push anything down. */
    Strip(64),

    /** A full page: no scene. */
    Hidden(0),
}

/**
 * Chooses the banner for Today from how much is already on the page.
 *
 * @param blocks weight of what is shown: Next/suggestion/workout cards count 3, each to-do row 1, the mood prompt 2.
 * @param nothingPlanned no next item, no rows and no suggestion.
 * @param offline the offline page keeps its own notice and stays plain.
 */
fun todayBannerMode(blocks: Int, nothingPlanned: Boolean, offline: Boolean): BannerMode = when {
    offline -> BannerMode.Hidden
    nothingPlanned -> BannerMode.Empty
    blocks <= 5 -> BannerMode.Full
    blocks <= 8 -> BannerMode.Strip
    else -> BannerMode.Hidden
}
