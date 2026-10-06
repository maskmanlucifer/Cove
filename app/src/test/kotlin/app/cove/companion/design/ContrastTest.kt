package app.cove.companion.design

import androidx.compose.ui.graphics.Color
import java.io.File
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG 2.x checks for the palette, plus token completeness and the no-hard-coded-colour rule for feature code. */
class ContrastTest {
    private fun lin(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

    private fun luminance(c: Color): Double = 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)

    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = luminance(a).coerceAtLeast(luminance(b)) to luminance(a).coerceAtMost(luminance(b))
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(name: String, fg: Color, bg: Color, min: Double, failures: MutableList<String>) {
        val r = ratio(fg, bg)
        if (r < min) failures += "$name %.2f < $min".format(r)
    }

    private fun failures(c: CoveColors): List<String> {
        val f = mutableListOf<String>()
        val surfaces = mapOf("canvas" to c.canvas, "card" to c.card, "well" to c.well)
        for ((n, bg) in surfaces) {
            check("ink/$n", c.ink, bg, 7.0, f)
            check("muted/$n", c.muted, bg, 4.5, f)
            check("accent/$n", c.accent, bg, 4.5, f)
            check("alert/$n", c.alert, bg, 4.5, f)
            check("saved/$n", c.saved, bg, 4.5, f)
        }
        check("tail/canvas", c.tail, c.canvas, 3.0, f)
        check("dockInactive/card", c.dockInactive, c.card, 4.5, f)
        check("onInk/ink", c.onInk, c.ink, 7.0, f)
        check("onAccent/accent", c.onAccent, c.accent, 4.5, f)
        check("accent/accentSoft", c.accent, c.accentSoft, 4.5, f)
        check("ink/accentSoft", c.ink, c.accentSoft, 7.0, f)
        check("ink/wellStrong", c.ink, c.wellStrong, 7.0, f)
        check("muted/wellStrong", c.muted, c.wellStrong, 4.5, f)
        check("placeholder/card", c.placeholder, c.card, 3.0, f)
        for (bg in listOf(c.card, c.canvas)) {
            check("ring", c.ring, bg, 3.0, f)
            check("switchOff", c.switchOff, bg, 3.0, f)
        }
        for ((i, h) in c.hues.withIndex()) {
            check("ink/hue$i.tint", c.ink, h.tint, 7.0, f)
            check("muted/hue$i.tint", c.muted, h.tint, 4.5, f)
            check("accent/hue$i.tint", c.accent, h.tint, 4.5, f)
            check("hue$i.strong dot/card", h.strong, c.card, if (c.isDark) 3.0 else 1.5, f)
        }
        return f
    }

    @Test fun lightPairsPass() = assertTrue(failures(LightColors).toString(), failures(LightColors).isEmpty())

    @Test fun darkPairsPass() = assertTrue(failures(DarkColors).toString(), failures(DarkColors).isEmpty())

    @Test fun hueSetIsCompleteAndDistinct() {
        for (c in listOf(LightColors, DarkColors)) {
            assertEquals(HueName.entries.size, c.hues.size)
            assertEquals(c.hues.size, c.hues.map { it.strong }.toSet().size)
            assertEquals(c.hues.size, c.hues.map { it.tint }.toSet().size)
        }
        assertNotEquals(LightColors.canvas, DarkColors.canvas)
    }

    @Test fun hueAssignmentIsDeterministicAndSpread() {
        val keys = listOf("Home", "Shopping", "Personal", "Errands", "Work", "Health", "Ideas", "Travel")
        assertEquals(keys.map { LightColors.hueFor(it) }, keys.map { LightColors.hueFor(it) })
        assertTrue(keys.map { LightColors.hueFor(it) }.toSet().size >= 3)
        assertTrue(keys.none { LightColors.hueFor(it) == LightColors.hue(HueName.Sky) })
        assertEquals(LightColors.hues.indexOf(LightColors.hueFor("Home")), DarkColors.hues.indexOf(DarkColors.hueFor("Home")))
    }

    @Test fun featureCodeHasNoHardCodedColours() {
        val allowed = setOf("PhotoViewer.kt", "JournalPhotos.kt", "ProfileCropper.kt")
        val bad = File("src/main/kotlin/app/cove/companion/feature").walkTopDown()
            .filter { it.extension == "kt" && it.name !in allowed }
            .filter { Regex("Color\\(0x|Color\\.parseColor").containsMatchIn(it.readText()) }
            .map { it.name }.toList()
        assertTrue("Use Cove.colors tokens instead of literals in: $bad", bad.isEmpty())
    }
}
