package app.cove.companion.feature.widgets

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import app.cove.companion.design.DarkColors
import app.cove.companion.design.HueName
import app.cove.companion.design.LightColors
import app.cove.companion.design.hue

/** Widget colours: the app's design tokens, switching with the system theme (widgets cannot read `Cove.colors`). */
internal object WidgetColors {
    val card = ColorProvider(day = LightColors.card, night = DarkColors.card)
    val ink = ColorProvider(day = LightColors.ink, night = DarkColors.ink)
    val onInk = ColorProvider(day = LightColors.onInk, night = DarkColors.onInk)
    val muted = ColorProvider(day = LightColors.muted, night = DarkColors.muted)
    val tail = ColorProvider(day = LightColors.tail, night = DarkColors.tail)
    val placeholder = ColorProvider(day = LightColors.placeholder, night = DarkColors.placeholder)

    val accent = ColorProvider(day = LightColors.accent, night = DarkColors.accent)
    val onAccent = ColorProvider(day = LightColors.onAccent, night = DarkColors.onAccent)

    /** Quiet tinted card backgrounds: Next is sun, Tasks leaf, Spent coral. */
    val sunCard = ColorProvider(day = LightColors.hue(HueName.Sun).tint, night = DarkColors.hue(HueName.Sun).tint)
    val leafCard = ColorProvider(day = LightColors.hue(HueName.Leaf).tint, night = DarkColors.hue(HueName.Leaf).tint)
    val coralCard = ColorProvider(day = LightColors.hue(HueName.Coral).tint, night = DarkColors.hue(HueName.Coral).tint)

    /** The "Tap to talk" tile: ink in light, a raised well in dark so it stays visible on dark wallpapers. */
    val voiceCard = ColorProvider(day = LightColors.ink, night = DarkColors.wellStrong)
    val onVoiceCard = ColorProvider(day = LightColors.onInk, night = DarkColors.ink)
    val onVoiceMuted = ColorProvider(day = DarkColors.muted, night = DarkColors.muted)
}

/** Text styles taken from the frame (14 label, 17 medium title). */
internal object WidgetText {
    val label = TextStyle(fontSize = 14.sp, color = WidgetColors.muted)
    val title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = WidgetColors.ink)
}

/** The rounded card every widget sits on (28dp radius as in the frame). */
internal fun cardModifier(padding: Int, color: androidx.glance.unit.ColorProvider = WidgetColors.card, vertical: Int = padding): GlanceModifier =
    GlanceModifier.cornerRadius(28.dp).background(color).padding(horizontal = padding.dp, vertical = vertical.dp)

/** Tap action that opens the app. */
@androidx.compose.runtime.Composable
internal fun openApp(): androidx.glance.action.Action {
    val context = androidx.glance.LocalContext.current
    return androidx.glance.appwidget.action.actionStartActivity(android.content.Intent(context, app.cove.companion.MainActivity::class.java))
}
