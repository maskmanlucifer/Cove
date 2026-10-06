package app.cove.companion.navigation

/** Debug-only launch overrides filled from `MainActivity`'s intent so screenshots can open a tab, segment or sheet directly. */
object DebugLaunch {
    /** Dock tab name, e.g. "plan". */
    var tab: String? = null

    /** Segment index inside the Plan tab. */
    var segment: Int? = null

    /** Plan sheet key: `task:<title or id>`, `add:event`, `add:todo`, `categories`. */
    var sheet: String? = null

    /** Title typed into the add sheet; also stops it from grabbing the keyboard. */
    var title: String? = null
}
