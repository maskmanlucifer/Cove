package app.cove.companion.data.categorize

/**
 * Built-in keyword groups. A group only matters when the user has a category whose name is one of its
 * [candidates] (first existing candidate wins), so Cove never invents categories.
 *
 * @property strong words that count double (a specific merchant beats a generic word)
 */
internal class RuleGroup(
    val words: Set<String>,
    val candidates: List<String>,
    val strong: Set<String> = emptySet(),
)

/** The built-in rules, in tie-break order (earlier group wins equal scores). */
internal object BuiltInRules {
    private fun words(s: String) = s.split(' ', '\n').filter { it.isNotEmpty() }.toSet()

    val groups: List<RuleGroup> = listOf(
        RuleGroup(
            words(
                "lunch dinner breakfast brunch snack snacks food meal meals pizza burger biryani cafe coffee tea chai restaurant " +
                    "swiggy zomato dominos mcdonalds kfc starbucks juice dessert icecream bakery dhaba eatery thali sandwich " +
                    "momos dosa idli paratha takeaway pluxee sodexo",
            ),
            listOf("eating out", "dining", "dining out", "restaurants", "restaurant", "food", "meals", "food drink", "eats"),
        ),
        RuleGroup(
            words("groceries grocery milk vegetables vegetable veggies fruit fruits blinkit zepto bigbasket instamart dmart supermarket kirana eggs bread atta rice curd"),
            listOf("groceries", "grocery", "food", "supermarket", "household"),
            strong = setOf("instamart", "blinkit", "zepto", "bigbasket", "groceries"),
        ),
        RuleGroup(
            words("cab uber ola rapido auto taxi metro bus train irctc fastag toll parking rickshaw commute ride namma yatri"),
            listOf("transport", "transportation", "commute", "commuting", "travel", "cab", "rides"),
        ),
        RuleGroup(
            words("petrol diesel fuel cng"),
            listOf("fuel", "petrol", "vehicle", "car", "transport", "commute", "travel"),
            strong = setOf("petrol", "diesel", "fuel"),
        ),
        RuleGroup(
            words("flight flights hotel airbnb makemytrip goibibo cleartrip resort trip vacation holiday"),
            listOf("travel", "trips", "holidays", "vacation", "transport"),
        ),
        RuleGroup(
            words("bill bills electricity bescom tneb mseb jio airtel vodafone recharge postpaid prepaid dth tatasky broadband wifi internet insurance"),
            listOf("bills", "utilities", "bills utilities", "home", "house", "household"),
        ),
        RuleGroup(
            words("rent gas lpg cylinder maid plumber electrician furniture repair repairs maintenance society cook"),
            listOf("home", "house", "household", "housing", "rent", "utilities"),
        ),
        RuleGroup(
            words("pharmacy medicine medicines medical doctor dentist hospital clinic apollo medplus pharmeasy netmeds chemist lab tablets health therapy consultation optician gym"),
            listOf("health", "medical", "pharmacy", "healthcare", "medicine", "wellness", "fitness"),
        ),
        RuleGroup(
            words(
                "movie movies netflix hotstar spotify primevideo youtube game games gaming concert party drinks bar show fun cinema pvr inox " +
                    "bookmyshow disney jiocinema sonyliv zee steam playstation club pub outing",
            ),
            listOf("fun", "entertainment", "leisure", "hobbies", "subscriptions", "recreation"),
        ),
        RuleGroup(
            words("amazon flipkart myntra ajio meesho nykaa shopping clothes clothing shoes shirt jeans dress mall ikea decathlon electronics headphones"),
            listOf("shopping", "clothes", "clothing"),
        ),
        RuleGroup(
            words("gift gifts present flowers bouquet"),
            listOf("gifts", "gifting", "presents"),
        ),
        RuleGroup(
            words("course udemy coursera books tuition fees school college stationery"),
            listOf("education", "learning", "books", "study"),
        ),
        RuleGroup(
            words("salon haircut spa grooming barber parlour"),
            listOf("personal care", "grooming", "beauty", "self care"),
        ),
    )
}
