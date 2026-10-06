package app.cove.companion.ai.provider.rules

/** Keyword-based category guesses for to-dos and expenses; the user can change them before saving. */
object CategoryGuess {
    private val shopping = setOf(
        "milk", "bread", "eggs", "butter", "cheese", "batteries", "battery", "soap", "shampoo", "toothpaste", "rice", "atta",
        "sugar", "salt", "oil", "coffee", "tea", "fruit", "fruits", "vegetables", "veggies", "onions", "tomatoes", "potatoes",
        "detergent", "tissues", "diapers", "bulbs", "bulb", "groceries", "snacks", "juice", "yogurt", "curd", "chips", "gift",
        "card", "dish", "paper", "towels", "water", "bottle", "charger", "cable",
    )
    private val homeWords = setOf(
        "fix", "repair", "clean", "tap", "sink", "bathroom", "kitchen", "laundry", "plants", "plant", "trash", "garbage",
        "vacuum", "mop", "dust", "bed", "fan", "light", "washing", "dishes", "fridge", "leak", "paint", "organise", "organize",
        "tidy", "garden", "curtains", "wifi", "router", "plumber", "electrician",
    )
    private val errands = setOf(
        "post", "courier", "parcel", "bank", "atm", "pharmacy", "chemist", "dry", "cleaner", "cleaners", "return", "collect",
        "pickup", "renew", "passport", "license", "appointment", "dentist", "doctor", "visit", "submit", "deposit", "recharge",
    )
    private val personal = setOf("call", "text", "reply", "message", "email", "mail", "ping", "birthday", "wish", "write", "read", "book", "plan")

    /** Category name for a to-do title among Shopping, Home, Errands and Personal. */
    fun todo(title: String, afterAdd: Boolean = false): String? {
        val words = title.lowercase().split(Regex("[^a-z']+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val first = words.first()
        if (first in personal) return "Personal"
        if (words.any { it in homeWords } && first !in setOf("buy", "get", "pick")) return "Home"
        if (words.any { it in errands }) return "Errands"
        if (words.any { it in shopping } || first in setOf("buy", "get")) return "Shopping"
        if (afterAdd && words.size <= 3 && first !in verbs) return "Shopping"
        return "Personal"
    }

    private val verbs = setOf("fix", "call", "clean", "pay", "send", "reply", "book", "submit", "water", "take", "check", "finish", "email", "text", "do", "write", "read", "plan", "renew", "return", "pick", "wash", "cook")

    /** Expense category name among Food, Transport, Home, Fun and Other. */
    fun expense(text: String): String {
        val words = text.lowercase().split(Regex("[^a-z']+")).toSet()
        return when {
            words.any { it in setOf("lunch", "dinner", "breakfast", "coffee", "tea", "snack", "snacks", "food", "groceries", "grocery", "pizza", "burger", "cafe", "café", "restaurant", "swiggy", "zomato", "meal", "biryani", "juice", "milk", "vegetables", "fruit", "fruits") } -> "Food"
            words.any { it in setOf("cab", "uber", "ola", "auto", "taxi", "metro", "bus", "train", "fuel", "petrol", "diesel", "parking", "toll", "flight", "ticket", "rickshaw") } -> "Transport"
            words.any { it in setOf("rent", "electricity", "bill", "bills", "wifi", "internet", "gas", "maid", "plumber", "furniture", "repair", "repairs") } -> "Home"
            words.any { it in setOf("movie", "movies", "netflix", "game", "games", "concert", "party", "drinks", "bar", "spotify", "show", "fun") } -> "Fun"
            else -> "Other"
        }
    }
}
