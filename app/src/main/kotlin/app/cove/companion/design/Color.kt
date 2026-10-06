package app.cove.companion.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colour tokens from the design's style sheet (light "canvas" and dark "night"). */
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
    val hairline: Color,
    val dockInactive: Color,
    val alert: Color,
    val saved: Color,
    val scrim: Color,
    val isDark: Boolean,
)

val LightColors = CoveColors(
    canvas = Color(0xFFF4F4F2),
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF16171A),
    onInk = Color(0xFFF4F4F2),
    muted = Color(0xFF6B6D73),
    tail = Color(0xFF8E9096),
    placeholder = Color(0xFFA4A6AB),
    well = Color(0xFFF0F0EE),
    wellStrong = Color(0xFFE9E9E6),
    hairline = Color(0xFFEDEDEA),
    dockInactive = Color(0xFF6B6D73),
    alert = Color(0xFFB5562F),
    saved = Color(0xFF3F7A5A),
    scrim = Color(0x2E16171A),
    isDark = false,
)

val DarkColors = CoveColors(
    canvas = Color(0xFF111113),
    card = Color(0xFF1B1B1E),
    ink = Color(0xFFEDEDEF),
    onInk = Color(0xFF111113),
    muted = Color(0xFF9A9CA3),
    tail = Color(0xFF6B6D73),
    placeholder = Color(0xFF6A6C72),
    well = Color(0xFF26262A),
    wellStrong = Color(0xFF2C2D31),
    hairline = Color(0xFF26262A),
    dockInactive = Color(0xFF7A7C83),
    alert = Color(0xFFE08A64),
    saved = Color(0xFF6DB38E),
    scrim = Color(0x99000000),
    isDark = true,
)

/** Voice orb gradient stops (peach, lilac, sky over a pale base). */
object OrbColors {
    val Peach = Color(0xFFF6CDB0)
    val Lilac = Color(0xFFC7BAF2)
    val Sky = Color(0xFFA8D6EA)
    val Base = Color(0xFFEDE8F4)
}

val LocalCoveColors = staticCompositionLocalOf { LightColors }
