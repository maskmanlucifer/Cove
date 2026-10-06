package app.cove.companion.feature.alarms

import app.cove.companion.data.local.entity.AlarmEntity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AlarmMirrorTest {
    @get:Rule val tmp = TemporaryFolder()

    private val wake = AlarmEntity("a", "Wake up", 390, 0b1111111, "wake", "Chime", false, 5)
    private val off = AlarmEntity("b", "Gym", 420, 0b0000011, enabled = false)
    private val gone = AlarmEntity("c", "Old", 500, 0, deletedAt = 9)

    @Test fun roundTripKeepsEveryRingField() {
        val m = AlarmMirror(File(tmp.root, "m.json"))
        m.write(listOf(wake, off, gone))
        val back = m.read()
        assertEquals(listOf("a"), back.map { it.id })
        assertEquals(wake, back.single().toEntity())
        assertEquals(390, m.find("a")!!.minutes)
        assertEquals(5, m.find("a")!!.snoozeMinutes)
        assertNull(m.find("b"))
    }

    @Test fun missingOrBrokenFileReadsAsEmpty() {
        assertTrue(AlarmMirror(File(tmp.root, "none.json")).read().isEmpty())
        assertTrue(AlarmMirror(File(tmp.root, "bad.json").apply { writeText("{nope") }).read().isEmpty())
    }

    @Test fun removeDropsOnlyThatAlarm() {
        val m = AlarmMirror(File(tmp.root, "m.json"))
        m.write(listOf(wake, wake.copy(id = "z")))
        m.remove("a")
        assertEquals(listOf("z"), m.ids())
    }

    @Test fun newerFilesWithExtraFieldsStayReadable() {
        val json = """[{"id":"a","label":"x","minutes":1,"daysMask":0,"kind":"wake","sound":"s","gentleRise":true,"snoozeMinutes":9,"enabled":true,"future":1}]"""
        assertEquals("a", AlarmMirror.decode(json).single().id)
    }
}
