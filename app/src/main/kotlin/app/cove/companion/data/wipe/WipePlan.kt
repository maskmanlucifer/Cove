package app.cove.companion.data.wipe

/** One group of things "Clear all data" removes, in the order it removes them. */
enum class WipeArea(val label: String) {
    /** `cove.db` and its SQLite sidecars: removed first so a half-finished wipe can never leave old data readable. */
    Database("Encrypted database"),

    /** Journal photos, voice notes, thumbnails, DataStore files, local recovery copies, drafts and the key file. */
    Files("Photos, voice notes and files"),

    /** Credentials, alarm mirror, crash notes, staged restores and the database key file. */
    NoBackup("Saved connections and keys"),

    /** Cache files, including temporary backup downloads. */
    Cache("Temporary files"),

    /** SharedPreferences: settings, sign-in session, Drive consent flag, undo state, scheduler registries. */
    Preferences("Preferences and sign-in"),

    /** Every Android Keystore key Cove created (database, credentials, session). */
    Keystore("Keystore keys"),
}

/**
 * Pure description of what "Clear all data" deletes. It is the same for every caller (Me and the Recovery screen's
 * "Start fresh"): the app returns to a factory-new state, so nothing is kept except Android's own library prefs.
 */
data class WipePlan(val alsoCloud: Boolean = false) {
    /** Areas removed on this phone, in order. */
    val areas: List<WipeArea> = WipeArea.entries

    /** Database file names (in the databases folder) that belong to Cove. */
    fun isDatabaseFile(name: String): Boolean = name == DB_NAME || name.startsWith("$DB_NAME-")

    /** True when a file in `no_backup` must be removed; WorkManager's job database lives there and holds no user data. */
    fun removesNoBackup(fileName: String): Boolean = !fileName.startsWith(WORK_DB)

    /** True when a preferences file must be removed; only WorkManager's own bookkeeping survives. */
    fun removesPrefs(fileName: String): Boolean = KEEP_PREFS.none { fileName.startsWith(it) }

    /** True when [feature] (for example credentials) survives the wipe: never, by design. */
    fun keeps(feature: String): Boolean = false

    companion object {
        /** File name of the Room database. */
        const val DB_NAME = "cove.db"
        private const val WORK_DB = "androidx.work.workdb"
        private val KEEP_PREFS = listOf("androidx.work")
    }
}
