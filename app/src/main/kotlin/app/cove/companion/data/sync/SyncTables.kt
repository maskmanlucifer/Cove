package app.cove.companion.data.sync

/**
 * One synced table. [key] is the server column used as row id, [bools] are the columns Room stores as 0/1 but
 * Postgres stores as boolean, [localOnly] are columns a pull must never overwrite on an existing row.
 */
data class SyncTable(
    val name: String,
    val key: String = "id",
    val bools: Set<String> = emptySet(),
    val localOnly: Set<String> = emptySet(),
    val conflictAware: Boolean = false,
)

/** The 15 synced tables, mirroring `supabase/migrations/0001_init.sql`. */
object SyncTables {
    val all = listOf(
        SyncTable(
            "settings",
            bools = setOf("spoken_replies", "one_thing_mode", "brief_on", "suggestions_on", "biometric_lock", "onboarded", "upload_on_wifi_only"),
            localOnly = setOf("biometric_lock", "onboarded"),
        ),
        SyncTable("alarms", bools = setOf("gentle_rise", "enabled"), conflictAware = true),
        SyncTable("todo_categories"),
        SyncTable("todos", bools = setOf("remind", "done"), conflictAware = true),
        SyncTable("events", conflictAware = true),
        SyncTable("habits", bools = setOf("after_wake_up", "show_on_today")),
        SyncTable("habit_logs"),
        SyncTable("expense_categories", bools = setOf("carry_over", "alert_at80")),
        SyncTable("expenses"),
        SyncTable("journal_entries"),
        SyncTable("journal_media"),
        SyncTable("decisions"),
        SyncTable("suggestion_prefs", key = "kind", bools = setOf("muted")),
        SyncTable("voice_commands", bools = setOf("undone")),
        SyncTable("briefs", key = "day"),
    )

    private val byName = all.associateBy { it.name }

    /** The table called [name], or null when it is not synced. */
    fun find(name: String): SyncTable? = byName[name]

    /** `alertAt80` -> `alert_at80`: Room property names to server column names. */
    fun snake(name: String): String = buildString {
        for (ch in name) {
            if (ch.isUpperCase()) append('_').append(ch.lowercaseChar()) else append(ch)
        }
    }
}
