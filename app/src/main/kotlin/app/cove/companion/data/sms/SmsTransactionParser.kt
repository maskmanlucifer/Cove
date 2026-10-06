package app.cove.companion.data.sms

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Direction of money relative to the user's own account. */
enum class Direction { Debit, Credit }

/** Why a message was not taken as a transaction. Debug and tests only; never shown to the user. */
enum class Rejection {
    Empty, PersonalSender, Otp, Promo, Future, Request, Failed, Due, Statement, BalanceOnly, CardBillAck, Mandate, NoAmount, NoDirection, WeakStructure,
}

/** Fields read from one bank or UPI message. Never holds the message text. */
data class ParsedSms(
    val amountPaise: Long,
    val direction: Direction,
    val merchant: String?,
    val at: Long,
    /** True when [at] came from the message text rather than the SMS timestamp. */
    val dateFromText: Boolean,
    val last4: String?,
    /** UPI, Card, Cash or Bank transfer: matches the app's "Paid with" values. */
    val paidWith: String,
    val ref: String?,
    val bank: String?,
    val confidence: Float,
    /** Stable identity of the counterparty (see [PayeeKey]); null for ATM cash and generic or unreadable payees. */
    val payeeKey: String? = null,
)

/** Outcome of [SmsTransactionParser.parse]. */
sealed interface ParseResult {
    data class Accepted(val tx: ParsedSms) : ParseResult
    data class Rejected(val reason: Rejection) : ParseResult
}

/**
 * Reads Indian bank, card and UPI SMS into [ParsedSms], on the phone and in pure Kotlin (see `docs/SMS_IMPORT.md`).
 * It rejects anything that is not a completed money movement: OTPs, offers, reminders, failed or future debits,
 * requests, statements, balance-only texts and messages from personal numbers.
 */
object SmsTransactionParser {
    /** Bumped when parsing rules change so earlier "ignored" decisions are looked at again. */
    const val VERSION = 1

    private const val MAX_PAISE = 100_00_00_000L * 100

    private val pastVerb = Regex("""(?i)\b(debited|credited|spent|sent|paid|received|withdrawn|withdrawal|purchase[d]?|refund(?:ed)?|deposited|transferred|charged|dr|cr)\b""")
    private val txnDone = Regex("""(?i)\b(debited|credited|spent|sent|paid|received|withdrawn|refunded|deposited|charged)\b""")
    private val otpCode = Regex("""(?i)(\b(otp|one[- ]time (password|pin)|verification code|passcode|security code)\b\W{0,12}(is|code)?\W{0,6}\d{4,8}\b|\b\d{4,8}\b\W{0,3}(is|as)\W{0,6}(your|the|ur)?\W{0,6}(otp|one[- ]time|verification|passcode))""")
    private val otpWord = Regex("""(?i)\b(otp|one[- ]time password|verification code)\b""")
    private val hardPromo = Regex("""(?i)(pre-?approved|congratulations|you have won|you('ve| have) been selected|apply now|apply today|loan offer|personal loan|limited period|upgrade your|download the|click (here|on|the link)|bit\.ly|\bwin\s+(up|a|rs|₹|prizes?))""")
    private val softPromo = Regex("""(?i)\b(offers?|cashback offer|eligible|reward points?|avail|get up ?to|t&c|tnc|flat \d+%|coupon|voucher|unlock|activate)\b""")
    private val future = Regex("""(?i)(will be (auto[- ]?)?(debited|deducted|charged|credited)|will get (debited|deducted)|to be (debited|deducted)|pre-?debit|upcoming|is scheduled|scheduled (for|on)|will be processed|would be debited|shall be debited)""")
    private val request = Regex("""(?i)(requested money|collect request|payment request|has requested|is requesting|requesting (you|rs|inr|₹)|request(ed)? (of|for) (rs|inr|₹)|approve (the )?request|money request|pending request|accept (the )?request)""")
    private val failed = Regex("""(?i)\b(failed|failure|declined|unsuccessful|not successful|could not be (processed|completed)|not processed|unable to (process|complete)|revers(ed|al)|insufficient|rejected|cancell?ed|did not go through|not completed|txn fail)\b""")
    private val statement = Regex("""(?i)\b(statement|e-?statement|bill (is )?(generated|of rs)|bill amount)\b""")
    private val due = Regex("""(?i)(\b(is|are|was) due\b|\bdue (on|by|date|for)\b|\bminimum (amount )?due\b|\bmin(\.)? (amt|amount) due\b|\btotal (amount )?due\b|\bover ?due\b|\bpay (before|by)\b|\bpayment due\b|\bdue amount\b)""")
    private val balance = Regex("""(?i)\b(avl\.?|avail(able)?\.?|a/?c)\s*(bal|balance)\b|\bbal(ance)?\s*(is|:|rs|inr|₹)|\bbalance enquiry\b""")
    private val mandateNotice = Regex("""(?i)(mandate|autopay|auto[- ]?pay|standing instruction|e-?mandate)""")
    private val mandateSetup = Regex("""(?i)\b(created|registered|set ?up|approved|revoked|paused|resumed|modified|cancell?ed|authori[sz]ed|successfully (registered|created))\b""")
    private val cardBillAck = Regex("""(?i)(towards (your )?(credit )?card|credit card (bill )?payment|payment (of .{0,20})?(has been |is )?received (towards|on your)|thank you for (the |your )?payment|payment received (for|towards|on)|has been received (towards|against) (your )?(card|bill))""")

