package app.cove.companion.design.illustrations

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The colours illustrations are painted with: the "calm garden" palette (warm paper, leaf greens, sun yellow,
 * orange, a touch of lilac and blossom pink). The dark variant is deep moss with dimmer, slightly desaturated
 * colours. Illustrations use only these: never blue-grey, and never theme tokens, so art stays stable while the
 * app theme changes.
 *
 * @param dark true for the deep-moss variant.
 */
@Immutable
class IllusPalette(val dark: Boolean) {
    private fun c(light: Long, night: Long) = Color(if (dark) night else light)

    val paper = c(0xFFF6F1E6, 0xFF121A14)
    val warmWhite = c(0xFFFFFCF5, 0xFF1B261E)
    val ink = c(0xFF1F2B22, 0xFFEDE6D3)
    val leaf = c(0xFF7DB36B, 0xFF5E9459)
    val leafLight = c(0xFFA8D08F, 0xFF76AC69)
    val leafDeep = c(0xFF2F6B45, 0xFF2A5A3A)
    val leafSoft = c(0xFFE1EEDA, 0xFF24382A)
    val grassDark = c(0xFF3E7F4B, 0xFF1F4430)
    val sage = c(0xFFA9BFA0, 0xFF6E8568)
    val olive = c(0xFF5B6B3F, 0xFF4B5A35)
    val sun = c(0xFFF2C14E, 0xFFD9AE45)
    val sunSoft = c(0xFFFBEBC2, 0xFF4A4026)
    val orange = c(0xFFEE9B4B, 0xFFCC8240)
    val coral = c(0xFFEE8B60, 0xFFC97352)
    val coralSoft = c(0xFFFBE0D2, 0xFF4A3128)
    val lilac = c(0xFFB9A8E6, 0xFF8F84C2)
    val lilacSoft = c(0xFFE9E3F8, 0xFF332F4A)
    val pink = c(0xFFE98FB0, 0xFFC27391)
    val pinkSoft = c(0xFFFADCE7, 0xFF4A2E3A)
    val clay = c(0xFFC98B67, 0xFFA67555)
    val sky = c(0xFF9CCFE0, 0xFF6FA2B3)

    /** Dark pupils: the same on both themes so the mascot's face always reads. */
    val eye = Color(0xFF1F2B22)
    val glow = c(0xFFFFE9A8, 0xFFF2C97A)
    val shade = if (dark) Color(0xFF000000).copy(alpha = 0.34f) else Color(0xFF1F2B22).copy(alpha = 0.13f)
    val nightTop = c(0xFF352F5B, 0xFF171528)
    val nightLow = c(0xFF8A6FA8, 0xFF3A3057)
    /** Petal white: stays light in dark mode so white flowers still glow. */
    val petal = c(0xFFFFFDF7, 0xFFDDD6C0)
    /** Paper-sheet white for props (notebooks, cards, envelopes): stays light in dark mode. */
    val sheet = c(0xFFFFFCF5, 0xFFE9E2CC)
    val cream = c(0xFFFFF6E0, 0xFFE8DFC5)

    /** Light-to-dark body tones of the mascot (sage with a clay warmth). */
    val bodyLight = c(0xFFE9E3C4, 0xFFB5B38F)
    val bodyMid = c(0xFFC7C096, 0xFF979470)
    val bodyDark = c(0xFF9A9468, 0xFF6C6A4C)
    val hand = c(0xFFD9AE8A, 0xFFB08662)

    /** Mixes two palette colours. */
    fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t)
}
