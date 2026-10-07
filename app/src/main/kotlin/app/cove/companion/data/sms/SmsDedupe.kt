package app.cove.companion.data.sms

import app.cove.companion.data.categorize.CategoryTokens
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** What is recorded in `sms_import_log.outcome`. */
object SmsImportOutcome {
    const val IMPORTED = "imported"
    const val DUPLICATE = "duplicate"
    const val IGNORED = "ignored"
    const val SKIPPED = "skipped"

    /** Found by live capture and waiting for the user; the details are in `sms_pending`. */
    const val PENDING = "pending"
}

/** A message as read from the inbox or pasted. The text lives only in memory while it is parsed. */
data class SmsMessage(val providerId: Long?, val sender: String?, val body: String, val receivedAt: Long)

/** Identity of one message: its hash key and, when it came from the inbox, the provider row id. */
data class MessageId(val key: String, val providerId: Long?)

/** The fields two records are compared on to decide whether they describe one payment. */
data class TxSig(val amountPaise: Long, val direction: Direction, val last4: String?, val merchant: String?, val ref: String?, val at: Long)

/** One transaction found in one or more messages, before the user decides. Holds no message text. */
data class Candidate(val tx: ParsedSms, val messages: List<MessageId>) {
    /** Stable id used by the UI: the first message's key. */
    val id: String get() = messages.first().key
    val sig: TxSig get() = tx.toSig()
}

fun ParsedSms.toSig() = TxSig(amountPaise, direction, last4, merchant, ref, at)

/** An existing expense (manual, voice or imported) the importer compares candidates with. */
data class ExistingExpense(val id: String, val amountPaise: Long, val kind: String, val spentAt: Long, val note: String, val externalRef: String?)

/** The expense a candidate seems to duplicate. [exact] means same reference, or same amount, day and merchant. */
data class ExistingMatch(val expenseId: String, val note: String, val amountPaise: Long, val at: Long, val exact: Boolean)

/** A decided message from `sms_import_log`, as far as comparison is concerned. */
data class LoggedTx(val sig: TxSig, val outcome: String)

/** Rules that stop the same payment being imported twice. Pure, so every rule is unit-tested; see `docs/SMS_IMPORT.md`. */
object SmsDedupe {
    /** Messages for one payment from different senders arrive within this window. */
    const val SAME_PAYMENT_WINDOW_MS = 10 * 60_000L

    /** How far from a candidate an existing expense may lie to be flagged as a possible duplicate. */
    const val EXISTING_WINDOW_MS = 36 * 3_600_000L

    private val spaces = Regex("""\s+""")

    /** Hash key of a message: SHA-256 of the upper-cased sender and the case- and space-normalised body. */
    fun messageKey(sender: String?, body: String): String {
        val norm = (sender?.trim()?.uppercase().orEmpty()) + "|" + body.lowercase().replace(spaces, " ").trim()
        val digest = MessageDigest.getInstance("SHA-256").digest(norm.toByteArray(Charsets.UTF_8))
        return "msg:" + digest.joinToString("") { "%02x".format(it) }
    }

    /** Key of a log row rebuilt from an expense that has a reference. */
    fun refKey(ref: String) = "ext:$ref"

    /** True when two merchant or note texts share a word (up to a plural) or one contains the other. */
    fun similarText(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val ta = CategoryTokens.tokens(a)
        val tb = CategoryTokens.tokens(b)
        if (ta.any { x -> tb.any { y -> CategoryTokens.same(x, y) } }) return true
        val ja = ta.joinToString("")
        val jb = tb.joinToString("")
        return ja.length >= 4 && jb.length >= 4 && (ja.contains(jb) || jb.contains(ja))
    }

    /**
     * Whether [a] and [b] describe one payment: same direction and amount, and the same reference; or, with no
     * conflicting reference, within [SAME_PAYMENT_WINDOW_MS] with the same last four digits or a similar merchant
     * and no contradiction in either.
     */
    fun sameTransaction(a: TxSig, b: TxSig): Boolean {
        if (a.direction != b.direction || a.amountPaise != b.amountPaise) return false
        if (a.ref != null && b.ref != null) return a.ref == b.ref
        if (abs(a.at - b.at) > SAME_PAYMENT_WINDOW_MS) return false
        val digitsAgree = a.last4 != null && a.last4 == b.last4
        val digitsClash = a.last4 != null && b.last4 != null && a.last4 != b.last4
        val nameAgree = similarText(a.merchant, b.merchant)
        val nameClash = !a.merchant.isNullOrBlank() && !b.merchant.isNullOrBlank() && !nameAgree
        if (digitsClash || (nameClash && !digitsAgree)) return false
        return digitsAgree || nameAgree
    }