    private val amountRegex = Regex("""(?i)(?<![a-z0-9])(?:rs\.?|inr|₹)\s*[:\-]?\s*(\d[\d,]*(?:\.\d{1,2})?)(?![\d])""")
    private val bareAmount = Regex("""(?i)\b(?:debited|credited|withdrawn|spent|sent|paid|received|debit|credit|charged|txn|transaction)\s+(?:by|for|with|of|an amount of|amount)?\s*(?:rs\.?|inr|₹)?\s*(\d[\d,]*\.\d{1,2}|\d{2,}(?:,\d{2,3})*)(?![\d/\-:])""")
    private val balanceBefore = Regex("""(?i)(bal(ance)?|avl|avail(able)?|limit|lmt|outstanding|due|min(imum)?|total)\W{0,12}$""")

    private val debitWord = Regex("""(?i)\b(?:debited|debit(?!\s*card)|spent|sent|paid|payment of|purchase[d]?|withdrawn|withdrawal|charged|charge of|transferred to|transfer to|trf to)\b|\bdr\b\.?(?=\s*(?:from|to|a/c|acct|\d))""")
    private val creditWord = Regex("""(?i)\b(?:credited|credit(?!\s*(?:card|limit|score|facility))|received|refund(?:ed)?|deposited|added to)\b|\bcr\b\.?(?=\s*(?:to|from|a/c|acct|\d))""")
    private val cardUse = Regex("""(?i)(thank you for using.{0,60}card|card .{0,40}(used|swiped)|used (your|at)|transaction of|txn of|txn rs|txn inr|payment of rs)""")

