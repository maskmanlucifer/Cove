package app.cove.companion.design.illustrations

/** Which outline a scene is painted into. */
internal enum class ArtShape { Circle, Wide, Free }

/**
 * A finished scene: its virtual size, the mascot standing in it (if any) and the painting routine. Painting is
 * deterministic, runs off the main thread once per size, and ends up in the bitmap cache.
 */
internal class SceneArt(val w: Float, val h: Float, val shape: ArtShape, val mascot: MascotSpot?, val paint: Painter.() -> Unit)

/**
 * The illustration library. Spot scenes are round "avatar" vignettes that sit on the page; the four time-of-day
 * scenes and [Welcome] are wide, carry their own sky and meant for [SceneBanner] or a full-width hero.
 *
 * To add one: add an entry here and write `internal fun xArt(): SceneArt` in a `*Scenes.kt` file; it shows up in the
 * debug gallery (`debug/illustrations`) automatically.
 */
enum class Scene(internal val make: () -> SceneArt) {
    Morning(::morningArt), Afternoon(::afternoonArt), Evening(::eveningArt), Night(::nightArt),
    Todos(::todosArt), Schedule(::scheduleArt), Money(::moneyArt), Journal(::journalArt), Habits(::habitsArt),
    Alarms(::alarmsArt), Training(::trainingArt), Voice(::voiceArt), Messages(::messagesArt),
    Synced(::syncedArt), Offline(::offlineArt), Help(::helpArt), Lantern(::lanternArt), Secure(::secureArt),
    Cleared(::clearedArt), Welcome(::welcomeArt), Celebration(::celebrationArt), Lock(::lockArt),
    ;

    internal val art: SceneArt by lazy(make)

    /** True for the four wide time-of-day scenes that carry their own sky. */
    val isTimeOfDay: Boolean get() = ordinal < 4

    /** True for scenes that are wide rather than round. */
    val isWide: Boolean get() = isTimeOfDay || this == Welcome
}