    private fun richness(t: ParsedSms) =
        (if (t.ref != null) 3 else 0) + (if (t.merchant != null) 3 else 0) + (if (t.last4 != null) 1 else 0) + (if (t.dateFromText) 1 else 0) + t.confidence

    /**
     * Collapses candidates that are the same payment (see [sameTransaction]; a bank text and a UPI-app text)
     * into one, keeping the richer record and filling its gaps from the other. All message ids are kept so every
     * message gets logged. The result is ordered newest first.
     */
    fun collapse(candidates: List<Candidate>): List<Candidate> {
        val groups = ArrayList<Candidate>()
        val byRef = HashMap<String, Int>()
        for (c in candidates.sortedBy { it.tx.at }) {
            var idx = c.tx.ref?.let { r -> byRef[r + c.tx.direction + c.tx.amountPaise] }
            if (idx == null) {
                var i = groups.size - 1
                while (i >= 0 && groups[i].tx.at >= c.tx.at - SAME_PAYMENT_WINDOW_MS) {
                    if (sameTransaction(groups[i].sig, c.sig)) { idx = i; break }
                    i--
                }
            }
            if (idx == null) {
                groups += c
                c.tx.ref?.let { byRef[it + c.tx.direction + c.tx.amountPaise] = groups.size - 1 }
            } else {
                groups[idx] = merge(groups[idx], c)
                groups[idx].tx.ref?.let { byRef[it + c.tx.direction + c.tx.amountPaise] = idx }
            }
        }
        return groups.sortedByDescending { it.tx.at }
    }

    private fun merge(a: Candidate, b: Candidate): Candidate {
        val (win, lose) = if (richness(b.tx) > richness(a.tx)) b to a else a to b
        val w = win.tx
        val l = lose.tx
        val tx = w.copy(
            merchant = w.merchant ?: l.merchant, last4 = w.last4 ?: l.last4, ref = w.ref ?: l.ref, bank = w.bank ?: l.bank, payeeKey = w.payeeKey ?: l.payeeKey,
            confidence = maxOf(w.confidence, l.confidence),
        )
        return Candidate(tx, (win.messages + lose.messages).distinctBy { it.key })
    }

    /** Ids of [candidates] that [logged] decisions already cover (same reference, or the same payment). */
    fun coveredByLog(candidates: List<Candidate>, logged: List<LoggedTx>): Set<String> =
        candidates.filter { c -> logged.any { l -> sameTransaction(c.sig, l.sig) } }.map { it.id }.toSet()

    /**
     * Finds, for each candidate, the existing expense it probably repeats. Same reference always matches; otherwise
     * the expense must have the same direction and amount within [EXISTING_WINDOW_MS], and is [ExistingMatch.exact]
     * on the same local day with a similar merchant. Each expense is matched at most once, so two real payments of
     * the same amount next to one manual entry leave one candidate unflagged.
     */
    fun matchExisting(candidates: List<Candidate>, existing: List<ExistingExpense>, zone: ZoneId = ZoneId.systemDefault()): Map<String, ExistingMatch> {
        val taken = HashSet<String>()
        val out = HashMap<String, ExistingMatch>()
        fun toMatch(e: ExistingExpense, exact: Boolean) = ExistingMatch(e.id, e.note, e.amountPaise, e.spentAt, exact)
        for (c in candidates) {
            val ref = c.tx.ref ?: continue
            val e = existing.firstOrNull { it.externalRef == ref && it.id !in taken } ?: continue
            taken += e.id
            out[c.id] = toMatch(e, true)
        }
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        for (c in candidates.sortedBy { it.tx.at }) {
            if (c.id in out) continue
            val kind = if (c.tx.direction == Direction.Debit) "spent" else "received"
            val pool = existing.filter { it.id !in taken && it.amountPaise == c.tx.amountPaise && it.kind == kind && abs(it.spentAt - c.tx.at) <= EXISTING_WINDOW_MS }
            if (pool.isEmpty()) continue
            fun exact(e: ExistingExpense) = day(e.spentAt) == day(c.tx.at) && similarText(e.note, c.tx.merchant)
            val best = pool.sortedWith(compareByDescending<ExistingExpense> { exact(it) }.thenBy { abs(it.spentAt - c.tx.at) }).first()
            taken += best.id
            out[c.id] = toMatch(best, exact(best))
        }
        return out
    }
}