    private val monthNames = mapOf("jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12)
    private val dateNumeric = Regex("""(?<![\d/\-])(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4}|\d{2})(?![\d])""")
    private val dateIso = Regex("""(?<!\d)(\d{4})-(\d{2})-(\d{2})(?!\d)""")
    private val dateMonth = Regex("""(?i)(?<![\d])(\d{1,2})[ \-]?(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[ \-,]*(\d{4}|\d{2})(?![\d])""")
    private val timeRegex = Regex("""(?<!\d)([01]?\d|2[0-3]):([0-5]\d)(?::([0-5]\d))?(?!\d)""")

    private val upiSlash = Regex("""(?i)\b(?:UPI|IMPS|NEFT|RTGS)[/\-](?:P2[MAP][/\-])?([A-Z0-9]{8,24})[/\-]([^/\n]{2,}?)(?=\s{2,}|\s+(?:SMS|Not|Call|If|Avl|Bal|Dispute|Ref|UTR)\b|\s*[/.]|\s*$|\s*-\s*[A-Z][a-z]+ Bank)""")
    private val upiSlashNoName = Regex("""(?i)\bUPI[/:\-]\s*(?:P2[MAP][/\-])?(\d{9,18})""")
    private val vpa = Regex("""(?i)(?<![A-Za-z0-9._\-])([a-z0-9][a-z0-9._\-]{1,40})@([a-z][a-z0-9]{1,20})\b(?!\.[a-z]{2,})""")
    private val last4Regex = Regex("""(?i)(?<![a-z])(?:a/c|ac|acct?|account|card|credit card|debit card|a\.c)\b(?:\s|no\.?|number|ending|with|in|is|[:.#*xX\-])*?(\d{3,6})(?![\d])""")
    private val refPatterns = listOf(
        Regex("""(?i)\bupi\s*(?:ref(?:erence)?|txn|transaction)?\s*(?:no\.?|number|id)?\s*[:.\-#]?\s*(\d{9,18})\b"""),
        Regex("""(?i)\b(?:utr|rrn|ref(?:erence)?|ref\s*no|refno|txn\s*(?:id|no)|transaction\s*(?:id|no|ref)|imps\s*ref|neft\s*ref)\s*(?:no\.?|number|id)?\s*[:.\-#]?\s*([A-Z0-9]{6,24})\b"""),
        Regex("""(?i)\bupi[:/]\s*(\d{9,18})"""),
    )

    private val bankByKey = linkedMapOf(
        "HDFC" to "HDFC Bank", "ICICI" to "ICICI Bank", "SBI" to "SBI", "AXIS" to "Axis Bank", "KOTAK" to "Kotak Bank",
        "IDFC" to "IDFC First Bank", "YESB" to "Yes Bank", "YES" to "Yes Bank", "CANBNK" to "Canara Bank", "CNRB" to "Canara Bank",
        "BOB" to "Bank of Baroda", "BARODA" to "Bank of Baroda", "PNB" to "PNB", "FEDBNK" to "Federal Bank", "FEDERAL" to "Federal Bank",
        "INDUS" to "IndusInd Bank", "PYTM" to "Paytm Payments Bank", "PAYTM" to "Paytm Payments Bank", "PYTBNK" to "Paytm Payments Bank",
        "AMEX" to "American Express", "SCB" to "Standard Chartered", "CITI" to "Citi", "HSBC" to "HSBC", "RBL" to "RBL Bank",
        "AUBANK" to "AU Small Finance Bank", "IDBI" to "IDBI Bank", "UBI" to "Union Bank", "BOI" to "Bank of India", "CBI" to "Central Bank",
        "SLICE" to "Slice", "JUPITER" to "Jupiter", "GPAY" to "Google Pay", "PHONEPE" to "PhonePe", "AMAZON" to "Amazon Pay", "BHIM" to "BHIM",
    )
    private val bodyBank = Regex("""(?i)\b(HDFC|ICICI|Axis|Kotak(?: Mahindra)?|IDFC(?: FIRST)?|Yes|Canara|Federal|IndusInd|Paytm Payments|Union|Bank of Baroda|Bank of India|Standard Chartered|RBL|AU Small Finance|SBI|PNB|IDBI|American Express|Amex)\b(?: Bank)?""")
    private val senderShape = Regex("""^[A-Z]{2}-[A-Z0-9&]{3,9}(-[A-Z])?$|^[A-Z][A-Z0-9&]{4,10}$""")
    private val strongToken = Regex("""(?i)(a/c|acct|account|card|upi|vpa|imps|neft|rtgs|utr|rrn|\bref\b|@[a-z]{2,})""")

    private val noiseTokens = setOf("rzp", "razorpay", "pay", "upi", "qr", "ybl", "ibl", "axl", "oksbi", "okaxis", "okicici", "okhdfcbank", "paytm", "paytmqr", "bharatpe", "gpay", "payu", "merchant", "store", "online", "vyapar")
    private val knownCaps = setOf(
        "irctc", "kfc", "bsnl", "lic", "hdfc", "icici", "sbi", "bpcl", "hpcl", "iocl", "dmart", "pvr", "bmtc", "dtc", "bms", "emi", "atm", "upi", "gst", "mrf", "nse", "bse", "npci",
    )

    /**
     * Parses [body] sent by [sender] at [receivedAt] (epoch millis).
     *
     * @param sender alphanumeric sender id such as `AX-HDFCBK`, or null for text the user pasted (which then needs strong structure).
     * @param zone zone the message's own date and time are read in.
     */
    fun parse(sender: String?, body: String, receivedAt: Long, zone: ZoneId = ZoneId.systemDefault()): ParseResult {
        val text = body.replace(' ', ' ').replace(Regex("""\s+"""), " ").trim()
        if (text.isEmpty()) return reject(Rejection.Empty)
        val senderId = sender?.trim()?.takeIf { it.isNotEmpty() }
        if (senderId != null && isPersonalNumber(senderId)) return reject(Rejection.PersonalSender)

        val hasDone = txnDone.containsMatchIn(text) || pastVerb.containsMatchIn(text)
        if (otpCode.containsMatchIn(text) || (otpWord.containsMatchIn(text) && !txnDone.containsMatchIn(text))) return reject(Rejection.Otp)
        if (hardPromo.containsMatchIn(text) || (softPromo.containsMatchIn(text) && !hasDone)) return reject(Rejection.Promo)
        if (future.containsMatchIn(text)) return reject(Rejection.Future)
        if (request.containsMatchIn(text)) return reject(Rejection.Request)
        if (failed.containsMatchIn(text)) return reject(Rejection.Failed)
        if (statement.containsMatchIn(text)) return reject(Rejection.Statement)
        if (due.containsMatchIn(text) && !Regex("""(?i)\b(debited|credited|spent|withdrawn|received)\b""").containsMatchIn(text)) return reject(Rejection.Due)
        if (cardBillAck.containsMatchIn(text)) return reject(Rejection.CardBillAck)
        if (mandateNotice.containsMatchIn(text) && mandateSetup.containsMatchIn(text) && !Regex("""(?i)\b(debited|deducted|charged|paid)\b""").containsMatchIn(text)) return reject(Rejection.Mandate)

        val amount = findAmount(text) ?: return reject(if (balance.containsMatchIn(text) && !hasDone) Rejection.BalanceOnly else Rejection.NoAmount)
        val direction = findDirection(text) ?: return reject(if (balance.containsMatchIn(text)) Rejection.BalanceOnly else Rejection.NoDirection)

        val last4 = last4Regex.find(text)?.groupValues?.get(1)?.takeLast(4)
        val ref = findRef(text)
        val vpaMatch = vpa.find(text)
        val bankish = senderId != null && senderShape.matches(senderId.uppercase())
        val strong = strongToken.containsMatchIn(text) && (last4 != null || ref != null || vpaMatch != null || Regex("""(?i)\b(upi|atm|imps|neft|rtgs)\b""").containsMatchIn(text))
        if (!bankish && !strong) return reject(Rejection.WeakStructure)

        val atm = Regex("""(?i)\batm\b|cash withdrawal|withdrawn at""").containsMatchIn(text)
        val merchant = if (atm && direction == Direction.Debit) "ATM withdrawal" else findMerchant(text, direction, vpaMatch)
        val (at, fromText) = findInstant(text, receivedAt, zone)
        val paidWith = when {
            atm -> "Cash"
            Regex("""(?i)\bcard\b""").containsMatchIn(text) && vpaMatch == null && !Regex("""(?i)\bupi\b""").containsMatchIn(text) -> "Card"
            Regex("""(?i)\bupi\b|@[a-z]{2,}|\bvpa\b""").containsMatchIn(text) -> "UPI"
            Regex("""(?i)^(sent|paid)\b""").containsMatchIn(text) -> "UPI"
            else -> "Bank transfer"
        }
        val bank = bankFor(senderId, text)
        var conf = 0.45f
        if (bankish) conf += 0.2f
        if (ref != null) conf += 0.1f
        if (last4 != null) conf += 0.1f
        if (merchant != null) conf += 0.1f
        if (fromText) conf += 0.05f
        val payeeKey = if (atm) null else PayeeKey.derive(payeeVpa(text), merchant, paidWith)
        return ParseResult.Accepted(ParsedSms(amount, direction, merchant, at, fromText, last4, paidWith, ref, bank, conf.coerceAtMost(1f), payeeKey))
    }

    private val ownHandleCue = Regex("""(?i)(your|own|linked|registered)\W*(upi\W*)?(id|vpa)?\W*$""")

    /** First UPI handle in [text] that is not the user's own ("your VPA", "linked to VPA"). */
    private fun payeeVpa(text: String): String? = vpa.findAll(text).firstOrNull { m ->
        !ownHandleCue.containsMatchIn(text.substring(maxOf(0, m.range.first - 16), m.range.first))
    }?.let { it.groupValues[1] + "@" + it.groupValues[2] }

    /** True for senders that are phone numbers (a person), as opposed to alphanumeric bank ids. */
    fun isPersonalNumber(sender: String): Boolean = sender.count { it.isDigit() } >= 7 && sender.none { it.isLetter() }

    /** Splits text a user pasted into separate messages: blank lines separate them, and so do lines that each hold an amount. */
    fun splitPasted(text: String): List<String> {
        val chunks = text.split(Regex("""\r?\n\s*\r?\n""")).map { it.trim() }.filter { it.isNotEmpty() }
        return chunks.flatMap { chunk ->
            val lines = chunk.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val withAmount = lines.count { amountRegex.containsMatchIn(it) }
            if (lines.size > 1 && withAmount >= 2 && withAmount == lines.size) lines else listOf(chunk.replace(Regex("""\s*\r?\n\s*"""), " "))
        }
    }

    private fun reject(r: Rejection) = ParseResult.Rejected(r)

    private fun findAmount(text: String): Long? {
        for (m in amountRegex.findAll(text)) {
            val before = text.substring(maxOf(0, m.range.first - 22), m.range.first)
            if (balanceBefore.containsMatchIn(before)) continue
            toPaise(m.groupValues[1])?.let { return it }
        }
        for (m in bareAmount.findAll(text)) {
            val before = text.substring(maxOf(0, m.range.first - 22), m.range.first)
            if (balanceBefore.containsMatchIn(before)) continue
            toPaise(m.groupValues[1])?.let { return it }
        }
        return null
    }

    private fun toPaise(s: String): Long? {
        val v = runCatching { BigDecimal(s.replace(",", "")) }.getOrNull() ?: return null
        val paise = v.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
        return paise.takeIf { it in 1..MAX_PAISE }
    }

    private fun findDirection(text: String): Direction? {
        val d = debitWord.find(text)?.range?.first
        val c = creditWord.find(text)?.range?.first
        return when {
            d != null && c != null -> if (d <= c) Direction.Debit else Direction.Credit
            d != null -> Direction.Debit
            c != null -> Direction.Credit
            cardUse.containsMatchIn(text) -> Direction.Debit
            else -> null
        }
    }

    private fun findRef(text: String): String? {
        for (p in refPatterns) {
            for (m in p.findAll(text)) {
                val v = m.groupValues[1]
                if (v.count { it.isDigit() } >= 4 && v.length >= 6) return v.uppercase()
            }
        }
        upiSlash.find(text)?.let { return it.groupValues[1].uppercase() }
        return null
    }

    private val stop = """(?=\s+on\s|\s+dated\b|\s+dt\b|\s+ref|\s+upi|\s+utr|\s+via\b|\s+thru\b|\s+avl|\s+bal|\s+from\b|\s+using\b|\s+is\b|\s+has\b|\s+credited|\s+debited|\s+txn|\s+not\s+you|\s+if\s|\s+call|\s+sms|\s*\(|\s*[,;]|\.\s|\.$|$)"""
    private val debitTargets = listOf(
        Regex("""(?i)\b(?:autopay|auto-?pay|e-?mandate|mandate)\s+(?:for|to)\s+(.{2,50}?)(?=\s+mandate|\s+on\s|\.\s|\.$|,|$)"""),
        Regex("""(?i)\b(?:thank you for using.{0,60}?\bfor\s+(?:rs\.?|inr|₹)\s*[\d,.]+\s+at)\s+(.{2,50}?)$stop"""),
        Regex("""(?i)\b(?:to|towards|trf to|transfer to)\s+(?!your\b|a/c\b|acct\b|account\b|card\b|vpa\b|rs\b|inr\b|₹|\d)(.{2,50}?)$stop"""),
        Regex("""(?i)\bat\s+(?!atm\b)(.{2,50}?)$stop"""),
        Regex("""(?i); ?(.{2,40}?)\s+credited\b"""),
        Regex("""(?i)\binfo[:\-]\s*(.{2,50}?)$stop"""),
        Regex("""(?i)\d{1,2}:\d{2}(?::\d{2})?\s+(?!avl|sms|not\b)([A-Za-z][A-Za-z0-9 &.'\-]{2,40}?)\s+(?:avl|avail|sms|not\b|call|if\b)"""),
    )
    private val creditTargets = listOf(
        Regex("""(?i)\b(?:from|by)\s+(?!your\b|a/c\b|acct\b|account\b|card\b|vpa\b|a\.c\b|neft\b|imps\b|upi\b|rs\b|inr\b|₹|\d)(.{2,50}?)$stop"""),
        Regex("""(?i)\b(?:refund|order|cashback)\b.{0,40}?\bat\s+(.{2,40}?)$stop"""),
    )

    private fun findMerchant(text: String, direction: Direction, vpaMatch: MatchResult?): String? {
        upiSlash.find(text)?.let { m -> cleanName(m.groupValues[2])?.let { return it } }
        val named = Regex("""(?i)\b(?:NEFT|RTGS|IMPS)[\-/ ]([A-Z0-9]{6,})[\-/ ]([A-Za-z][A-Za-z .&]{2,40}?)(?=\s*(?:\.|,|$|-\s|\s+Ref|\s+UTR))""").find(text)
        named?.let { cleanName(it.groupValues[2])?.let { n -> return n } }
        if (vpaMatch != null) nameFromVpa(vpaMatch.groupValues[1])?.let { return it }
        val targets = if (direction == Direction.Debit) debitTargets else creditTargets
        for (p in targets) {
            p.find(text)?.let { m -> cleanName(m.groupValues[1])?.let { return it } }
        }
        if (vpaMatch != null) nameFromVpa(vpaMatch.groupValues[1])?.let { return it }
        return null
    }

    private fun nameFromVpa(local: String): String? {
        val tokens = local.lowercase().split(Regex("""[._\-]+""")).map { it.replace(Regex("""\d+$"""), "") }.filter { it.length >= 2 && it !in noiseTokens }
        if (tokens.isEmpty()) return null
        if (Regex("""(?i)^(paytmqr|paytm\.?s|q)\d""").containsMatchIn(local)) return null
        if (tokens.all { it.all { ch -> ch.isDigit() } }) return null
        if (local.take(8).all { it.isDigit() } && local.length >= 10) return null
        return titleCase(tokens.take(2).joinToString(" "))
    }

    /** Cleans a raw merchant or payee fragment into a short title-cased name, or null when nothing useful is left. */
    internal fun cleanName(raw: String): String? {
        var s = raw
        s = s.replace(Regex("""(?i)\b(vpa|upi id|upi|ref(?:erence)?|utr|rrn|imps|neft|rtgs|p2[map]|txn|id|no)\b[:.\-]?"""), " ")
        s = s.replace(Regex("""[A-Za-z0-9._\-]+@[A-Za-z0-9]+"""), " ")
        s = s.replace(Regex("""\b\d{6,}\b"""), " ")
        s = s.replace(Regex("""(?i)\b(pvt\.?|private|ltd\.?|limited|llp|inc\.?)\b\.?"""), " ")
        s = s.replace(Regex("""[^A-Za-z0-9 &'.\-]"""), " ").replace(Regex("""\s+"""), " ").trim(' ', '.', '-', '&')
        if (s.length < 2 || s.none { it.isLetter() }) return null
        if (Regex("""(?i)^(a/?c|acct|account|bank|your|the|you|me|self|xx+\d*)\b""").containsMatchIn(s)) return null
        return titleCase(s).take(40).trim()
    }

    private fun titleCase(s: String): String = s.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { w ->
        val lower = w.lowercase()
        when {
            lower in knownCaps -> lower.uppercase()
            else -> lower.replaceFirstChar { it.uppercase() }
        }
    }

    private fun bankFor(sender: String?, text: String): String? {
        val key = sender?.uppercase()?.substringAfter('-')?.substringBefore('-')
        if (key != null) bankByKey.entries.firstOrNull { key.contains(it.key) }?.let { return it.value }
        val b = bodyBank.find(text)?.groupValues?.get(1) ?: return null
        val norm = b.uppercase().replace(" ", "")
        return bankByKey.entries.firstOrNull { norm.startsWith(it.key) || it.key.startsWith(norm) }?.value ?: (titleCase(b) + " Bank")
    }

    private fun findInstant(text: String, receivedAt: Long, zone: ZoneId): Pair<Long, Boolean> {
        val received = java.time.Instant.ofEpochMilli(receivedAt).atZone(zone)
        val date = findDate(text)
        val time = timeRegex.find(text)?.let { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].ifEmpty { "0" }.toInt()) }
        if (date == null || date.isAfter(received.toLocalDate().plusDays(1)) || date.isBefore(received.toLocalDate().minusDays(400))) {
            return receivedAt to false
        }
        val t = time ?: if (date == received.toLocalDate()) received.toLocalTime().withNano(0) else LocalTime.NOON
        return LocalDateTime.of(date, t).atZone(zone).toInstant().toEpochMilli() to true
    }

    private fun findDate(text: String): LocalDate? {
        val cands = ArrayList<Pair<Int, LocalDate>>()
        dateIso.findAll(text).forEach { m -> safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { cands += m.range.first to it } }
        dateNumeric.findAll(text).forEach { m ->
            val y = m.groupValues[3].let { if (it.length == 2) 2000 + it.toInt() else it.toInt() }
            safeDate(y, m.groupValues[2].toInt(), m.groupValues[1].toInt())?.let { cands += m.range.first to it }
        }
        dateMonth.findAll(text).forEach { m ->
            val y = m.groupValues[3].let { if (it.length == 2) 2000 + it.toInt() else it.toInt() }
            safeDate(y, monthNames.getValue(m.groupValues[2].lowercase()), m.groupValues[1].toInt())?.let { cands += m.range.first to it }
        }
        return cands.minByOrNull { it.first }?.second
    }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate.of(y, m, d) }.getOrNull()
}
