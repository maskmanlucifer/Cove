package app.cove.companion.navigation

/** Route names. Arguments use `{name}`; build concrete routes with the helpers below. */
object Routes {
    const val Welcome = "welcome"
    const val WakeTime = "wake-time"
    const val MicPermission = "permission/mic"
    const val AlarmPermission = "permission/alarms"

    const val Main = "main"
    const val Voice = "voice"
    const val Brief = "brief"
    const val OneThing = "one-thing"

    const val Alarms = "alarms"
    const val AlarmEdit = "alarms/{id}"
    fun alarmEdit(id: String = "new") = "alarms/$id"

    const val ExpenseEdit = "money/expense/{id}"
    fun expenseEdit(id: String = "new") = "money/expense/$id"
    const val MoneyCategories = "money/categories"
    const val MoneyCategory = "money/category/{id}"
    fun moneyCategory(id: String = "new") = "money/category/$id"
    const val MoneyCategoryDetail = "money/category-detail/{id}"
    fun moneyCategoryDetail(id: String) = "money/category-detail/$id"

    const val MoneyReview = "money/review"

    const val Habits = "habits"
    const val HabitNew = "habits/new"
    const val HabitEdit = "habits/edit/{id}"
    fun habitEdit(id: String) = "habits/edit/$id"

    const val JournalEdit = "journal/{id}"
    fun journalEdit(id: String = "new") = "journal/$id"

    const val SyncConflict = "sync/conflict"
    const val Connect = "connect"

    /** Connect services opened from "I already use Cove", with Continue and Skip the setup questions. */
    const val ConnectOnboarding = "connect/onboarding"
}

/** Navigation actions exposed to screens, so they never touch `NavController` directly. */
class Nav(
    val go: (String) -> Unit,
    val back: () -> Unit,
    /** Clears the back stack and opens [Routes.Main]. */
    val home: () -> Unit,
)
