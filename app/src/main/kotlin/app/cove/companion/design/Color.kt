package app.cove.companion.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colour tokens of the "calm garden" palette (light "paper" and dark "moss"); values are documented in `docs/DESIGN.md`. */
@Immutable
data class CoveColors(
    val canvas: Color,
    val card: Color,
    val ink: Color,
    val onInk: Color,
    val muted: Color,
    val tail: Color,
    val placeholder: Color,
    val well: Color,
    val wellStrong: Color,
    /** Off-state switch track; at least 3:1 against card and canvas (WCAG 1.4.11). */
    val switchOff: Color,
    val hairline: Color,
    val dockInactive: Color,
    val alert: Color,
    val saved: Color,
    val scrim: Color,
    /** Leaf green for tappable secondary actions, active nav and selected states; ink stays for primary buttons. */
    val accent: Color,
    val accentSoft: Color,
    /** Text and icons on a filled [accent]. */
    val onAccent: Color,
    /** Functional outline of an empty checkbox or ring; at least 3:1 against card and canvas. */
    val ring: Color,
    /** Decorative pale line (unfilled chart dots, dividers on art); no contrast requirement. */
    val quiet: Color,
    /** Warm brown-olive tint for elevation shadows. */
    val shadow: Color,
    /** The warm highlight set in a fixed order, see [Hue]. */
    val hues: List<Hue>,
    val isDark: Boolean,
)

/**
 * A warm highlight: [strong] for dots, bars and lines (non-text, 3:1 on card), [tint] for a quiet surface
 * that ink text stays readable on.
 */
@Immutable
data class Hue(val strong: Color, val tint: Color)

/** Names of the highlight hues, in the order of [CoveColors.hues]. */
enum class HueName { Sun, Coral, Lilac, Blossom, Leaf, Sky }

/** Highlight hue by [name]. */
fun CoveColors.hue(name: HueName): Hue = hues[name.ordinal]

/**
 * Deterministic hue for [key] (category id, habit name): the same key always gets the same hue.
 * Sky is left out of the rotation; it is for explicit, sparing use.
 */
fun CoveColors.hueFor(key: String): Hue = hues[PaletteHues[Math.floorMod(stableHash(key), PaletteHues.size)].ordinal]

/** Hue of a mood word (calm leaf, good sun, tired lilac, low coral); anything else falls back to [hueFor]. */
fun CoveColors.moodHue(mood: String): Hue = when (mood.lowercase()) {
    "calm" -> hue(HueName.Leaf)
    "good" -> hue(HueName.Sun)
    "tired" -> hue(HueName.Lilac)
    "low" -> hue(HueName.Coral)
    else -> hueFor(mood)
}

private val PaletteHues = listOf(HueName.Leaf, HueName.Sun, HueName.Coral, HueName.Lilac, HueName.Blossom)

/** FNV-1a so hue assignment never depends on the JVM's `hashCode`. */
private fun stableHash(s: String): Int {
    var h = 0x811C9DC5.toInt()
    for (ch in s) { h = (h xor ch.code) * 16777619 }
    return h
}

val LightColors = CoveColors(
    canvas = Color(0xFFF6F1E6),
    card = Color(0xFFFFFCF5),
    ink = Color(0xFF1F2B22),
    onInk = Color(0xFFF6F1E6),
    muted = Color(0xFF55614F),
    tail = Color(0xFF7D8874),
    placeholder = Color(0xFF858F7F),
    well = Color(0xFFECE6D6),
    wellStrong = Color(0xFFE3DCC8),
    switchOff = Color(0xFF7C8776),
    hairline = Color(0xFFE9E3D2),
    dockInactive = Color(0xFF55614F),
    alert = Color(0xFFA34824),
    saved = Color(0xFF336E4E),
    scrim = Color(0x2E1F2B22),
    accent = Color(0xFF2F6B45),
    accentSoft = Color(0xFFE1EEDA),
    onAccent = Color(0xFFFFFCF5),
    ring = Color(0xFF7C8776),
    quiet = Color(0xFFD9D4BF),
    shadow = Color(0x1F3A3420),
    hues = listOf(
        Hue(Color(0xFFF2C14E), Color(0xFFFBEBC2)),
        Hue(Color(0xFFEE8B60), Color(0xFFFBE0D2)),
        Hue(Color(0xFFB9A8E6), Color(0xFFE9E3F8)),
        Hue(Color(0xFFE98FB0), Color(0xFFFADCE7)),
        Hue(Color(0xFF7DB36B), Color(0xFFE1EEDA)),
        Hue(Color(0xFF9CCFE0), Color(0xFFDDEFF5)),
    ),
    isDark = false,
)

val DarkColors = CoveColors(
    canvas = Color(0xFF121A14),
    card = Color(0xFF1B261E),
    ink = Color(0xFFEEE9D8),
    onInk = Color(0xFF121A14),
    muted = Color(0xFFA5B09E),
    tail = Color(0xFF7A8674),
    placeholder = Color(0xFF8A9684),
    well = Color(0xFF25322A),
    wellStrong = Color(0xFF2D3B32),
    switchOff = Color(0xFF66745F),
    hairline = Color(0xFF26332A),
    dockInactive = Color(0xFF93A08C),
    alert = Color(0xFFE08A64),
    saved = Color(0xFF6DB38E),
    scrim = Color(0x99000000),
    accent = Color(0xFF9ED08F),
    accentSoft = Color(0xFF243A2A),
    onAccent = Color(0xFF121A14),
    ring = Color(0xFF66745F),
    quiet = Color(0xFF3A4A3F),
    shadow = Color(0x66000000),
    hues = listOf(
        Hue(Color(0xFFD9AE4A), Color(0xFF3A3420)),
        Hue(Color(0xFFE0866A), Color(0xFF3D2B24)),
        Hue(Color(0xFFA99BDB), Color(0xFF2E2A40)),
        Hue(Color(0xFFD98AA8), Color(0xFF3B2630)),
        Hue(Color(0xFF86BA76), Color(0xFF24392A)),
        Hue(Color(0xFF7DB5C8), Color(0xFF20343B)),
    ),
    isDark = true,
)

/** Voice orb gradient stops: a warm garden blend (peach, sun, leaf, lilac) over a cream base. */
object OrbColors {
    val Peach = Color(0xFFF6CDB0)
    val Sun = Color(0xFFF5DC9A)
    val Leaf = Color(0xFFB9D8A6)
    val Lilac = Color(0xFFD3C6EE)
    val Base = Color(0xFFF3EBD8)

    /** Kept for scene art that still draws a sky. */
    val Sky = Color(0xFFA8D6EA)
}

val LocalCoveColors = staticCompositionLocalOf { LightColors }
