package app.cove.companion.ai.prompt

import app.cove.companion.ai.model.SameRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Prompt for deciding whether a payment found in a bank message repeats an expense the user already logged. Each item
 * holds two names and two local times only: amounts are scrubbed (they already matched on the phone) and no message
 * text is sent. The reply is `{"a":[1,0,...]}`: 1 when it is the same payment, 0 when not.
 */
object SamePaymentPrompt {
    const val SYSTEM = "Each item pairs a payment seen in a bank message (\"p\", with time \"pt\") with an expense the user logged " +
        "by hand (\"e\", with time \"et\"). The amounts are equal. Decide whether both describe the same purchase, e.g. " +
        "\"Swiggy\" and \"Lunch\" a few minutes apart are the same, \"Uber\" and \"Groceries\" are not. " +
        "Reply with JSON only: {\"a\":[...]} with exactly one 1 (same) or 0 (different) per item, in order."

    /** The user turn as JSON; names go through [CategoryPrompt.scrub], so no amount ever leaves the phone. */
    fun user(request: SameRequest): String = buildJsonObject {
        put("items", JsonArray(request.pairs.map { p ->
            buildJsonObject {
                put("p", JsonPrimitive(CategoryPrompt.scrub(p.payment)))
                put("pt", JsonPrimitive(p.paymentTime))
                put("e", JsonPrimitive(CategoryPrompt.scrub(p.existing)))
                put("et", JsonPrimitive(p.existingTime))
            }
        }))
    }.toString()
}
