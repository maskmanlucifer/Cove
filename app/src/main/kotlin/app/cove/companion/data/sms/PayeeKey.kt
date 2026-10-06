package app.cove.companion.data.sms

/**
 * Stable identity of the other side of a payment, derived on the phone from a parsed message (see `docs/SMS_IMPORT.md`).
 * Pure. Never uses an account's last four digits: those identify the user's own account, not the payee.
 *
 * Two shapes of key:
 * - `vpa:<handle>`: a UPI handle, lowercased and trimmed, kept whole (`vpa:zomato@okaxis`). QR merchant handles are
 *   stable per shop, so the whole `name@psp` is the identity. The one exception is the `paytmqr<digits><suffix>` family,
 *   whose per-QR suffix is dropped (`vpa:paytmqr2810050501@paytm`): the digits are the merchant, the suffix only
 *   tells one printed QR code of that merchant from another. Any other pattern is left alone, because guessing at
 *   "the stable part" could merge two different shops.
 * - `name:<TYPE>:<NAME>`: a normalised merchant name plus the payment type (`UPI`, `CARD`, `BANK`), for card, NEFT,
 *   IMPS and UPI texts that carry only a name. Names lose store numbers, ids, city, "PVT LTD" and "INDIA".
 *
 * No key (null) is better than a wrong one: generic fallbacks ("Payment"), ATM cash and empty names return null.
 */
object PayeeKey {
    private const val VPA_PREFIX = "vpa:"
    private const val NAME_PREFIX = "name:"
    private const val MAX_NAME = 40

    private val vpaShape = Regex("""^[a-z0-9][a-z0-9._\-]{1,40}@[a-z][a-z0-9]{1,20}$""")
    private val paytmQr = Regex("""^(paytmqr\d{8,})[a-z0-9]*$""")

    private val noiseWords = setOf(
        "PVT", "LTD", "PRIVATE", "LIMITED", "LLP", "INC", "INDIA", "STORE", "STORES", "OUTLET", "BRANCH", "THE",
    )
    private val cities = setOf(
        "MUMBAI", "DELHI", "NEW DELHI", "BANGALORE", "BENGALURU", "HYDERABAD", "CHENNAI", "KOLKATA", "PUNE", "GURGAON", "GURUGRAM",
        "NOIDA", "AHMEDABAD", "JAIPUR", "LUCKNOW", "KOCHI", "INDORE", "BHOPAL", "SURAT", "CHANDIGARH", "THANE", "NAGPUR", "GOA", "MYSORE",
    )

    /** Names that say nothing about who was paid; they never become a key and never teach or match words. */
    private val generic = setOf(
        "PAYMENT", "PAYMENTS", "MONEY RECEIVED", "ATM WITHDRAWAL", "ATM", "UPI", "UPI PAYMENT", "TRANSFER", "BANK TRANSFER", "MERCHANT",
        "PAY", "ONLINE", "PURCHASE", "TRANSACTION", "CARD", "CASH", "SELF", "UNKNOWN", "PAID", "NO NOTE",
    )

    /**
     * The key for a payment to or from [vpa] (a full `name@psp` handle, preferred) or [merchant] (a cleaned name), paid by
     * [paidWith] (`UPI`, `Card`, `Cash`, `Bank transfer`). Null when there is nothing stable to key on.
     */
    fun derive(vpa: String?, merchant: String?, paidWith: String): String? =
        vpa?.let(::fromVpa) ?: merchant?.let { fromName(it, paidWith) }

    /** `vpa:` key for a UPI handle, or null when [raw] is not a handle. */
    fun fromVpa(raw: String): String? {
        val h = raw.trim().lowercase()
        if (!vpaShape.matches(h)) return null
        val local = h.substringBefore('@')
        val psp = h.substringAfter('@')
        val handle = paytmQr.matchEntire(local)?.let { it.groupValues[1] + "@" + psp } ?: h
        return VPA_PREFIX + handle
    }

    /** `name:<TYPE>:<NAME>` key for a merchant name, or null for generic, empty or cash payments. */
    fun fromName(name: String, paidWith: String): String? {
        val type = typeOf(paidWith) ?: return null
        val n = normalName(name) ?: return null
        return "$NAME_PREFIX$type:$n"
    }

    /** Uppercase merchant words without store numbers, ids, trailing city, company suffixes or "INDIA"; null when nothing specific is left or it is generic. */
    fun normalName(name: String): String? {
        val words = name.uppercase().replace(Regex("""[^A-Z0-9 ]"""), " ").split(' ')
            .filter { it.isNotEmpty() && it !in noiseWords && it.count(Char::isDigit) < 3 }
            .toMutableList()
        while (words.size > 1 && words.last() in cities) words.removeAt(words.lastIndex)
        val out = words.joinToString(" ").take(MAX_NAME).trim()
        if (out.length < 2 || out in generic) return null
        return out
    }

    /** True for a note that is only a fallback label ("Payment", "Money received"), which must not teach or match words. */
    fun isGenericNote(note: String): Boolean {
        val n = note.uppercase().replace(Regex("""[^A-Z ]"""), " ").trim().replace(Regex("""\s+"""), " ")
        return n.isEmpty() || n in generic
    }

    /** The UPI handle of a `vpa:` key, or null for name keys. */
    fun handleOf(key: String?): String? = key?.takeIf { it.startsWith(VPA_PREFIX) }?.removePrefix(VPA_PREFIX)

    private fun typeOf(paidWith: String): String? = when (paidWith) {
        "UPI" -> "UPI"
        "Card" -> "CARD"
        "Bank transfer" -> "BANK"
        "Cash" -> null
        else -> paidWith.uppercase().filter { it.isLetter() }.ifEmpty { null }
    }
}
