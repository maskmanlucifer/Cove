package app.cove.companion.data.local

/**
 * SQL of `MIGRATION_7_8`: the simple weekday training model replaces the programme/session tables.
 * [CREATE] is identical to what Room generates for version 8 (a unit test checks it).
 */
object TrainingMigration {
    /** Tables and indices added in version 8. */
    val CREATE: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `plan_exercises` (`id` TEXT NOT NULL, `weekday` INTEGER NOT NULL, `name` TEXT NOT NULL, `weightKg` REAL NOT NULL, `sets` INTEGER NOT NULL, `reps` INTEGER NOT NULL, `incrementKg` REAL NOT NULL, `sort` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_plan_exercises_weekday` ON `plan_exercises` (`weekday`)",
        "CREATE TABLE IF NOT EXISTS `day_overrides` (`id` TEXT NOT NULL, `day` INTEGER NOT NULL, `planExerciseId` TEXT NOT NULL, `weightKg` REAL, `dismissed` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_day_overrides_day` ON `day_overrides` (`day`)",
        "CREATE TABLE IF NOT EXISTS `exercise_logs` (`id` TEXT NOT NULL, `day` INTEGER NOT NULL, `name` TEXT NOT NULL, `weightKg` REAL NOT NULL, `targetSets` INTEGER NOT NULL, `targetReps` INTEGER NOT NULL, `reps` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_exercise_logs_day` ON `exercise_logs` (`day`)",
    )

    private const val OLD_DAY = "CAST(julianday(date(%s.loggedAt / 1000, 'unixepoch', 'localtime')) - 2440587.5 + 0.5 AS INTEGER)"

    /**
     * Keeps the history: one `exercise_logs` row per lift and day from the old sets (top weight only, so warm-ups do
     * not count). The old programme, sessions and rest settings are not converted; the plan starts blank.
     */
    val KEEP_HISTORY: List<String> = listOf(
        """
        INSERT OR REPLACE INTO exercise_logs (id, day, name, weightKg, targetSets, targetReps, reps, updatedAt, deletedAt)
        SELECT s.d || '|' || lower(trim(e.name)), s.d, e.name, MAX(s.weightKg), e.sets, e.repMin,
               group_concat(s.reps, ','), MAX(s.updatedAt), NULL
        FROM (SELECT t.*, ${OLD_DAY.format("t")} AS d FROM set_logs t WHERE t.deletedAt IS NULL ORDER BY t.loggedAt, t.setNo) s
        JOIN exercises e ON e.id = s.exerciseId
        WHERE s.weightKg = (
            SELECT MAX(x.weightKg) FROM set_logs x
            WHERE x.exerciseId = s.exerciseId AND x.deletedAt IS NULL AND ${OLD_DAY.format("x")} = s.d
        )
        GROUP BY s.d, s.exerciseId
        """.trimIndent(),
        "INSERT INTO outbox (tableName, rowId, op, queuedAt) SELECT 'exercise_logs', id, 'upsert', updatedAt FROM exercise_logs",
    )

    private val OLD_TABLES = listOf("exercises", "workout_plans", "plan_days", "workout_sessions", "set_logs")

    /** Settings keep only the unit; the dropped tables leave no pending sync work behind. */
    val DROP_OLD: List<String> = listOf(
        "CREATE TABLE `training_settings_new` (`id` TEXT NOT NULL, `unit` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "INSERT INTO training_settings_new (id, unit, updatedAt) SELECT id, unit, updatedAt FROM training_settings",
        "DROP TABLE training_settings",
        "ALTER TABLE training_settings_new RENAME TO training_settings",
    ) + OLD_TABLES.map { "DROP TABLE IF EXISTS `$it`" } +
        listOf("outbox", "sync_state", "sync_conflicts").map { table ->
            val col = "tableName"
            "DELETE FROM $table WHERE $col IN (${OLD_TABLES.joinToString(",") { "'$it'" }})"
        }

    /** Every statement of the migration, in order. */
    val ALL: List<String> = CREATE + KEEP_HISTORY + DROP_OLD
}
