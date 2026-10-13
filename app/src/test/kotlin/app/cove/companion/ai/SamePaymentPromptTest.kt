package app.cove.companion.ai

import app.cove.companion.ai.model.SamePair
import app.cove.companion.ai.model.SameRequest
import app.cove.companion.ai.prompt.SamePaymentPrompt
import app.cove.companion.ai.schema.SamePaymentSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SamePaymentPromptTest {
    @Test fun onlyNamesAndTimesLeaveThePhone() {
        val user = SamePaymentPrompt.user(SameRequest(listOf(SamePair("Swiggy ₹340", "Tue 13 Oct, 1:20 pm", "Lunch 340 rs", "Tue 13 Oct, 1:05 pm"))))
        assertFalse(user, user.contains("340"))
        assertFalse(user.contains("₹"))
        assertEquals("""{"items":[{"p":"Swiggy","pt":"Tue 13 Oct, 1:20 pm","e":"Lunch","et":"Tue 13 Oct, 1:05 pm"}]}""", user)
    }

    @Test fun replyMustBeOneZeroOrOnePerPair() {
        assertEquals(listOf(true, false), SamePaymentSchema.parse("""{"a":[1,0]}""", 2))
        assertEquals(listOf(true), SamePaymentSchema.parse("```json\n{\"a\":[1]}\n```", 1))
        assertNull("wrong length", SamePaymentSchema.parse("""{"a":[1]}""", 2))
        assertNull("not 0 or 1", SamePaymentSchema.parse("""{"a":[2]}""", 1))
        assertNull("strings", SamePaymentSchema.parse("""{"a":["1"]}""", 1))
        assertNull("not json", SamePaymentSchema.parse("yes", 1))
    }
}
