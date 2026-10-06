package app.cove.companion.feature.journal

import app.cove.companion.navigation.Routes
import java.time.LocalDate

private const val NEW_PREFIX = "new-"

/** Route that opens an empty entry dated [day]. `journal/new` means today. */
fun journalNewRoute(day: LocalDate) = Routes.journalEdit("$NEW_PREFIX${day.toEpochDay()}")

/** The day encoded in a `new-<epochDay>` id; null for `new` and for existing entry ids. */
fun newEntryDay(id: String): LocalDate? =
    id.removePrefix(NEW_PREFIX).takeIf { id.startsWith(NEW_PREFIX) }?.toLongOrNull()?.let(LocalDate::ofEpochDay)
