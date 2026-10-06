package app.cove.companion.design.illustrations

/**
 * The illustration library. Spot scenes are 4:3 and sit on the page background; the four time-of-day scenes are
 * wide, carry their own sky and are meant for [SceneBanner].
 *
 * To add one: add an entry here, write `internal fun xArt(p: ScenePalette): Art` in a `*Scenes.kt` file, and it
 * shows up in the debug gallery (`debug/illustrations`) automatically.
 */
enum class Scene(internal val art: (ScenePalette) -> Art) {
    Morning(::morningArt), Afternoon(::afternoonArt), Evening(::eveningArt), Night(::nightArt),
    Todos(::todosArt), Schedule(::scheduleArt), Money(::moneyArt), Journal(::journalArt), Habits(::habitsArt),
    Alarms(::alarmsArt), Training(::trainingArt), Voice(::voiceArt), Messages(::messagesArt),
    Synced(::syncedArt), Offline(::offlineArt), Help(::helpArt), Lantern(::lanternArt), Secure(::secureArt),
    Cleared(::clearedArt),
}
