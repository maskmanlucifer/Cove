package app.cove.companion.feature.training

import app.cove.companion.feature.training.engine.BodyWeightInput
import app.cove.companion.feature.training.engine.ChartMath
import app.cove.companion.feature.training.engine.ChartRange
import app.cove.companion.feature.training.engine.WeightFormat
import app.cove.companion.feature.training.engine.WeightUnit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartAndInputTest {
    @Test fun keypadBuildsAWeightWithOneDecimal() {
        var t = ""
        "68.4".forEach { t = BodyWeightInput.push(t, it) }
        assertEquals("68.4", t)
        assertEquals("68.4", BodyWeightInput.push(t, '9'))
        assertEquals("68", BodyWeightInput.back(t).dropLast(1))
        assertEquals(68.4, BodyWeightInput.value(t, WeightUnit.Kg)!!, 0.0)
        assertNull(BodyWeightInput.value("5", WeightUnit.Kg))
        assertNull(BodyWeightInput.value("", WeightUnit.Kg))
        assertEquals("0.", BodyWeightInput.push("", '.'))
    }

    @Test fun axisCoversTheValues() {
        val a = ChartMath.axis(listOf(68.4, 69.1), 0.5)
        assert(a.lo <= 68.4 && a.hi >= 69.1)
        assertEquals(listOf(0, 1, 2), ChartMath.labelIndexes(3))
        assertEquals(emptyList<Int>(), ChartMath.labelIndexes(0))
    }

    @Test fun rangesFilterByDays() {
        val today = LocalDate.of(2026, 10, 6)
        val pts = listOf(today.minusDays(100) to 1.0, today.minusDays(40) to 2.0, today.minusDays(5) to 3.0)
        assertEquals(1, ChartRange.Month.filter(pts, today).size)
        assertEquals(2, ChartRange.ThreeMonths.filter(pts, today).size)
        assertEquals(3, ChartRange.All.filter(pts, today).size)
    }

    @Test fun weightFormat() {
        assertEquals("62.5", WeightFormat.trim(62.5))
        assertEquals("60", WeightFormat.trim(60.0))
        assertEquals("132.3 lb", WeightFormat.withUnit(60.0, WeightUnit.Lb))
        assertEquals(62.5, WeightFormat.parse("62,5")!!, 0.0)
        assertNull(WeightFormat.parse("x"))
    }
}
