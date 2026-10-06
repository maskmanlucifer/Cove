package app.cove.companion.feature.plan

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleInputTest {
    @Test fun lineBreaksAreRemoved() = assertEquals("Buy milk", titleInput("Buy milk\n"))

    @Test fun leadingSpacesAreDropped() = assertEquals("Buy milk", titleInput("   Buy milk"))

    @Test fun inputIsCappedAtTheLimit() = assertEquals(TITLE_MAX, titleInput("x".repeat(500)).length)
}
