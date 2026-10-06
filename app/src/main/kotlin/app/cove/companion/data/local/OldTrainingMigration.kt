package app.cove.companion.data.local

/** SQL of `MIGRATION_6_7`: the first training tables, kept so databases at version 6 still upgrade. */
internal object OldTrainingMigration {
    val STATEMENTS: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `exercises` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `muscleGroup` TEXT NOT NULL, `kind` TEXT NOT NULL, `incrementKg` REAL NOT NULL, `repMin` INTEGER NOT NULL, `repMax` INTEGER NOT NULL, `sets` INTEGER NOT NULL, `sort` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `workout_plans` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `daysPerWeek` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `plan_days` (`id` TEXT NOT NULL, `planId` TEXT NOT NULL, `dayType` TEXT NOT NULL, `exerciseIds` TEXT NOT NULL, `sort` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_plan_days_planId` ON `plan_days` (`planId`)",
        "CREATE TABLE IF NOT EXISTS `workout_sessions` (`id` TEXT NOT NULL, `dayType` TEXT NOT NULL, `plannedAt` INTEGER NOT NULL, `startedAt` INTEGER, `endedAt` INTEGER, `note` TEXT NOT NULL, `exerciseIds` TEXT NOT NULL, `skippedIds` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_workout_sessions_startedAt` ON `workout_sessions` (`startedAt`)",
        "CREATE TABLE IF NOT EXISTS `set_logs` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `setNo` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `reps` INTEGER NOT NULL, `loggedAt` INTEGER NOT NULL, `source` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_set_logs_sessionId` ON `set_logs` (`sessionId`)",
        "CREATE INDEX IF NOT EXISTS `index_set_logs_exerciseId` ON `set_logs` (`exerciseId`)",
        "CREATE TABLE IF NOT EXISTS `body_weights` (`day` INTEGER NOT NULL, `kg` REAL NOT NULL, `note` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`day`))",
        "CREATE TABLE IF NOT EXISTS `training_settings` (`id` TEXT NOT NULL, `unit` TEXT NOT NULL, `daysPerWeek` INTEGER NOT NULL, `restSeconds` INTEGER NOT NULL, `weekdays` TEXT NOT NULL, `startMinutes` INTEGER NOT NULL, `startWeights` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    )
}
